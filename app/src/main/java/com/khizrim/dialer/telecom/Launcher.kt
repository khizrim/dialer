package com.khizrim.dialer.telecom

import android.Manifest
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.telecom.TelecomManager
import com.khizrim.dialer.R
import com.khizrim.dialer.model.Method
import com.khizrim.dialer.model.Person

/** Запуск способа связи: звонок через Telecom, SMS или нужный экран мессенджера. */
object Launcher {

    /** Возвращает null при успехе или короткий текст ошибки для тоста. */
    fun perform(context: Context, person: Person, method: Method, number: String? = person.mainNumber): String? {
        val channel = person.channels.firstOrNull { it.method == method }
        return when {
            method == Method.PHONE -> number?.let { call(context, it) } ?: context.getString(R.string.err_no_number)
            method == Method.SMS -> number?.let { sms(context, it) } ?: context.getString(R.string.err_no_number)
            channel?.dataId != null && channel.mimeType != null -> {
                val uri = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, channel.dataId)
                val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, channel.mimeType).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                start(context, intent, context.getString(R.string.err_app_failed, method.network(context)))
            }
            else -> context.getString(R.string.err_app_missing, method.network(context))
        }
    }

    /** Установлено не из магазина на Android 13+: система может молча отказать в роли звонилки. */
    fun mayBeRestricted(context: Context): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 33) return false
        return runCatching {
            context.packageManager.getInstallSourceInfo(context.packageName).packageSource != android.content.pm.PackageInstaller.PACKAGE_SOURCE_STORE
        }.getOrDefault(true)
    }

    /** [pinned]: SIM выбрали руками ([sim], или «спросить» при [ask]) — запомненную не берём. */
    @android.annotation.SuppressLint("MissingPermission")
    fun call(context: Context, number: String, sim: String? = null, ask: Boolean = false, pinned: Boolean = false): String? {
        val uri = Uri.fromParts("tel", number, null)
        val tm = context.getSystemService(TelecomManager::class.java)
        val allowed = context.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        if (allowed && tm != null) {
            return try {
                val handle = when {
                    ask -> null
                    pinned -> runCatching { tm.callCapablePhoneAccounts }.getOrNull().orEmpty().firstOrNull { it.id == sim }
                    else -> simHandle(context, tm, number)
                }
                tm.placeCall(uri, Bundle().apply { handle?.let { putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, it) } })
                null
            } catch (e: SecurityException) {
                context.getString(R.string.err_call)
            }
        }
        // мы и есть звонилка: ACTION_DIAL вернулся бы к нам же, поэтому просим разрешение
        if (isDefaultDialer(context)) return context.getString(R.string.err_call_permission)
        return start(context, Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), context.getString(R.string.err_call))
    }

    /** SIM-карты, с которых можно звонить: (id, подпись). Пусто без READ_PHONE_STATE. */
    @android.annotation.SuppressLint("MissingPermission")
    fun sims(context: Context): List<Pair<String, String>> {
        val tm = context.getSystemService(TelecomManager::class.java) ?: return emptyList()
        val list = runCatching { tm.callCapablePhoneAccounts }.getOrNull().orEmpty()
        return list.mapIndexed { i, h ->
            val label = runCatching { tm.getPhoneAccount(h)?.label?.toString() }.getOrNull()?.takeIf { it.isNotBlank() }
            h.id to (label?.let { "SIM ${i + 1} · $it" } ?: context.getString(R.string.sim_n, i + 1))
        }
    }

    /** SIM, запомненная для номера, иначе выбранная в настройках, если она всё ещё в телефоне. */
    @android.annotation.SuppressLint("MissingPermission")
    fun simHandle(context: Context, tm: TelecomManager, number: String? = null): android.telecom.PhoneAccountHandle? {
        val prefs = com.khizrim.dialer.data.Prefs(context)
        val accounts = runCatching { tm.callCapablePhoneAccounts }.getOrNull().orEmpty()
        return listOfNotNull(number?.let { prefs.simFor(it) }, prefs.simForCalls).firstNotNullOfOrNull { id -> accounts.firstOrNull { it.id == id } }
    }

    /** Голосовая почта оператора. */
    fun voicemail(context: Context): String? {
        val tm = context.getSystemService(TelecomManager::class.java) ?: return context.getString(R.string.err_call)
        if (context.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) return context.getString(R.string.err_call_permission)
        return try { tm.placeCall(Uri.fromParts("voicemail", "", null), Bundle()); null } catch (e: Exception) { context.getString(R.string.err_call) }
    }

    fun sms(context: Context, number: String): String? =
        start(context, Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), context.getString(R.string.err_no_sms))

    fun addContact(context: Context, number: String): String? {
        val intent = Intent(ContactsContract.Intents.Insert.ACTION)
            .setType(ContactsContract.RawContacts.CONTENT_TYPE)
            .putExtra(ContactsContract.Intents.Insert.PHONE, number)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return start(context, intent, context.getString(R.string.err_no_contacts_app))
    }

    /** Новый контакт с уже набранным именем — системным редактором. */
    fun addContactNamed(context: Context, name: String): String? {
        val intent = Intent(ContactsContract.Intents.Insert.ACTION)
            .setType(ContactsContract.RawContacts.CONTENT_TYPE)
            .putExtra(ContactsContract.Intents.Insert.NAME, name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return start(context, intent, context.getString(R.string.err_no_contacts_app))
    }

    fun editContact(context: Context, person: Person): String? {
        val uri = ContactsContract.Contacts.getLookupUri(person.id, person.lookupKey)
        val intent = Intent(Intent.ACTION_EDIT).setDataAndType(uri, ContactsContract.Contacts.CONTENT_ITEM_TYPE)
            .putExtra("finishActivityOnSaveCompleted", true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return start(context, intent, context.getString(R.string.err_no_contacts_app))
    }

    private fun start(context: Context, intent: Intent, error: String): String? = try {
        context.startActivity(intent)
        null
    } catch (e: ActivityNotFoundException) {
        error
    } catch (e: SecurityException) {
        error
    }

    fun isDefaultDialer(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = context.getSystemService(RoleManager::class.java)
            return rm?.isRoleHeld(RoleManager.ROLE_DIALER) == true
        }
        val tm = context.getSystemService(TelecomManager::class.java)
        return tm?.defaultDialerPackage == context.packageName
    }

    fun defaultDialerRequest(context: Context): Intent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = context.getSystemService(RoleManager::class.java) ?: return null
            if (!rm.isRoleAvailable(RoleManager.ROLE_DIALER)) return null
            return rm.createRequestRoleIntent(RoleManager.ROLE_DIALER)
        }
        return Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
            .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
    }
}
