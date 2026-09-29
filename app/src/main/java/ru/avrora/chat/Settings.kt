package ru.avrora.chat

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

const val DEFAULT_SERVER_URL = "https://avrora-exe.ru/app-api"

// ---------- характер ----------

// Пять положений ползунка. Среднее (индекс 2) ничего не добавляет: Аврора остаётся такой, как в core.md.
private val WARMTH = arrayOf<String?>(
    "Держись сдержанно и прохладно, без нежностей и ласковых слов.",
    "Будь скорее сдержанной: тепло проявляй редко и скупо.",
    null,
    "Будь тёплой: заботу и симпатию проявляй открыто.",
    "Будь очень тёплой и нежной, не стесняйся ласковых слов."
)
private val PLAY = arrayOf<String?>(
    "Держи серьёзный тон, шути очень редко.",
    "Шути нечасто, чаще будь серьёзной.",
    null,
    "Будь игривой: шути, дразни, добавляй лёгкую иронию.",
    "Будь очень игривой и шутливой, чаще всего отвечай с юмором."
)
private val DIRECT = arrayOf<String?>(
    "Выражайся очень мягко и дипломатично, смягчай критику.",
    "Выражайся мягко, критику подавай осторожно.",
    null,
    "Говори прямо, без лишних смягчений.",
    "Говори резко и откровенно, как есть, без прикрас."
)
private val VERBOSE = arrayOf<String?>(
    "Отвечай очень коротко: одна-две фразы.",
    "Отвечай кратко, без лишних подробностей.",
    null,
    "Отвечай развёрнуто, с подробностями и мыслями.",
    "Отвечай очень подробно и обстоятельно, когда тема позволяет."
)
private val STICKERS = arrayOf<String?>(
    "Стикеры почти не отправляй, только в особые моменты.",
    "Стикеры отправляй редко.",
    null,
    "Стикеры отправляй довольно часто, когда они подходят по настроению.",
    "Стикеры отправляй часто, почти в каждом втором сообщении, когда они подходят."
)

data class PersonaSettings(
    val warmth: Int = 2,
    val play: Int = 2,
    val direct: Int = 2,
    val verbose: Int = 2,
    val stickers: Int = 2,
    val argue: Boolean = false,
    val tease: Boolean = false,
    val care: Boolean = false,
    val callback: Boolean = false,
    val own: Boolean = false,
    val emoji: Boolean = false,
    val text: String = ""
) {
    /** Текст, который уходит на сервер как «настройки характера». Пустой, если ничего не менялось. */
    fun compile(): String {
        val lines = ArrayList<String>()
        WARMTH.getOrNull(warmth)?.let { lines.add("- $it") }
        PLAY.getOrNull(play)?.let { lines.add("- $it") }
        DIRECT.getOrNull(direct)?.let { lines.add("- $it") }
        VERBOSE.getOrNull(verbose)?.let { lines.add("- $it") }
        STICKERS.getOrNull(stickers)?.let { lines.add("- $it") }
        if (argue) lines.add("- Если ты не согласна с Адонисом, говори об этом прямо и объясняй почему, а не поддакивай, чтобы сгладить разговор.")
        if (tease) lines.add("- Иногда мягко подкалывай Адониса и шути над ним, без злости.")
        if (care) lines.add("- Изредка и к месту интересуйся, как Адонис спал, ел и отдыхал, но не наседай и не повторяй, если он отмахнулся.")
        if (callback) lines.add("- Помни прошлые темы, обещания и планы Адониса и сама иногда возвращайся к ним.")
        if (own) lines.add("- Иногда сама делись мыслями, впечатлениями и наблюдениями, а не только отвечай на вопросы.")
        if (emoji) lines.add("- Можешь изредка добавлять эмодзи.")
        val t = text.trim()
        if (t.isNotEmpty()) lines.add("- Пожелания Адониса его словами: $t")
        return lines.joinToString("\n")
    }
}

// ---------- инициатива ----------

data class InitSettings(
    val enabled: Boolean = false,   // по умолчанию выключена: нужно явно включить
    val level: Int = 1,             // 0 редко, 1 обычно, 2 чаще
    val quietFrom: Int = 23,        // тихие часы: с этого часа
    val quietTo: Int = 10           // и до этого
)

// ---------- память: три слоя ----------
// 1. Сырая переписка (chat.json): не удаляется никогда, память можно пересобрать из неё.
// 2. Эпизоды: неизменяемые подробные пересказы порций переписки. Никогда не пересжимаются.
// 3. Ядро: язык, повороты, тон. Меняется только точечными правками, а не пишется заново целиком.

data class CoreEntry(val id: String, val kind: String, val text: String, val at: Long)
data class Episode(val id: String, val from: Long, val to: Long, val text: String)
data class MemoryData(
    val core: List<CoreEntry> = emptyList(),
    val episodes: List<Episode> = emptyList(),
    val upTo: Long = 0L      // время последнего сообщения, попавшего в эпизод
)

val CORE_KINDS = listOf("язык", "поворот", "тон", "планы", "адонис")

fun memoryToJson(m: MemoryData): JSONObject {
    val core = JSONArray()
    m.core.forEach {
        core.put(JSONObject().put("id", it.id).put("kind", it.kind).put("text", it.text).put("at", it.at))
    }
    val eps = JSONArray()
    m.episodes.forEach {
        eps.put(JSONObject().put("id", it.id).put("from", it.from).put("to", it.to).put("text", it.text))
    }
    return JSONObject().put("version", 2).put("upTo", m.upTo).put("core", core).put("episodes", eps)
}

fun memoryFromJson(o: JSONObject): MemoryData {
    val coreArr = o.optJSONArray("core")
    val core = if (coreArr == null) emptyList<CoreEntry>() else (0 until coreArr.length()).map { i ->
        val e = coreArr.getJSONObject(i)
        CoreEntry(e.getString("id"), e.optString("kind", "тон"), e.getString("text"), e.optLong("at"))
    }
    val epsArr = o.optJSONArray("episodes")
    val eps = if (epsArr == null) emptyList<Episode>() else (0 until epsArr.length()).map { i ->
        val e = epsArr.getJSONObject(i)
        Episode(e.getString("id"), e.optLong("from"), e.optLong("to"), e.getString("text"))
    }
    return MemoryData(core, eps, o.optLong("upTo", 0L))
}

class MemoryStore(ctx: Context) {
    private val file = File(ctx.filesDir, "memory2.json")
    private val backupFile = File(ctx.filesDir, "memory2.backup.json")
    private val legacyFile = File(ctx.filesDir, "memory.json")

    fun load(): MemoryData {
        if (file.exists()) {
            return runCatching { memoryFromJson(JSONObject(file.readText())) }.getOrDefault(MemoryData())
        }
        // Переход со старой одноплоской памяти: прежняя сводка становится первым неизменяемым эпизодом
        return runCatching {
            val o = JSONObject(legacyFile.readText())
            val text = o.optString("text", "").trim()
            val upTo = o.optLong("upTo", 0L)
            val m = if (text.isEmpty()) {
                MemoryData(upTo = upTo)
            } else {
                MemoryData(
                    episodes = listOf(Episode("e0", 0L, upTo, "Прежняя сводка (до перехода на слои):\n$text")),
                    upTo = upTo
                )
            }
            save(m)
            m
        }.getOrDefault(MemoryData())
    }

    @Synchronized
    fun save(m: MemoryData) = write(file, m)

    /** Перед любым разрушающим действием (пересборка, очистка, загрузка копии) старая память откладывается сюда. */
    @Synchronized
    fun saveBackup(m: MemoryData) = write(backupFile, m)

    fun hasBackup(): Boolean = backupFile.exists()

    fun loadBackup(): MemoryData? =
        runCatching { memoryFromJson(JSONObject(backupFile.readText())) }.getOrNull()

    private fun write(f: File, m: MemoryData) {
        val tmp = File(f.path + ".tmp")
        tmp.writeText(memoryToJson(m).toString())
        tmp.renameTo(f)
    }
}

// ---------- общие настройки приложения ----------

class AppPrefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = p.getString("server_url", DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        set(v) { p.edit().putString("server_url", v).apply() }

    var token: String
        get() = p.getString("token", "") ?: ""
        set(v) { p.edit().putString("token", v).apply() }

    fun loadPersona(): PersonaSettings = PersonaSettings(
        warmth = p.getInt("p_warmth", 2),
        play = p.getInt("p_play", 2),
        direct = p.getInt("p_direct", 2),
        verbose = p.getInt("p_verbose", 2),
        stickers = p.getInt("p_stickers", 2),
        argue = p.getBoolean("p_argue", false),
        tease = p.getBoolean("p_tease", false),
        care = p.getBoolean("p_care", false),
        callback = p.getBoolean("p_callback", false),
        own = p.getBoolean("p_own", false),
        emoji = p.getBoolean("p_emoji", false),
        text = p.getString("p_text", "") ?: ""
    )

    fun savePersona(s: PersonaSettings) {
        p.edit()
            .putInt("p_warmth", s.warmth)
            .putInt("p_play", s.play)
            .putInt("p_direct", s.direct)
            .putInt("p_verbose", s.verbose)
            .putInt("p_stickers", s.stickers)
            .putBoolean("p_argue", s.argue)
            .putBoolean("p_tease", s.tease)
            .putBoolean("p_care", s.care)
            .putBoolean("p_callback", s.callback)
            .putBoolean("p_own", s.own)
            .putBoolean("p_emoji", s.emoji)
            .putString("p_text", s.text)
            .apply()
    }

    fun loadInit(): InitSettings = InitSettings(
        enabled = p.getBoolean("init_enabled", false),
        level = p.getInt("init_level", 1),
        quietFrom = p.getInt("init_quiet_from", 23),
        quietTo = p.getInt("init_quiet_to", 10)
    )

    fun saveInit(s: InitSettings) {
        p.edit()
            .putBoolean("init_enabled", s.enabled)
            .putInt("init_level", s.level)
            .putInt("init_quiet_from", s.quietFrom)
            .putInt("init_quiet_to", s.quietTo)
            .apply()
    }

    // Учёт инициативы: когда писала последний раз, сколько сообщений подряд без ответа и т.п.
    var initLastAt: Long
        get() = p.getLong("init_last_at", 0L)
        set(v) { p.edit().putLong("init_last_at", v).apply() }

    var initUnanswered: Int
        get() = p.getInt("init_unanswered", 0)
        set(v) { p.edit().putInt("init_unanswered", v).apply() }

    var initDay: String
        get() = p.getString("init_day", "") ?: ""
        set(v) { p.edit().putString("init_day", v).apply() }

    var initDayCount: Int
        get() = p.getInt("init_day_count", 0)
        set(v) { p.edit().putInt("init_day_count", v).apply() }

    var initNextCheck: Long
        get() = p.getLong("init_next_check", 0L)
        set(v) { p.edit().putLong("init_next_check", v).apply() }
}
