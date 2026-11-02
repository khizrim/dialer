package com.khizrim.dialer.model

/** Приложение, через которое можно связаться с контактом. */
enum class Channel(val title: String) {
    PHONE("Телефон"),
    WHATSAPP("WhatsApp"),
    TELEGRAM("Telegram"),
    SIGNAL("Signal"),
    VIBER("Viber"),
}

enum class MethodKind { CALL, VIDEO, MESSAGE }

/** Конкретный способ связи: приложение + тип действия. */
enum class CommMethod(val channel: Channel, val kind: MethodKind, val title: String) {
    PHONE_CALL(Channel.PHONE, MethodKind.CALL, "Обычный звонок"),
    SMS(Channel.PHONE, MethodKind.MESSAGE, "SMS"),
    WHATSAPP_CALL(Channel.WHATSAPP, MethodKind.CALL, "Аудиозвонок WhatsApp"),
    WHATSAPP_VIDEO(Channel.WHATSAPP, MethodKind.VIDEO, "Видеозвонок WhatsApp"),
    WHATSAPP_CHAT(Channel.WHATSAPP, MethodKind.MESSAGE, "Чат WhatsApp"),
    TELEGRAM_CALL(Channel.TELEGRAM, MethodKind.CALL, "Аудиозвонок Telegram"),
    TELEGRAM_VIDEO(Channel.TELEGRAM, MethodKind.VIDEO, "Видеозвонок Telegram"),
    TELEGRAM_CHAT(Channel.TELEGRAM, MethodKind.MESSAGE, "Чат Telegram"),
    SIGNAL_CALL(Channel.SIGNAL, MethodKind.CALL, "Звонок Signal"),
    VIBER_CALL(Channel.VIBER, MethodKind.CALL, "Звонок Viber"),
    ;

    val shortTitle: String
        get() = when (kind) {
            MethodKind.CALL -> if (channel == Channel.PHONE) "Звонок" else "${channel.title} · звонок"
            MethodKind.VIDEO -> "${channel.title} · видео"
            MethodKind.MESSAGE -> if (channel == Channel.PHONE) "SMS" else "${channel.title} · чат"
        }
}

data class Contact(
    val id: Long,
    val name: String,
    val phone: String,
    val label: String = "Мобильный",
    val channels: Set<Channel>,
    /** null означает «спрашивать каждый раз». */
    val defaultMethod: CommMethod?,
    val favorite: Boolean = false,
    val note: String? = null,
) {
    val availableMethods: List<CommMethod>
        get() = CommMethod.entries.filter { it.channel in channels }

    val initials: String
        get() = name.split(" ").filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.first().uppercase() }
}

enum class CallDirection { INCOMING, OUTGOING, MISSED, BLOCKED }

data class RecentCall(
    val id: Long,
    val contactId: Long?,
    val number: String,
    val method: CommMethod,
    val direction: CallDirection,
    val whenText: String,
    val durationText: String? = null,
    val callerIdLabel: String? = null,
)

/** Сценарии демо-экрана входящего звонка. */
enum class IncomingScenario(val title: String) {
    CONTACT("Звонит контакт"),
    UNKNOWN_IDENTIFIED("Неизвестный, АОН определил"),
    SPAM("Подозрение на спам"),
}
