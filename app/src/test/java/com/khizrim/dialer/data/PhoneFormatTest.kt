package com.khizrim.dialer.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneFormatTest {
    @Test fun russianMask() {
        assertEquals("+7 (916) 123-45-67", PhoneFormat.formatTyping("+79161234567", "RU"))
        assertEquals("8 (916) 123-45-67", PhoneFormat.formatTyping("89161234567", "RU"))
        assertEquals("123-45-67", PhoneFormat.formatTyping("1234567", "RU"))
        assertEquals("7 (916) 123-45-67", PhoneFormat.formatTyping("79161234567", "RU"))
        assertEquals("712-34-56", PhoneFormat.formatTyping("7123456", "RU"))
    }

    @Test fun prettyWithoutPlus() = assertEquals("+7 (903) 555-66-77", PhoneFormat.pretty("79035556677", "RU"))

    @Test fun usMask() {
        assertEquals("(415) 555-0132", PhoneFormat.formatTyping("4155550132", "US"))
        assertEquals("8 (916) 123-45-67", PhoneFormat.formatTyping("89161234567", "US"))
    }

    @Test fun t9() {
        assertEquals("259", T9.encode("Аня"))
        assertEquals("6453", T9.encode("Mike"))
    }
}
