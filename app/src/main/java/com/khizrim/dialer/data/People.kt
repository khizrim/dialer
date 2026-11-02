package com.khizrim.dialer.data

import com.khizrim.dialer.model.HistoryEntry
import com.khizrim.dialer.model.Method.PHONE
import com.khizrim.dialer.model.Method.SIG_CALL
import com.khizrim.dialer.model.Method.SMS
import com.khizrim.dialer.model.Method.TG_CALL
import com.khizrim.dialer.model.Method.TG_CHAT
import com.khizrim.dialer.model.Method.TG_VIDEO
import com.khizrim.dialer.model.Method.VB_CALL
import com.khizrim.dialer.model.Method.WA_CALL
import com.khizrim.dialer.model.Method.WA_CHAT
import com.khizrim.dialer.model.Method.WA_VIDEO
import com.khizrim.dialer.model.Person

/** Демо-данные, те же, что в HTML-прототипе. */
object People {
    val all = listOf(
        Person(
            1, "Мама", "Мамой", "+7 (916) 123-45-67", listOf(WA_VIDEO, WA_CALL, TG_CALL, PHONE, SMS), WA_VIDEO, favorite = true,
            history = listOf(
                HistoryEntry("Пт", "Видеозвонок WhatsApp, 12 мин"),
                HistoryEntry("Чт", "Исходящий, 4 мин"),
                HistoryEntry("Ср", "Пропущенный", missed = true),
                HistoryEntry("Пн", "Входящий, 22 мин"),
            ),
        ),
        Person(2, "Аня", "Аней", "+7 (916) 000-00-00", listOf(WA_CHAT, WA_CALL, PHONE, SMS), WA_CHAT, history = listOf(HistoryEntry("Вчера", "Чат в WhatsApp"))),
        Person(
            3, "Дима", "Димой", "+7 (985) 456-78-90", listOf(PHONE, TG_CALL, TG_CHAT, SMS), PHONE, missedNote = "Пропущенный, вчера",
            history = listOf(HistoryEntry("Вчера", "Пропущенный", missed = true), HistoryEntry("Вт", "Исходящий, 2 мин")),
        ),
        Person(4, "Иван Петров", "Иваном", "+7 (903) 555-12-34", listOf(TG_CHAT, TG_CALL, TG_VIDEO, PHONE), TG_CHAT, history = listOf(HistoryEntry("Сб", "Звонок в Telegram, 9 мин"))),
        Person(5, "Александра", "Александрой", "+7 (926) 345-67-89", listOf(WA_CHAT, WA_CALL, PHONE), WA_CHAT),
        Person(6, "Алексей В.", "Алексеем", "+7 (911) 234-56-78", listOf(PHONE, SIG_CALL, SMS), PHONE, history = listOf(HistoryEntry("12 окт", "Входящий, 3 мин"))),
        Person(7, "Анна Котова", "Анной", "+7 (926) 123-45-67", listOf(PHONE, WA_CALL, SMS), PHONE),
        Person(8, "Бабушка", "Бабушкой", "+7 (495) 111-22-33", listOf(PHONE, SMS), PHONE, favorite = true, history = listOf(HistoryEntry("Вс", "Исходящий, 31 мин"))),
        Person(9, "Борис", "Борисом", "+7 (915) 012-34-56", listOf(WA_CHAT, PHONE), WA_CHAT),
        Person(10, "Виктор Сергеевич", "Виктором Сергеевичем", "+7 (495) 980-11-22", listOf(PHONE, SMS), PHONE),
        Person(11, "Елена Соколова", "Еленой", "+7 (999) 567-89-01", listOf(PHONE, WA_CALL, VB_CALL), VB_CALL),
        Person(12, "Ксения", "Ксенией", "+7 (903) 789-01-23", listOf(TG_CHAT, TG_CALL, PHONE), null),
        Person(13, "Михаил Новиков", "Михаилом", "+7 (925) 890-12-34", listOf(PHONE, TG_CALL), TG_CALL),
        Person(14, "Ольга", "Ольгой", "+7 (977) 901-23-45", listOf(WA_VIDEO, WA_CALL, PHONE), WA_CALL),
        Person(15, "Сергей Попов", "Сергеем", "+7 (926) 135-79-24", listOf(TG_CALL, PHONE), TG_CALL),
        Person(16, "Татьяна", "Татьяной", "+7 (905) 246-80-13", listOf(WA_CALL, PHONE), WA_CALL),
    )

    /** Главная: ближайший человек внизу, у большого пальца. */
    val home = listOf(2, 3, 4, 1)
    val missed = setOf(3)
}
