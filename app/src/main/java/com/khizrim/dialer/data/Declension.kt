package com.khizrim.dialer.data

import com.github.petrovich4j.Case
import com.github.petrovich4j.Gender
import com.github.petrovich4j.NameType
import com.github.petrovich4j.Petrovich

/**
 * Склонение имён из контактов через Petrovich (petrovich4j, MIT).
 * Нужно для «Как связаться с Мамой». Для нерусских имён возвращаем null,
 * и интерфейс показывает нейтральную подпись.
 */
object Declension {
    private val petrovich by lazy { Petrovich() }
    private val cyrillic = Regex("^[А-Яа-яЁё-]+$")
    private val femaleSoft = setOf("любовь", "нинель", "адель", "юдифь", "эсфирь", "руфь", "рахиль", "агарь")

    /** «Иван Петров» → «Иваном Петровым», «Мама» → «Мамой». */
    fun instrumental(name: String): String? {
        val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty() || words.size > 3) return null
        val first = words[0]
        if (!cyrillic.matches(first)) return null
        val gender = genderOf(words)
        return runCatching {
            words.mapIndexed { i, w ->
                when {
                    !cyrillic.matches(w) -> w // инициалы, «В.» и прочее оставляем как есть
                    i == 0 -> say(w, NameType.FirstName, gender)
                    isPatronymic(w) -> say(w, NameType.PatronymicName, gender)
                    else -> say(w, NameType.LastName, gender)
                }
            }.joinToString(" ")
        }.getOrNull()
    }

    /** «с Мамой», но «со Святославом». */
    fun withPreposition(declined: String): String {
        val w = declined.lowercase()
        val vowels = "аеёиоуыэюя"
        val so = w.length > 1 && w[0] in "сзшжщ" && w[1] !in vowels || w.startsWith("мн")
        return (if (so) "со " else "с ") + declined
    }

    private fun say(word: String, type: NameType, gender: Gender): String {
        // Petrovich ждёт слово с заглавной буквы; сохраняем регистр исходника
        val out = petrovich.say(word, type, gender, Case.Instrumental)
        return if (word.first().isLowerCase()) out.replaceFirstChar { it.lowercase() } else out
    }

    private fun isPatronymic(w: String): Boolean {
        val l = w.lowercase()
        return l.endsWith("вич") || l.endsWith("вна") || l.endsWith("чна") || l.endsWith("ична") || l.endsWith("ич")
    }

    internal fun genderOf(words: List<String>): Gender {
        words.drop(1).firstOrNull { isPatronymic(it) }?.lowercase()?.let { return if (it.endsWith("на")) Gender.Female else Gender.Male }
        words.drop(1).firstOrNull()?.lowercase()?.let { last ->
            if (last.endsWith("ова") || last.endsWith("ева") || last.endsWith("ина") || last.endsWith("ая") || last.endsWith("ёва")) return Gender.Female
            if (last.endsWith("ов") || last.endsWith("ев") || last.endsWith("ин") || last.endsWith("ий") || last.endsWith("ой") || last.endsWith("ёв")) return Gender.Male
        }
        val f = words[0].lowercase()
        return when {
            f.endsWith("а") || f.endsWith("я") -> Gender.Female // для склонения «Дима» и «Мама» ведут себя одинаково
            f.endsWith("ь") -> if (f in femaleSoft) Gender.Female else Gender.Male
            else -> Gender.Male
        }
    }

    /** Мужские имена на -а/-я и «семейные» слова мужского рода. */
    private val maleA = setOf(
        "илья", "никита", "кузьма", "фома", "лука", "савва", "данила", "гаврила", "фаддей",
        "дима", "миша", "ваня", "петя", "коля", "паша", "гоша", "лёша", "леша", "серёжа", "сережа",
        "вова", "витя", "костя", "толя", "юра", "гена", "боря", "слава", "стёпа", "степа", "федя", "егорка",
        "папа", "дедушка", "дед", "дядя", "братишка", "сынуля",
    )

    private val ambiguous = setOf("саша", "женя", "валя", "шура")

    /** Для рода в подписях вроде «Убрана с главной». Саша и Женя без фамилии считаются мужскими. */
    fun isFeminine(name: String): Boolean {
        val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty() || words.none { w -> w.any { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' } }) return false
        val first = words[0].lowercase()
        if (first in maleA) return false
        if (first in ambiguous) return words.size > 1 && feminineRest(words.drop(1).map { it.lowercase() })
        return genderOf(words) == Gender.Female
    }

    private fun feminineRest(rest: List<String>): Boolean {
        rest.firstOrNull { isPatronymic(it) }?.let { return it.endsWith("на") }
        val last = rest.first()
        return last.endsWith("ова") || last.endsWith("ева") || last.endsWith("ина") || last.endsWith("ая") || last.endsWith("ёва")
    }
}
