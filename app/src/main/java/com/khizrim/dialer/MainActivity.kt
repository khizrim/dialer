package com.khizrim.dialer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.khizrim.dialer.telecom.CallNotifications
import com.khizrim.dialer.ui.DialerApp
import com.khizrim.dialer.ui.theme.DialerTheme

class MainActivity : ComponentActivity() {
    private val vm: DialerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        CallNotifications.createChannels(this)
        if (savedInstanceState == null) { vm.countLaunch(); handle(intent) }
        setContent {
            DialerTheme { DialerApp(vm) }
        }
        // заставка держится, пока не загрузились люди (но не дольше 1 с), — без пустого экрана между ней и главной
        val started = android.os.SystemClock.uptimeMillis()
        val content = findViewById<android.view.View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                val go = vm.ready || android.os.SystemClock.uptimeMillis() - started > 1000
                if (go) content.viewTreeObserver.removeOnPreDrawListener(this)
                return go
            }
        })
    }

    override fun onResume() {
        super.onResume()
        vm.reload()
        CallNotifications.clearSummaries(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** tel:-ссылки и «Набрать номер» из других приложений открывают клавиатуру с номером. */
    private fun handle(intent: Intent?) {
        val tel = intent?.data?.takeIf { it.scheme == "tel" }?.schemeSpecificPart
        when {
            intent?.getBooleanExtra(CallNotifications.EXTRA_OPEN_UNKNOWN, false) == true -> vm.openUnknown()
            intent?.action == ACTION_ADD_CONTACT -> vm.addToContacts(intent.getStringExtra(EXTRA_NUMBER).orEmpty())
            intent?.action == CallNotifications.ACTION_OPEN_MISSED -> vm.openMissed(intent.getStringExtra(CallNotifications.EXTRA_MISSED_NUMBER))
            // «Перезвонить» из уведомления о пропущенном
            intent?.action == CallNotifications.ACTION_CALL_BACK && tel != null -> vm.callNumber(tel, intent.getStringExtra(CallNotifications.EXTRA_SIM))
            intent?.action == Intent.ACTION_DIAL -> vm.openKeypad(tel.orEmpty())
            intent?.action == Intent.ACTION_VIEW && tel != null -> vm.openKeypad(tel)
            // «Пропущенные» из системного уведомления: на главную, там они наверху
            intent?.action == Intent.ACTION_VIEW -> vm.goHome()
        }
    }

    /** Цифры с аппаратной клавиатуры (DeX, TalkBack) попадают в набор, когда он открыт. */
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (vm.keypadOpen) {
            val ch = event.unicodeChar.toChar()
            when {
                ch in '0'..'9' || ch == '*' || ch == '#' || ch == '+' -> { vm.type(ch); return true }
                keyCode == android.view.KeyEvent.KEYCODE_DEL -> { vm.backspace(); return true }
                keyCode == android.view.KeyEvent.KEYCODE_ENTER -> { vm.callTyped(); return true }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        const val ACTION_ADD_CONTACT = "com.khizrim.dialer.ADD_CONTACT"
        const val EXTRA_NUMBER = "number"
    }
}
