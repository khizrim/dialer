package com.khizrim.dialer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeclensionTest {
    private fun ins(name: String) = Declension.instrumental(name)

    @Test fun familyWords() {
        assertEquals("Мамой", ins("Мама"))
        assertEquals("Бабушкой", ins("Бабушка"))
    }

    @Test fun firstNames() {
        assertEquals("Аней", ins("Аня"))
        assertEquals("Димой", ins("Дима"))
        assertEquals("Ксенией", ins("Ксения"))
        assertEquals("Игорем", ins("Игорь"))
        assertEquals("Любовью", ins("Любовь"))
        assertEquals("Борисом", ins("Борис"))
    }

    @Test fun fullNames() {
        assertEquals("Иваном Петровым", ins("Иван Петров"))
        assertEquals("Еленой Соколовой", ins("Елена Соколова"))
        assertEquals("Виктором Сергеевичем", ins("Виктор Сергеевич"))
        assertEquals("Алексеем В.", ins("Алексей В."))
    }

    @Test fun nonRussianNamesAreLeftAlone() {
        assertNull(ins("Mike"))
        assertNull(ins(""))
    }

    @Test fun preposition() {
        assertEquals("с Мамой", Declension.withPreposition("Мамой"))
        assertEquals("со Святославом", Declension.withPreposition("Святославом"))
        assertEquals("с Сергеем", Declension.withPreposition("Сергеем"))
    }
}
