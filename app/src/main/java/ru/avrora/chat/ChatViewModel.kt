package ru.avrora.chat

import android.app.Application
import android.content.Context
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

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val store = ChatStore(app)
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
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
    var serverUrl by mutableStateOf(prefs.getString("server_url", "https://avrora-exe.ru/app-api")!!)
        private set
    var token by mutableStateOf(prefs.getString("token", "")!!)
        private set

    init {
        val (bs, bp) = library.builtIn()
        val (us, up) = library.user()
        stickers.addAll(bs + us)
        photos.addAll(bp + up)
    }

    fun saveSettings(url: String, tok: String) {
        serverUrl = url.trim()
        token = tok.trim()
        prefs.edit().putString("server_url", serverUrl).putString("token", token).apply()
    }

    fun clearNotice() { notice = null }

    // ---------- отправка ----------

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || sending) return
        addUserMessage(t)
    }

    fun sendSticker(item: CatalogItem) {
        if (sending) return
        addUserMessage("[стикер:${item.id}]")
    }

    fun sendPhoto(item: CatalogItem) {
        if (sending) return
        addUserMessage("[фото:${item.id}]")
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
            persist()
            sending = false
            requestReply()
        }
    }

    private fun addUserMessage(content: String) {
        messages.add(ChatMessage("user", content))
        persist()
        requestReply()
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

    // ---------- запрос к серверу ----------

    /** Для модели мои вложения превращаются в понятный текст. Её собственные теги остаются как есть. */
    private fun describeForModel(text: String): String =
        TAG_RE.replace(text) { mr ->
            val id = mr.groupValues[2]
            when (mr.groupValues[1]) {
                "стикер", "sticker" -> {
                    val d = stickers.firstOrNull { it.id == id }?.desc
                    if (d != null) "[Адонис отправил стикер: $d]" else "[Адонис отправил стикер]"
                }
                "фото", "photo" -> {
                    val d = photos.firstOrNull { it.id == id }?.desc
                    if (d != null) "[Адонис отправил фото из альбома: $d]" else "[Адонис отправил фото из альбома]"
                }
                else -> "[Адонис прислал фото. Что на нём, ты не видишь: ориентируйся на подпись и контекст.]"
            }
        }.trim()

    private fun requestReply() {
        sending = true
        error = null
        val history = messages.toList().map {
            if (it.role == "user") it.copy(content = describeForModel(it.content)) else it
        }
        val st = stickers.toList()
        val ph = photos.toList()
        viewModelScope.launch {
            try {
                val reply = AuroraApi.chat(serverUrl, token, history, st, ph)
                messages.add(ChatMessage("assistant", reply))
                persist()
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
}
