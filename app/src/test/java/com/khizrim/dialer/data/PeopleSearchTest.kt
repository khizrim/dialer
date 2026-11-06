package com.khizrim.dialer.data

import com.khizrim.dialer.model.Person
import com.khizrim.dialer.model.Phone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PeopleSearchTest {
    private fun person(id: Long, name: String, vararg phones: String) =
        Person(id, "k$id", name, phones.map { Phone(it, "") }, emptyList(), null, false)

    private val anya = person(1, "Аня Смирнова", "+79161234567")
    private val anton = person(2, "Антон", "+79035550000")
    private val ivan = person(3, "Иван Петров", "+79260001122")
    private val anna = person(4, "Анна-Мария", "+79990000000")
    private val people = listOf(anya, anton, ivan, anna)

    private fun search(scores: Map<Long, Double> = emptyMap()) =
        PeopleSearch.build(people, { scores[it.id] ?: 0.0 }, { it })

    @Test fun firstWordPrefix() {
        val hits = search().find("25")
        assertTrue(hits.any { it.person == anya })
        assertTrue(hits.any { it.person == anton })
        assertEquals(0..1, hits.first { it.person == anya }.nameRange)
    }

    @Test fun frequentFirstAmongEqual() {
        assertEquals(anton, search(mapOf(2L to 5.0)).find("25").first().person)
        assertEquals(anya, search(mapOf(1L to 5.0)).find("25").first().person)
    }

    @Test fun laterWordAndHyphen() {
        // «Петров» = 536652, «Мария» после дефиса = 52649
        assertEquals(ivan, search().find("5366").single().person)
        val m = search().find("5264").single()
        assertEquals(anna, m.person)
        assertEquals(5..8, m.nameRange)
    }

    @Test fun initials() {
        // И + П = 4 5
        assertEquals(ivan, search().find("45").first().person)
    }

    @Test fun phoneDigitsAndEightAsSeven() {
        assertEquals(anya, search().find("1234").single().person)
        val h = search().find("8916").single()
        assertEquals(anya, h.person)
    }

    @Test fun suggestionsOnlyFrequent() {
        val s = search(mapOf(3L to 2.0, 1L to 1.0)).suggestions()
        assertEquals(listOf(ivan, anya), s.map { it.person })
    }

    @Test fun emptyTypingFindsNothing() {
        assertTrue(search().find("").isEmpty())
    }
}
