package com.khizrim.dialer.telecom

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.DisconnectCause
import android.telecom.VideoProfile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.khizrim.dialer.R
import com.khizrim.dialer.data.Blocklist
import com.khizrim.dialer.data.ContactsSource
import com.khizrim.dialer.model.CallPhase
import com.khizrim.dialer.model.CallState
import java.util.concurrent.Executors

/**
 * Единственный источник правды о звонках. InCallService кладёт сюда звонки,
 * экран звонка читает [state] и вызывает действия.
 */
object CallManager {
    var state by mutableStateOf<CallState?>(null)
        private set

    private val calls = mutableListOf<Call>()
    private val names = HashMap<String, String?>()
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var dtmf: String? = null
    private var endText: String? = null
    private var appContext: Context? = null
    internal var service: OzInCallService? = null

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, newState: Int) = refresh()
        override fun onDetailsChanged(call: Call, details: Call.Details) = refresh()
    }

    private val primary: Call?
        get() {
            val visible = calls.filter { it !in silenced }
            return visible.firstOrNull { it.state == Call.STATE_RINGING }
                ?: visible.firstOrNull { it.state == Call.STATE_ACTIVE }
                ?: visible.firstOrNull { it.state != Call.STATE_DISCONNECTED }
                ?: visible.firstOrNull()
        }

    internal fun attach(service: OzInCallService) {
        this.service = service
        appContext = service.applicationContext
    }

    internal fun detach() {
        service = null
    }

    internal fun add(call: Call) {
        calls += call
        call.registerCallback(callback)
        endText = null
        val number = number(call)
        if (number.isNotEmpty() && !names.containsKey(number)) {
            names[number] = null
            val ctx = appContext
            if (ctx != null) io.execute {
                val name = ContactsSource.nameFor(ctx, number)
                main.post { names[number] = name; refresh() }
            }
        }
        refresh()
    }

    /** Звонки с незнакомых номеров, которые пользователь попросил не показывать. */
    private val silenced = mutableSetOf<Call>()

    fun isSilenced(call: Call): Boolean = call in silenced

    internal fun silence(call: Call) { silenced += call }

    /** Есть ли сейчас звонок, который надо показать пользователю. */
    val hasVisibleCall: Boolean get() = calls.any { it !in silenced && it.state != Call.STATE_DISCONNECTED }

    internal fun remove(call: Call) {
        call.unregisterCallback(callback)
        silenced -= call
        calls -= call
        if (calls.none { it !in silenced }) {
            dtmf = null
            // держим экран «Звонок завершён» ещё 1.2 с, потом он гаснет
            main.postDelayed({ if (calls.none { it !in silenced }) state = null }, 1200)
        } else {
            refresh()
        }
    }

    internal fun audioChanged() = refresh()

    private fun number(call: Call): String = call.details?.handle?.schemeSpecificPart.orEmpty()

    private fun refresh() {
        val call = primary ?: return
        val number = number(call)
        val audio: CallAudioState? = service?.callAudioState
        val phase = when (call.state) {
            Call.STATE_RINGING -> CallPhase.RINGING
            Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_NEW, Call.STATE_SELECT_PHONE_ACCOUNT -> CallPhase.DIALING
            Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING -> CallPhase.ENDED
            else -> CallPhase.ACTIVE
        }
        val connectedAt = call.details?.connectTimeMillis ?: 0L
        if (phase == CallPhase.ENDED && endText == null) {
            val ctx = appContext
            endText = if (ctx == null) "" else when (call.details?.disconnectCause?.code) {
                DisconnectCause.REJECTED -> ctx.getString(R.string.end_rejected)
                DisconnectCause.MISSED -> ctx.getString(R.string.end_missed)
                DisconnectCause.BUSY -> ctx.getString(R.string.end_busy)
                DisconnectCause.ERROR -> ctx.getString(R.string.end_error)
                else -> if (connectedAt > 0) ctx.getString(R.string.end_done_time, timer((System.currentTimeMillis() - connectedAt) / 1000))
                else ctx.getString(R.string.end_done)
            }
        }
        state = CallState(
            name = names[number],
            number = number,
            label = null,
            phase = phase,
            connectedAt = connectedAt,
            muted = audio?.isMuted == true,
            speaker = audio?.route == CallAudioState.ROUTE_SPEAKER,
            dtmf = dtmf,
            endText = endText.orEmpty(),
            onHold = call.state == Call.STATE_HOLDING,
        )
        // тихий незнакомый звонок: без уведомления и без экрана
        if (call !in silenced) service?.let { CallNotifications.update(it, state, call) }
    }

    // ---------- действия ----------
    fun answer() { calls.firstOrNull { it.state == Call.STATE_RINGING }?.answer(VideoProfile.STATE_AUDIO_ONLY) }

    fun decline() {
        val ringing = calls.firstOrNull { it.state == Call.STATE_RINGING }
        if (ringing != null) ringing.reject(false, null) else primary?.disconnect()
    }

    fun hangup() { primary?.disconnect() }

    /** Отклонить и отправить SMS. Текст отправляет сама система. */
    fun rejectWithMessage(text: String) {
        calls.firstOrNull { it.state == Call.STATE_RINGING }?.reject(true, text)
    }

    fun block(context: Context): Boolean {
        val call = primary ?: return false
        val ok = Blocklist.block(context, number(call))
        decline()
        return ok
    }

    @Suppress("DEPRECATION")
    fun toggleMute() { val s = state ?: return; service?.setMuted(!s.muted) }

    @Suppress("DEPRECATION")
    fun toggleSpeaker() {
        val s = state ?: return
        service?.setAudioRoute(if (s.speaker) CallAudioState.ROUTE_WIRED_OR_EARPIECE else CallAudioState.ROUTE_SPEAKER)
    }

    fun toggleDtmf() { dtmf = if (dtmf == null) "" else null; refresh() }

    fun tone(d: Char) {
        val call = primary ?: return
        call.playDtmfTone(d)
        main.postDelayed({ call.stopDtmfTone() }, 150)
        dtmf = (dtmf ?: "") + d
        refresh()
    }

    fun currentNumber(): String? = primary?.let { number(it) }
    fun currentName(): String? = primary?.let { names[number(it)] }

    fun timer(seconds: Long): String = "%02d:%02d".format(seconds / 60, seconds % 60)
}
