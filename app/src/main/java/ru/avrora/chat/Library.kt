package ru.avrora.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

/** Стикер или фото из общей библиотеки. model: строка asset-адреса или File. */
data class CatalogItem(
    val id: String,
    val desc: String,
    val builtIn: Boolean,
    val model: Any,
    val aspect: Float
)

/** Теги во вложениях: [стикер:id], [фото:id], [снимок:файл]. */
val TAG_RE = Regex("""\[(стикер|фото|снимок|sticker|photo)\s*:\s*([A-Za-z0-9_.\-]+)\s*\]""")

sealed class Part {
    data class Text(val s: String) : Part()
    data class Sticker(val id: String) : Part()
    data class Photo(val id: String) : Part()
    data class Snap(val file: String) : Part()
}

fun parseParts(content: String): List<Part> {
    val out = ArrayList<Part>()
    var last = 0
    for (m in TAG_RE.findAll(content)) {
        val before = content.substring(last, m.range.first).trim()
        if (before.isNotEmpty()) out.add(Part.Text(before))
        val id = m.groupValues[2]
        when (m.groupValues[1]) {
            "стикер", "sticker" -> out.add(Part.Sticker(id))
            "фото", "photo" -> out.add(Part.Photo(id))
            else -> out.add(Part.Snap(id))
        }
        last = m.range.last + 1
    }
    val tail = content.substring(last).trim()
    if (tail.isNotEmpty()) out.add(Part.Text(tail))
    return out
}

class Library(private val ctx: Context) {
    private val userFile = File(ctx.filesDir, "library.json")
    private val stickerDir = File(ctx.filesDir, "stickers").also { it.mkdirs() }
    private val albumDir = File(ctx.filesDir, "album").also { it.mkdirs() }
    val sentDir = File(ctx.filesDir, "sent").also { it.mkdirs() }

    // ---------- чтение ----------

    fun builtIn(): Pair<List<CatalogItem>, List<CatalogItem>> {
        val json = runCatching {
            JSONObject(ctx.assets.open("catalog.json").bufferedReader().use { it.readText() })
        }.getOrNull() ?: return Pair(emptyList(), emptyList())
        return Pair(readAssets(json.optJSONArray("stickers")), readAssets(json.optJSONArray("photos")))
    }

    private fun readAssets(arr: JSONArray?): List<CatalogItem> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            CatalogItem(
                id = o.getString("id"),
                desc = o.getString("desc"),
                builtIn = true,
                model = "file:///android_asset/" + o.getString("file"),
                aspect = o.optDouble("aspect", 1.0).toFloat()
            )
        }
    }

    fun user(): Pair<List<CatalogItem>, List<CatalogItem>> {
        val json = readUserJson()
        return Pair(
            readUser(json.optJSONArray("stickers"), stickerDir),
            readUser(json.optJSONArray("photos"), albumDir)
        )
    }

    private fun readUser(arr: JSONArray?, dir: File): List<CatalogItem> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val f = File(dir, o.getString("file"))
            if (f.exists()) {
                CatalogItem(
                    id = o.getString("id"),
                    desc = o.getString("desc"),
                    builtIn = false,
                    model = f,
                    aspect = o.optDouble("aspect", 1.0).toFloat()
                )
            } else null
        }
    }

    private fun readUserJson(): JSONObject =
        runCatching { JSONObject(userFile.readText()) }.getOrElse { JSONObject() }

    private fun writeUserJson(json: JSONObject) {
        val tmp = File(userFile.path + ".tmp")
        tmp.writeText(json.toString())
        tmp.renameTo(userFile)
    }

    // ---------- добавление и удаление ----------

    fun addSticker(uri: Uri, desc: String, cutBlack: Boolean): CatalogItem {
        var bmp = decodeBitmap(ctx, uri, 512)
        if (cutBlack) bmp = cutBlackBackground(bmp)
        val id = newId()
        val fileName = "$id.png"
        val file = File(stickerDir, fileName)
        saveBitmap(bmp, file, png = true)
        val aspect = bmp.width.toFloat() / bmp.height.toFloat()
        appendUser("stickers", id, desc, fileName, aspect)
        return CatalogItem(id, desc, false, file, aspect)
    }

    fun addPhoto(uri: Uri, desc: String): CatalogItem {
        val bmp = decodeBitmap(ctx, uri, 1280)
        val id = newId()
        val fileName = "$id.jpg"
        val file = File(albumDir, fileName)
        saveBitmap(bmp, file, png = false)
        val aspect = bmp.width.toFloat() / bmp.height.toFloat()
        appendUser("photos", id, desc, fileName, aspect)
        return CatalogItem(id, desc, false, file, aspect)
    }

    /** Фото, которое я отправляю в чат из галереи. Возвращает имя файла. */
    fun saveSnap(uri: Uri): String {
        val bmp = decodeBitmap(ctx, uri, 1280)
        val name = "s" + System.currentTimeMillis() + ".jpg"
        saveBitmap(bmp, File(sentDir, name), png = false)
        return name
    }

    @Synchronized
    private fun appendUser(key: String, id: String, desc: String, file: String, aspect: Float) {
        val json = readUserJson()
        val arr = json.optJSONArray(key) ?: JSONArray().also { json.put(key, it) }
        arr.put(
            JSONObject()
                .put("id", id)
                .put("desc", desc)
                .put("file", file)
                .put("aspect", aspect.toDouble())
        )
        writeUserJson(json)
    }

    @Synchronized
    fun remove(item: CatalogItem) {
        (item.model as? File)?.delete()
        val json = readUserJson()
        for (key in listOf("stickers", "photos")) {
            val old = json.optJSONArray(key) ?: continue
            val kept = JSONArray()
            for (i in 0 until old.length()) {
                val o = old.getJSONObject(i)
                if (o.getString("id") != item.id) kept.put(o)
            }
            json.put(key, kept)
        }
        writeUserJson(json)
    }

    private fun newId(): String =
        "u" + System.currentTimeMillis().toString(36) + (0..35).random().toString(36)
}

// ---------- работа с картинками ----------

private fun decodeBitmap(ctx: Context, uri: Uri, maxSide: Int): Bitmap {
    val bmp: Bitmap
    if (Build.VERSION.SDK_INT >= 28) {
        val src = ImageDecoder.createSource(ctx.contentResolver, uri)
        bmp = ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            val scale = maxSide.toFloat() / maxOf(w, h).toFloat()
            if (scale < 1f) {
                decoder.setTargetSize(
                    (w * scale).toInt().coerceAtLeast(1),
                    (h * scale).toInt().coerceAtLeast(1)
                )
            }
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw IllegalStateException("Не удалось открыть картинку")
    }
    return fitBitmap(bmp, maxSide)
}

private fun fitBitmap(bmp: Bitmap, maxSide: Int): Bitmap {
    val side = maxOf(bmp.width, bmp.height)
    if (side <= maxSide) return bmp
    val scale = maxSide.toFloat() / side.toFloat()
    return Bitmap.createScaledBitmap(
        bmp,
        (bmp.width * scale).toInt().coerceAtLeast(1),
        (bmp.height * scale).toInt().coerceAtLeast(1),
        true
    )
}

private fun saveBitmap(bmp: Bitmap, file: File, png: Boolean) {
    file.outputStream().use { out ->
        if (png) bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        else bmp.compress(Bitmap.CompressFormat.JPEG, 88, out)
    }
}

/**
 * Убирает чёрный фон вокруг стикера (заливкой от краёв кадра) и обрезает лишнее.
 * Чёрные детали внутри белого контура остаются нетронутыми.
 */
private fun cutBlackBackground(src: Bitmap, threshold: Int = 70): Bitmap {
    val w = src.width
    val h = src.height
    val px = IntArray(w * h)
    src.getPixels(px, 0, w, 0, 0, w, h)

    fun dark(i: Int): Boolean {
        val c = px[i]
        val lum = (((c shr 16) and 255) * 3 + ((c shr 8) and 255) * 6 + (c and 255)) / 10
        return lum < threshold
    }

    val seen = BooleanArray(w * h)
    val stack = IntArray(w * h)
    var sp = 0
    fun push(i: Int) {
        if (!seen[i] && dark(i)) {
            seen[i] = true
            stack[sp++] = i
        }
    }

    // нижний край не засеваем: ноги стикера могут быть обрезаны краем картинки
    for (x in 0 until w) push(x)
    for (y in 0 until h) {
        push(y * w)
        push(y * w + w - 1)
    }
    while (sp > 0) {
        val i = stack[--sp]
        val x = i % w
        val y = i / w
        if (x > 0) push(i - 1)
        if (x < w - 1) push(i + 1)
        if (y > 0) push(i - w)
        if (y < h - 1) push(i + w)
    }

    var x0 = w
    var y0 = h
    var x1 = -1
    var y1 = -1
    for (i in px.indices) {
        if (seen[i]) {
            px[i] = 0
        } else {
            val x = i % w
            val y = i / w
            if (x < x0) x0 = x
            if (x > x1) x1 = x
            if (y < y0) y0 = y
            if (y > y1) y1 = y
        }
    }
    if (x1 < x0 || y1 < y0) return src

    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    out.setPixels(px, 0, w, 0, 0, w, h)
    return Bitmap.createBitmap(out, x0, y0, x1 - x0 + 1, y1 - y0 + 1)
}

fun imageAspect(file: File): Float {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, o)
    return if (o.outWidth > 0 && o.outHeight > 0) o.outWidth.toFloat() / o.outHeight.toFloat() else 0.75f
}

/**
 * Фото для модели: уменьшенный JPEG в base64. Перекодирование стирает служебные данные (геометки и т.п.).
 * Если получается слишком тяжело, качество снижается ступенями.
 */
fun encodeForModel(file: File, maxSide: Int = 896): String? {
    if (!file.exists()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val bmp = BitmapFactory.decodeFile(file.path, opts) ?: return null
    val scaled = fitBitmap(bmp, maxSide)
    for (q in intArrayOf(78, 62, 48)) {
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, q, out)
        val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        if (b64.length <= 420_000 || q == 48) return b64
    }
    return null
}
