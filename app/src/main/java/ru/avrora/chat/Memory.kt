package ru.avrora.chat

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MemOp(val op: String, val id: String, val kind: String, val text: String)
data class CompressResult(val episode: String, val ops: List<MemOp>)

fun newId(prefix: String): String =
    prefix + System.currentTimeMillis().toString(36) + (0..35).random().toString(36)

private val PROTECTED_KINDS = setOf("язык", "поворот", "тон")

/**
 * Применение правок ядра, предложенных моделью. Защита от эрозии:
 * - правка не может заметно укоротить запись (иначе сохраняется и старая, и новая);
 * - записи о языке, поворотах и тоне модель удалять не может, это может только человек.
 */
fun applyOps(core: List<CoreEntry>, ops: List<MemOp>, now: Long): List<CoreEntry> {
    val list = core.toMutableList()
    for (op in ops) {
        val text = op.text.trim()
        when (op.op) {
            "add" -> {
                if (text.isNotEmpty() && op.kind in CORE_KINDS) {
                    list.add(CoreEntry(newId("c"), op.kind, text, now))
                }
            }
            "update" -> {
                val i = list.indexOfFirst { it.id == op.id }
                if (i >= 0 && text.isNotEmpty()) {
                    val old = list[i]
                    if (text.length * 10 >= old.text.length * 7) {
                        list[i] = old.copy(text = text)
                    } else {
                        list.add(CoreEntry(newId("c"), old.kind, text, now))
                    }
                }
            }
            "remove" -> {
                val e = list.firstOrNull { it.id == op.id }
                if (e != null && e.kind !in PROTECTED_KINDS) list.remove(e)
            }
        }
    }
    return list
}

/** Что уходит Авроре в запросе: ядро целиком, свежие эпизоды и старые эпизоды, всплывающие по теме разговора. */
object MemoryContext {
    private const val CORE_CAP = 6000
    private const val RECENT = 3
    private const val RETRIEVED = 2
    private val WORD = Regex("""\p{L}{4,}""")

    private fun stems(s: String): Set<String> =
        WORD.findAll(s.lowercase()).map { it.value.take(5) }.toSet()

    fun build(mem: MemoryData, recentText: String): String {
        if (mem.core.isEmpty() && mem.episodes.isEmpty()) return ""
        val fmt = SimpleDateFormat("d MMM yyyy", Locale("ru"))
        val sb = StringBuilder()

        if (mem.core.isNotEmpty()) {
            sb.append("НАШ ЯЗЫК И ОБЩАЯ ИСТОРИЯ (основа ваших отношений; слова и формулы здесь имеют тот смысл, что записан):\n")
            val ordered = CORE_KINDS.flatMap { k -> mem.core.filter { it.kind == k } } +
                mem.core.filter { it.kind !in CORE_KINDS }
            var used = 0
            for (e in ordered) {
                val line = "- [${e.kind}] ${e.text}\n"
                if (used + line.length > CORE_CAP) continue
                sb.append(line)
                used += line.length
            }
        }

        val recent = mem.episodes.takeLast(RECENT)
        val older = mem.episodes.dropLast(RECENT)
        if (recent.isNotEmpty()) {
            sb.append("\nНЕДАВНИЕ ЭПИЗОДЫ (подробный пересказ того, что было):\n")
            recent.forEach { sb.append("- (${fmt.format(Date(it.to))}) ${it.text}\n") }
        }

        val q = stems(recentText)
        if (q.isNotEmpty() && older.isNotEmpty()) {
            val hits = older
                .map { ep -> Pair(ep, stems(ep.text).intersect(q).size) }
                .filter { it.second >= 3 }
                .sortedByDescending { it.second }
                .take(RETRIEVED)
            if (hits.isNotEmpty()) {
                sb.append("\nЧТО ВСПЛЫВАЕТ ИЗ БОЛЕЕ РАННЕГО (по теме разговора):\n")
                hits.forEach { sb.append("- (${fmt.format(Date(it.first.to))}) ${it.first.text}\n") }
            }
        }
        return sb.toString().trim()
    }
}

private val LINE_RE = Regex("""^\s*(Адонис|Аврора)\s*:\s*(.*)$""")

/** Разбор вставленного отрывка: строки вида «Адонис: ...» и «Аврора: ...», строки без метки продолжают предыдущую реплику. */
fun parseTranscript(text: String): List<Pair<String, String>> {
    val out = ArrayList<Pair<String, String>>()
    for (raw in text.lines()) {
        val m = LINE_RE.matchEntire(raw)
        if (m != null) {
            out.add(Pair(if (m.groupValues[1] == "Адонис") "user" else "assistant", m.groupValues[2]))
        } else if (raw.isNotBlank()) {
            if (out.isEmpty()) {
                out.add(Pair("user", raw.trim()))
            } else {
                val last = out.removeAt(out.size - 1)
                out.add(Pair(last.first, last.second + "\n" + raw.trim()))
            }
        }
    }
    return out
}

/** Короткое описание предложения для журнала и экрана памяти. */
fun proposalSummary(p: Proposal): String {
    val what = when (p.op) {
        "add" -> "добавить"
        "update" -> "изменить"
        "remove" -> "удалить"
        else -> p.op
    }
    return "$what [${p.kind}] ${p.text}".take(180)
}

/** Применение предложения к ядру. direct=false пускает правку от сжатия через защитные правила; после её «да» вызывается с direct=true. */
fun applyProposal(core: List<CoreEntry>, p: Proposal, text: String, now: Long, direct: Boolean): List<CoreEntry> {
    if (!direct && p.source == "summary") {
        return applyOps(core, listOf(MemOp(p.op, p.targetId, p.kind, text)), now)
    }
    return when (p.op) {
        "add" -> core + CoreEntry(newId("c"), p.kind, text, now)
        "update" -> core.map { if (it.id == p.targetId) it.copy(kind = p.kind, text = text) else it }
        "remove" -> core.filter { it.id != p.targetId }
        else -> core
    }
}
