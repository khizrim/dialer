package com.khizrim.dialer.telecom

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.Connection
import com.khizrim.dialer.callerid.CallerId
import com.khizrim.dialer.callerid.CallerIdPrefs
import com.khizrim.dialer.callerid.SpamAction
import com.khizrim.dialer.data.ContactsSource
import com.khizrim.dialer.data.Prefs

/**
 * Решает судьбу входящего ещё до того, как телефон зазвонит.
 *
 * Контакты звонят всегда. Подозрительные номера (подмена, «звонки-сбросы») по настройке
 * глушатся, сбрасываются или просто подписываются. «Не беспокоить незнакомым» глушит
 * остальных незнакомых. Заглушённый звонок не отклоняется: он тихо дозванивается и
 * остаётся в журнале как пропущенный.
 */
class OzScreeningService : CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        val incoming = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || details.callDirection == Call.Details.DIRECTION_INCOMING
        if (!incoming) {
            respondToCall(details, CallResponse.Builder().build())
            return
        }
        // база номеров и контакты читаются с диска: не держим главный поток
        Thread({
            val response = runCatching { decide(details) }.getOrElse { CallResponse.Builder().build() }
            respondToCall(details, response)
        }, "screening").start()
    }

    private fun decide(details: Call.Details): CallResponse {
        val response = CallResponse.Builder()
        val number = details.handle?.schemeSpecificPart.orEmpty()
        if (number.isNotBlank() && ContactsSource.nameFor(this, number) != null) return response.build()

        val verificationFailed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            details.callerNumberVerificationStatus == Connection.VERIFICATION_STATUS_FAILED
        val facts = CallerId.facts(this, number, verificationFailed)
        val prefs = CallerIdPrefs(this)
        if (facts.suspicious && CallerId.actsOn(this, facts, prefs)) {
            when (prefs.action) {
                SpamAction.REJECT -> {
                    // без звонка и без системного уведомления, но в тихой сводке «Незнакомые номера»
                    runCatching { CallNotifications.showSilenced(this) }
                    return response
                        .setDisallowCall(true)
                        .setRejectCall(true)
                        .setSkipNotification(true)
                        .build()
                }
                SpamAction.SILENCE -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) response.setSilenceCall(true)
                SpamAction.LABEL -> {}
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && Prefs(this).silenceUnknownActive(this)) response.setSilenceCall(true)
        return response.build()
    }
}
