package com.khizrim.dialer.telecom

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import com.khizrim.dialer.data.ContactsSource
import com.khizrim.dialer.data.Prefs

/**
 * «Не беспокоить незнакомым»: на Android 10+ глушим звонок ещё до того, как телефон зазвонит.
 * Звонок не отклоняется: он тихо дозванивается и остаётся в журнале как пропущенный.
 */
class OzScreeningService : CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        val incoming = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || details.callDirection == Call.Details.DIRECTION_INCOMING
        val response = CallResponse.Builder()
        if (incoming && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && Prefs(this).silenceUnknown) {
            val number = details.handle?.schemeSpecificPart.orEmpty()
            if (number.isBlank() || ContactsSource.nameFor(this, number) == null) response.setSilenceCall(true)
        }
        respondToCall(details, response.build())
    }
}
