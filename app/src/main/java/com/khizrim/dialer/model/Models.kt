package com.khizrim.dialer.model

import androidx.annotation.DrawableRes
import com.khizrim.dialer.R

enum class MethodKind { CALL, CHAT }

/** Способ связи: что произойдёт по нажатию на имя. */
enum class Method(@DrawableRes val icon: Int, val title: String, val network: String, val kind: MethodKind) {
    WA_VIDEO(R.drawable.ic_oz_video, "Видео в WhatsApp", "WhatsApp", MethodKind.CALL),
    WA_CALL(R.drawable.ic_oz_phone, "Звонок в WhatsApp", "WhatsApp", MethodKind.CALL),
    WA_CHAT(R.drawable.ic_oz_chat, "Чат в WhatsApp", "WhatsApp", MethodKind.CHAT),
    TG_CALL(R.drawable.ic_oz_phone, "Звонок в Telegram", "Telegram", MethodKind.CALL),
    TG_VIDEO(R.drawable.ic_oz_video, "Видео в Telegram", "Telegram", MethodKind.CALL),
    TG_CHAT(R.drawable.ic_oz_send, "Написать в Telegram", "Telegram", MethodKind.CHAT),
    SIG_CALL(R.drawable.ic_oz_phone, "Звонок в Signal", "Signal", MethodKind.CALL),
    VB_CALL(R.drawable.ic_oz_phone, "Звонок в Viber", "Viber", MethodKind.CALL),
    PHONE(R.drawable.ic_oz_phone, "Обычный звонок", "Телефон", MethodKind.CALL),
    SMS(R.drawable.ic_oz_sms, "Написать SMS", "SMS", MethodKind.CHAT),
}

data class HistoryEntry(val day: String, val text: String, val missed: Boolean = false)

data class Person(
    val id: Int,
    val name: String,
    /** Творительный падеж для «Как связаться с …». */
    val instrumental: String,
    val phone: String,
    val methods: List<Method>,
    /** null означает «спрашивать каждый раз». */
    val defaultMethod: Method?,
    val favorite: Boolean = false,
    val history: List<HistoryEntry> = emptyList(),
    val missedNote: String? = null,
)

enum class CallKind { INCOMING, UNKNOWN, SPAM, OUTGOING }
enum class CallPhase { RINGING, ACTIVE, ENDED }

data class CallState(
    val kind: CallKind,
    val personId: Int? = null,
    val number: String? = null,
    val callerLabel: String? = null,
    val network: String = "Телефон",
    val phase: CallPhase,
    val seconds: Int = 0,
    val muted: Boolean = false,
    val speaker: Boolean = false,
    /** null, когда клавиши тонового набора скрыты. */
    val dtmf: String? = null,
    val endText: String = "",
)
