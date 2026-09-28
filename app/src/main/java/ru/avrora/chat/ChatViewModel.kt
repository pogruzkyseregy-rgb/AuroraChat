package ru.avrora.chat

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val store = ChatStore(app)
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val messages = mutableStateListOf<ChatMessage>().apply { addAll(store.load()) }

    var sending by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var serverUrl by mutableStateOf(prefs.getString("server_url", "https://avrora-exe.ru/app-api")!!)
        private set
    var token by mutableStateOf(prefs.getString("token", "")!!)
        private set

    fun saveSettings(url: String, tok: String) {
        serverUrl = url.trim()
        token = tok.trim()
        prefs.edit().putString("server_url", serverUrl).putString("token", token).apply()
    }

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || sending) return
        messages.add(ChatMessage("user", t))
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

    private fun requestReply() {
        sending = true
        error = null
        viewModelScope.launch {
            try {
                val reply = AuroraApi.chat(serverUrl, token, messages.toList())
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
