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

    private const val HISTORY_LIMIT = 40

    private fun catalogJson(items: List<CatalogItem>): JSONArray {
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("id", it.id).put("desc", it.desc)) }
        return arr
    }

    private fun historyJson(history: List<ChatMessage>): JSONArray {
        val arr = JSONArray()
        history.takeLast(HISTORY_LIMIT).forEach {
            arr.put(JSONObject().put("role", it.role).put("content", it.content))
        }
        return arr
    }

    private suspend fun post(baseUrl: String, path: String, token: String, body: JSONObject): JSONObject =
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url(baseUrl.trimEnd('/') + path)
                .header("Authorization", "Bearer $token")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                when {
                    resp.code == 401 -> throw IOException("Сервер не принял токен. Проверь его в настройках.")
                    !resp.isSuccessful -> throw IOException("Сервер ответил ${resp.code}: ${text.take(200)}")
                }
                JSONObject(text)
            }
        }

    suspend fun chat(
        baseUrl: String,
        token: String,
        history: List<ChatMessage>,
        stickers: List<CatalogItem>,
        photos: List<CatalogItem>,
        memory: String,
        persona: String,
        images: List<Pair<Int, String>> = emptyList()
    ): String {
        val imgs = JSONArray()
        images.forEach { imgs.put(JSONObject().put("i", it.first).put("data", it.second)) }
        val body = JSONObject()
            .put("messages", historyJson(history))
            .put("memory", memory)
            .put("persona", persona)
            .put("stickers", catalogJson(stickers))
            .put("photos", catalogJson(photos))
            .put("images", imgs)
        return post(baseUrl, "/chat", token, body).getString("reply")
    }

    /**
     * Порция сырой переписки превращается в эпизод (неизменяемый подробный пересказ)
     * и точечные правки ядра. Ядро целиком не переписывается.
     */
    suspend fun compressMemory(
        baseUrl: String,
        token: String,
        core: List<CoreEntry>,
        messages: List<Pair<String, String>>
    ): CompressResult {
        val coreArr = JSONArray()
        core.forEach { coreArr.put(JSONObject().put("id", it.id).put("kind", it.kind).put("text", it.text)) }
        val arr = JSONArray()
        messages.forEach { arr.put(JSONObject().put("role", it.first).put("content", it.second)) }
        val res = post(baseUrl, "/memory", token, JSONObject().put("core", coreArr).put("messages", arr))
        val opsArr = res.optJSONArray("ops")
        val ops = if (opsArr == null) emptyList<MemOp>() else (0 until opsArr.length()).map { i ->
            val o = opsArr.getJSONObject(i)
            MemOp(o.optString("op"), o.optString("id"), o.optString("kind"), o.optString("text"))
        }
        return CompressResult(res.optString("episode", "").trim(), ops)
    }

    /** Аврора решает, писать ли первой. null означает, что она решила промолчать. */
    suspend fun initiative(
        baseUrl: String,
        token: String,
        history: List<ChatMessage>,
        memory: String,
        persona: String,
        reason: String,
        gapMinutes: Long,
        localTime: String,
        unanswered: Int,
        stickers: List<CatalogItem>,
        photos: List<CatalogItem>
    ): String? {
        val body = JSONObject()
            .put("messages", historyJson(history))
            .put("memory", memory)
            .put("persona", persona)
            .put("reason", reason)
            .put("gap_minutes", gapMinutes)
            .put("local_time", localTime)
            .put("unanswered", unanswered)
            .put("stickers", catalogJson(stickers))
            .put("photos", catalogJson(photos))
        val res = post(baseUrl, "/initiative", token, body)
        if (res.optBoolean("skip", false)) return null
        val msg = res.optString("message", "").trim()
        return if (msg.isEmpty()) null else msg
    }
}
