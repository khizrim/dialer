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

    /** Известные определители: их видно даже тогда, когда функция внутри приложения ещё выключена. */
    val KNOWN = listOf(YANDEX, "com.yandex.browser", "com.kaspersky.who_calls", "com.truecaller", "app.source.getcontact", "com.numbuster.android")

    data class App(val pkg: String, val label: String, val ready: Boolean)

    /**
     * Установленные определители, кроме нас. ready = служба определителя включена и приложение
     * можно выбрать в «Определитель номера и спам». Яндекс включает её только после того, как
     * определитель включили в самом приложении. Порядок: готовые первыми, Яндекс выше других.
     */
    fun apps(context: Context): List<App> = runCatching {
        val pm = context.packageManager
        val intent = Intent(CallScreeningService.SERVICE_INTERFACE)
        val enabled = pm.queryIntentServices(intent, 0).map { it.serviceInfo.packageName }.toSet()
        val all = pm.queryIntentServices(intent, PackageManager.MATCH_DISABLED_COMPONENTS).map { it.serviceInfo.packageName }.toSet()
        val known = KNOWN.filter { pkg -> runCatching { pm.getApplicationInfo(pkg, 0) }.isSuccess }
        (all + known + enabled)
            .filter { it != context.packageName }
            .mapNotNull { pkg ->
                val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrNull() ?: return@mapNotNull null
                App(pkg, label, pkg in enabled)
            }
            .sortedWith(compareBy({ !it.ready }, { it.pkg != YANDEX }))
    }.getOrDefault(emptyList())

    /** Совместимость: названия установленных определителей. */
    fun installed(context: Context): List<String> = apps(context).map { it.label }

    /**
     * Куда вести по нажатию:
     * есть готовый определитель → системный выбор «Определитель номера и спам»;
     * установлен, но выключен → само приложение, чтобы включить там определитель;
     * ничего нет → Яндекс в магазине.
     */
    fun chooseIntent(context: Context, apps: List<App> = apps(context)): Intent {
        val ready = apps.firstOrNull { it.ready }
        val pending = apps.firstOrNull()
        val intent = when {
            // системный выбор «Определитель номера и спам»: там видно, что уже выбрано на телефоне
            (ready != null || pending != null) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            pending != null -> context.packageManager.getLaunchIntentForPackage(pending.pkg)
                ?: Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pending.pkg, null))
            else -> Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$YANDEX"))
        }
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

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
