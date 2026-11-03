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
    /** Можно ли поставить звонок на удержание. */
    val canHold: Boolean = false,
    /** Две линии можно объединить в конференцию. */
    val canMerge: Boolean = false,
    /** Второй звонок: на удержании или входящий поверх текущего. */
    val other: OtherLine? = null,
    /** Куда сейчас идёт звук и какие варианты есть (маска CallAudioState.ROUTE_*). */
    val route: Int = 0,
    val routes: Int = 0,
    val bluetoothName: String? = null,
    /** Подписи SIM-карт, когда система спрашивает, с какой звонить. */
    val simChoice: List<String>? = null,
    /** Через какую SIM идёт звонок, когда их две. */
    val sim: String? = null,
    /** Определитель считает номер подозрительным: тёплый фон и подпись цветом warn. */
    val suspicious: Boolean = false,
    /** Звонок не прошёл: ошибка сети, оператор отказал. */
    val failed: Boolean = false,
) {
    /** Кроме телефона и динамика есть наушники или Bluetooth: показываем выбор. */
    val hasRouteChoice: Boolean get() = routes and (ROUTE_BLUETOOTH or ROUTE_WIRED) != 0

    companion object {
        // значения как в android.telecom.CallAudioState, чтобы модель не зависела от Android
        const val ROUTE_EARPIECE = 1
        const val ROUTE_BLUETOOTH = 2
        const val ROUTE_WIRED = 4
        const val ROUTE_SPEAKER = 8
    }
}

/** Вторая линия: имя или номер и что с ней происходит. */
data class OtherLine(val title: String, val ringing: Boolean, val onHold: Boolean)
