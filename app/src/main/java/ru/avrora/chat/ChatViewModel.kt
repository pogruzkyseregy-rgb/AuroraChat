package ru.avrora.chat

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val store = ChatStore(app)
    private val prefs = AppPrefs(app)
    private val inbox = Inbox(app)
    private val memoryStore = MemoryStore(app)
    val library = Library(app)

    val messages = mutableStateListOf<ChatMessage>().apply { addAll(store.load()) }
    val stickers = mutableStateListOf<CatalogItem>()
    val photos = mutableStateListOf<CatalogItem>()

    var sending by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var notice by mutableStateOf<String?>(null)
        private set
    var serverUrl by mutableStateOf(prefs.serverUrl)
        private set
    var token by mutableStateOf(prefs.token)
        private set
    var persona by mutableStateOf(prefs.loadPersona())
        private set
    var initSettings by mutableStateOf(prefs.loadInit())
        private set
    var memory by mutableStateOf(memoryStore.load())
        private set
    var compressing by mutableStateOf(false)
        private set
    var compressStatus by mutableStateOf("")
        private set
    var hasMemoryBackup by mutableStateOf(memoryStore.hasBackup())
        private set
    var probing by mutableStateOf(false)
        private set
    var probeResult by mutableStateOf<String?>(null)
        private set
    var testing by mutableStateOf(false)
        private set

    init {
        val (bs, bp) = library.builtIn()
        val (us, up) = library.user()
        stickers.addAll(bs + us)
        photos.addAll(bp + up)

        drainInbox()
        InitiativeScheduler.apply(app, initSettings.enabled)
        viewModelScope.launch { InboxBus.flow.collect { drainInbox() } }
    }

    // ---------- настройки ----------

    fun saveSettings(url: String, tok: String) {
        serverUrl = url.trim()
        token = tok.trim()
        prefs.serverUrl = serverUrl
        prefs.token = token
    }

    fun clearNotice() { notice = null }

    fun updatePersona(p: PersonaSettings) {
        persona = p
        prefs.savePersona(p)
    }

    fun updateInit(s: InitSettings) {
        initSettings = s
        prefs.saveInit(s)
        InitiativeScheduler.apply(getApplication(), s.enabled)
    }

    /** Проверка кнопкой: Аврора пишет сразу, в обход пауз и лимитов. */
    fun testInitiative() {
        if (testing) return
        testing = true
        viewModelScope.launch {
            val r = try {
                InitiativeEngine.tick(getApplication(), true)
            } catch (e: Exception) {
                InitiativeOutcome.FAILED
            }
            notice = when (r) {
                InitiativeOutcome.SENT -> "Аврора написала"
                InitiativeOutcome.SILENT -> "Аврора решила промолчать"
                InitiativeOutcome.NO_CHAT -> "Сначала поговори с Авророй"
                InitiativeOutcome.NO_TOKEN -> "Сначала укажи токен"
                else -> "Не получилось: нет связи с сервером"
            }
            testing = false
        }
    }

    /** Забрать сообщения, которые Аврора написала, пока приложение было закрыто. */
    fun drainInbox() {
        val items = inbox.drain()
        if (items.isNotEmpty()) {
            messages.addAll(items)
            persist()
        }
    }

    // ---------- отправка ----------

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || sending) return
        addUserMessage(t)
    }

    /** Текст и прикреплённый стикер или фото из альбома уходят одним сообщением: сначала текст, потом вложение. */
    fun sendWithTag(text: String, tag: String) {
        if (sending) return
        val t = text.trim()
        addUserMessage(if (t.isEmpty()) tag else "$t\n$tag")
    }

    /** Фото из галереи прямо в чат, с необязательной подписью. */
    fun sendSnap(uri: Uri, caption: String) {
        if (sending) return
        sending = true
        error = null
        viewModelScope.launch {
            val name = try {
                withContext(Dispatchers.IO) { library.saveSnap(uri) }
            } catch (e: Exception) {
                error = "Не удалось открыть фото"
                sending = false
                return@launch
            }
            val c = caption.trim()
            messages.add(ChatMessage("user", if (c.isEmpty()) "[снимок:$name]" else "[снимок:$name]\n$c"))
            noteUserActivity()
            persist()
            sending = false
            requestReply()
        }
    }

    private fun addUserMessage(content: String) {
        messages.add(ChatMessage("user", content))
        noteUserActivity()
        persist()
        requestReply()
    }

    /** Я написал: счётчик «сообщений Авроры без ответа» и паузы инициативы обнуляются. */
    private fun noteUserActivity() {
        prefs.initUnanswered = 0
        prefs.initNextCheck = 0L
    }

    /** Повторить запрос, если последний ответ не пришёл. */
    fun retry() {
        if (!sending && messages.lastOrNull()?.role == "user") requestReply()
    }

    fun clearChat() {
        messages.clear()
        error = null
        persist()
    }

    // ---------- библиотека ----------

    fun addSticker(uri: Uri, desc: String, cutBlack: Boolean) {
        viewModelScope.launch {
            try {
                val item = withContext(Dispatchers.IO) { library.addSticker(uri, desc, cutBlack) }
                stickers.add(item)
                notice = "Стикер добавлен"
            } catch (e: Exception) {
                notice = "Не получилось добавить: ${e.message ?: "ошибка"}"
            }
        }
    }

    fun addPhoto(uri: Uri, desc: String) {
        viewModelScope.launch {
            try {
                val item = withContext(Dispatchers.IO) { library.addPhoto(uri, desc) }
                photos.add(item)
                notice = "Фото добавлено в альбом"
            } catch (e: Exception) {
                notice = "Не получилось добавить: ${e.message ?: "ошибка"}"
            }
        }
    }

    fun removeItem(item: CatalogItem) {
        if (item.builtIn) return
        stickers.remove(item)
        photos.remove(item)
        viewModelScope.launch(Dispatchers.IO) { library.remove(item) }
    }

    // ---------- память ----------
    // Сырые сообщения не удаляются никогда: сжатие только решает, что уходит в запрос целиком.
    // Эпизоды неизменяемы, ядро правится точечно, поэтому сжатие не накапливает потери.

    private fun setMemory(m: MemoryData) {
        memory = m
        memoryStore.save(m)
    }

    fun saveCore(id: String?, kind: String, text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        val core = if (id == null) {
            memory.core + CoreEntry(newId("c"), kind, t, System.currentTimeMillis())
        } else {
            memory.core.map { if (it.id == id) it.copy(kind = kind, text = t) else it }
        }
        setMemory(memory.copy(core = core))
    }

    fun deleteCore(id: String) = setMemory(memory.copy(core = memory.core.filter { it.id != id }))

    fun deleteEpisode(id: String) = setMemory(memory.copy(episodes = memory.episodes.filter { it.id != id }))

    private fun backupMemory() {
        memoryStore.saveBackup(memory)
        hasMemoryBackup = true
    }

    fun clearMemory() {
        backupMemory()
        setMemory(MemoryData(upTo = memory.upTo))
        notice = "Память очищена. Прежнюю можно вернуть"
    }

    fun restoreMemoryBackup() {
        val b = memoryStore.loadBackup()
        if (b == null) {
            notice = "Копии памяти нет"
            return
        }
        setMemory(b)
        notice = "Память возвращена"
    }

    /** Сжать всё, кроме последних 6 сообщений, прямо сейчас. */
    fun compressNow() = compress(keep = 6, threshold = 6, announce = true)

    /** Память строится заново из всей сырой переписки, например после улучшения промпта сжатия. */
    fun rebuildMemory() {
        if (compressing) return
        if (messages.size <= RAW_KEEP) {
            notice = "Переписки пока мало для пересборки"
            return
        }
        backupMemory()
        setMemory(MemoryData())
        compress(keep = RAW_KEEP, threshold = RAW_KEEP, announce = true)
    }

    private fun compress(keep: Int, threshold: Int, announce: Boolean) {
        if (compressing) return
        val pending = messages.filter { it.time > memory.upTo }
        if (pending.size <= threshold) {
            if (announce) notice = "Пока нечего сжимать"
            return
        }
        val batches = pending.dropLast(keep).chunked(CHUNK)
        val st = stickers.toList()
        val ph = photos.toList()
        compressing = true
        viewModelScope.launch {
            try {
                var done = 0
                for (batch in batches) {
                    done += 1
                    compressStatus = "$done из ${batches.size}"
                    val plain = batch.map { Pair(it.role, toPlainForMemory(it.content, st, ph)) }
                    val res = AuroraApi.compressMemory(serverUrl, token, memory.core, plain)
                    if (res.episode.isBlank()) throw IllegalStateException("пустой ответ модели")
                    val now = System.currentTimeMillis()
                    // Порция считается сжатой только после успешного ответа: при сбое ничего не теряется
                    setMemory(
                        MemoryData(
                            core = applyOps(memory.core, res.ops, now),
                            episodes = memory.episodes +
                                Episode(newId("e"), batch.first().time, batch.last().time, res.episode),
                            upTo = batch.last().time
                        )
                    )
                }
                if (announce) notice = "Память обновлена"
            } catch (e: Exception) {
                if (announce) notice = "Не получилось сжать: ${e.message ?: "нет связи"}"
            } finally {
                compressing = false
                compressStatus = ""
            }
        }
    }

    /** Проверка: что получится из вставленного отрывка. Память при этом не меняется. */
    fun runProbe(text: String) {
        if (probing) return
        val msgs = parseTranscript(text)
        if (msgs.isEmpty()) {
            notice = "Вставь отрывок переписки"
            return
        }
        probing = true
        probeResult = null
        viewModelScope.launch {
            try {
                val res = AuroraApi.compressMemory(serverUrl, token, emptyList(), msgs)
                probeResult = buildString {
                    append("ЭПИЗОД\n").append(res.episode).append("\n\nПРАВКИ ЯДРА\n")
                    if (res.ops.isEmpty()) {
                        append("(нет)")
                    } else {
                        res.ops.forEach { append("• ${it.op} [${it.kind}] ${it.text}\n") }
                    }
                }
            } catch (e: Exception) {
                probeResult = "Ошибка: ${e.message ?: "нет связи"}"
            } finally {
                probing = false
            }
        }
    }

    // ---------- резервная копия вне телефона ----------

    fun exportJson(): String = JSONObject()
        .put("app", "aurora-chat")
        .put("version", 1)
        .put("messages", messagesToJson(messages.toList()))
        .put("memory", memoryToJson(memory))
        .toString()

    /** Заменяет переписку и память копией. Текущие перед этим откладываются. */
    fun importBackup(text: String) {
        try {
            val o = JSONObject(text)
            val msgs = messagesFromJson(o.getJSONArray("messages"))
            val mem = memoryFromJson(o.getJSONObject("memory"))
            File(getApplication<Application>().filesDir, "chat.before-import.json")
                .writeText(messagesToJson(messages.toList()).toString())
            backupMemory()
            messages.clear()
            messages.addAll(msgs)
            persist()
            setMemory(mem)
            notice = "Копия загружена: ${msgs.size} сообщений"
        } catch (e: Exception) {
            notice = "Не удалось прочитать копию"
        }
    }

    // ---------- запрос к серверу ----------

    private fun requestReply() {
        if (token.isBlank()) {
            error = "Укажи токен в настройках"
            return
        }
        sending = true
        error = null
        val st = stickers.toList()
        val ph = photos.toList()
        val history = messages
            .filter { it.time > memory.upTo }
            .takeLast(HISTORY)
            .map { if (it.role == "user") it.copy(content = describeForModel(it.content, st, ph)) else it }
        val recentUser = messages.filter { it.role == "user" }.takeLast(3)
            .joinToString(" ") { TAG_RE.replace(it.content, " ") }
        val mem = MemoryContext.build(memory, recentUser)
        val per = persona.compile()
        viewModelScope.launch {
            try {
                val reply = AuroraApi.chat(serverUrl, token, history, st, ph, mem, per)
                messages.add(ChatMessage("assistant", reply))
                persist()
                compress(keep = RAW_KEEP, threshold = TRIGGER, announce = false)
            } catch (e: Exception) {
                error = e.message ?: "Нет связи с сервером"
            } finally {
                sending = false
            }
        }
    }

    private fun persist() {
        val snapshot = messages.toList()
        viewModelScope.launch(Dispatchers.IO) { store.save(snapshot) }
    }

    companion object {
        private const val HISTORY = 40     // сколько последних несжатых сообщений уходит целиком
        private const val TRIGGER = 40     // больше стольких несжатых: сжимаем старые
        private const val RAW_KEEP = 16    // столько последних остаётся «живыми» после сжатия
        private const val CHUNK = 40       // сколько сообщений уходит в один эпизод
    }
}
