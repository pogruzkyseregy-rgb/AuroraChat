package ru.avrora.chat

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ChatMessage(
    val role: String,      // "user" или "assistant"
    val content: String,
    val time: Long = System.currentTimeMillis()
)

/** Переписка хранится в JSON-файле внутри приложения. */
class ChatStore(context: Context) {
    private val file = File(context.filesDir, "chat.json")

    fun load(): List<ChatMessage> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                ChatMessage(o.getString("role"), o.getString("content"), o.optLong("time"))
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun save(list: List<ChatMessage>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("role", it.role).put("content", it.content).put("time", it.time))
        }
        val tmp = File(file.path + ".tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(file)
    }
}
