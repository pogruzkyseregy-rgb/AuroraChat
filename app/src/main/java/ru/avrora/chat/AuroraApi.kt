package ru.avrora.chat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object AuroraApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(150, TimeUnit.SECONDS)
        .build()

    /** Сколько последних сообщений отправлять целиком. Остальное позже заменит сжатая память. */
    private const val HISTORY_LIMIT = 30

    suspend fun chat(baseUrl: String, token: String, history: List<ChatMessage>): String =
        withContext(Dispatchers.IO) {
            val msgs = JSONArray()
            history.takeLast(HISTORY_LIMIT).forEach {
                msgs.put(JSONObject().put("role", it.role).put("content", it.content))
            }
            val body = JSONObject()
                .put("messages", msgs)
                .put("memory", "")   // этап 2: сжатая память
                .put("persona", "")  // этап 3: настройки характера
                .toString()
                .toRequestBody("application/json".toMediaType())

            val req = Request.Builder()
                .url(baseUrl.trimEnd('/') + "/chat")
                .header("Authorization", "Bearer $token")
                .post(body)
                .build()

            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                when {
                    resp.code == 401 -> throw IOException("Сервер не принял токен. Проверь его в настройках.")
                    !resp.isSuccessful -> throw IOException("Сервер ответил ${resp.code}: ${text.take(200)}")
                }
                JSONObject(text).getString("reply")
            }
        }
}
