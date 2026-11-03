package com.khizrim.dialer.telecom

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.DisconnectCause
import android.telecom.VideoProfile
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.os.Build
import com.khizrim.dialer.model.OtherLine
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
    private val labels = HashMap<String, String?>()
    private val suspicious = HashSet<String>()
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var dtmf: String? = null
    private var endText: String? = null
    private var appContext: Context? = null
    internal var service: OzInCallService? = null

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, newState: Int) {
            if (newState == Call.STATE_SELECT_PHONE_ACCOUNT) autoPickSim(call)
            // входящий перестал звонить: ответ «после завершения текущего» больше не нужен
            if (call.state != Call.STATE_RINGING && call != endingForAnswer && pendingAnswer != null && visible.none { it.state == Call.STATE_RINGING }) { pendingAnswer = null; endingForAnswer = null }
            if (newState == Call.STATE_DISCONNECTED) pendingAnswer?.let { r -> if (call == endingForAnswer) { pendingAnswer = null; endingForAnswer = null; main.post(r) } }
            refresh()
        }
        override fun onDetailsChanged(call: Call, details: Call.Details) = refresh()
    }

    private val visible: List<Call> get() = calls.filter { it !in silenced }

    /** Главный звонок на экране: входящий, затем идущий, затем набираемый, затем на удержании. */
    private val primary: Call?
        get() {
            val v = visible
            return v.firstOrNull { it.state == Call.STATE_RINGING }
                ?: v.firstOrNull { it.state == Call.STATE_ACTIVE }
                ?: v.firstOrNull { it.state in DIALING_STATES }
                ?: v.firstOrNull { it.state == Call.STATE_HOLDING }
                ?: v.firstOrNull { it.state != Call.STATE_DISCONNECTED }
                ?: v.firstOrNull()
        }

    /** Вторая живая линия, если есть. */
    private fun secondary(primary: Call): Call? =
        visible.firstOrNull { it != primary && it.state != Call.STATE_DISCONNECTED && it.state != Call.STATE_DISCONNECTING }

    private val DIALING_STATES = setOf(Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_NEW, Call.STATE_SELECT_PHONE_ACCOUNT, Call.STATE_PULLING_CALL)

    internal fun attach(service: OzInCallService) {
        this.service = service
        appContext = service.applicationContext
        // маска номера по стране SIM, даже если приложение не открывали
        if (com.khizrim.dialer.data.PhoneFormat.simRegion == null) {
            com.khizrim.dialer.data.PhoneFormat.simRegion = com.khizrim.dialer.data.PhoneFormat.detectRegion(service)
        }
    }

    internal fun detach() {
        service = null
    }

    internal fun add(call: Call) {
        calls += call
        call.registerCallback(callback)
        endText = null
        lookupName(call)
        autoPickSim(call)
        refresh()
    }

    private fun lookupName(call: Call) {
        val number = number(call)
        if (number.isNotEmpty() && !names.containsKey(number)) {
            names[number] = null
            val ctx = appContext
            if (ctx != null) io.execute {
                val name = ContactsSource.nameFor(ctx, number)
                // незнакомым номерам — подпись определителя: оператор и регион или «Похоже на спам»
                val label = if (name == null) runCatching { com.khizrim.dialer.callerid.CallerId.labelFor(ctx, number) }.getOrNull() else null
                val spam = name == null && runCatching { com.khizrim.dialer.callerid.CallerId.isSuspicious(ctx, number) }.getOrDefault(false)
                main.post { names[number] = name; labels[number] = label; if (spam) suspicious += number; refresh() }
            }
        }
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

    private fun title(call: Call): String {
        val n = number(call)
        return names[n] ?: n.takeIf { it.isNotEmpty() }?.let { com.khizrim.dialer.data.PhoneFormat.pretty(it) }
            ?: appContext?.getString(R.string.hidden_number).orEmpty()
    }

    /** SIM-карты, из которых система просит выбрать для исходящего. */
    private fun simAccounts(call: Call): List<PhoneAccountHandle> {
        if (call.state != Call.STATE_SELECT_PHONE_ACCOUNT) return emptyList()
        val extras = call.details?.intentExtras ?: call.details?.extras ?: return emptyList()
        @Suppress("DEPRECATION")
        val list = call.details?.extras?.getParcelableArrayList<PhoneAccountHandle>(Call.AVAILABLE_PHONE_ACCOUNTS)
            ?: extras.getParcelableArrayList<PhoneAccountHandle>(Call.AVAILABLE_PHONE_ACCOUNTS)
        if (!list.isNullOrEmpty()) return list
        // система не передала список: берём SIM-карты сами
        @android.annotation.SuppressLint("MissingPermission")
        val own = runCatching { appContext?.getSystemService(TelecomManager::class.java)?.callCapablePhoneAccounts }.getOrNull()
        return own.orEmpty()
    }

    /** Если SIM одна, выбирать нечего: звоним с неё сразу. */
    private fun autoPickSim(call: Call) {
        if (call.state != Call.STATE_SELECT_PHONE_ACCOUNT) return
        val accounts = simAccounts(call)
        if (accounts.size == 1) call.phoneAccountSelected(accounts[0], false)
    }

    private fun simLabel(handle: PhoneAccountHandle, index: Int): String {
        val ctx = appContext ?: return "SIM ${index + 1}"
        val label = runCatching { ctx.getSystemService(TelecomManager::class.java)?.getPhoneAccount(handle)?.label?.toString() }.getOrNull()
        return label?.takeIf { it.isNotBlank() } ?: ctx.getString(R.string.sim_n, index + 1)
    }

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
            label = labels[number],
            suspicious = number in suspicious,
            phase = phase,
            connectedAt = connectedAt,
            muted = audio?.isMuted == true,
            speaker = audio?.route == CallAudioState.ROUTE_SPEAKER,
            dtmf = dtmf,
            endText = endText.orEmpty(),
            onHold = call.state == Call.STATE_HOLDING,
            canHold = call.details?.can(Call.Details.CAPABILITY_HOLD) == true || call.details?.can(Call.Details.CAPABILITY_SUPPORT_HOLD) == true,
            other = secondary(call)?.let { o ->
                OtherLine(title(o), ringing = o.state == Call.STATE_RINGING, onHold = o.state == Call.STATE_HOLDING)
            },
            route = audio?.route ?: 0,
            routes = audio?.supportedRouteMask ?: 0,
            bluetoothName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) audio?.activeBluetoothDevice?.let { btName(it) } else null,
            simChoice = simAccounts(call).takeIf { it.size > 1 }?.mapIndexed { i, h -> simLabel(h, i) },
        )
        // тихий незнакомый звонок: без уведомления и без экрана
        if (call !in silenced) service?.let { CallNotifications.update(it, state, call) }
    }

    // ---------- действия ----------
    /** Ответить. Если уже идёт разговор, система сама поставит его на удержание. */
    fun answer() { visible.firstOrNull { it.state == Call.STATE_RINGING }?.answer(VideoProfile.STATE_AUDIO_ONLY) }

    /** Завершить текущий и ответить на второй. */
    fun endAndAnswer() {
        val ringing = visible.firstOrNull { it.state == Call.STATE_RINGING } ?: return
        val active = visible.firstOrNull { it != ringing && it.state == Call.STATE_ACTIVE }
        if (active == null) { ringing.answer(VideoProfile.STATE_AUDIO_ONLY); return }
        // отвечаем, когда текущий действительно завершился; удержанный не трогаем
        endingForAnswer = active
        pendingAnswer = Runnable { if (ringing.state == Call.STATE_RINGING) ringing.answer(VideoProfile.STATE_AUDIO_ONLY) }
        active.disconnect()
    }

    private var endingForAnswer: Call? = null
    private var pendingAnswer: Runnable? = null

    fun decline() {
        val ringing = visible.firstOrNull { it.state == Call.STATE_RINGING }
        if (ringing != null) ringing.reject(false, null) else primary?.disconnect()
    }

    fun hangup() { primary?.disconnect() }

    fun toggleHold() {
        val call = primary ?: return
        if (call.state == Call.STATE_HOLDING) call.unhold() else call.hold()
    }

    /** Поменять линии местами: вернуть удержанный, текущий система удержит сама. */
    fun swap() {
        val call = primary ?: return
        val other = secondary(call) ?: return
        if (other.state == Call.STATE_HOLDING) other.unhold() else if (call.state == Call.STATE_HOLDING) call.unhold()
    }

    fun chooseSim(index: Int) {
        val call = visible.firstOrNull { it.state == Call.STATE_SELECT_PHONE_ACCOUNT } ?: return
        val handle = simAccounts(call).getOrNull(index) ?: return
        call.phoneAccountSelected(handle, false)
    }

    @Suppress("DEPRECATION")
    fun setRoute(route: Int) { service?.setAudioRoute(route) }

    /** Отклонить и отправить SMS. Текст отправляет сама система. */
    fun rejectWithMessage(text: String) {
        visible.firstOrNull { it.state == Call.STATE_RINGING }?.reject(true, text)
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
        val back = if (s.routes and CallAudioState.ROUTE_BLUETOOTH != 0) CallAudioState.ROUTE_BLUETOOTH else CallAudioState.ROUTE_WIRED_OR_EARPIECE
        service?.setAudioRoute(if (s.speaker) back else CallAudioState.ROUTE_SPEAKER)
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun btName(device: android.bluetooth.BluetoothDevice): String? = runCatching { device.name }.getOrNull()

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
