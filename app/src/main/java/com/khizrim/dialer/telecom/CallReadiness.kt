package com.khizrim.dialer.telecom

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
import com.khizrim.dialer.R

/** Что мешает увидеть входящий звонок, и куда отправить человека, чтобы это исправить. */
enum class CallAlert(@StringRes val text: Int, @StringRes val action: Int, val dismissable: Boolean = false) {
    NOT_DEFAULT(R.string.alert_not_default, R.string.alert_fix),
    CALL_LOG(R.string.alert_call_log, R.string.alert_fix),
    NOTIFICATIONS(R.string.alert_notifications, R.string.alert_fix),
    CONTACTS_FOR_SILENCE(R.string.alert_contacts_silence, R.string.alert_fix),
    FULL_SCREEN(R.string.alert_full_screen, R.string.alert_fix),
    XIAOMI(R.string.alert_xiaomi, R.string.alert_open, dismissable = true),
}

object CallReadiness {
    private val xiaomi = setOf("xiaomi", "redmi", "poco")

    /** Самая важная проблема или null. */
    fun check(context: Context, isDefault: Boolean, xiaomiDismissed: Boolean, silenceUnknown: Boolean = false): CallAlert? {
        if (!isDefault) return CallAlert.NOT_DEFAULT
        // без журнала лента пустая и не отличает входящие от исходящих: об этом надо сказать
        if (context.checkSelfPermission(android.Manifest.permission.READ_CALL_LOG) != android.content.pm.PackageManager.PERMISSION_GRANTED) return CallAlert.CALL_LOG
        val nm = context.getSystemService(NotificationManager::class.java) ?: return null
        if (!nm.areNotificationsEnabled()) return CallAlert.NOTIFICATIONS
        if (silenceUnknown && context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return CallAlert.CONTACTS_FOR_SILENCE
        if (Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent()) return CallAlert.FULL_SCREEN
        if (!xiaomiDismissed && Build.MANUFACTURER.lowercase() in xiaomi) return CallAlert.XIAOMI
        return null
    }

    fun fixIntent(context: Context, alert: CallAlert): Intent? {
        val pkg = context.packageName
        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
        return when (alert) {
            CallAlert.NOT_DEFAULT -> Launcher.defaultDialerRequest(context)
            CallAlert.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
            CallAlert.CONTACTS_FOR_SILENCE, CallAlert.CALL_LOG -> details
            CallAlert.FULL_SCREEN -> if (Build.VERSION.SDK_INT >= 34)
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.fromParts("package", pkg, null)) else details
            // экран разрешений MIUI: «Экран блокировки», «Всплывающие окна», «Запуск в фоне»
            CallAlert.XIAOMI -> Intent("miui.intent.action.APP_PERM_EDITOR")
                .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                .putExtra("extra_pkgname", pkg)
        }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun fallbackIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Снять системное уведомление о пропущенных, когда человек открыл приложение. */
    // MODIFY_PHONE_STATE не нужен: звонилке по умолчанию система разрешает это сама
    @android.annotation.SuppressLint("MissingPermission")
    fun clearMissed(context: Context) {
        runCatching { context.getSystemService(android.telecom.TelecomManager::class.java)?.cancelMissedCallsNotification() }
    }
}
