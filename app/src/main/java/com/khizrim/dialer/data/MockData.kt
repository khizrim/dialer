package com.khizrim.dialer.data

import com.khizrim.dialer.model.CallDirection
import com.khizrim.dialer.model.Channel
import com.khizrim.dialer.model.CommMethod
import com.khizrim.dialer.model.Contact
import com.khizrim.dialer.model.RecentCall

object MockData {
    private val all = setOf(Channel.PHONE, Channel.WHATSAPP, Channel.TELEGRAM)

    val contacts = listOf(
        Contact(1, "Мама", "+7 916 123-45-67", channels = all, defaultMethod = CommMethod.WHATSAPP_VIDEO, favorite = true),
        Contact(2, "Алексей Смирнов", "+7 903 555-12-34", channels = all + Channel.SIGNAL, defaultMethod = CommMethod.TELEGRAM_CALL, favorite = true),
        Contact(3, "Анна Ковалёва", "+7 925 777-88-99", channels = all, defaultMethod = CommMethod.TELEGRAM_VIDEO, favorite = true),
        Contact(4, "Бабушка", "+7 495 111-22-33", label = "Домашний", channels = setOf(Channel.PHONE), defaultMethod = CommMethod.PHONE_CALL, favorite = true),
        Contact(5, "Борис Петров", "+7 911 234-56-78", channels = setOf(Channel.PHONE, Channel.WHATSAPP), defaultMethod = CommMethod.WHATSAPP_CALL),
        Contact(6, "Вика Орлова", "+7 926 345-67-89", channels = all + Channel.VIBER, defaultMethod = null),
        Contact(7, "Дмитрий Волков", "+7 985 456-78-90", label = "Рабочий", channels = setOf(Channel.PHONE, Channel.TELEGRAM), defaultMethod = CommMethod.TELEGRAM_CHAT, note = "Коллега, лучше писать"),
        Contact(8, "Елена Соколова", "+7 999 567-89-01", channels = all, defaultMethod = CommMethod.PHONE_CALL),
        Contact(9, "Игорь Морозов", "+7 916 678-90-12", channels = setOf(Channel.PHONE, Channel.SIGNAL), defaultMethod = CommMethod.SIGNAL_CALL),
        Contact(10, "Ксения Лебедева", "+7 903 789-01-23", channels = all, defaultMethod = CommMethod.WHATSAPP_CHAT),
        Contact(11, "Михаил Новиков", "+7 925 890-12-34", channels = setOf(Channel.PHONE, Channel.VIBER), defaultMethod = CommMethod.VIBER_CALL),
        Contact(12, "Ольга Кузнецова", "+7 977 901-23-45", channels = all, defaultMethod = null),
        Contact(13, "Павел Зайцев", "+7 915 012-34-56", channels = setOf(Channel.PHONE), defaultMethod = CommMethod.PHONE_CALL),
        Contact(14, "Сергей Попов", "+7 926 135-79-24", channels = all, defaultMethod = CommMethod.TELEGRAM_CALL),
        Contact(15, "Татьяна Белова", "+7 905 246-80-13", channels = all, defaultMethod = CommMethod.WHATSAPP_CALL),
    )

    val recents = listOf(
        RecentCall(1, 1, "+7 916 123-45-67", CommMethod.WHATSAPP_VIDEO, CallDirection.OUTGOING, "Сегодня, 13:20", "12:04"),
        RecentCall(2, null, "+7 495 980-00-00", CommMethod.PHONE_CALL, CallDirection.BLOCKED, "Сегодня, 11:02", callerIdLabel = "Спам · «Финансовые услуги»"),
        RecentCall(3, 2, "+7 903 555-12-34", CommMethod.TELEGRAM_CALL, CallDirection.INCOMING, "Сегодня, 09:45", "3:18"),
        RecentCall(4, null, "+7 800 555-35-35", CommMethod.PHONE_CALL, CallDirection.MISSED, "Вчера, 18:30", callerIdLabel = "Служба доставки"),
        RecentCall(5, 3, "+7 925 777-88-99", CommMethod.TELEGRAM_VIDEO, CallDirection.OUTGOING, "Вчера, 15:10", "25:40"),
        RecentCall(6, 4, "+7 495 111-22-33", CommMethod.PHONE_CALL, CallDirection.MISSED, "Вчера, 10:05"),
        RecentCall(7, 8, "+7 999 567-89-01", CommMethod.PHONE_CALL, CallDirection.INCOMING, "Пн, 20:12", "1:02"),
        RecentCall(8, 5, "+7 911 234-56-78", CommMethod.WHATSAPP_CALL, CallDirection.OUTGOING, "Пн, 12:47", "7:55"),
    )
}
