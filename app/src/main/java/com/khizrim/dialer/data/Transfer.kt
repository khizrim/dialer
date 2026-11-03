package com.khizrim.dialer.data

import android.content.Context
import android.net.Uri
import com.khizrim.dialer.model.Person
import org.json.JSONArray
import org.json.JSONObject

/**
 * Перенос настроек людей в файл и обратно. Ключи контактов на другом телефоне другие,
 * поэтому людей сопоставляем по номерам. Лишнего не трогаем: что не совпало, пропускаем.
 */
object Transfer {
    private const val VERSION = 1

    fun export(context: Context, uri: Uri, people: List<Person>): Boolean = runCatching {
        val sp = context.getSharedPreferences("ozero", Context.MODE_PRIVATE)
        val list = JSONArray()
        people.forEach { p ->
            val def = sp.getString("def:${p.lookupKey}", null)
            val num = sp.getString("num:${p.lookupKey}", null)
            if (def == null && num == null) return@forEach
            list.put(JSONObject().apply {
                put("name", p.name)
                put("numbers", JSONArray(p.phones.map { Numbers.key(it.number) }))
                def?.let { put("default", it) }
                num?.let { put("number", Numbers.key(it)) }
            })
        }
        val root = JSONObject().apply {
            put("app", "ozero-phone")
            put("version", VERSION)
            put("people", list)
            put("blocked", JSONArray(Blocklist.list(context)))
            put("settings", JSONObject().apply {
                put("silenceUnknown", sp.getBoolean("silenceUnknown", false))
            })
        }
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(root.toString(2).toByteArray()) } != null
    }.getOrDefault(false)

    /** Возвращает число людей, для которых что-то восстановили, или null, если файл не наш. */
    fun import(context: Context, uri: Uri, people: List<Person>): Int? = runCatching {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: return null
        val root = JSONObject(text)
        if (root.optString("app") != "ozero-phone") return null
        val sp = context.getSharedPreferences("ozero", Context.MODE_PRIVATE)
        val byNumber = HashMap<String, Person>()
        people.forEach { p -> p.phones.forEach { byNumber[Numbers.key(it.number)] = p } }
        val e = sp.edit()
        var restored = 0
        val list = root.optJSONArray("people") ?: JSONArray()
        for (i in 0 until list.length()) {
            val o = list.getJSONObject(i)
            val nums = o.optJSONArray("numbers") ?: continue
            val person = (0 until nums.length()).firstNotNullOfOrNull { byNumber[nums.getString(it)] } ?: continue
            o.optString("default").takeIf { it.isNotEmpty() }?.let { e.putString("def:${person.lookupKey}", it) }
            o.optString("number").takeIf { it.isNotEmpty() }?.let { key ->
                person.phones.firstOrNull { Numbers.key(it.number) == key }?.let { e.putString("num:${person.lookupKey}", it.number) }
            }
            restored++
        }
        root.optJSONObject("settings")?.let { s -> if (s.has("silenceUnknown")) e.putBoolean("silenceUnknown", s.getBoolean("silenceUnknown")) }
        e.apply()
        if (Blocklist.canUse(context)) {
            val have = Blocklist.list(context).map { Numbers.key(it) }.toSet()
            val blocked = root.optJSONArray("blocked") ?: JSONArray()
            for (i in 0 until blocked.length()) blocked.getString(i).let { if (Numbers.key(it) !in have) Blocklist.block(context, it) }
        }
        restored
    }.getOrNull()
}
