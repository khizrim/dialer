package com.khizrim.dialer.telecom

import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import com.khizrim.dialer.model.CallPhase
import com.khizrim.dialer.ui.call.CallActions
import com.khizrim.dialer.ui.call.CallScreen
import com.khizrim.dialer.ui.components.rememberLast
import com.khizrim.dialer.ui.theme.DialerTheme
import com.khizrim.dialer.ui.theme.LocalReducedMotion
import com.khizrim.dialer.ui.theme.rememberReducedMotion

/** Экран звонка поверх всего, в том числе поверх экрана блокировки. */
class CallActivity : ComponentActivity() {
    private var proximity: PowerManager.WakeLock? = null

    /** Экран заблокирован: на входящем отвечаем удержанием. Не снимаем блокировку сами. */
    private var locked by androidx.compose.runtime.mutableStateOf(false)
    private var lockListener: Any? = null

    private fun readLock() { locked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true }

    override fun onResume() {
        super.onResume()
        readLock()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        readLock()
        if (Build.VERSION.SDK_INT >= 33) {
            val km = getSystemService(KeyguardManager::class.java)
            val l = KeyguardManager.KeyguardLockedStateListener { locked = it }
            // слушателю может не хватить прав: тогда достаточно проверки в onResume
            if (runCatching { km?.addKeyguardLockedStateListener(mainExecutor, l) }.isSuccess) lockListener = l
        }
        // ответ из уведомления: только при первом создании, не после поворота
        if (savedInstanceState == null) consumeAnswer(intent)

        val actions = object : CallActions {
            override fun answer() = CallManager.answer()
            override fun decline() = CallManager.decline()
            override fun hangup() = CallManager.hangup()
            override fun quickSms() = CallManager.rejectWithMessage(getString(com.khizrim.dialer.R.string.quick_reply))
            override fun remind() {
                val number = CallManager.currentNumber() ?: return
                CallNotifications.scheduleReminder(applicationContext, CallManager.currentName() ?: number, number)
                CallManager.decline()
            }
            override fun block() { CallManager.block(applicationContext) }
            override fun toggleMute() = CallManager.toggleMute()
            override fun toggleSpeaker() = CallManager.toggleSpeaker()
            override fun toggleDtmf() = CallManager.toggleDtmf()
            override fun tone(d: Char) = CallManager.tone(d)
            override fun endAndAnswer() = CallManager.endAndAnswer()
            override fun toggleHold() = CallManager.toggleHold()
            override fun swap() = CallManager.swap()
            override fun chooseSim(index: Int) = CallManager.chooseSim(index)
            override fun setRoute(route: Int) = CallManager.setRoute(route)
            override fun merge() = CallManager.merge()
            // «Добавить»: клавиатура-поиск поверх, текущий звонок система удержит при наборе
            override fun addCall() {
                startActivity(Intent(this@CallActivity, com.khizrim.dialer.MainActivity::class.java).setAction(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            override fun message() {
                val number = CallManager.currentNumber() ?: return
                Launcher.sms(applicationContext, number)
            }
        }

        setContent {
            DialerTheme {
                CompositionLocalProvider(LocalReducedMotion provides rememberReducedMotion()) {
                    val live = CallManager.state
                    val shown = rememberLast(live)
                    BackHandler { /* во время звонка «назад» ничего не закрывает */ }
                    LaunchedEffect(Unit) {
                        snapshotFlow { CallManager.state }.collect { s ->
                            if (s == null) finishAndRemoveTask()
                            proximity(s != null && s.phase != CallPhase.RINGING && s.phase != CallPhase.ENDED && s.route == com.khizrim.dialer.model.CallState.ROUTE_EARPIECE)
                        }
                    }
                    if (shown != null) CallScreen(shown, actions, locked = locked)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeAnswer(intent)
    }

    private fun consumeAnswer(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_ANSWER, false) != true) return
        intent.removeExtra(EXTRA_ANSWER)
        setIntent(intent)
        CallManager.answer()
    }

    override fun onDestroy() {
        proximity(false)
        if (Build.VERSION.SDK_INT >= 33) (lockListener as? KeyguardManager.KeyguardLockedStateListener)?.let {
            runCatching { getSystemService(KeyguardManager::class.java)?.removeKeyguardLockedStateListener(it) }
        }
        super.onDestroy()
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
    }

    /** Гасим экран, когда телефон у уха. */
    private fun proximity(on: Boolean) {
        if (on) {
            if (proximity?.isHeld == true) return
            val pm = getSystemService(PowerManager::class.java) ?: return
            if (!pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) return
            proximity = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "ozero:call").also { it.acquire(3 * 60 * 60 * 1000L) }
        } else {
            proximity?.let { if (it.isHeld) it.release() }
            proximity = null
        }
    }

    companion object {
        const val EXTRA_ANSWER = "answer"
        fun intent(context: Context): Intent = Intent(context, CallActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
