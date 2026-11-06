package com.khizrim.dialer.telecom

import android.os.Build

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.CallLog.Calls
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.TelecomManager
import com.khizrim.dialer.callerid.CallerId
import com.khizrim.dialer.callerid.CallerIdPrefs
import com.khizrim.dialer.callerid.SpamAction
import com.khizrim.dialer.data.Prefs
import com.khizrim.dialer.MainActivity
import com.khizrim.dialer.R
import com.khizrim.dialer.data.ContactsSource
import com.khizrim.dialer.model.CallPhase
import com.khizrim.dialer.model.CallState

/** Связь с системной телефонией. Работает, когда Ozero Phone выбран звонилкой по умолчанию. */
class OzInCallService : InCallService() {
    override fun onCreate() {
        super.onCreate()
        CallManager.attach(this)
        CallerId.warmUp(this)
    }

    override fun onDestroy() {
        CallManager.detach()
        CallNotifications.cancel(this)
        super.onDestroy()
    }

    private val io = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onCallAdded(call: Call) {
        val mayBeSilenced = Prefs(this).silenceUnknownActive(this) || CallerIdPrefs(this).action == SpamAction.SILENCE
        if (call.state == Call.STATE_RINGING && mayBeSilenced) {
            // поиск в контактах и в базе номеров не на главном потоке: большая книга не задержит входящий
            val number = call.details?.handle?.schemeSpecificPart.orEmpty()
            io.execute {
                val silent = runCatching { CallerId.shouldSilence(this, number) }.getOrDefault(false)
                main.post {
                    if (call.state == Call.STATE_DISCONNECTED) return@post
                    if (silent) addSilenced(call) else addVisible(call)
                }
            }
            return
        }
        addVisible(call)
    }

    /** «Не беспокоить незнакомым»: без звонка, без уведомления; звонок останется в журнале. */
    // silenceRinger доступен звонилке по умолчанию без MODIFY_PHONE_STATE
    @android.annotation.SuppressLint("MissingPermission")
    private fun addSilenced(call: Call) {
        CallManager.silence(call)
        CallNotifications.showSilenced(this)
        runCatching { getSystemService(TelecomManager::class.java)?.silenceRinger() }
        CallManager.add(call)
    }

    private fun addVisible(call: Call) {
        CallManager.add(call)
        // исходящий открываем сразу; входящий показывает полноэкранное уведомление
        if (call.state != Call.STATE_RINGING) {
            startActivity(CallActivity.intent(this))
        }
    }

    override fun onCallRemoved(call: Call) {
        CallManager.remove(call)
        if (!CallManager.hasVisibleCall) CallNotifications.cancel(this)
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
    private const val CHANNEL_MISSED = "missed"
    private const val CHANNEL_SILENT = "silent"
    private const val ID_MISSED = 3
    private const val ID_MISSED_SUMMARY = 5
    private const val TAG_MISSED = "missed:"
    private const val GROUP_MISSED = "com.khizrim.dialer.MISSED"
    const val EXTRA_SIM = "sim"
    private const val ID_SILENT = 4
    const val EXTRA_OPEN_UNKNOWN = "open_unknown"
    const val ACTION_OPEN_MISSED = "com.khizrim.dialer.OPEN_MISSED"
    const val EXTRA_MISSED_NUMBER = "missed_number"
    private const val ID_CALL = 1
    private const val ID_REMIND = 2

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_INCOMING, context.getString(R.string.ch_incoming), NotificationManager.IMPORTANCE_HIGH).apply {
                // звонок звенит сам через систему, уведомлению звук не нужен
                setSound(null, null)
                enableVibration(false)
            },
        )
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ONGOING, context.getString(R.string.ch_ongoing), NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_REMIND, context.getString(R.string.ch_remind), NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_MISSED, context.getString(R.string.ch_missed), NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_SILENT, context.getString(R.string.ch_silent), NotificationManager.IMPORTANCE_LOW))
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
        val who = state.name ?: state.number.takeIf { it.isNotEmpty() }?.let { com.khizrim.dialer.data.PhoneFormat.pretty(it) } ?: context.getString(R.string.hidden_number)
        val ringing = state.phase == CallPhase.RINGING
        // наш экран входящего уже открыт — уведомление тихое, без всплывающей плашки поверх имени
        val b = Notification.Builder(context, if (ringing && !CallActivity.shown) CHANNEL_INCOMING else CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_oz_phone)
            .setContentTitle(who)
            .setContentText(context.getString(if (ringing) R.string.incoming_call else if (state.phase == CallPhase.DIALING) R.string.calling else R.string.call_in_progress))
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(open)
        if (ringing) {
            b.setFullScreenIntent(open, true)
            val decline = action(context, CallActionReceiver.DECLINE, 1)
            // «Ответить» открывает экран звонка напрямую: запуск экрана из приёмника Android 12+ запрещает
            val answer = PendingIntent.getActivity(
                context, 11, CallActivity.intent(context).putExtra(CallActivity.EXTRA_ANSWER, true),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // системный вид входящего: крупные «Ответить» и «Отклонить», выше остальных уведомлений
                // своя иконка: фото контакта или трубка на мягком фоне — иначе система рисует кружок с буквой
                val person = android.app.Person.Builder().setName(who).setImportant(true)
                    .setIcon(callerIcon(context, state.number)).build()
                b.setStyle(Notification.CallStyle.forIncomingCall(person, decline, answer))
            } else {
                b.addAction(Notification.Action.Builder(null, context.getString(R.string.decline), decline).build())
                b.addAction(Notification.Action.Builder(null, context.getString(R.string.answer), answer).build())
            }
        } else {
            b.setUsesChronometer(state.connectedAt > 0).setWhen(if (state.connectedAt > 0) state.connectedAt else System.currentTimeMillis())
            b.addAction(Notification.Action.Builder(null, context.getString(R.string.end), action(context, CallActionReceiver.HANGUP, 3)).build())
        }
        runCatching { nm.notify(ID_CALL, b.build()) }
    }

    /**
     * Пропущенные: по уведомлению на каждый номер в одной группе, у каждого свои «Перезвонить» и «Написать».
     * Номера берём из журнала (новые пропущенные), а не из сводки Telecom: там при нескольких
     * пропущенных остаётся только последний номер, и «Перезвонить» звонил бы не тому.
     */
    fun showMissed(context: Context, count: Int, number: String?) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val old = runCatching { nm.activeNotifications.filter { it.tag?.startsWith(TAG_MISSED) == true }.map { it.tag } }.getOrDefault(emptyList())
        if (count <= 0) { old.forEach { nm.cancel(it, ID_MISSED) }; nm.cancel(ID_MISSED); nm.cancel(ID_MISSED_SUMMARY); return }
        val fresh = newMissed(context).ifEmpty { listOfNotNull(number?.let { MissedGroup(it, 1, System.currentTimeMillis(), null) }) }
        if (fresh.isEmpty()) { nm.cancel(ID_MISSED); return }
        nm.cancel(ID_MISSED) // старое сводное уведомление без тега
        val keep = fresh.map { TAG_MISSED + com.khizrim.dialer.data.Numbers.key(it.number) }.toSet()
        old.filter { it !in keep }.forEach { nm.cancel(it, ID_MISSED) }
        fresh.forEach { g -> notifyMissed(context, nm, g) }
        if (fresh.size > 1) {
            val total = fresh.sumOf { it.count }
            val summary = Notification.Builder(context, CHANNEL_MISSED)
                .setSmallIcon(R.drawable.ic_oz_missed)
                .setContentTitle(context.resources.getQuantityString(R.plurals.missed_many, total, total))
                .setCategory(Notification.CATEGORY_MISSED_CALL)
                .setGroup(GROUP_MISSED).setGroupSummary(true)
                .setContentIntent(openMissed(context, null, 30))
                .setAutoCancel(true)
                .build()
            runCatching { nm.notify(ID_MISSED_SUMMARY, summary) }
        } else nm.cancel(ID_MISSED_SUMMARY)
    }

    private class MissedGroup(val number: String, val count: Int, val last: Long, val account: String?)

    /** Новые (непрочитанные) пропущенные из журнала, по номеру: сколько раз и когда последний. */
    private fun newMissed(context: Context): List<MissedGroup> = runCatching {
        val rows = ArrayList<Triple<String, Long, String?>>()
        context.contentResolver.query(
            Calls.CONTENT_URI, arrayOf(Calls.NUMBER, Calls.DATE, Calls.PHONE_ACCOUNT_ID),
            "${Calls.TYPE} = ${Calls.MISSED_TYPE} AND ${Calls.NEW} = 1", null, "${Calls.DATE} DESC",
        )?.use { cur -> while (cur.moveToNext() && rows.size < 200) rows += Triple(cur.getString(0).orEmpty(), cur.getLong(1), cur.getString(2)) }
        rows.filter { it.first.isNotBlank() }
            .groupBy { com.khizrim.dialer.data.Numbers.key(it.first) }
            .values.map { g -> MissedGroup(g.first().first, g.size, g.first().second, g.first().third) }
            .sortedByDescending { it.last }
            .take(8)
    }.getOrDefault(emptyList())

    private fun openMissed(context: Context, number: String?, code: Int): PendingIntent = PendingIntent.getActivity(
        // пропущенный от контакта → его карточка, иначе → лента с фильтром «Пропущенные»
        context, code, Intent(context, MainActivity::class.java).setAction(ACTION_OPEN_MISSED)
            .putExtra(EXTRA_MISSED_NUMBER, number)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun notifyMissed(context: Context, nm: NotificationManager, g: MissedGroup) {
        val key = com.khizrim.dialer.data.Numbers.key(g.number)
        val code = 1000 + (key.hashCode() and 0xFFFF) * 3
        val name = ContactsSource.nameFor(context, g.number)
        val who = name ?: com.khizrim.dialer.data.PhoneFormat.pretty(g.number)
        val b = Notification.Builder(context, CHANNEL_MISSED)
            .setSmallIcon(R.drawable.ic_oz_missed)
            .setContentTitle(if (g.count == 1) context.getString(R.string.missed_from, who) else context.resources.getQuantityString(R.plurals.missed_many_from, g.count, g.count, who))
            .setContentText(if (name != null) com.khizrim.dialer.data.PhoneFormat.pretty(g.number) else null)
            .setWhen(g.last).setShowWhen(true)
            .setCategory(Notification.CATEGORY_MISSED_CALL)
            .setGroup(GROUP_MISSED)
            .setContentIntent(openMissed(context, if (name != null) g.number else null, code))
            .setAutoCancel(true)
        // «Перезвонить» — с той SIM, на которую пришёл звонок
        val call = PendingIntent.getActivity(
            context, code + 1, Intent(context, MainActivity::class.java).setAction(ACTION_CALL_BACK).setData(android.net.Uri.fromParts("tel", g.number, null))
                .putExtra(EXTRA_SIM, g.account)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val sms = PendingIntent.getActivity(
            context, code + 2, Intent(Intent.ACTION_SENDTO, android.net.Uri.fromParts("smsto", g.number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        b.addAction(Notification.Action.Builder(null, context.getString(R.string.call_back), call).build())
        b.addAction(Notification.Action.Builder(null, context.getString(R.string.write), sms).build())
        runCatching { nm.notify(TAG_MISSED + key, ID_MISSED, b.build()) }
    }

    /** Фото звонящего из контактов или трубка на мягком голубом круге. */
    private fun callerIcon(context: Context, number: String): android.graphics.drawable.Icon? = runCatching {
        val photo = if (number.isNotBlank()) runCatching {
            val uri = android.net.Uri.withAppendedPath(android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI, android.net.Uri.encode(number))
            context.contentResolver.query(uri, arrayOf(android.provider.ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }?.let { s -> context.contentResolver.openInputStream(android.net.Uri.parse(s))?.use { android.graphics.BitmapFactory.decodeStream(it) } }
        }.getOrNull() else null
        if (photo != null) return@runCatching android.graphics.drawable.Icon.createWithBitmap(photo)
        val size = 96
        val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE6F1FF.toInt() }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        context.getDrawable(R.drawable.ic_oz_phone)?.mutate()?.let { d ->
            d.setTint(0xFF1A6FE0.toInt())
            d.setBounds(size / 4, size / 4, size * 3 / 4, size * 3 / 4)
            d.draw(canvas)
        }
        android.graphics.drawable.Icon.createWithBitmap(bmp)
    }.getOrNull()

    /** Одно тихое сводное уведомление о незнакомых без звука. */
    fun showSilenced(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val prefs = context.getSharedPreferences("ozero", Context.MODE_PRIVATE)
        val count = prefs.getInt("silencedCount", 0) + 1
        prefs.edit().putInt("silencedCount", count).apply()
        val open = PendingIntent.getActivity(
            context, 40, Intent(context, MainActivity::class.java).putExtra(EXTRA_OPEN_UNKNOWN, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(context, CHANNEL_SILENT)
            .setSmallIcon(R.drawable.ic_oz_bell_off)
            .setContentTitle(context.resources.getQuantityString(R.plurals.silenced_summary, count, count))
            .setContentText(context.getString(R.string.silenced_text))
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, context.getString(R.string.look), open).build())
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(ID_SILENT, n) }
    }

    /** Перезвонили одному номеру: снимаем только его уведомление, остальные пропущенные остаются. */
    fun clearMissed(context: Context, number: String) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.cancel(TAG_MISSED + com.khizrim.dialer.data.Numbers.key(number), ID_MISSED)
        val left = runCatching { nm.activeNotifications.count { it.tag?.startsWith(TAG_MISSED) == true && it.tag != TAG_MISSED + com.khizrim.dialer.data.Numbers.key(number) } }.getOrDefault(0)
        if (left <= 1) nm.cancel(ID_MISSED_SUMMARY)
        // в журнале пропущенные этого номера больше не новые, иначе следующая сводка вернёт уведомление
        val key = com.khizrim.dialer.data.Numbers.key(number)
        Thread {
            runCatching {
                val ids = ArrayList<Long>()
                context.contentResolver.query(
                    Calls.CONTENT_URI, arrayOf(Calls._ID, Calls.NUMBER),
                    "${Calls.TYPE} = ${Calls.MISSED_TYPE} AND ${Calls.NEW} = 1", null, null,
                )?.use { c -> while (c.moveToNext()) if (com.khizrim.dialer.data.Numbers.key(c.getString(1).orEmpty()) == key) ids += c.getLong(0) }
                if (ids.isNotEmpty()) {
                    val v = android.content.ContentValues().apply { put(Calls.NEW, 0) }
                    context.contentResolver.update(Calls.CONTENT_URI, v, "${Calls._ID} IN (${ids.joinToString(",")})", null)
                }
            }
        }.start()
    }

    /** Человек открыл приложение: сводки больше не нужны. */
    fun clearSummaries(context: Context) {
        context.getSharedPreferences("ozero", Context.MODE_PRIVATE).edit().putInt("silencedCount", 0).apply()
        context.getSystemService(NotificationManager::class.java)?.let { nm ->
            nm.cancel(ID_SILENT); nm.cancel(ID_MISSED); nm.cancel(ID_MISSED_SUMMARY)
            runCatching { nm.activeNotifications.filter { it.tag?.startsWith(TAG_MISSED) == true }.forEach { nm.cancel(it.tag, ID_MISSED) } }
        }
    }

    const val ACTION_CALL_BACK = "com.khizrim.dialer.CALL_BACK"

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
            .setContentTitle(context.getString(R.string.remind_title, name))
            .setContentText(context.getString(R.string.remind_text))
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

/** Telecom просит звонилку по умолчанию показать пропущенные сама: делаем это в своём тоне. */
class MissedCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION) return
        val count = intent.getIntExtra(TelecomManager.EXTRA_NOTIFICATION_COUNT, 0)
        val number = intent.getStringExtra(TelecomManager.EXTRA_NOTIFICATION_PHONE_NUMBER)
        val pending = goAsync()
        Thread {
            runCatching { CallNotifications.showMissed(context, count, number) }
            pending.finish()
        }.start()
    }
}
