package com.khizrim.dialer.data

import android.content.ContentValues
import android.content.Context
import android.content.res.Resources
import android.provider.BlockedNumberContract
import android.provider.BlockedNumberContract.BlockedNumbers
import android.provider.CallLog
import com.khizrim.dialer.R
import com.khizrim.dialer.model.Method
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Запись журнала звонков. */
data class CallRecord(val number: String, val type: Int, val date: Long, val duration: Long) {
    val missed: Boolean get() = type == CallLog.Calls.MISSED_TYPE
    fun text(res: Resources): String = when (type) {
        CallLog.Calls.INCOMING_TYPE -> Dates.withDuration(res, res.getString(R.string.h_incoming), duration)
        CallLog.Calls.OUTGOING_TYPE -> if (duration > 0) Dates.withDuration(res, res.getString(R.string.h_outgoing), duration) else res.getString(R.string.h_outgoing_no_answer)
        CallLog.Calls.MISSED_TYPE -> res.getString(R.string.h_missed)
        CallLog.Calls.REJECTED_TYPE -> res.getString(R.string.h_rejected)
        CallLog.Calls.BLOCKED_TYPE -> res.getString(R.string.h_blocked)
        CallLog.Calls.VOICEMAIL_TYPE -> res.getString(R.string.h_voicemail)
        else -> res.getString(R.string.h_call)
    }
}

object CallLogSource {
    fun load(context: Context, limit: Int = 500): List<CallRecord> = runCatching {
        val uri = CallLog.Calls.CONTENT_URI.buildUpon().appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, limit.toString()).build()
        val projection = arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION)
        val out = ArrayList<CallRecord>()
        context.contentResolver.query(uri, projection, null, null, CallLog.Calls.DATE + " DESC")?.use { c ->
            while (c.moveToNext()) {
                val number = c.getString(0)?.takeIf { it.isNotBlank() } ?: continue
                out += CallRecord(number, c.getInt(1), c.getLong(2), c.getLong(3))
            }
        }
        out
    }.getOrDefault(emptyList())
}

/** Связь через мессенджер, которую журнал звонков не видит. Храним сами. */
data class Interaction(val key: String, val method: Method, val at: Long)

/** Локальные настройки: способ по умолчанию, скрытые с главной, история действий. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("ozero", Context.MODE_PRIVATE)

    /** null: способ не выбран, берём первый подходящий. ASK: спрашивать каждый раз. */
    fun defaultFor(lookupKey: String): String? = sp.getString("def:$lookupKey", null)
    fun setDefault(lookupKey: String, method: Method?) {
        sp.edit().putString("def:$lookupKey", method?.name ?: ASK).apply()
    }

    fun hiddenAt(key: String): Long = sp.getLong("hid:$key", 0L)
    fun hide(keys: Collection<String>, at: Long = System.currentTimeMillis()) {
        val e = sp.edit()
        keys.forEach { e.putLong("hid:$it", at) }
        e.apply()
    }
    fun unhide(key: String) { sp.edit().remove("hid:$key").apply() }

    fun interactions(): List<Interaction> = sp.getString("log", "").orEmpty().lines().mapNotNull { line ->
        val parts = line.split('\t')
        if (parts.size != 3) return@mapNotNull null
        val m = runCatching { Method.valueOf(parts[1]) }.getOrNull() ?: return@mapNotNull null
        Interaction(parts[0], m, parts[2].toLongOrNull() ?: return@mapNotNull null)
    }

    fun log(key: String, method: Method, at: Long = System.currentTimeMillis()) {
        val lines = (interactions() + Interaction(key, method, at)).takeLast(200)
        sp.edit().putString("log", lines.joinToString("\n") { "${it.key}\t${it.method.name}\t${it.at}" }).apply()
    }

    var roleSkipped: Boolean
        get() = sp.getBoolean("roleSkipped", false)
        set(v) { sp.edit().putBoolean("roleSkipped", v).apply() }

    var xiaomiHintSeen: Boolean
        get() = sp.getBoolean("xiaomiHint", false)
        set(v) { sp.edit().putBoolean("xiaomiHint", v).apply() }

    /** Глушим незнакомых, только если можем отличить знакомых: без доступа к контактам глушили бы всех. */
    fun silenceUnknownActive(context: Context): Boolean =
        silenceUnknown && context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED

    var silenceUnknown: Boolean
        get() = sp.getBoolean("silenceUnknown", false)
        set(v) { sp.edit().putBoolean("silenceUnknown", v).apply() }

    companion object { const val ASK = "ASK" }
}

/** Системный чёрный список. Писать в него может только звонилка по умолчанию. */
object Blocklist {
    fun canUse(context: Context): Boolean = runCatching { BlockedNumberContract.canCurrentUserBlockNumbers(context) }.getOrDefault(false)

    fun list(context: Context): List<String> = runCatching {
        val out = ArrayList<String>()
        context.contentResolver.query(BlockedNumbers.CONTENT_URI, arrayOf(BlockedNumbers.COLUMN_ORIGINAL_NUMBER), null, null, null)?.use { c ->
            while (c.moveToNext()) c.getString(0)?.let { out += it }
        }
        out
    }.getOrDefault(emptyList())

    fun block(context: Context, number: String): Boolean = runCatching {
        val values = ContentValues().apply { put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number) }
        context.contentResolver.insert(BlockedNumbers.CONTENT_URI, values) != null
    }.getOrDefault(false)

    fun unblock(context: Context, number: String): Boolean = runCatching {
        BlockedNumberContract.unblock(context, number) > 0
    }.getOrDefault(false)
}

object Dates {
    private val locale: Locale get() = Locale.getDefault()

    /** «Сегодня», «Вчера», «Пн», «12 окт». */
    fun day(res: Resources, ts: Long, now: Long = System.currentTimeMillis()): String {
        val a = Calendar.getInstance().apply { timeInMillis = ts }
        val b = Calendar.getInstance().apply { timeInMillis = now }
        val days = daysBetween(a, b)
        return when {
            days <= 0 -> res.getString(R.string.today)
            days == 1 -> res.getString(R.string.yesterday)
            days < 7 -> SimpleDateFormat("EE", locale).format(Date(ts)).replaceFirstChar { it.uppercase() }.trimEnd('.')
            a.get(Calendar.YEAR) == b.get(Calendar.YEAR) -> SimpleDateFormat("d MMM", locale).format(Date(ts)).trimEnd('.')
            else -> SimpleDateFormat("d MMM yyyy", locale).format(Date(ts))
        }
    }

    /** «Пропущенный, вчера» для подписи на главной. */
    fun missed(res: Resources, ts: Long): String {
        val a = Calendar.getInstance().apply { timeInMillis = ts }
        val days = daysBetween(a, Calendar.getInstance())
        val whenText = when {
            days <= 0 -> res.getString(R.string.at_time, DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(Date(ts)))
            days == 1 -> res.getString(R.string.yesterday_lower)
            else -> day(res, ts)
        }
        return res.getString(R.string.missed_when, whenText)
    }

    fun withDuration(res: Resources, base: String, seconds: Long): String = when {
        seconds <= 0 -> base
        seconds < 60 -> res.getString(R.string.dur_sec, base, seconds.toInt())
        else -> res.getString(R.string.dur_min, base, (seconds / 60).toInt())
    }

    private fun daysBetween(a: Calendar, b: Calendar): Int {
        val x = a.clone() as Calendar
        val y = b.clone() as Calendar
        listOf(x, y).forEach {
            it.set(Calendar.HOUR_OF_DAY, 0); it.set(Calendar.MINUTE, 0); it.set(Calendar.SECOND, 0); it.set(Calendar.MILLISECOND, 0)
        }
        return ((y.timeInMillis - x.timeInMillis) / 86_400_000L).toInt()
    }
}
