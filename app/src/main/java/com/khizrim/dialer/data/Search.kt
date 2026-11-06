package com.khizrim.dialer.data

import com.khizrim.dialer.model.Person

/** Совпадение в поиске: какие символы имени и номера подсветить. */
data class SearchHit(val person: Person, val phone: String, val nameRange: IntRange?, val phoneRange: IntRange?, val rank: Int)

/**
 * Поиск с клавиатуры: T9 по началу любого слова имени, по инициалам и по цифрам номера.
 * Индекс строится один раз при загрузке, чтобы набор не тормозил на сотнях контактов.
 */
class PeopleSearch private constructor(private val entries: List<Entry>) {
    private class Word(val start: Int, val t9: String)
    private class Phone(val shown: String, val digits: String)
    private class Entry(val person: Person, val words: List<Word>, val initials: String, val phones: List<Phone>, val score: Double)

    /** Ближе к началу — лучше: имя с первого слова, другое слово, инициалы, номер. */
    fun find(typed: String, limit: Int = 4): List<SearchHit> {
        val kpd = PhoneFormat.digits(typed)
        if (kpd.isEmpty()) return emptyList()
        val alt = if (kpd.startsWith("8") && kpd.length > 1) "7" + kpd.drop(1) else null
        return entries.mapNotNull { e ->
            var rank = Int.MAX_VALUE
            var nameRange: IntRange? = null
            for ((i, w) in e.words.withIndex()) {
                if (w.t9.startsWith(kpd)) {
                    rank = if (i == 0) 0 else 1
                    nameRange = w.start until w.start + kpd.length
                    break
                }
            }
            if (nameRange == null && kpd.length >= 2 && e.initials == kpd) rank = 2
            var phone = e.phones.firstOrNull()?.shown ?: ""
            var phoneRange: IntRange? = null
            for (ph in e.phones) {
                var at = ph.digits.indexOf(kpd)
                var len = kpd.length
                if (at < 0 && alt != null) { at = ph.digits.indexOf(alt); len = alt.length }
                if (at >= 0) {
                    phone = ph.shown
                    phoneRange = PhoneFormat.digitPos(ph.shown, at)..PhoneFormat.digitPos(ph.shown, at + len - 1)
                    if (rank == Int.MAX_VALUE) rank = 3
                    break
                }
            }
            if (rank == Int.MAX_VALUE) null else Triple(SearchHit(e.person, phone, nameRange, phoneRange, rank), e.score, e.person.name)
        }
            // среди равных совпадений выше тот, с кем общаетесь чаще
            .sortedWith(compareBy<Triple<SearchHit, Double, String>> { it.first.rank }.thenByDescending { it.second }.thenBy { it.third })
            .take(limit)
            .map { it.first }
    }

    /** Подсказки до набора: с кем общаетесь чаще всего и избранные. */
    fun suggestions(limit: Int = 4): List<SearchHit> =
        entries.filter { it.score > 0.0 }
            .sortedByDescending { it.score }
            .take(limit)
            .map { SearchHit(it.person, it.phones.firstOrNull()?.shown ?: "", null, null, 0) }

    companion object {
        val EMPTY = PeopleSearch(emptyList())
        private val separators = Regex("[\\s\\-]+")

        fun build(people: List<Person>, score: (Person) -> Double, pretty: (String) -> String = { PhoneFormat.pretty(it) }): PeopleSearch =
            PeopleSearch(people.map { p ->
                val words = ArrayList<Word>()
                separators.split(p.name).fold(0) { from, part ->
                    if (part.isEmpty()) return@fold from
                    val at = p.name.indexOf(part, from)
                    words += Word(at, T9.encode(part))
                    at + part.length
                }
                val initials = words.joinToString("") { it.t9.take(1) }
                val phones = p.phones.map { val s = pretty(it.number); Phone(s, PhoneFormat.digits(s)) }
                Entry(p, words, initials, phones, score(p))
            })
    }
}
