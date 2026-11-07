package com.khizrim.dialer.data

import com.khizrim.dialer.model.Person
import com.khizrim.dialer.model.Phone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSearchTest {
    private fun p(id: Long, name: String, nick: String? = null, company: String? = null, phone: String = "+7999000000$id") =
        Person(id, "k$id", name, listOf(Phone(phone, "mobile")), emptyList(), null, false, nick, company, null)

    private val people = listOf(p(1, "Дима Петров"), p(2, "Артём"), p(3, "Khayvat"), p(4, "Олег", company = "Яндекс"), p(5, "Вадим"), p(6, "Илья Смирнов"), p(7, "Юля"), p(8, "Наталья"))

    private fun names(q: String) = TextSearch.find(people, q, { 0.0 }).map { it.person.name }

    @Test fun direct() = assertEquals("Дима Петров", names("дим").first())
    @Test fun yoIsYe() = assertEquals(listOf("Артём"), names("артем"))
    @Test fun wrongLayout() = assertEquals("Дима Петров", names("Lbvf").first())
    @Test fun latinToCyr() = assertEquals("Дима Петров", names("dima").first())
    @Test fun cyrToLatin() = assertEquals(listOf("Khayvat"), names("хайв"))
    @Test fun company() {
        val h = TextSearch.find(people, "янд", { 0.0 }).single()
        assertEquals(MatchField.COMPANY, h.field)
    }
    @Test fun wordStartBeatsMiddle() = assertEquals(listOf("Дима Петров", "Вадим"), names("дим"))
    @Test fun secondWord() = assertTrue("Дима Петров" in names("петр"))
    @Test fun number() = assertEquals(listOf("Олег"), names("0004"))
    @Test fun softSignLatin() = assertEquals(listOf("Илья Смирнов"), names("Ilya"))
    @Test fun yuLatin() = assertEquals(listOf("Юля"), names("yulya"))
    @Test fun natalya() = assertEquals(listOf("Наталья"), names("natalya"))
    @Test fun latinKeepsHighlight() = assertEquals(0 until 4, TextSearch.find(people, "dima", { 0.0 }).first().nameRange)
}
