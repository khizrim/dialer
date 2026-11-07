package com.khizrim.dialer.data

import com.khizrim.dialer.model.Person

/** Где нашлось совпадение, если не прямо в имени. */
enum class MatchVia { DIRECT, LAYOUT, TRANSLIT }
enum class MatchField { NAME, NICKNAME, COMPANY, TITLE, PHONE }

data class TextHit(
    val person: Person,
    /** Подсветка в имени; null, если совпало не в имени. */
    val nameRange: IntRange?,
    val field: MatchField,
    /** Текст поля, где нашлось (компания, прозвище, номер), для второй строки. */
    val fieldText: String?,
    val via: MatchVia,
    val rank: Int,
)

/**
 * Поиск по имени с системной клавиатуры. Регистр и ё=е не важны, понимает транслит в обе стороны
 * и текст, набранный не в той раскладке (Lbvf → Дима). Начало слова выше середины, дальше — частота общения.
 */
object TextSearch {
    private const val QW = "qwertyuiop[]asdfghjkl;'zxcvbnm,.`"
    private const val YC = "йцукенгшщзхъфывапролджэячсмитьбюё"

    fun norm(s: String): String = s.lowercase().replace('ё', 'е')

    /** Lbvf → дима, Вшьф → dima. */
    fun swapLayout(s: String): String = buildString {
        for (ch in s) {
            val i = QW.indexOf(ch)
            val j = YC.indexOf(ch)
            append(if (i >= 0) YC[i] else if (j >= 0) QW[j] else ch)
        }
    }.replace('ё', 'е')

    private val lat2cyr = listOf(
        "shch" to "щ", "sch" to "щ", "yo" to "е", "zh" to "ж", "kh" to "х", "ts" to "ц", "ch" to "ч", "sh" to "ш",
        "yu" to "ю", "iu" to "ю", "ya" to "я", "ia" to "я", "ye" to "е", "a" to "а", "b" to "б", "v" to "в", "g" to "г",
        "d" to "д", "e" to "е", "z" to "з", "i" to "и", "y" to "й", "k" to "к", "l" to "л", "m" to "м", "n" to "н",
        "o" to "о", "p" to "п", "r" to "р", "s" to "с", "t" to "т", "u" to "у", "f" to "ф", "h" to "х", "c" to "к",
        "w" to "в", "x" to "кс", "q" to "к", "j" to "дж",
    )
    private val cyr2lat = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ж' to "zh", 'з' to "z", 'и' to "i",
        'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s",
        'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "kh", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "shch",
        'ъ' to "", 'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya",
    )

    fun toCyr(s: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val hit = lat2cyr.firstOrNull { s.startsWith(it.first, i) }
            if (hit != null) { out.append(hit.second); i += hit.first.length } else { out.append(s[i]); i++ }
        }
        return out.toString()
    }

    fun toLat(s: String): String = buildString { s.forEach { append(cyr2lat[it] ?: it.toString()) } }

    /** Варианты запроса: как набрано, другая раскладка, транслит. */
    fun variants(query: String): List<Pair<String, MatchVia>> {
        val q = norm(query.trim())
        if (q.isEmpty()) return emptyList()
        val hasLat = q.any { it in 'a'..'z' }
        val hasCyr = q.any { it in 'а'..'я' }
        val out = LinkedHashMap<String, MatchVia>()
        out[q] = MatchVia.DIRECT
        swapLayout(q).let { if (it !in out) out[it] = MatchVia.LAYOUT }
        if (hasLat) toCyr(q).let { if (it !in out) out[it] = MatchVia.TRANSLIT }
        if (hasCyr) toLat(q).let { if (it !in out) out[it] = MatchVia.TRANSLIT }
        return out.map { it.key to it.value }
    }

    /** Позиция совпадения: в начале слова (rank 0) лучше, чем внутри (rank 1). */
    private fun locate(field: String, v: String): Pair<Int, Int>? {
        var at = field.indexOf(v)
        var best: Pair<Int, Int>? = null
        while (at >= 0) {
            val wordStart = at == 0 || !field[at - 1].isLetterOrDigit()
            if (wordStart) return at to 0
            if (best == null) best = at to 1
            at = field.indexOf(v, at + 1)
        }
        return best
    }

    fun find(people: List<Person>, query: String, score: (Person) -> Double, limit: Int = 8): List<TextHit> {
        val vs = variants(query)
        if (vs.isEmpty()) return emptyList()
        val digits = query.filter { it.isDigit() }
        val lat = norm(query.trim()).takeIf { q -> q.any { it in 'a'..'z' } && q.none { it in 'а'..'я' } }
        val numeric = digits.length >= 3 && digits.length >= query.count { !it.isWhitespace() } - 2
        return people.mapNotNull { p ->
            var best: TextHit? = null
            fun offer(h: TextHit) { if (best == null || h.rank < best!!.rank) best = h }
            val name = norm(p.name)
            for ((v, via) in vs) {
                locate(name, v)?.let { (at, r) -> offer(TextHit(p, at until at + v.length, MatchField.NAME, null, via, r * 10 + via.ordinal)) }
                listOf(MatchField.NICKNAME to p.nickname, MatchField.COMPANY to p.company, MatchField.TITLE to p.title).forEach { (f, text) ->
                    if (text != null) locate(norm(text), v)?.let { (_, r) -> offer(TextHit(p, null, f, text, via, 20 + r * 10 + via.ordinal)) }
                }
            }
            // после прямых вариантов, чтобы у них осталась подсветка; латиница сравнивается и с именем в латинице: «ilya» → Илья, «yulya» → Юля (по буквам «ilya» дал бы «илйа»)
            if (lat != null) {
                locate(toLat(name), lat)?.let { (_, r) -> offer(TextHit(p, null, MatchField.NAME, null, MatchVia.TRANSLIT, r * 10 + MatchVia.TRANSLIT.ordinal)) }
                p.nickname?.let { n -> locate(toLat(norm(n)), lat)?.let { (_, r) -> offer(TextHit(p, null, MatchField.NICKNAME, n, MatchVia.TRANSLIT, 20 + r * 10 + MatchVia.TRANSLIT.ordinal)) } }
            }
            if (numeric) p.phones.firstOrNull { it.number.filter(Char::isDigit).contains(digits) }?.let {
                offer(TextHit(p, null, MatchField.PHONE, it.number, MatchVia.DIRECT, 40))
            }
            best?.let { it to score(p) }
        }
            .sortedWith(compareBy<Pair<TextHit, Double>> { it.first.rank }.thenByDescending { it.second }.thenBy { it.first.person.name })
            .take(limit)
            .map { it.first }
    }
}
