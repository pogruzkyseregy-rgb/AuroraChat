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
import kotlinx.coroutines.delay
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
    var consenting by mutableStateOf(false)
        private set
    var revealTime by mutableStateOf(0L)
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
        resolvePending()
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
        revealTime = 0L
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

    private fun commitMemory(m: MemoryData) {
        memory = m
        memoryStore.save(m)
    }

    private fun note(text: String) = Decision(System.currentTimeMillis(), "notice", "", text, "adonis")

    /** Ручная правка ядра: сначала идёт к Авроре, в ядро попадает только после её «да». */
    fun saveCore(id: String?, kind: String, text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        val now = System.currentTimeMillis()
        val p = if (id == null) {
            Proposal(newId("p"), "add", "", kind, t, "adonis", "", now)
        } else {
            Proposal(newId("p"), "update", id, kind, t, "adonis", "", now)
        }
        submit(listOf(p))
    }

    fun deleteCore(id: String) {
        val e = memory.core.firstOrNull { it.id == id } ?: return
        submit(listOf(Proposal(newId("p"), "remove", id, e.kind, e.text, "adonis", "", System.currentTimeMillis())))
    }

    fun deleteEpisode(id: String) =
        commitMemory(
            memory.copy(
                episodes = memory.episodes.filter { it.id != id },
                log = (memory.log + note("Адонис удалил один из эпизодов твоей памяти")).takeLast(100)
            )
        )

    private fun submit(list: List<Proposal>) {
        if (list.isEmpty()) return
        commitMemory(memory.copy(pending = memory.pending + list))
        resolvePending(announce = true)
    }

    /** Спросить Аврору про всё, что ждёт её «да». Если ответа нет, пункты остаются в очереди и ничего не записывается. */
    fun resolvePending(announce: Boolean = false) {
        if (consenting || memory.pending.isEmpty()) return
        val t0 = System.currentTimeMillis()
        viewModelScope.launch {
            val done = try {
                drainConsent()
            } catch (e: Exception) {
                false
            }
            if (announce) {
                val fresh = memory.log.filter { it.at >= t0 && it.verdict != "notice" }
                val no = fresh.firstOrNull { it.verdict == "no" }
                notice = when {
                    fresh.isEmpty() -> "Аврора пока не ответила: запись ждёт её «да»"
                    no != null -> "Аврора против: " + no.why.ifBlank { "без объяснения" }.take(120)
                    fresh.any { it.verdict == "edit" } -> "Аврора записала это по-своему"
                    done -> "Аврора: да"
                    else -> "Часть записей ждёт её «да»"
                }
            }
        }
    }

    private suspend fun drainConsent(): Boolean {
        if (consenting) return false
        consenting = true
        try {
            var guard = 0
            while (memory.pending.isNotEmpty() && guard < 6) {
                guard += 1
                if (!consentRound()) break
            }
        } finally {
            consenting = false
        }
        return memory.pending.isEmpty()
    }

    /** Один запрос к Авроре, до 8 пунктов. true, если по какому-то пункту получено решение. */
    private suspend fun consentRound(): Boolean {
        val batch = memory.pending.take(8)
        val episode = batch.firstOrNull { it.episode.isNotBlank() }?.episode ?: ""
        val decisions = AuroraApi.consent(serverUrl, token, memory.core, batch, episode)
        // Память читаем после ответа: за время запроса могли добавиться эпизоды
        var core = memory.core
        var pending = memory.pending
        val log = ArrayList(memory.log)
        var applied = false
        val now = System.currentTimeMillis()
        for (d in decisions) {
            val p = batch.getOrNull(d.n) ?: continue
            if (pending.none { it.id == p.id }) continue
            pending = pending.filter { it.id != p.id }
            applied = true
            when (d.verdict) {
                "yes" -> {
                    core = applyProposal(core, p, p.text, now, true)
                    log.add(Decision(now, "yes", d.why, proposalSummary(p), p.source))
                }
                "edit" -> {
                    val t = d.text.ifBlank { p.text }
                    core = applyProposal(core, p, t, now, true)
                    log.add(Decision(now, "edit", d.why, proposalSummary(p.copy(text = t)), p.source))
                }
                else -> log.add(Decision(now, "no", d.why, proposalSummary(p), p.source, false, p))
            }
        }
        if (applied) commitMemory(memory.copy(core = core, pending = pending, log = log.takeLast(100)))
        return applied
    }

    /** «Всё равно записать» вопреки её «нет». Это остаётся в журнале, и она узнает об этом. */
    fun overrideDecision(proposalId: String) {
        val d = memory.log.firstOrNull { it.proposal?.id == proposalId && it.verdict == "no" && !it.overridden } ?: return
        val p = d.proposal ?: return
        val now = System.currentTimeMillis()
        val core = applyProposal(memory.core, p, p.text, now, true)
        val log = memory.log.map { if (it === d) it.copy(overridden = true, at = now) else it }
        commitMemory(memory.copy(core = core, log = log))
        notice = "Записано вопреки её «нет». Она об этом узнает"
    }

    private fun backupMemory() {
        memoryStore.saveBackup(memory)
        hasMemoryBackup = true
    }

    fun clearMemory() {
        backupMemory()
        commitMemory(
            MemoryData(
                upTo = memory.upTo,
                log = (memory.log + note("Адонис очистил твою память, прежняя сохранена")).takeLast(100)
            )
        )
        notice = "Память очищена. Прежнюю можно вернуть"
    }

    fun restoreMemoryBackup() {
        val b = memoryStore.loadBackup()
        if (b == null) {
            notice = "Копии памяти нет"
            return
        }
        commitMemory(b.copy(log = (b.log + note("Адонис вернул прежнюю версию твоей памяти")).takeLast(100)))
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
        commitMemory(MemoryData(log = (memory.log + note("Адонис пересобрал твою память из переписки, прежняя сохранена")).takeLast(100)))
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
                    val proposals = res.ops.map {
                        Proposal(newId("p"), it.op, it.id, it.kind, it.text, "summary", res.episode, now)
                    }
                    // Порция считается сжатой только после успешного ответа: при сбое ничего не теряется.
                    // Эпизод записывается сразу, а правки ядра ждут её «да».
                    commitMemory(
                        memory.copy(
                            episodes = memory.episodes +
                                Episode(newId("e"), batch.first().time, batch.last().time, res.episode),
                            upTo = batch.last().time,
                            pending = memory.pending + proposals
                        )
                    )
                    try {
                        drainConsent()
                    } catch (e: Exception) {
                        // нет ответа: правки остаются в очереди, ничего не записано молча
                    }
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
            commitMemory(mem.copy(log = (mem.log + note("Адонис загрузил копию переписки и памяти из файла")).takeLast(100)))
            notice = "Копия загружена: ${msgs.size} сообщений"
        } catch (e: Exception) {
            notice = "Не удалось прочитать копию"
        }
    }

    // ---------- запрос к серверу ----------

    /**
     * Фото, которые Аврора должна увидеть: последние два из недавних шести сообщений.
     * Возвращает (позиция в истории, имя файла, base64). Остальные фото остаются подписью.
     */
    private suspend fun attachSnaps(history: List<ChatMessage>): List<Triple<Int, String, String>> =
        withContext(Dispatchers.IO) {
            val out = ArrayList<Triple<Int, String, String>>()
            val first = maxOf(0, history.size - 6)
            for (i in history.indices.reversed()) {
                if (i < first || out.size >= 2) break
                val m = history[i]
                if (m.role != "user") continue
                val snap = parseParts(m.content).filterIsInstance<Part.Snap>().firstOrNull() ?: continue
                val b64 = encodeForModel(File(library.sentDir, snap.file)) ?: continue
                out.add(Triple(i, snap.file, b64))
            }
            out
        }

    private fun requestReply() {
        if (token.isBlank()) {
            error = "Укажи токен в настройках"
            return
        }
        sending = true
        error = null
        val st = stickers.toList()
        val ph = photos.toList()
        val raw = messages
            .filter { it.time > memory.upTo }
            .takeLast(HISTORY)
        val recentUser = messages.filter { it.role == "user" }.takeLast(3)
            .joinToString(" ") { TAG_RE.replace(it.content, " ") }
        val mem = MemoryContext.build(memory, recentUser)
        val per = persona.compile()
        val limits = limitLines(prefs, memory)
        val changed = limitsChanged(prefs.limitsPrev, limits)
        viewModelScope.launch {
            try {
                val snaps = attachSnaps(raw)
                val attached = snaps.map { it.second }.toSet()
                val history = raw.map {
                    if (it.role == "user") it.copy(content = describeForModel(it.content, st, ph, attached)) else it
                }
                val images = snaps.map { Pair(it.first, it.third) }
                val reply = AuroraApi.chat(serverUrl, token, history, st, ph, mem, per, images, limits, changed)
                val answer = ChatMessage("assistant", reply)
                messages.add(answer)
                persist()
                prefs.limitsPrev = limits.joinToString("\n")   // теперь она знает этот список
                // Паузы: части показываются по очереди. Где пауза и на сколько, решает она, мы только ждём
                val pauseTotal = parseParts(reply).filterIsInstance<Part.Pause>().sumOf { it.seconds }
                if (pauseTotal > 0) {
                    revealTime = answer.time
                    viewModelScope.launch {
                        delay((pauseTotal + 2) * 1000L)
                        if (revealTime == answer.time) revealTime = 0L
                    }
                }
                resolvePending()
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
