package com.khizrim.dialer.callerid

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.CallLog
import android.provider.Settings
import android.telecom.CallScreeningService

/**
 * Внешние определители номера (Яндекс, Kaspersky Who Calls, …).
 *
 * Android 10+ даёт им отдельную роль «Определитель номера и спам»: система сама передаёт
 * им каждый входящий, они блокируют его или рисуют свою плашку поверх экрана звонка.
 * Звонилка их подпись не получает, поэтому мы не мешаем им и показываем,
 * что именно они заблокировали.
 */
object ExternalCallerId {
    /** Яндекс держит определитель номера в приложении «Яндекс с Алисой». */
    const val YANDEX = "ru.yandex.searchplugin"

    /** Установленные приложения-определители, кроме нас. Порядок: Яндекс первым. */
    fun installed(context: Context): List<String> = runCatching {
        val pm = context.packageManager
        pm.queryIntentServices(Intent(CallScreeningService.SERVICE_INTERFACE), PackageManager.GET_META_DATA)
            .map { it.serviceInfo.packageName }
            .filter { it != context.packageName }
            .distinct()
            .sortedBy { if (it == YANDEX) 0 else 1 }
            .mapNotNull { pkg -> runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrNull() }
    }.getOrDefault(emptyList())

    /** Куда вести по нажатию: выбор приложения-определителя или, если его нет, Яндекс в магазине. */
    fun chooseIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && installed(context).isNotEmpty()) {
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$YANDEX"))
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun storeFallback(): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$YANDEX")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Кто из внешних определителей заблокировал звонки: ключ — время звонка из журнала.
     * Android 10+ сам пишет это в журнал (CALL_SCREENING_APP_NAME).
     */
    fun blockedBy(context: Context, limit: Int = 300): Map<Long, String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyMap()
        return runCatching {
            val out = HashMap<Long, String>()
            val uri = CallLog.Calls.CONTENT_URI.buildUpon().appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, limit.toString()).build()
            val projection = arrayOf(CallLog.Calls.DATE, CallLog.Calls.CALL_SCREENING_APP_NAME, CallLog.Calls.CALL_SCREENING_COMPONENT_NAME)
            val where = "${CallLog.Calls.TYPE} IN (${CallLog.Calls.BLOCKED_TYPE}, ${CallLog.Calls.REJECTED_TYPE})"
            context.contentResolver.query(uri, projection, where, null, CallLog.Calls.DATE + " DESC")?.use { c ->
                while (c.moveToNext()) {
                    val app = c.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                    val component = c.getString(2).orEmpty()
                    if (component.startsWith(context.packageName + "/")) continue
                    out[c.getLong(0)] = app
                }
            }
            out
        }.getOrDefault(emptyMap())
    }
}
