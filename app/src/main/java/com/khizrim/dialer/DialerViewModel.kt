package com.khizrim.dialer

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.khizrim.dialer.data.Blocklist
import com.khizrim.dialer.data.CallLogSource
import com.khizrim.dialer.data.CallRecord
import com.khizrim.dialer.data.ContactsSource
import com.khizrim.dialer.data.Dates
import com.khizrim.dialer.data.Interaction
import com.khizrim.dialer.data.Numbers
import com.khizrim.dialer.data.PhoneFormat
import com.khizrim.dialer.data.Prefs
import com.khizrim.dialer.data.T9
import com.khizrim.dialer.model.HistoryEntry
import com.khizrim.dialer.model.HomeEntry
import com.khizrim.dialer.model.Method
import com.khizrim.dialer.model.Person
import com.khizrim.dialer.telecom.Launcher
import com.khizrim.dialer.ui.theme.Motion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { HOME, CONTACTS, PERSON, BLOCKED }
enum class SheetMode { OTHER, DEFAULT }
enum class MenuContext { HOME, SEARCH }

data class SheetState(val mode: SheetMode, val personId: Long)
data class MenuState(val entry: HomeEntry, val context: MenuContext)
data class ConfirmState(val title: String, val description: String, val verb: String, val run: () -> Unit)
data class ToastState(val text: String, val undo: Boolean = false, val id: Long = System.nanoTime())

/** Совпадение в поиске: какие символы имени и номера подсветить. */
data class SearchHit(val person: Person, val phone: String, val nameRange: IntRange?, val phoneRange: IntRange?, val rank: Int)

private class Loaded(
    val people: List<Person>,
    val calls: List<CallRecord>,
    val interactions: List<Interaction>,
    val blocked: List<String>,
)

/** Состояние приложения поверх настоящих контактов, журнала звонков и системного чёрного списка. */
class DialerViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()
    private val prefs = Prefs(app)
    val region: String = PhoneFormat.defaultRegion

    var ready by mutableStateOf(false); private set
    var isDefaultDialer by mutableStateOf(false); private set
    var hasContacts by mutableStateOf(false); private set
    var hasCallLog by mutableStateOf(false); private set

    /** «Не сейчас» на шаге со звонилкой по умолчанию. */
    var roleSkipped by mutableStateOf(false)

    val needsOnboarding: Boolean get() = !hasContacts || !hasCallLog || (!isDefaultDialer && !roleSkipped)

    var people by mutableStateOf<List<Person>>(emptyList()); private set
    private var byNumber: Map<String, Person> = emptyMap()
    private var byId: Map<Long, Person> = emptyMap()
    private var calls: List<CallRecord> = emptyList()
    private var interactions: List<Interaction> = emptyList()
    var blocked by mutableStateOf<List<String>>(emptyList()); private set

    val home = mutableStateListOf<HomeEntry>()
    private var defaultsVersion by mutableStateOf(0)

    var screen by mutableStateOf(Screen.HOME); private set
    var personId by mutableStateOf<Long?>(null); private set
    private var from = Screen.HOME
    private var fromSearch = false

    var keypadOpen by mutableStateOf(false); private set
    var keypad by mutableStateOf(""); private set

    var tidy by mutableStateOf(false); private set
    var leaving by mutableStateOf<String?>(null); private set
    private var removed: List<Pair<Int, HomeEntry>> = emptyList()

    var sheet by mutableStateOf<SheetState?>(null); private set
    var menu by mutableStateOf<MenuState?>(null); private set
    var confirm by mutableStateOf<ConfirmState?>(null); private set
    var toast by mutableStateOf<ToastState?>(null); private set

    private var toastJob: Job? = null
    private var loadJob: Job? = null

    private fun granted(p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    /** Перечитываем всё при каждом возвращении в приложение. */
    fun reload() {
        isDefaultDialer = Launcher.isDefaultDialer(ctx)
        hasContacts = granted(Manifest.permission.READ_CONTACTS)
        hasCallLog = granted(Manifest.permission.READ_CALL_LOG)
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                Loaded(
                    people = if (hasContacts) ContactsSource.load(ctx) else emptyList(),
                    calls = if (hasCallLog) CallLogSource.load(ctx) else emptyList(),
                    interactions = prefs.interactions(),
                    blocked = if (isDefaultDialer) Blocklist.list(ctx) else emptyList(),
                )
            }
            people = loaded.people
            byId = loaded.people.associateBy { it.id }
            byNumber = buildMap { loaded.people.forEach { p -> p.phones.forEach { put(Numbers.key(it.number), p) } } }
            calls = loaded.calls
            interactions = loaded.interactions
            blocked = loaded.blocked
            rebuildHome()
            ready = true
        }
    }

    // ---------- главная ----------
    private fun keyOf(number: String): String = byNumber[Numbers.key(number)]?.key ?: "n:" + Numbers.key(number)

    private fun rebuildHome() {
        class Acc(var last: Long = 0L, var lastMissed: Long = 0L, var lastOther: Long = 0L, var number: String? = null)
        val acc = LinkedHashMap<String, Acc>()
        calls.forEach { r ->
            if (r.type == android.provider.CallLog.Calls.BLOCKED_TYPE || r.type == android.provider.CallLog.Calls.VOICEMAIL_TYPE) return@forEach
            val a = acc.getOrPut(keyOf(r.number)) { Acc() }
            if (a.number == null) a.number = r.number
            a.last = maxOf(a.last, r.date)
            if (r.missed) a.lastMissed = maxOf(a.lastMissed, r.date) else a.lastOther = maxOf(a.lastOther, r.date)
        }
        interactions.forEach { i ->
            val a = acc.getOrPut(i.key) { Acc() }
            a.last = maxOf(a.last, i.at)
            a.lastOther = maxOf(a.lastOther, i.at)
        }
        val personByKey = people.associateBy { it.key }
        val entries = acc.mapNotNull { (key, a) ->
            if (prefs.hiddenAt(key) >= a.last) return@mapNotNull null
            val person = personByKey[key]
            if (person == null && !key.startsWith("n:")) return@mapNotNull null
            val missed = if (a.lastMissed > a.lastOther) Dates.missed(a.lastMissed) else null
            HomeEntry(key, person, if (person == null) a.number else null, a.last, missed)
        }.sortedBy { it.lastAt }.takeLast(HOME_SIZE)
        // обновляем список на месте, чтобы строки переезжали, а не мигали
        if (entries.map { it.key } != home.map { it.key } || entries != home.toList()) {
            home.clear()
            home.addAll(entries)
        }
    }

    // ---------- навигация ----------
    fun person(id: Long): Person? = byId[id]

    fun openContacts() { closeKeypad(); screen = Screen.CONTACTS }
    fun openBlocked() { screen = Screen.BLOCKED }
    fun goHome() { screen = Screen.HOME }

    fun openPerson(id: Long) {
        fromSearch = keypadOpen
        from = if (screen == Screen.PERSON) Screen.HOME else screen
        menu = null; sheet = null
        keypadOpen = false
        personId = id
        screen = Screen.PERSON
    }

    val canGoBack: Boolean
        get() = confirm != null || sheet != null || menu != null || keypadOpen || screen != Screen.HOME

    fun back(): Boolean = when {
        confirm != null -> { confirm = null; true }
        sheet != null -> { sheet = null; true }
        menu != null -> { menu = null; true }
        keypadOpen -> { closeKeypad(); true }
        screen == Screen.PERSON -> { leavePerson(); true }
        screen == Screen.BLOCKED -> { screen = Screen.CONTACTS; true }
        screen == Screen.CONTACTS -> { screen = Screen.HOME; true }
        else -> false
    }

    fun leavePerson() {
        screen = from
        if (fromSearch) keypadOpen = true
    }

    // ---------- клавиатура = поиск ----------
    fun openKeypad(prefill: String = "") { keypad = prefill.filter { it.isDigit() || it in "+*#" }.take(18); keypadOpen = true }
    fun closeKeypad() { keypadOpen = false; keypad = "" }
    fun type(ch: Char) { if (keypad.length < 18) keypad += ch }
    fun backspace() { keypad = keypad.dropLast(1) }
    fun clearKeypad() { keypad = "" }

    fun searchResults(): List<SearchHit> {
        val kpd = PhoneFormat.digits(keypad)
        if (kpd.isEmpty()) return emptyList()
        val alt = if (kpd.startsWith("8")) "7" + kpd.drop(1) else null
        return people.mapNotNull { p ->
            var nameRange: IntRange? = null
            var rank = 5
            var pos = 0
            for ((w, word) in p.name.split(" ").withIndex()) {
                if (kpd.length <= word.length && T9.encode(word.take(kpd.length)) == kpd) {
                    nameRange = pos until pos + kpd.length
                    rank = w
                    break
                }
                pos += word.length + 1
            }
            var phone = p.phones.firstOrNull()?.number?.let { PhoneFormat.pretty(it) } ?: ""
            var phoneRange: IntRange? = null
            for (ph in p.phones) {
                val shown = PhoneFormat.pretty(ph.number)
                val pd = PhoneFormat.digits(shown)
                var pi = pd.indexOf(kpd)
                var plen = kpd.length
                if (pi < 0 && alt != null) { pi = pd.indexOf(alt); plen = alt.length }
                if (pi >= 0) {
                    phone = shown
                    phoneRange = PhoneFormat.digitPos(shown, pi)..PhoneFormat.digitPos(shown, pi + plen - 1)
                    break
                }
            }
            if (nameRange != null || phoneRange != null) SearchHit(p, phone, nameRange, phoneRange, rank) else null
        }.sortedWith(compareBy<SearchHit> { it.rank }.thenBy { it.person.name }).take(4)
    }

    fun callTyped() {
        if (PhoneFormat.digits(keypad).isEmpty()) return
        val number = keypad
        closeKeypad()
        Launcher.call(ctx, number)?.let { say(it) }
    }

    fun addTypedToContacts() { Launcher.addContact(ctx, keypad)?.let { say(it) } }

    // ---------- действия ----------
    @Suppress("UNUSED_EXPRESSION")
    fun defaultOf(p: Person): Method? {
        defaultsVersion // читаем, чтобы Compose перерисовал после смены способа
        val saved = prefs.defaultFor(p.lookupKey)
        if (saved == Prefs.ASK) return null
        saved?.let { s -> runCatching { Method.valueOf(s) }.getOrNull()?.takeIf { it in p.methods }?.let { return it } }
        return if (Method.PHONE in p.methods) Method.PHONE else p.methods.firstOrNull()
    }

    fun isAsk(p: Person): Boolean { defaultsVersion; return prefs.defaultFor(p.lookupKey) == Prefs.ASK }

    /** Выполнить способ связи. Без способа по умолчанию показываем выбор. */
    fun perform(p: Person, method: Method?) {
        if (method == null) { menu = null; sheet = SheetState(SheetMode.OTHER, p.id); return }
        sheet = null; menu = null
        closeKeypad()
        val error = Launcher.perform(ctx, p, method)
        if (error != null) { say(error); return }
        prefs.log(p.key, method)
        prefs.unhide(p.key)
    }

    /** Тап по строке главной. */
    fun tap(e: HomeEntry) {
        val p = e.person
        if (p != null) { perform(p, defaultOf(p)); return }
        e.number?.let { n -> closeKeypad(); Launcher.call(ctx, n)?.let { say(it) } }
    }

    fun openMenu(e: HomeEntry, ctx: MenuContext) { menu = MenuState(e, ctx) }
    fun openMenu(p: Person) { menu = MenuState(HomeEntry(p.key, p, null, 0L, null), MenuContext.SEARCH) }
    fun closeMenu() { menu = null }
    fun openSheet(p: Person, mode: SheetMode) { menu = null; sheet = SheetState(mode, p.id) }
    fun closeSheet() { sheet = null }

    fun pickInSheet(m: Method) {
        val s = sheet ?: return
        val p = person(s.personId) ?: return
        if (s.mode == SheetMode.DEFAULT) {
            prefs.setDefault(p.lookupKey, m)
            defaultsVersion++
            sheet = null
            say("Теперь по нажатию: ${m.title}")
        } else {
            perform(p, m)
        }
    }

    fun sheetFooter() {
        val s = sheet ?: return
        val p = person(s.personId) ?: return
        if (s.mode == SheetMode.DEFAULT) {
            prefs.setDefault(p.lookupKey, null)
            defaultsVersion++
            sheet = null
            say("Теперь будем спрашивать каждый раз")
        } else {
            sheet = s.copy(mode = SheetMode.DEFAULT)
        }
    }

    fun toggleFavorite(p: Person) {
        val next = !p.starred
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { ContactsSource.setStarred(ctx, p, next) }
            if (ok) {
                people = people.map { if (it.id == p.id) it.copy(starred = next) else it }
                byId = people.associateBy { it.id }
                say(if (next) "Добавлено в избранное" else "Убрано из избранного")
            } else {
                say("Не получилось изменить избранное")
            }
        }
    }

    fun history(p: Person): List<HistoryEntry> {
        val keys = p.phones.map { Numbers.key(it.number) }.toSet()
        val events = ArrayList<Triple<Long, String, Boolean>>()
        calls.filter { Numbers.key(it.number) in keys }.take(12).forEach { events += Triple(it.date, it.text, it.missed) }
        interactions.filter { it.key == p.key }.takeLast(12).forEach { events += Triple(it.at, it.method.title, false) }
        return events.sortedByDescending { it.first }.take(6).map { HistoryEntry(Dates.day(it.first), it.second, it.third) }
    }

    fun smsTo(number: String) { Launcher.sms(ctx, number)?.let { say(it) } }

    fun editContact(p: Person) { Launcher.editContact(ctx, p)?.let { say(it) } }
    fun addToContacts(number: String) { menu = null; Launcher.addContact(ctx, number)?.let { say(it) } }

    // ---------- быстрая уборка ----------
    fun removeFromHome(key: String) {
        val at = home.indexOfFirst { it.key == key }
        if (at < 0) return
        val entry = home[at]
        menu = null
        leaving = key
        viewModelScope.launch {
            delay(Motion.FADE.toLong())
            home.removeAt(at)
            leaving = null
            prefs.hide(listOf(key))
            removed = listOf(at to entry)
            tidy = true
            say("${entry.title} ${if (entry.person == null) "убран" else removedWord(entry.title)} с главной", undo = true)
        }
    }

    private fun removedWord(name: String): String = if (name.trim().split(" ").first().last() in "ая") "убрана" else "убран"

    fun undo() {
        removed.sortedBy { it.first }.forEach { (at, e) ->
            prefs.unhide(e.key)
            home.add(minOf(at, home.size), e)
        }
        removed = emptyList()
        tidy = false
        toastJob?.cancel()
        toast = null
    }

    fun askClearHome() {
        confirm = ConfirmState(
            "Очистить главную?",
            "Имена уйдут с главной, контакты останутся. Люди вернутся сюда, когда вы с ними свяжетесь.",
            "Очистить",
        ) {
            prefs.hide(home.map { it.key })
            home.clear(); tidy = false; toast = null; removed = emptyList()
        }
    }

    fun cancelConfirm() { confirm = null }
    fun runConfirm() { val c = confirm ?: return; confirm = null; c.run() }

    // ---------- чёрный список ----------
    fun askBlock(number: String) {
        menu = null
        if (!isDefaultDialer) { say("Блокировать номера может только звонилка по умолчанию"); return }
        val shown = PhoneFormat.pretty(number)
        confirm = ConfirmState(
            "Заблокировать $shown?",
            "Звонки и SMS с этого номера больше не будут доходить. Разблокировать можно во «Всех контактах».",
            "Заблокировать",
        ) {
            viewModelScope.launch {
                val ok = withContext(Dispatchers.IO) { Blocklist.block(ctx, number) }
                say(if (ok) "Номер $shown заблокирован" else "Не получилось заблокировать")
                if (ok) { prefs.hide(listOf("n:" + Numbers.key(number))); reload() }
            }
        }
    }

    fun unblock(number: String) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { Blocklist.unblock(ctx, number) }
            if (ok) blocked = blocked - number
            say(if (ok) "Номер ${PhoneFormat.pretty(number)} разблокирован" else "Не получилось разблокировать")
        }
    }

    // ---------- тост ----------
    fun say(text: String, undo: Boolean = false) {
        toastJob?.cancel()
        toast = ToastState(text, undo)
        toastJob = viewModelScope.launch {
            delay(if (undo) 4000 else 2600)
            toast = null
            if (undo) { tidy = false; removed = emptyList() }
        }
    }

    companion object {
        const val HOME_SIZE = 6
    }
}
