package com.khizrim.dialer.telecom

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import com.khizrim.dialer.MainActivity
import com.khizrim.dialer.R
import com.khizrim.dialer.model.CallPhase
import com.khizrim.dialer.model.CallState

/** Связь с системной телефонией. Работает, когда Ozero Phone выбран звонилкой по умолчанию. */
class OzInCallService : InCallService() {
    override fun onCreate() {
        super.onCreate()
        CallManager.attach(this)
    }

    override fun onDestroy() {
        CallManager.detach()
        CallNotifications.cancel(this)
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        CallManager.add(call)
        // исходящий открываем сразу; входящий показывает полноэкранное уведомление
        if (call.state != Call.STATE_RINGING) {
            startActivity(CallActivity.intent(this))
        }
    }

    override fun onCallRemoved(call: Call) {
        CallManager.remove(call)
        if (CallManager.currentNumber() == null) CallNotifications.cancel(this)
    }

    @Deprecated("Deprecated in Java")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        CallManager.audioChanged()
    }
}

object CallNotifications {
    private const val CHANNEL_INCOMING = "incoming"
    private const val CHANNEL_ONGOING = "ongoing"
    private const val CHANNEL_REMIND = "remind"
    private const val ID_CALL = 1
    private const val ID_REMIND = 2

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_INCOMING, "Входящие звонки", NotificationManager.IMPORTANCE_HIGH).apply {
                // звонок звенит сам через систему, уведомлению звук не нужен
                setSound(null, null)
                enableVibration(false)
            },
        )
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ONGOING, "Идущий звонок", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_REMIND, "Напоминания перезвонить", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun action(context: Context, act: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, code,
            Intent(context, CallActionReceiver::class.java).setAction(act),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun update(context: Context, state: CallState?, call: Call) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (state == null || state.phase == CallPhase.ENDED) return
        val open = PendingIntent.getActivity(context, 10, CallActivity.intent(context), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val who = state.name ?: state.number.ifEmpty { "Скрытый номер" }
        val ringing = state.phase == CallPhase.RINGING
        val b = Notification.Builder(context, if (ringing) CHANNEL_INCOMING else CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_oz_phone)
            .setContentTitle(who)
            .setContentText(if (ringing) "Входящий звонок" else if (state.phase == CallPhase.DIALING) "Вызов" else "Идёт звонок")
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(open)
        if (ringing) {
            b.setFullScreenIntent(open, true)
            b.addAction(Notification.Action.Builder(null, "Отклонить", action(context, CallActionReceiver.DECLINE, 1)).build())
            // «Ответить» открывает экран звонка напрямую: запуск экрана из приёмника Android 12+ запрещает
            val answer = PendingIntent.getActivity(
                context, 11, CallActivity.intent(context).putExtra(CallActivity.EXTRA_ANSWER, true),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            b.addAction(Notification.Action.Builder(null, "Ответить", answer).build())
        } else {
            b.setUsesChronometer(state.connectedAt > 0).setWhen(if (state.connectedAt > 0) state.connectedAt else System.currentTimeMillis())
            b.addAction(Notification.Action.Builder(null, "Завершить", action(context, CallActionReceiver.HANGUP, 3)).build())
        }
        runCatching { nm.notify(ID_CALL, b.build()) }
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(ID_CALL)
    }

    /** «Напомнить»: через час покажем уведомление «Перезвонить». */
    fun scheduleReminder(context: Context, name: String, number: String) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, ReminderReceiver::class.java).putExtra("name", name).putExtra("number", number)
        val pi = PendingIntent.getBroadcast(context, number.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 60 * 60 * 1000L, pi)
    }

    fun showReminder(context: Context, name: String, number: String) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val open = PendingIntent.getActivity(
            context, 20,
            Intent(context, MainActivity::class.java).setAction(Intent.ACTION_DIAL).setData(android.net.Uri.fromParts("tel", number, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(context, CHANNEL_REMIND)
            .setSmallIcon(R.drawable.ic_oz_remind)
            .setContentTitle("Перезвонить: $name")
            .setContentText("Вы просили напомнить час назад")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(ID_REMIND, n) }
    }
}

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            DECLINE -> CallManager.decline()
            HANGUP -> CallManager.hangup()
        }
    }

    companion object {
        const val DECLINE = "com.khizrim.dialer.DECLINE"
        const val HANGUP = "com.khizrim.dialer.HANGUP"
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val number = intent.getStringExtra("number") ?: return
        CallNotifications.showReminder(context, intent.getStringExtra("name") ?: number, number)
    }
}
