package ru.avrora.chat

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.MutableSharedFlow
import org.json.JSONArray
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.random.Random

// ---------- «почтовый ящик»: сообщения Авроры, написанные в фоне ----------

object InboxBus {
    val flow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    fun signal() { flow.tryEmit(Unit) }
}

class Inbox(ctx: Context) {
    private val file = File(ctx.filesDir, "inbox.json")

    private fun read(): List<ChatMessage> =
        runCatching { messagesFromJson(JSONArray(file.readText())) }.getOrDefault(emptyList())

    fun peek(): List<ChatMessage> = synchronized(LOCK) { read() }

    fun add(m: ChatMessage) = synchronized(LOCK) {
        file.writeText(messagesToJson(read() + m).toString())
    }

    fun drain(): List<ChatMessage> = synchronized(LOCK) {
        val list = read()
        if (list.isNotEmpty()) file.delete()
        list
    }

    companion object {
        private val LOCK = Any()
    }
}

// ---------- уведомления ----------

object AuroraNotifications {
    private const val CHANNEL = "aurora_messages"
    private const val NOTIF_ID = 1001

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Сообщения Авроры", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    fun show(ctx: Context, text: String) {
        ensureChannel(ctx)
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            ctx,
            0,
            Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Аврора")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(NOTIF_ID, n)
        } catch (e: SecurityException) {
            // разрешение не выдано: сообщение всё равно окажется в чате
        }
    }

    fun clear(ctx: Context) {
        NotificationManagerCompat.from(ctx).cancel(NOTIF_ID)
    }
}

// ---------- планировщик ----------

class InitiativeWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        try {
            InitiativeEngine.tick(applicationContext, false)
        } catch (e: Exception) {
            // нет связи или ошибка сервера: просто попробуем в следующий раз
        }
        return Result.success()
    }
}

object InitiativeScheduler {
    private const val NAME = "aurora_initiative"

    fun apply(ctx: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(ctx)
        if (enabled) {
            val req = PeriodicWorkRequestBuilder<InitiativeWorker>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        } else {
            wm.cancelUniqueWork(NAME)
        }
    }
}

// ---------- решение: писать ли первой ----------

enum class InitiativeOutcome { SENT, SILENT, NOT_NOW, NO_CHAT, NO_TOKEN, FAILED }

private data class Rules(
    val dailyCap: Int,        // максимум сообщений в сутки
    val cooldownMin: Long,    // минимум минут между её сообщениями
    val pContinue: Double,    // вероятность попытки за проверку (раз в 30 минут)
    val pShare: Double,
    val pAbsent: Double
)

private fun rulesFor(level: Int): Rules = when (level) {
    0 -> Rules(1, 480L, 0.35, 0.03, 0.35)
    2 -> Rules(4, 120L, 0.70, 0.12, 0.70)
    else -> Rules(2, 240L, 0.50, 0.06, 0.50)
}

private fun isQuiet(hour: Int, from: Int, to: Int): Boolean = when {
    from == to -> false
    from < to -> hour >= from && hour < to
    else -> hour >= from || hour < to
}

private fun dayKey(t: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(t))

private const val HOUR_MS = 3_600_000L

object InitiativeEngine {

    /**
     * Одна проверка. Жёсткие правила (тихие часы, лимиты, паузы, остановка после трёх
     * сообщений без ответа) проверяет приложение. Есть ли настоящий повод, решает сама Аврора:
     * она может ответить «ПРОПУСК». force=true нужен только для проверки кнопкой в настройках.
     */
    suspend fun tick(ctx: Context, force: Boolean): InitiativeOutcome {
        val prefs = AppPrefs(ctx)
        val s = prefs.loadInit()
        if (!force && !s.enabled) return InitiativeOutcome.NOT_NOW
        if (prefs.token.isBlank()) return InitiativeOutcome.NO_TOKEN

        val inbox = Inbox(ctx)
        val all = ChatStore(ctx).load() + inbox.peek()
        if (all.isEmpty()) return InitiativeOutcome.NO_CHAT

        val last = all.last()
        val now = System.currentTimeMillis()
        val gapMin = (now - last.time) / 60_000L
        val unanswered = prefs.initUnanswered
        var reason = "test"
        var retryDelay = 3 * HOUR_MS

        if (!force) {
            // Она уже написала, а ответа ещё нет, либо последнее слово за мной, но без ответа Авроры
            if (last.role == "user") return InitiativeOutcome.NOT_NOW

            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            if (isQuiet(hour, s.quietFrom, s.quietTo)) return InitiativeOutcome.NOT_NOW

            val rules = rulesFor(s.level)
            val countToday = if (prefs.initDay == dayKey(now)) prefs.initDayCount else 0
            if (countToday >= rules.dailyCap) return InitiativeOutcome.NOT_NOW
            if (now - prefs.initLastAt < rules.cooldownMin * 60_000L) return InitiativeOutcome.NOT_NOW
            if (now < prefs.initNextCheck) return InitiativeOutcome.NOT_NOW
            if (unanswered >= 3) return InitiativeOutcome.NOT_NOW

            val recentlyActive = all.count { it.time > last.time - 3 * HOUR_MS } >= 4
            var p = 0.0
            when {
                unanswered == 0 && gapMin in 45L..239L && recentlyActive -> {
                    reason = "continue"; p = rules.pContinue; retryDelay = 2 * HOUR_MS
                }
                unanswered == 0 && gapMin in 240L..1319L -> {
                    reason = "share"; p = rules.pShare; retryDelay = 5 * HOUR_MS
                }
                unanswered == 0 && gapMin >= 1320L -> {
                    reason = "absent"; p = rules.pAbsent; retryDelay = 20 * HOUR_MS
                }
                unanswered == 1 && gapMin >= 1320L -> {
                    reason = "absent"; p = rules.pAbsent; retryDelay = 20 * HOUR_MS
                }
                unanswered == 2 && gapMin >= 72 * 60L -> {
                    reason = "absent"; p = rules.pAbsent; retryDelay = 24 * HOUR_MS
                }
                else -> return InitiativeOutcome.NOT_NOW
            }
            if (Random.nextDouble() > p) return InitiativeOutcome.NOT_NOW
        }

        val lib = Library(ctx)
        val (bs, bp) = lib.builtIn()
        val (us, up) = lib.user()
        val stickers = bs + us
        val photos = bp + up
        val mem = MemoryStore(ctx).load()
        val recentUser = all.filter { it.role == "user" }.takeLast(3)
            .joinToString(" ") { TAG_RE.replace(it.content, " ") }
        val history = all.filter { it.time > mem.upTo }.takeLast(30).map {
            if (it.role == "user") it.copy(content = describeForModel(it.content, stickers, photos)) else it
        }
        val localTime = SimpleDateFormat("EEEE, HH:mm", Locale("ru")).format(Date(now))

        val reply = try {
            AuroraApi.initiative(
                prefs.serverUrl, prefs.token, history, MemoryContext.build(mem, recentUser), prefs.loadPersona().compile(),
                reason, gapMin, localTime, unanswered, stickers, photos, limitLines(prefs, mem)
            )
        } catch (e: Exception) {
            return InitiativeOutcome.FAILED
        }

        if (reply == null) {
            if (!force) prefs.initNextCheck = now + retryDelay
            return InitiativeOutcome.SILENT
        }

        inbox.add(ChatMessage("assistant", reply, now, true))
        if (!force) {
            prefs.initLastAt = now
            prefs.initUnanswered = unanswered + 1
            val today = dayKey(now)
            prefs.initDayCount = (if (prefs.initDay == today) prefs.initDayCount else 0) + 1
            prefs.initDay = today
        }
        AuroraNotifications.show(ctx, notificationText(reply))
        InboxBus.signal()
        return InitiativeOutcome.SENT
    }

    private fun notificationText(reply: String): String {
        val parts = parseParts(reply)
        val text = parts.filterIsInstance<Part.Text>().joinToString(" ") { it.s }.trim()
        if (text.isNotEmpty()) return text.take(300)
        return if (parts.any { it is Part.Photo }) "Аврора прислала фото" else "Аврора прислала стикер"
    }
}
