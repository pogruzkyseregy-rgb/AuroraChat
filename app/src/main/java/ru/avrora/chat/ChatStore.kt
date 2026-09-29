package ru.avrora.chat

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ChatMessage(
    val role: String,      // "user" или "assistant"
    val content: String,
    val time: Long = System.currentTimeMillis(),
    val auto: Boolean = false   // сообщение, которое Аврора написала сама
)

fun messagesToJson(list: List<ChatMessage>): JSONArray {
    val arr = JSONArray()
    list.forEach {
        arr.put(
            JSONObject()
                .put("role", it.role)
                .put("content", it.content)
                .put("time", it.time)
                .put("auto", it.auto)
        )
    }
    return arr
}

fun messagesFromJson(arr: JSONArray): List<ChatMessage> =
    (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        ChatMessage(o.getString("role"), o.getString("content"), o.optLong("time"), o.optBoolean("auto", false))
    }

/** Переписка хранится в JSON-файле внутри приложения. */
class ChatStore(context: Context) {
    private val file = File(context.filesDir, "chat.json")

    fun load(): List<ChatMessage> {
        if (!file.exists()) return emptyList()
        return runCatching { messagesFromJson(JSONArray(file.readText())) }.getOrDefault(emptyList())
    }

    @Synchronized
    fun save(list: List<ChatMessage>) {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(messagesToJson(list).toString())
        tmp.renameTo(file)
    }
}
