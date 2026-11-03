package com.khizrim.dialer.model

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.khizrim.dialer.R

enum class MethodKind { CALL, CHAT }

/** Способ связи: что произойдёт по нажатию на имя. */
enum class Method(@DrawableRes val icon: Int, @StringRes val title: Int, val brand: String?, val kind: MethodKind) {
    WA_VIDEO(R.drawable.ic_oz_video, R.string.m_wa_video, "WhatsApp", MethodKind.CALL),
    WA_CALL(R.drawable.ic_oz_phone, R.string.m_wa_call, "WhatsApp", MethodKind.CALL),
    WA_CHAT(R.drawable.ic_oz_chat, R.string.m_wa_chat, "WhatsApp", MethodKind.CHAT),
    TG_CALL(R.drawable.ic_oz_phone, R.string.m_tg_call, "Telegram", MethodKind.CALL),
    TG_VIDEO(R.drawable.ic_oz_video, R.string.m_tg_video, "Telegram", MethodKind.CALL),
    TG_CHAT(R.drawable.ic_oz_send, R.string.m_tg_chat, "Telegram", MethodKind.CHAT),
    SIG_CALL(R.drawable.ic_oz_phone, R.string.m_sig_call, "Signal", MethodKind.CALL),
    SIG_CHAT(R.drawable.ic_oz_chat, R.string.m_sig_chat, "Signal", MethodKind.CHAT),
    VB_CALL(R.drawable.ic_oz_phone, R.string.m_vb_call, "Viber", MethodKind.CALL),
    VB_CHAT(R.drawable.ic_oz_chat, R.string.m_vb_chat, "Viber", MethodKind.CHAT),
    PHONE(R.drawable.ic_oz_phone, R.string.m_phone, null, MethodKind.CALL),
    SMS(R.drawable.ic_oz_sms, R.string.m_sms, "SMS", MethodKind.CHAT),
    ;

    fun label(context: Context): String = context.getString(title)
    fun network(context: Context): String = brand ?: context.getString(R.string.net_phone)
}

data class Phone(val number: String, val label: String)

/**
 * Способ связи, доступный конкретному человеку. Для мессенджеров это строка
 * в контактах, которую добавило само приложение (dataId + mimeType).
 */
data class Channel(val method: Method, val dataId: Long? = null, val mimeType: String? = null)

data class Person(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val phones: List<Phone>,
    val channels: List<Channel>,
    val photo: String?,
    val starred: Boolean,
) {
    val methods: List<Method> get() = channels.map { it.method }
    val mainNumber: String? get() = phones.firstOrNull()?.number
    val key: String get() = "c:$lookupKey"
}

data class HistoryEntry(val day: String, val text: String, val missed: Boolean = false)

/** Строка главной: человек из контактов или номер, которого нет в контактах. */
data class HomeEntry(
    val key: String,
    val person: Person?,
    val number: String?,
    val lastAt: Long,
    val missedNote: String?,
) {
    val title: String get() = person?.name ?: com.khizrim.dialer.data.PhoneFormat.pretty(number.orEmpty())
}

enum class CallPhase { RINGING, DIALING, ACTIVE, ENDED }

/** То, что показывает экран звонка. */
data class CallState(
    val name: String?,
    val number: String,
    val label: String?,
    val phase: CallPhase,
    val connectedAt: Long = 0L,
    val muted: Boolean = false,
    val speaker: Boolean = false,
    /** null, когда клавиши тонового набора скрыты. */
    val dtmf: String? = null,
    val endText: String = "",
    val onHold: Boolean = false,
)
