package com.khizrim.dialer.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.provider.ContactsContract.Data
import com.khizrim.dialer.model.Channel
import com.khizrim.dialer.model.Method
import com.khizrim.dialer.model.Person
import com.khizrim.dialer.model.Phone

/** Чтение настоящих контактов вместе со способами связи, которые добавили мессенджеры. */
object ContactsSource {

    /** MIME-типы строк, которые WhatsApp, Telegram, Signal и Viber кладут в контакты. */
    val messengerTypes: Map<String, Method> = mapOf(
        "vnd.android.cursor.item/vnd.com.whatsapp.profile" to Method.WA_CHAT,
        "vnd.android.cursor.item/vnd.com.whatsapp.voip.call" to Method.WA_CALL,
        "vnd.android.cursor.item/vnd.com.whatsapp.video.call" to Method.WA_VIDEO,
        "vnd.android.cursor.item/vnd.org.telegram.messenger.android.profile" to Method.TG_CHAT,
        "vnd.android.cursor.item/vnd.org.telegram.messenger.android.call" to Method.TG_CALL,
        "vnd.android.cursor.item/vnd.org.telegram.messenger.android.call.video" to Method.TG_VIDEO,
        "vnd.android.cursor.item/vnd.org.thoughtcrime.securesms.contact" to Method.SIG_CHAT,
        "vnd.android.cursor.item/vnd.org.thoughtcrime.securesms.call" to Method.SIG_CALL,
        "vnd.android.cursor.item/vnd.com.viber.voip.viber_number_call" to Method.VB_CALL,
        "vnd.android.cursor.item/vnd.com.viber.voip.viber_number_message" to Method.VB_CHAT,
    )

    private class Builder(val id: Long, val lookup: String, val name: String, val photo: String?, val starred: Boolean) {
        val phones = LinkedHashMap<String, Phone>()
        val channels = LinkedHashMap<Method, Channel>()
        var primaryNumber: String? = null
        var nickname: String? = null
        var company: String? = null
        var title: String? = null
    }

    fun load(context: Context): List<Person> {
        val types = listOf(CommonDataKinds.Phone.CONTENT_ITEM_TYPE, CommonDataKinds.Nickname.CONTENT_ITEM_TYPE, CommonDataKinds.Organization.CONTENT_ITEM_TYPE) + messengerTypes.keys
        val projection = arrayOf(
            Data._ID, Data.CONTACT_ID, Data.LOOKUP_KEY, Data.DISPLAY_NAME_PRIMARY, Data.PHOTO_THUMBNAIL_URI,
            Data.STARRED, Data.MIMETYPE, Data.DATA1, Data.DATA4, Data.IS_SUPER_PRIMARY,
            CommonDataKinds.Phone.TYPE, CommonDataKinds.Phone.LABEL,
        )
        val selection = Data.MIMETYPE + " IN (" + types.joinToString(",") { "?" } + ")"
        val builders = LinkedHashMap<Long, Builder>()
        context.contentResolver.query(Data.CONTENT_URI, projection, selection, types.toTypedArray(), null)?.use { c ->
            val iId = c.getColumnIndexOrThrow(Data._ID)
            val iContact = c.getColumnIndexOrThrow(Data.CONTACT_ID)
            val iLookup = c.getColumnIndexOrThrow(Data.LOOKUP_KEY)
            val iName = c.getColumnIndexOrThrow(Data.DISPLAY_NAME_PRIMARY)
            val iPhoto = c.getColumnIndexOrThrow(Data.PHOTO_THUMBNAIL_URI)
            val iStar = c.getColumnIndexOrThrow(Data.STARRED)
            val iMime = c.getColumnIndexOrThrow(Data.MIMETYPE)
            val iData = c.getColumnIndexOrThrow(Data.DATA1)
            val iPrimary = c.getColumnIndexOrThrow(Data.IS_SUPER_PRIMARY)
            val iData4 = c.getColumnIndexOrThrow(Data.DATA4)
            val iType = c.getColumnIndexOrThrow(CommonDataKinds.Phone.TYPE)
            val iLabel = c.getColumnIndexOrThrow(CommonDataKinds.Phone.LABEL)
            while (c.moveToNext()) {
                val contactId = c.getLong(iContact)
                val name = c.getString(iName)?.takeIf { it.isNotBlank() } ?: continue
                val b = builders.getOrPut(contactId) {
                    Builder(contactId, c.getString(iLookup) ?: contactId.toString(), name, c.getString(iPhoto), c.getInt(iStar) == 1)
                }
                val mime = c.getString(iMime)
                if (mime == CommonDataKinds.Phone.CONTENT_ITEM_TYPE) {
                    val number = c.getString(iData)?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                    val norm = Numbers.key(number)
                    if (norm !in b.phones) {
                        val label = CommonDataKinds.Phone.getTypeLabel(context.resources, c.getInt(iType), c.getString(iLabel)).toString()
                        b.phones[norm] = Phone(number, label.lowercase())
                    }
                    if (c.getInt(iPrimary) == 1) b.primaryNumber = norm
                } else if (mime == CommonDataKinds.Nickname.CONTENT_ITEM_TYPE) {
                    if (b.nickname == null) b.nickname = c.getString(iData)?.takeIf { it.isNotBlank() }
                } else if (mime == CommonDataKinds.Organization.CONTENT_ITEM_TYPE) {
                    // DATA1 — компания, DATA4 — должность
                    if (b.company == null) b.company = c.getString(iData)?.takeIf { it.isNotBlank() }
                    if (b.title == null) b.title = c.getString(iData4)?.takeIf { it.isNotBlank() }
                } else {
                    val method = messengerTypes[mime] ?: continue
                    if (method !in b.channels) b.channels[method] = Channel(method, c.getLong(iId), mime)
                }
            }
        }
        return builders.values.mapNotNull { b ->
            if (b.phones.isEmpty() && b.channels.isEmpty()) return@mapNotNull null
            val phones = b.phones.entries.sortedBy { if (it.key == b.primaryNumber) 0 else 1 }.map { it.value }
            val channels = buildList {
                addAll(b.channels.values.sortedBy { it.method.ordinal })
                if (phones.isNotEmpty()) { add(Channel(Method.PHONE)); add(Channel(Method.SMS)) }
            }
            Person(b.id, b.lookup, b.name, phones, channels, b.photo, b.starred, b.nickname, b.company, b.title)
        }
    }

    fun setStarred(context: Context, person: Person, starred: Boolean): Boolean = runCatching {
        val values = ContentValues().apply { put(ContactsContract.Contacts.STARRED, if (starred) 1 else 0) }
        val uri: Uri = ContactsContract.Contacts.getLookupUri(person.id, person.lookupKey)
        context.contentResolver.update(uri, values, null, null) > 0
    }.getOrDefault(false)

    /** Имя по номеру для экрана звонка. */
    fun nameFor(context: Context, number: String): String? = runCatching {
        if (number.isBlank()) return null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    /** Подпись номера в контакте («мобильный», «рабочий») для экрана входящего. */
    fun phoneLabelFor(context: Context, number: String): String? = runCatching {
        if (number.isBlank()) return null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.TYPE, ContactsContract.PhoneLookup.LABEL), null, null, null)?.use { c ->
            if (c.moveToFirst()) CommonDataKinds.Phone.getTypeLabel(context.resources, c.getInt(0), c.getString(1)).toString().lowercase() else null
        }
    }.getOrNull()
}

/** Сравнение номеров без кода страны и форматирования. */
object Numbers {
    fun key(number: String): String {
        val d = number.filter { it.isDigit() }
        return if (d.length > 10) d.takeLast(10) else d
    }
}
