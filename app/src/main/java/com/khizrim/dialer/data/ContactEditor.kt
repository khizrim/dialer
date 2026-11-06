package com.khizrim.dialer.data

import android.content.ContentProviderOperation
import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts

/** Номер в редакторе: id строки в контактах (null — новый), сам номер и тип. */
data class EditPhone(val dataId: Long?, val number: String, val type: Int = CommonDataKinds.Phone.TYPE_MOBILE, val label: String? = null)

data class EditDraft(
    val contactId: Long?,
    val rawContactId: Long?,
    val nameDataId: Long?,
    val name: String,
    val phones: List<EditPhone>,
)

/** Простое редактирование контакта: имя и номера. Остальное — в системных Контактах. */
object ContactEditor {
    /** Типы, которые предлагаем: мобильный, домашний, рабочий, другой. */
    val types = listOf(CommonDataKinds.Phone.TYPE_MOBILE, CommonDataKinds.Phone.TYPE_HOME, CommonDataKinds.Phone.TYPE_WORK, CommonDataKinds.Phone.TYPE_OTHER)

    fun typeLabel(context: Context, type: Int, label: String?): String =
        CommonDataKinds.Phone.getTypeLabel(context.resources, type, label).toString()

    fun load(context: Context, contactId: Long, fallbackName: String): EditDraft {
        var raw: Long? = null
        var nameId: Long? = null
        var name = fallbackName
        val phones = ArrayList<EditPhone>()
        runCatching {
            context.contentResolver.query(
                Data.CONTENT_URI,
                arrayOf(Data._ID, Data.RAW_CONTACT_ID, Data.MIMETYPE, Data.DATA1, CommonDataKinds.Phone.TYPE, CommonDataKinds.Phone.LABEL),
                Data.CONTACT_ID + "=? AND " + Data.MIMETYPE + " IN (?,?)",
                arrayOf(contactId.toString(), CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE),
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    if (raw == null) raw = c.getLong(1)
                    if (c.getString(2) == CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE) {
                        if (nameId == null) { nameId = c.getLong(0); raw = c.getLong(1); c.getString(3)?.takeIf { it.isNotBlank() }?.let { name = it } }
                    } else {
                        val n = c.getString(3)?.trim().orEmpty()
                        if (n.isNotEmpty() && phones.none { Numbers.key(it.number) == Numbers.key(n) }) phones += EditPhone(c.getLong(0), n, c.getInt(4), c.getString(5))
                    }
                }
            }
        }
        return EditDraft(contactId, raw, nameId, name, phones)
    }

    fun blank(number: String): EditDraft = EditDraft(null, null, null, "", if (number.isBlank()) listOf(EditPhone(null, "")) else listOf(EditPhone(null, number)))

    /** Сохраняет черновик. Возвращает true при успехе. */
    fun save(context: Context, before: EditDraft?, after: EditDraft): Boolean = runCatching {
        val ops = ArrayList<ContentProviderOperation>()
        val phones = after.phones.filter { it.number.isNotBlank() }
        val raw = after.rawContactId
        if (after.contactId == null || raw == null) {
            ops += ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                .withValue(RawContacts.ACCOUNT_TYPE, null).withValue(RawContacts.ACCOUNT_NAME, null).build()
            if (after.name.isNotBlank()) ops += ContentProviderOperation.newInsert(Data.CONTENT_URI).withValueBackReference(Data.RAW_CONTACT_ID, 0)
                .withValue(Data.MIMETYPE, CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(CommonDataKinds.StructuredName.DISPLAY_NAME, after.name.trim()).build()
            phones.forEach { p ->
                ops += ContentProviderOperation.newInsert(Data.CONTENT_URI).withValueBackReference(Data.RAW_CONTACT_ID, 0)
                    .withValue(Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(CommonDataKinds.Phone.NUMBER, p.number.trim())
                    .withValue(CommonDataKinds.Phone.TYPE, p.type).build()
            }
        } else {
            // имя: меняем только если изменилось, иначе не трогаем отчество, фамилию и прочее
            if (after.name.trim() != before?.name?.trim() && after.name.isNotBlank()) {
                ops += if (after.nameDataId != null) {
                    ContentProviderOperation.newUpdate(Data.CONTENT_URI).withSelection(Data._ID + "=?", arrayOf(after.nameDataId.toString()))
                        .withValue(CommonDataKinds.StructuredName.DISPLAY_NAME, after.name.trim())
                        .withValue(CommonDataKinds.StructuredName.GIVEN_NAME, null)
                        .withValue(CommonDataKinds.StructuredName.FAMILY_NAME, null)
                        .withValue(CommonDataKinds.StructuredName.MIDDLE_NAME, null)
                        .withValue(CommonDataKinds.StructuredName.PREFIX, null)
                        .withValue(CommonDataKinds.StructuredName.SUFFIX, null).build()
                } else {
                    ContentProviderOperation.newInsert(Data.CONTENT_URI).withValue(Data.RAW_CONTACT_ID, raw)
                        .withValue(Data.MIMETYPE, CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                        .withValue(CommonDataKinds.StructuredName.DISPLAY_NAME, after.name.trim()).build()
                }
            }
            val kept = phones.mapNotNull { it.dataId }.toSet()
            before?.phones?.filter { it.dataId != null && it.dataId !in kept }?.forEach { p ->
                ops += ContentProviderOperation.newDelete(Data.CONTENT_URI).withSelection(Data._ID + "=?", arrayOf(p.dataId.toString())).build()
            }
            phones.forEach { p ->
                val old = before?.phones?.firstOrNull { it.dataId == p.dataId && p.dataId != null }
                if (old == null) {
                    ops += ContentProviderOperation.newInsert(Data.CONTENT_URI).withValue(Data.RAW_CONTACT_ID, raw)
                        .withValue(Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                        .withValue(CommonDataKinds.Phone.NUMBER, p.number.trim())
                        .withValue(CommonDataKinds.Phone.TYPE, p.type).build()
                } else if (old.number != p.number || old.type != p.type) {
                    ops += ContentProviderOperation.newUpdate(Data.CONTENT_URI).withSelection(Data._ID + "=?", arrayOf(p.dataId.toString()))
                        .withValue(CommonDataKinds.Phone.NUMBER, p.number.trim())
                        .withValue(CommonDataKinds.Phone.TYPE, p.type)
                        .withValue(CommonDataKinds.Phone.LABEL, if (p.type == CommonDataKinds.Phone.TYPE_CUSTOM) p.label else null).build()
                }
            }
        }
        if (ops.isEmpty()) return true
        context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        true
    }.getOrDefault(false)
}
