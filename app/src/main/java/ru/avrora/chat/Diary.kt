package ru.avrora.chat

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val DIARY_HOUR_MS = 3_600_000L

/** Запись дневника. upTo: время последнего сообщения переписки, вошедшего в запись. */
data class DiaryEntry(val id: String, val at: Long, val text: String, val open: String, val upTo: Long)

class DiaryStore(ctx: Context) {
    private val file = File(ctx.filesDir, "diary.json")

    fun load(): List<DiaryEntry> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                DiaryEntry(o.getString("id"), o.optLong("at"), o.getString("text"), o.optString("open", ""), o.optLong("upTo"))
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun save(list: List<DiaryEntry>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("at", it.at).put("text", it.text).put("open", it.open).put("upTo", it.upTo))
        }
        val tmp = File(file.path + ".tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(file)
    }
}

private fun diaryDay(): SimpleDateFormat = SimpleDateFormat("d MMMM", Locale("ru"))

/** Последние записи для её запроса: так она помнит собственные мысли и видит незакрытое. */
fun diaryContext(entries: List<DiaryEntry>, count: Int = 2): String {
    val last = entries.takeLast(count)
    if (last.isEmpty()) return ""
    val fmt = diaryDay()
    return buildString {
        append("МОЙ ДНЕВНИК (последние записи, это мои мысли):\n")
        last.forEach {
            append("[").append(fmt.format(Date(it.at))).append("] ").append(it.text.take(700))
            if (it.open.isNotBlank()) append("\nХотела сказать: ").append(it.open)
            append("\n")
        }
    }.trim()
}

// ---------- когда писать ----------

enum class DiaryOutcome { WRITTEN, SKIPPED, NOT_NOW, NO_NEW, NO_TOKEN, FAILED }

object DiaryEngine {
    private val mutex = Mutex()

    /**
     * Раз в сутки вечером или ночью, если был разговор. Пишет ли она запись и что в ней, решает сама Аврора
     * (может ответить «писать нечего»). force=true нужен для кнопки «Написать сейчас».
     */
    suspend fun tick(ctx: Context, force: Boolean): DiaryOutcome = mutex.withLock {
        val prefs = AppPrefs(ctx)
        if (!force && !prefs.diaryEnabled) return@withLock DiaryOutcome.NOT_NOW
        if (prefs.token.isBlank()) return@withLock DiaryOutcome.NO_TOKEN

        val store = DiaryStore(ctx)
        val entries = store.load()
        val last = entries.lastOrNull()
        val all = ChatStore(ctx).load() + Inbox(ctx).peek()
        val fresh = all.filter { it.time > (last?.upTo ?: 0L) }
        val talked = fresh.count { it.role == "user" }
        val now = System.currentTimeMillis()

        if (force) {
            if (talked == 0) return@withLock DiaryOutcome.NO_NEW
        } else {
            if (talked < 3) return@withLock DiaryOutcome.NO_NEW
            if (last != null && now - last.at < 20 * DIARY_HOUR_MS) return@withLock DiaryOutcome.NOT_NOW
            if (now < prefs.diaryNextTry) return@withLock DiaryOutcome.NOT_NOW
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            if (hour in 3..18) return@withLock DiaryOutcome.NOT_NOW
        }

        val lib = Library(ctx)
        val (bs, bp) = lib.builtIn()
        val (us, up) = lib.user()
        val stickers = bs + us
        val photos = bp + up
        val mem = MemoryStore(ctx).load()
        val recentUser = all.filter { it.role == "user" }.takeLast(3)
            .joinToString(" ") { TAG_RE.replace(it.content, " ") }
        val memoryText = listOf(MemoryContext.build(mem, recentUser), diaryContext(entries, 3))
            .filter { it.isNotBlank() }.joinToString("\n\n")
        val plain = fresh.takeLast(60).map { Pair(it.role, toPlainForMemory(it.content, stickers, photos)) }
        val localTime = SimpleDateFormat("EEEE, d MMMM, HH:mm", Locale("ru")).format(Date(now))

        val res = try {
            AuroraApi.diary(prefs.serverUrl, prefs.token, plain, memoryText, localTime, limitLines(prefs, mem))
        } catch (e: Exception) {
            if (!force) prefs.diaryNextTry = now + 2 * DIARY_HOUR_MS
            return@withLock DiaryOutcome.FAILED
        }
        if (res == null) {
            // Она решила, что писать нечего: следующая попытка позже, сообщения остаются для будущей записи
            if (!force) prefs.diaryNextTry = now + 8 * DIARY_HOUR_MS
            return@withLock DiaryOutcome.SKIPPED
        }
        store.save(entries + DiaryEntry(newId("d"), now, res.entry, res.open, fresh.last().time))
        DiaryOutcome.WRITTEN
    }
}

// ---------- фоновая проверка ----------

class DiaryWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        try {
            DiaryEngine.tick(applicationContext, false)
        } catch (e: Exception) {
            // нет связи или ошибка сервера: попробуем в следующий раз
        }
        return Result.success()
    }
}

object DiaryScheduler {
    private const val NAME = "aurora_diary"

    fun apply(ctx: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(ctx)
        if (enabled) {
            val req = PeriodicWorkRequestBuilder<DiaryWorker>(3, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        } else {
            wm.cancelUniqueWork(NAME)
        }
    }
}
