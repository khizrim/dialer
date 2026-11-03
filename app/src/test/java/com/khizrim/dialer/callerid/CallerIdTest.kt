package com.khizrim.dialer.callerid

import com.khizrim.dialer.callerid.CallerFacts.Kind
import com.khizrim.dialer.callerid.CallerFacts.Suspicion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CallerIdTest {
    private val db: NumberDb by lazy {
        // юнит-тесты запускаются из каталога модуля app
        File("src/main/assets/callerid/ru.bin").inputStream().use { NumberDb.read(it) }
    }

    private fun facts(number: String, home: String = "RU", verificationFailed: Boolean = false) =
        Classifier.classify(CallerNumber.parse(number, home), db, verificationFailed)

    @Test fun parsesRussianForms() {
        val expected = CallerNumber.Russian(9161234567L)
        assertEquals(expected, CallerNumber.parse("+7 (916) 123-45-67"))
        assertEquals(expected, CallerNumber.parse("8 916 123 45 67"))
        assertEquals(expected, CallerNumber.parse("79161234567"))
        assertEquals(expected, CallerNumber.parse("9161234567"))
        assertEquals(916, expected.code)
    }

    @Test fun parsesOtherNumbers() {
        assertEquals(CallerNumber.Hidden, CallerNumber.parse(""))
        assertEquals(CallerNumber.Hidden, CallerNumber.parse(null))
        assertEquals(CallerNumber.Service("900"), CallerNumber.parse("900"))
        assertEquals(CallerNumber.Kazakhstan(7011234567L), CallerNumber.parse("+7 701 123 45 67"))
        assertEquals(CallerNumber.Foreign(375, "BY"), CallerNumber.parse("+375 29 123-45-67"))
        assertEquals(CallerNumber.Foreign(1, "US"), CallerNumber.parse("+1 212 555 0100"))
        assertEquals(CallerNumber.Foreign(44, "GB"), CallerNumber.parse("0044 20 7946 0958"))
        assertEquals(CallerNumber.Foreign(49, "DE"), CallerNumber.parse("810 49 30 1234567"))
        assertEquals(CallerNumber.Foreign(882, null), CallerNumber.parse("+882 1234 5678"))
        // без «+» за пределами России номер читаем как местный
        assertEquals(CallerNumber.Unknown, CallerNumber.parse("89161234567", homeIso = "DE"))
    }

    @Test fun databaseLoads() {
        assertTrue(db.size > 300_000)
        assertTrue(db.updated.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
    }

    @Test fun mobileOperatorAndRegion() {
        val mts = facts("+7 916 123-45-67")
        assertEquals(Kind.MOBILE, mts.kind)
        assertEquals("МТС", mts.operator)
        assertEquals("Москва и область", mts.region)
        assertNull(mts.suspicion)

        val beeline = facts("8 903 123-45-67")
        assertEquals("Билайн", beeline.operator)
    }

    @Test fun landlineRegion() {
        val spb = facts("+7 812 200-00-00")
        assertEquals(Kind.LANDLINE, spb.kind)
        assertNotNull(spb.region)
        assertTrue(spb.region!!.contains("Петербург"))

        val msk = facts("+7 495 123-45-67")
        assertTrue(msk.region!!.startsWith("Москва"))

        // дыра в городском реестре: не пугаем
        val gap = facts("+7 812 123-45-67")
        assertEquals(Kind.LANDLINE, gap.kind)
        assertFalse(gap.suspicious)
    }

    @Test fun tollFree() {
        assertEquals(Kind.TOLL_FREE, facts("8 800 555-35-35").kind)
        assertFalse(facts("8 800 555-35-35").suspicious)
    }

    @Test fun unallocatedIsSuspicious() {
        // код 945 на октябрь 2026 никому не выделен
        val f = facts("+7 945 123-45-67")
        assertEquals(Kind.UNALLOCATED, f.kind)
        assertEquals(Suspicion.UNALLOCATED, f.suspicion)
    }

    @Test fun foreignAndRiskyCodes() {
        val by = facts("+375 29 123-45-67")
        assertEquals(Kind.FOREIGN, by.kind)
        assertEquals("BY", by.countryIso)
        assertFalse(by.suspicious)

        val risky = facts("+232 76 123456")
        assertEquals(Suspicion.RISKY_COUNTRY, risky.suspicion)

        assertEquals("KZ", facts("+7 701 123 45 67").countryIso)
    }

    @Test fun spoofedByNetwork() {
        assertEquals(Suspicion.SPOOFED, facts("+7 916 123-45-67", verificationFailed = true).suspicion)
    }

    @Test fun withoutDatabaseNothingIsFlagged() {
        val f = Classifier.classify(CallerNumber.parse("+7 945 123-45-67"), db = null)
        assertEquals(Kind.UNKNOWN, f.kind)
        assertFalse(f.suspicious)
    }

    @Test fun everyRangeEdgeIsFound() {
        // первый и последний номер известного диапазона МТС 916
        assertEquals("МТС", facts("+7 916 000-00-00").operator)
        assertEquals("МТС", facts("+7 916 999-99-99").operator)
    }
}

class SpamPolicyTest {
    @Test fun strongSignalsAlwaysAct() {
        assertTrue(SpamPolicy.acts(Suspicion.SPOOFED, explicitChoice = false, dbAgeDays = 500))
        assertTrue(SpamPolicy.acts(Suspicion.RISKY_COUNTRY, explicitChoice = false, dbAgeDays = null))
    }

    @Test fun unallocatedOnlyLabelsByDefault() {
        assertFalse(SpamPolicy.acts(Suspicion.UNALLOCATED, explicitChoice = false, dbAgeDays = 10))
    }

    @Test fun unallocatedActsOnlyWhenChosenAndFresh() {
        assertTrue(SpamPolicy.acts(Suspicion.UNALLOCATED, explicitChoice = true, dbAgeDays = 30))
        assertFalse(SpamPolicy.acts(Suspicion.UNALLOCATED, explicitChoice = true, dbAgeDays = 120))
        assertFalse(SpamPolicy.acts(Suspicion.UNALLOCATED, explicitChoice = true, dbAgeDays = null))
    }

    @Test fun nothingToActOn() {
        assertFalse(SpamPolicy.acts(null, explicitChoice = true, dbAgeDays = 1))
    }
}

class RecentContactTest {
    private val now = 1_800_000_000_000L
    private val min = 60_000L
    private val day = 86_400_000L
    private fun e(n: String, type: Int, ago: Long) = RecentContact.Entry(n, type, now - ago)

    @Test fun outgoingWithinWeekIsExempt() {
        assertTrue(RecentContact.exempt("+7 916 123-45-67", listOf(e("89161234567", 2, 6 * day)), now))
        assertFalse(RecentContact.exempt("+7 916 123-45-67", listOf(e("89161234567", 2, 8 * day)), now))
    }

    @Test fun repeatWithinThreeMinutesIsExempt() {
        assertTrue(RecentContact.exempt("89161234567", listOf(e("+79161234567", 3, 2 * min)), now))
        assertFalse(RecentContact.exempt("89161234567", listOf(e("+79161234567", 3, 10 * min)), now))
    }

    @Test fun otherNumbersAndHiddenAreNot() {
        assertFalse(RecentContact.exempt("89161234567", listOf(e("89031234567", 2, min)), now))
        assertFalse(RecentContact.exempt("", listOf(e("", 2, min)), now))
    }
}

class NetworkNameTest {
    @Test fun usableNames() {
        assertEquals("ООО Ромашка", NetworkName.usable("  ООО Ромашка "))
        assertNull(NetworkName.usable("+7 (916) 123-45-67"))
        assertNull(NetworkName.usable(""))
        assertNull(NetworkName.usable(null))
    }

    @Test fun spamLabelsFromOperators() {
        assertTrue(NetworkName.looksLikeSpam("Возможно спам"))
        assertTrue(NetworkName.looksLikeSpam("Мошенники"))
        assertTrue(NetworkName.looksLikeSpam("SPAM"))
        assertFalse(NetworkName.looksLikeSpam("Сбербанк"))
        assertFalse(NetworkName.looksLikeSpam(null))
    }
}
