package com.khizrim.dialer.data

/** Маска номера при наборе, как у Apple: по коду страны или по региону телефона. */
object PhoneFormat {
    private val countryCodes = mapOf(
        "1" to "(XXX) XXX-XXXX", "7" to "(XXX) XXX-XX-XX", "20" to "XX XXXX XXXX", "30" to "XXX XXX XXXX",
        "31" to "X XXXXXXXX", "33" to "X XX XX XX XX", "34" to "XXX XX XX XX", "39" to "XXX XXX XXXX",
        "44" to "XXXX XXXXXX", "49" to "XXX XXXXXXXX", "86" to "XXX XXXX XXXX", "90" to "XXX XXX XX XX",
        "371" to "XX XXX XXX", "372" to "XXXX XXXX", "373" to "XX XXX XXX", "374" to "XX XXX XXX",
        "375" to "XX XXX-XX-XX", "380" to "XX XXX XX XX", "971" to "XX XXX XXXX", "992" to "XX XXX XXXX",
        "994" to "XX XXX XX XX", "995" to "XXX XX XX XX", "996" to "XXX XXX XXX", "998" to "XX XXX XX XX",
    )

    private data class Rule(val prefix: String?, val min: Int, val template: String)

    private val national = mapOf(
        "RU" to listOf(Rule("8", 2, "8 (XXX) XXX-XX-XX"), Rule(null, 8, "XXX XXX-XX-XX"), Rule(null, 5, "XXX-XX-XX")),
        "US" to listOf(Rule("1", 2, "1 (XXX) XXX-XXXX"), Rule(null, 8, "(XXX) XXX-XXXX"), Rule(null, 5, "XXX-XXXX")),
    )

    private fun fill(template: String, digits: String): String? {
        val out = StringBuilder()
        var i = 0
        var j = 0
        while (j < template.length && i < digits.length) {
            if (template[j] == 'X') out.append(digits[i++]) else out.append(template[j])
            j++
        }
        return if (i < digits.length) null else out.toString()
    }

    fun formatTyping(raw: String, region: String): String {
        if (raw.isEmpty() || raw.any { it in "*#,;" }) return raw
        if (raw[0] == '+') {
            val d = raw.substring(1)
            for (n in 1..minOf(3, d.length)) {
                val cc = d.substring(0, n)
                val tpl = countryCodes[cc] ?: continue
                val rest = d.substring(n)
                if (rest.isEmpty()) return "+$cc"
                return fill(tpl, rest)?.let { "+$cc $it" } ?: raw
            }
            return raw
        }
        val rules = national[region] ?: return raw
        for (rule in rules) {
            if (rule.prefix != null && !raw.startsWith(rule.prefix)) continue
            if (raw.length < rule.min) continue
            if (rule.prefix != null) {
                val g = fill(rule.template.substring(rule.prefix.length), raw.substring(rule.prefix.length))
                if (g != null) return rule.prefix + g
                continue
            }
            fill(rule.template, raw)?.let { return it }
        }
        return raw
    }

    /** Регион для маски: страна SIM-карты, иначе страна из настроек языка. Задаётся при старте. */
    @Volatile var simRegion: String? = null

    val defaultRegion: String
        get() = (simRegion ?: java.util.Locale.getDefault().country).uppercase().ifEmpty { "RU" }

    /** Страна по SIM или сети, например «RU». */
    fun detectRegion(context: android.content.Context): String? {
        val tm = context.getSystemService(android.telephony.TelephonyManager::class.java) ?: return null
        return listOf(runCatching { tm.simCountryIso }.getOrNull(), runCatching { tm.networkCountryIso }.getOrNull())
            .firstOrNull { !it.isNullOrBlank() }?.uppercase()
    }

    /** Сохранённый номер в аккуратном виде: +7 (916) 123-45-67. */
    fun pretty(number: String, region: String = defaultRegion): String {
        val raw = number.filter { it.isDigit() || it == '+' }
        if (raw.isEmpty()) return number
        val f = formatTyping(raw, region)
        return if (f == raw && raw.length > 4) number else f
    }

    fun digits(s: String): String = s.filter { it.isDigit() }

    /** Позиция n-й цифры внутри отформатированного номера. */
    fun digitPos(s: String, n: Int): Int {
        var c = 0
        s.forEachIndexed { i, ch ->
            if (ch.isDigit()) {
                if (c == n) return i
                c++
            }
        }
        return s.length
    }
}

/** Русская раскладка T9 (плюс латиница). «Аня» = 259. */
object T9 {
    private val map = mapOf(
        '2' to "абвгabc", '3' to "деёжзdef", '4' to "ийклghi", '5' to "мнопjkl",
        '6' to "рстуmno", '7' to "фхцчpqrs", '8' to "шщъыtuv", '9' to "ьэюяwxyz",
    )
    private val reverse: Map<Char, Char> = buildMap { map.forEach { (d, letters) -> letters.forEach { put(it, d) } } }

    fun encode(word: String): String = word.lowercase().map { reverse[it] ?: '#' }.joinToString("")
}
