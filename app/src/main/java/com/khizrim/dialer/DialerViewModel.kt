package com.khizrim.dialer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khizrim.dialer.data.People
import com.khizrim.dialer.data.PhoneFormat
import com.khizrim.dialer.data.T9
import com.khizrim.dialer.model.CallKind
import com.khizrim.dialer.model.CallPhase
import com.khizrim.dialer.model.CallState
import com.khizrim.dialer.model.Method
import com.khizrim.dialer.model.MethodKind
import com.khizrim.dialer.model.Person
import com.khizrim.dialer.ui.theme.Motion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

enum class Screen { HOME, CONTACTS, PERSON }
enum class SheetMode { OTHER, DEFAULT }
enum class MenuContext { HOME, SEARCH }

data class SheetState(val mode: SheetMode, val personId: Int)
data class MenuState(val personId: Int, val context: MenuContext)
data class ConfirmState(val title: String, val description: String, val verb: String, val run: () -> Unit)
data class ToastState(val text: String, val undo: Boolean = false, val id: Long = System.nanoTime())
data class Removed(val personId: Int, val at: Int)

/** Совпадение в поиске: какие символы имени и номера подсветить. */
data class SearchHit(val person: Person, val nameRange: IntRange?, val phoneRange: IntRange?, val rank: Int)

/** Всё состояние прототипа, в памяти, без настоящей телефонии. */
class DialerViewModel : ViewModel() {
    var region by mutableStateOf(if (Locale.getDefault().country == "US") "US" else "RU")

    var screen by mutableStateOf(Screen.HOME); private set
    var personId by mutableStateOf<Int?>(null); private set
    private var from = Screen.HOME
    private var fromSearch = false

    var keypadOpen by mutableStateOf(false); private set
    var keypad by mutableStateOf(""); private set

    val home = mutableStateListOf<Int>().apply { addAll(People.home) }
    private val missed = mutableStateListOf<Int>().apply { addAll(People.missed) }
    private val defaults = mutableStateMapOf<Int, Method?>()
    private val favorites = mutableStateMapOf<Int, Boolean>()

    var tidy by mutableStateOf(false); private set
    var leaving by mutableStateOf<Int?>(null); private set
    private var removed: Removed? = null

    var sheet by mutableStateOf<SheetState?>(null); private set
    var menu by mutableStateOf<MenuState?>(null); private set
    var confirm by mutableStateOf<ConfirmState?>(null); private set
    var toast by mutableStateOf<ToastState?>(null); private set
    var demoOpen by mutableStateOf(false); private set
    var call by mutableStateOf<CallState?>(null); private set

    private var toastJob: Job? = null
    private var timerJob: Job? = null
    private var endJob: Job? = null

    fun person(id: Int): Person = People.all.first { it.id == id }
    fun defaultOf(p: Person): Method? = if (defaults.containsKey(p.id)) defaults[p.id] else p.defaultMethod
    fun isFavorite(p: Person): Boolean = favorites[p.id] ?: p.favorite
    fun missedNote(p: Person): String? = if (p.id in missed) p.missedNote ?: "Пропущенный" else null
    fun hasMissed(p: Person): Boolean = p.id in missed

    // ---------- navigation ----------
    fun openContacts() { closeKeypad(); screen = Screen.CONTACTS }
    fun goHome() { screen = Screen.HOME }

    fun openPerson(id: Int) {
        fromSearch = keypadOpen
        from = if (screen == Screen.PERSON) Screen.HOME else screen
        menu = null; sheet = null
        keypadOpen = false
        personId = id
        screen = Screen.PERSON
    }

    val canGoBack: Boolean
        get() = call != null || confirm != null || sheet != null || menu != null || demoOpen || keypadOpen || screen != Screen.HOME

    /** Системная кнопка «назад»: закрываем верхний слой. Во время звонка ничего не делаем. */
    fun back(): Boolean = when {
        call != null -> true
        confirm != null -> { confirm = null; true }
        sheet != null -> { sheet = null; true }
        menu != null -> { menu = null; true }
        demoOpen -> { demoOpen = false; true }
        keypadOpen -> { closeKeypad(); true }
        screen == Screen.PERSON -> { leavePerson(); true }
        screen == Screen.CONTACTS -> { screen = Screen.HOME; true }
        else -> false
    }

    fun leavePerson() {
        screen = from
        if (fromSearch) keypadOpen = true
    }

    // ---------- keypad = search ----------
    fun openKeypad() { keypadOpen = true }
    fun closeKeypad() { keypadOpen = false; keypad = "" }
    fun type(ch: Char) { if (keypad.length < 18) keypad += ch }
    fun backspace() { keypad = keypad.dropLast(1) }
    fun clearKeypad() { keypad = "" }

    fun searchResults(): List<SearchHit> {
        val kpd = PhoneFormat.digits(keypad)
        if (kpd.isEmpty()) return home.map { SearchHit(person(it), null, null, 0) }
        val alt = if (kpd.startsWith("8")) "7" + kpd.drop(1) else null
        return People.all.mapNotNull { p ->
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
            val pd = PhoneFormat.digits(p.phone)
            var pi = pd.indexOf(kpd)
            var plen = kpd.length
            if (pi < 0 && alt != null) { pi = pd.indexOf(alt); plen = alt.length }
            val phoneRange = if (pi >= 0) PhoneFormat.digitPos(p.phone, pi)..PhoneFormat.digitPos(p.phone, pi + plen - 1) else null
            if (nameRange != null || phoneRange != null) SearchHit(p, nameRange, phoneRange, rank) else null
        }.sortedBy { it.rank }.take(4)
    }

    fun callTyped() {
        val kpd = PhoneFormat.digits(keypad)
        if (kpd.isEmpty()) return
        val num = PhoneFormat.formatTyping(keypad, region)
        keypadOpen = false
        keypad = ""
        startCall(CallState(kind = CallKind.OUTGOING, number = num, phase = CallPhase.ACTIVE))
    }

    // ---------- actions ----------
    /** Выполнить способ связи. Без способа по умолчанию показываем выбор. */
    fun perform(p: Person, method: Method?) {
        if (method == null) { menu = null; sheet = SheetState(SheetMode.OTHER, p.id); return }
        sheet = null; menu = null
        keypadOpen = false; keypad = ""
        if (method.kind == MethodKind.CALL) {
            startCall(CallState(kind = CallKind.OUTGOING, personId = p.id, network = method.network, phase = CallPhase.ACTIVE))
        } else {
            touch(p.id)
            say("${method.title}: ${p.name} (демо)")
        }
    }

    /** Человек, с которым связались, переезжает вниз стопки, к большому пальцу. */
    private fun touch(id: Int) {
        home.remove(id)
        home.add(id)
        while (home.size > 6) home.removeAt(0)
        missed.remove(id)
    }

    fun openMenu(p: Person, ctx: MenuContext) { menu = MenuState(p.id, ctx) }
    fun closeMenu() { menu = null }
    fun openSheet(p: Person, mode: SheetMode) { menu = null; sheet = SheetState(mode, p.id) }
    fun closeSheet() { sheet = null }

    fun pickInSheet(m: Method) {
        val s = sheet ?: return
        val p = person(s.personId)
        if (s.mode == SheetMode.DEFAULT) {
            defaults[p.id] = m
            sheet = null
            say("Теперь по нажатию: ${m.title}")
        } else {
            perform(p, m)
        }
    }

    fun sheetFooter() {
        val s = sheet ?: return
        if (s.mode == SheetMode.DEFAULT) {
            defaults[s.personId] = null
            sheet = null
            say("Теперь будем спрашивать каждый раз")
        } else {
            sheet = s.copy(mode = SheetMode.DEFAULT)
        }
    }

    fun toggleFavorite(p: Person) {
        val fav = isFavorite(p)
        favorites[p.id] = !fav
        say(if (fav) "Убрано из избранного" else "Добавлено в избранное")
    }

    // ---------- quick cleanup ----------
    fun removeFromHome(id: Int) {
        val at = home.indexOf(id)
        if (at < 0) return
        menu = null
        leaving = id
        viewModelScope.launch {
            delay(Motion.FADE.toLong())
            home.remove(id)
            leaving = null
            removed = Removed(id, at)
            tidy = true
            val p = person(id)
            val suffix = if (p.name.last() in "ая") "а" else ""
            say("${p.name} убран$suffix с главной", undo = true)
        }
    }

    fun undo() {
        val r = removed ?: return
        home.add(minOf(r.at, home.size), r.personId)
        removed = null
        tidy = false
        toastJob?.cancel()
        toast = null
    }

    fun askClearHome() {
        confirm = ConfirmState(
            "Очистить главную?",
            "Имена уйдут с главной, контакты останутся. Люди вернутся сюда, когда вы с ними свяжетесь.",
            "Очистить",
        ) { home.clear(); tidy = false; toast = null; removed = null }
    }

    fun cancelConfirm() { confirm = null }
    fun runConfirm() { val c = confirm ?: return; confirm = null; c.run() }

    // ---------- toast ----------
    fun say(text: String, undo: Boolean = false) {
        toastJob?.cancel()
        toast = ToastState(text, undo)
        toastJob = viewModelScope.launch {
            delay(if (undo) 4000 else 2600)
            toast = null
            if (undo) { tidy = false; removed = null }
        }
    }

    // ---------- calls ----------
    fun toggleDemo() { demoOpen = !demoOpen }

    fun demoIncoming(kind: CallKind) {
        demoOpen = false
        call = when (kind) {
            CallKind.INCOMING -> CallState(kind, personId = 1, phase = CallPhase.RINGING)
            CallKind.UNKNOWN -> CallState(kind, number = "+7 (495) 123-45-67", callerLabel = "Доставка, Москва", phase = CallPhase.RINGING)
            else -> CallState(CallKind.SPAM, number = "+7 (495) 980-00-00", callerLabel = "Финансовые услуги", phase = CallPhase.RINGING)
        }
    }

    private fun startCall(c: CallState) {
        call = c
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val cur = call ?: break
                if (cur.phase != CallPhase.ACTIVE) break
                call = cur.copy(seconds = cur.seconds + 1)
            }
        }
    }

    fun answer() {
        val c = call ?: return
        startCall(c.copy(phase = CallPhase.ACTIVE, network = "Телефон", seconds = 0))
    }

    fun toggleMute() { call = call?.let { it.copy(muted = !it.muted) } }
    fun toggleSpeaker() { call = call?.let { it.copy(speaker = !it.speaker) } }
    fun toggleDtmf() { call = call?.let { it.copy(dtmf = if (it.dtmf == null) "" else null) } }
    fun dtmf(d: Char) { call = call?.let { it.copy(dtmf = (it.dtmf ?: "") + d) } }

    fun hangup() = endCall("Звонок завершён, ${timer(call?.seconds ?: 0)}")
    fun decline() = endCall("Отклонён")
    fun quickSms() = endCall("Отклонён", "Отправили SMS: «Не могу говорить, перезвоню»")
    fun remind() = endCall("Отклонён", "Напомним перезвонить через час")

    fun askBlock() {
        val num = call?.number ?: return
        confirm = ConfirmState(
            "Заблокировать $num?",
            "Звонки и SMS с этого номера больше не будут доходить. Разблокировать можно в настройках.",
            "Заблокировать",
        ) { endCall("Номер заблокирован", "Номер $num заблокирован") }
    }

    private fun endCall(text: String, toastText: String? = null) {
        val c = call ?: return
        timerJob?.cancel()
        val wasActive = c.phase == CallPhase.ACTIVE
        call = c.copy(phase = CallPhase.ENDED, endText = text)
        endJob?.cancel()
        endJob = viewModelScope.launch {
            delay(1200)
            call = null
            toastText?.let { say(it) }
            // ждём, пока экран звонка погаснет, затем человек съезжает вниз стопки
            delay(Motion.FADE.toLong())
            if (wasActive) c.personId?.let { touch(it) }
        }
    }

    companion object {
        fun timer(t: Int): String = "%02d:%02d".format(t / 60, t % 60)
    }
}
