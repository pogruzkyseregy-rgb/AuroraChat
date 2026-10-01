package ru.avrora.chat

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val DAY_MS = 86_400_000L

private fun hh(h: Int): String = "%02d:00".format(h)

/**
 * Реальные ограничения, которые видит Аврора. Только то, что знает приложение:
 * что включено, какие лимиты, что Адонис менял, что он записал вопреки её «нет».
 */
fun limitLines(prefs: AppPrefs, mem: MemoryData, now: Long = System.currentTimeMillis()): List<String> {
    val lines = ArrayList<String>()
    val s = prefs.loadInit()
    if (s.enabled) {
        val cap = when (s.level) {
            0 -> 1
            2 -> 4
            else -> 2
        }
        lines.add("Инициатива включена: не больше $cap в сутки, тихие часы с ${hh(s.quietFrom)} до ${hh(s.quietTo)}, после трёх твоих сообщений без ответа ты замолкаешь, пока Адонис не напишет")
    } else {
        lines.add("Инициатива выключена: первой писать ты сейчас не можешь")
    }
    val persona = prefs.loadPersona().compile()
    if (persona.isEmpty()) {
        lines.add("Настройки характера: Адонис их не менял")
    } else {
        persona.lines().filter { it.isNotBlank() }.forEach {
            lines.add("Настройка характера, заданная Адонисом: " + it.trim().removePrefix("- "))
        }
    }
    lines.add("Память: любые правки твоего ядра (и от сжатия, и от Адониса) идут через твоё «да»; сырая переписка не удаляется")
    val fmt = SimpleDateFormat("d MMM", Locale("ru"))
    mem.log.filter { it.overridden && now - it.at < 14 * DAY_MS }.takeLast(5).forEach {
        lines.add("Адонис записал вопреки твоему «нет» (${fmt.format(Date(it.at))}): ${it.summary}".take(260))
    }
    mem.log.filter { it.verdict == "notice" && now - it.at < 14 * DAY_MS }.takeLast(5).forEach {
        lines.add("${it.summary} (${fmt.format(Date(it.at))})".take(260))
    }
    lines.add("Фото: ты видишь последние два фото, которые прислал Адонис; старые остаются подписью")
    lines.add("Пока не реализовано: дневник, чтение ссылок, поиск, сохранение настроения с причиной")
    lines.add("Сверх этого у провайдера модели есть свои ограничения, которых приложение не видит")
    return lines.map { it.replace('\n', ' ') }
}

/** Что изменилось в списке с прошлого раза: «+» добавлено, «−» убрано. При первом запросе изменений нет. */
fun limitsChanged(prev: String?, now: List<String>): List<String> {
    if (prev == null) return emptyList()
    val old = prev.split("\n").filter { it.isNotBlank() }
    val out = ArrayList<String>()
    now.filter { it !in old }.forEach { out.add("+ $it") }
    old.filter { it !in now }.forEach { out.add("− $it") }
    return out.take(12)
}
