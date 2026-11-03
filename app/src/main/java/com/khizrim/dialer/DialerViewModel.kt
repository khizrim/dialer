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
import com.khizrim.dialer.data.Declension
import com.khizrim.dialer.data.Interaction
import com.khizrim.dialer.data.Numbers
import com.khizrim.dialer.data.PhoneFormat
import com.khizrim.dialer.data.PeopleSearch
import com.khizrim.dialer.data.Prefs
import com.khizrim.dialer.data.SearchHit
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
import java.util.Locale

enum class Screen { HOME, CONTACTS, PERSON, BLOCKED, UNKNOWN, RECENTS, SETTINGS, EDIT, SEARCH }
enum class SheetMode { OTHER, DEFAULT }
enum class MenuContext { HOME, SEARCH }

data class SheetState(val mode: SheetMode, val personId: Long)
data class MenuState(val entry: HomeEntry, val context: MenuContext, val record: CallRecord? = null)
data class ConfirmState(val title: String, val description: String, val verb: String, val run: () -> Unit)
/** [action] — подпись своей кнопки в тосте (вместо «Вернуть»); её нажатие идёт в [DialerViewModel.toastTap]. */
data class ToastState(val text: String, val undo: Boolean = false, val action: String? = null, val id: Long = System.nanoTime())


private class Loaded(
    val people: List<Person>,
    val calls: List<CallRecord>,
    val interactions: List<Interaction>,
    val blocked: List<String>,
    val search: PeopleSearch = PeopleSearch.EMPTY,
)

/** Состояние приложения поверх настоящих контактов, журнала звонков и системного чёрного списка. */
class DialerViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()
    private val res get() = ctx.resources
    private fun str(id: Int, vararg args: Any): String = ctx.getString(id, *args)
    private val prefs = Prefs(app)
    val region: String = PhoneFormat.detectRegion(app)?.also { PhoneFormat.simRegion = it } ?: PhoneFormat.defaultRegion

    var ready by mutableStateOf(false); private set
    var isDefaultDialer by mutableStateOf(false); private set
    var hasContacts by mutableStateOf(false); private set
    var hasCallLog by mutableStateOf(false); private set

    /** «Не сейчас» на шаге со звонилкой по умолчанию. */
    var roleSkipped by mutableStateOf(prefs.roleSkipped)
        private set

    fun skipRole() { roleSkipped = true; prefs.roleSkipped = true }

    /** «Звонить без контактов»: пускаем в приложение без доступа к книге. */
    var permsSkipped by mutableStateOf(prefs.permsSkipped)
        private set

    fun skipPerms() { permsSkipped = true; prefs.permsSkipped = true }

    /** Уже спрашивали доступы хотя бы раз: если снова отказ, система сама не спросит. */
    var permsAsked by mutableStateOf(prefs.permsAsked)
        private set

    fun markPermsAsked() { permsAsked = true; prefs.permsAsked = true }

    val needsOnboarding: Boolean get() = ((!hasContacts || !hasCallLog) && !permsSkipped) || (!isDefaultDialer && !roleSkipped)

    var people by mutableStateOf<List<Person>>(emptyList()); private set
    private var byNumber: Map<String, Person> = emptyMap()
    private var byId: Map<Long, Person> = emptyMap()
    private var calls: List<CallRecord> = emptyList()
    private var interactions: List<Interaction> = emptyList()
    var blocked by mutableStateOf<List<String>>(emptyList()); private set

    val home = mutableStateListOf<HomeEntry>()

    /** «Не беспокоить незнакомым»: такие звонки не звонят и не попадают на главную. */
    var silenceUnknown by mutableStateOf(prefs.silenceUnknown); private set

    /** Что мешает увидеть входящий; показываем одну строку на главной. */
    var callAlert by mutableStateOf<com.khizrim.dialer.telecom.CallAlert?>(null); private set

    /** Звонки с номеров не из контактов, новые сверху. */
    var unknownCalls by mutableStateOf<List<CallRecord>>(emptyList()); private set
    private var defaultsVersion by mutableStateOf(0)

    var screen by mutableStateOf(Screen.HOME); private set
    var personId by mutableStateOf<Long?>(null); private set
    private var from = Screen.HOME
    private var fromSearch = false

    var keypadOpen by mutableStateOf(false); private set
    var keypad by mutableStateOf(""); private set
    private var search: PeopleSearch = PeopleSearch.EMPTY
    /** Найденные люди над клавиатурой; до набора — те, с кем общаетесь чаще. */
    var hits by mutableStateOf<List<SearchHit>>(emptyList()); private set

    var tidy by mutableStateOf(false); private set
    var leaving by mutableStateOf<String?>(null); private set
    private var removed: List<Pair<Int, HomeEntry>> = emptyList()

    var sheet by mutableStateOf<SheetState?>(null); private set
    /** Лист «Как включить звонки через Telegram/WhatsApp». */
    var syncHelp by mutableStateOf<String?>(null); private set
    fun openSyncHelp(app: String) { sheet = null; syncHelp = app }
    fun closeSyncHelp() { syncHelp = null }
    var menu by mutableStateOf<MenuState?>(null); private set
    var confirm by mutableStateOf<ConfirmState?>(null); private set
    var toast by mutableStateOf<ToastState?>(null); private set

    private var toastJob: Job? = null
    private var loadJob: Job? = null

    private fun granted(p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    /** Перечитываем всё при каждом возвращении в приложение. */
    fun reload() {
        offerDefault()
        isDefaultDialer = Launcher.isDefaultDialer(ctx)
        viewModelScope.launch { sims = withContext(Dispatchers.IO) { Launcher.sims(ctx) } }
        simForCalls = prefs.simForCalls
        hasContacts = granted(Manifest.permission.READ_CONTACTS)
        hasCallLog = granted(Manifest.permission.READ_CALL_LOG)
        callAlert = com.khizrim.dialer.telecom.CallReadiness.check(ctx, isDefaultDialer, prefs.xiaomiHintSeen, prefs.silenceUnknown)
        if (isDefaultDialer) com.khizrim.dialer.telecom.CallReadiness.clearMissed(ctx)
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                Loaded(
                    people = if (hasContacts) ContactsSource.load(ctx) else emptyList(),
                    calls = if (hasCallLog) CallLogSource.load(ctx) else emptyList(),
                    interactions = prefs.interactions(),
                    blocked = if (isDefaultDialer) Blocklist.list(ctx) else emptyList(),
                ).let { l -> Loaded(l.people, l.calls, l.interactions, l.blocked, buildSearch(l)) }
            }
            people = loaded.people
            byId = loaded.people.associateBy { it.id }
            byNumber = buildMap { loaded.people.forEach { p -> p.phones.forEach { put(Numbers.key(it.number), p) } } }
            calls = loaded.calls
            interactions = loaded.interactions
            blocked = loaded.blocked
            recentCalls = loaded.calls.map { it to byNumber[Numbers.key(it.number)] }
            unknownCalls = loaded.calls.filter { byNumber[Numbers.key(it.number)] == null }.take(100)
            search = loaded.search
            refreshHits()
            rebuildHome()
            ready = true
            pendingMissed?.let { pendingMissed = null; openMissed(it) }
        }
    }

    // ---------- главная ----------
    private fun keyOf(number: String): String = byNumber[Numbers.key(number)]?.key ?: "n:" + Numbers.key(number)

    /** Избранные для тихого ряда над нижней панелью главной. */
    var favNickname by mutableStateOf(prefs.favNickname); private set
    fun toggleFavNickname() { favNickname = !favNickname; prefs.favNickname = favNickname }
    /** Как подписан избранный: никнейм, если выбран и есть, иначе имя без фамилии. */
    fun favLabel(p: Person): String = p.nickname?.takeIf { favNickname && it.isNotBlank() } ?: if (p.nameIsNumber) p.shownName else p.name.substringBefore(' ')

    val favorites: List<Person> get() = people.filter { it.starred }.sortedByDescending { scores[it.key] ?: 0.0 }

    /** Незнакомые без звука в ленте свёрнуты в одну строку; нажатие раскрывает их прямо там. */
    var silentOpen by mutableStateOf(false); private set
    fun toggleSilent() { silentOpen = !silentOpen }

    /**
     * Лента главной — она же журнал: каждый человек или номер одной строкой на месте последнего контакта,
     * свежие внизу. «Пропущенные» оставляет только тех, у кого последний звонок пропущен.
     */
    val homeStack: List<HomeEntry> get() {
        val all = home.toList()
        val silent = all.filter(::isSilenced)
        val rest = all.filterNot(::isSilenced)
        val list = if (silent.isEmpty() || silentOpen) all
        else rest + HomeEntry("silent", null, null, silent.maxOf { it.lastAt }, null, silent = silent.size)
        return (if (missedOnly) list.filter { it.missedNote != null } else list).sortedBy { it.lastAt }
    }
    /** Незнакомый, чей звонок прошёл без звука (включено «Не беспокоить незнакомым»). */
    fun isSilenced(e: HomeEntry): Boolean = e.person == null && silenceUnknown && e.missedNote != null
    val homeHasMissed: Boolean get() = home.any { it.missedNote != null }

    private fun rebuildHome() {
        class Acc(var last: Long = 0L, var lastMissed: Long = 0L, var lastOther: Long = 0L, var number: String? = null, var lastCall: CallRecord? = null) {
            val missedAt = ArrayList<Long>()
        }
        val acc = LinkedHashMap<String, Acc>()
        calls.forEach { r ->
            if (r.type == android.provider.CallLog.Calls.BLOCKED_TYPE || r.type == android.provider.CallLog.Calls.VOICEMAIL_TYPE) return@forEach
            val a = acc.getOrPut(keyOf(r.number)) { Acc() }
            if (a.number == null) a.number = r.number
            if (r.date >= a.last) a.lastCall = r
            a.last = maxOf(a.last, r.date)
            if (r.missed) { a.lastMissed = maxOf(a.lastMissed, r.date); a.missedAt += r.date } else a.lastOther = maxOf(a.lastOther, r.date)
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
            val missed = if (a.lastMissed > a.lastOther) Dates.missed(res, a.lastMissed, a.missedAt.count { it > a.lastOther }) else null
            // исходящий без ответа — тоже стоит сказать; обычный звонок второй строки не получает
            val lc = a.lastCall
            val note = if (missed == null && lc != null && lc.date == a.last && lc.type == android.provider.CallLog.Calls.OUTGOING_TYPE && lc.duration <= 0) str(R.string.outgoing_no_answer) else null
            HomeEntry(key, person, if (person == null) a.number else null, a.last, missed, note)
        }.sortedBy { it.lastAt }.takeLast(FEED_SIZE).ifEmpty {
            // истории ещё нет: показываем избранных, чтобы главная не была пустой
            if (acc.isNotEmpty()) emptyList()
            else people.filter { it.starred && prefs.hiddenAt(it.key) == 0L }.take(HOME_SIZE).map { HomeEntry(it.key, it, null, 0L, null) }
        }
        // обновляем список на месте, чтобы строки переезжали, а не мигали
        if (entries.map { it.key } != home.map { it.key } || entries != home.toList()) {
            home.clear()
            home.addAll(entries)
        }
    }

    // ---------- навигация ----------
    fun person(id: Long): Person? = byId[id]

    fun openContacts() { closeKeypad(); screen = Screen.CONTACTS }
    /** Незнакомые и заблокированные открываются из настроек или из уведомления: назад — туда, откуда пришли. */
    private var listFrom = Screen.HOME
    fun openBlocked() { if (screen != Screen.BLOCKED) listFrom = originFor(screen); screen = Screen.BLOCKED }
    fun openUnknown() { if (screen != Screen.UNKNOWN) listFrom = originFor(screen); screen = Screen.UNKNOWN }
    // из «Всех контактов» по-прежнему возвращаемся в них; со всех прочих экранов — на главную или в настройки
    private fun originFor(s: Screen) = if (s == Screen.SETTINGS || s == Screen.CONTACTS) s else Screen.HOME

    /** «Найти в интернете»: открывает браузер, номер никуда больше не уходит. */
    fun searchWeb(number: String) {
        menu = null
        runCatching { ctx.startActivity(com.khizrim.dialer.callerid.CallerId.webSearchIntent(number).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    /** Журнал: открыт с главной (назад — на главную) или из контактов. */
    var recentsFromHome by mutableStateOf(false); private set
    var missedOnly by mutableStateOf(false); private set
    private var recentsFrom = Screen.HOME
    /** Журнала отдельным экраном больше нет: лента главной и есть журнал, «пропущенные» — её фильтр. */
    fun openRecents(fromHome: Boolean = false, missed: Boolean = false) {
        closeKeypad(); recentsFromHome = fromHome; missedOnly = missed; screen = Screen.HOME
    }
    fun showMissedOnly(v: Boolean) { missedOnly = v }

    val canClearLog: Boolean get() = granted(Manifest.permission.WRITE_CALL_LOG)

    /** «Очистить журнал»: все звонки или только пропущенные, если включён фильтр. С подтверждением. */
    fun askClearLog() {
        val missed = missedOnly
        confirm = ConfirmState(
            str(if (missed) R.string.clear_missed_title else R.string.clear_log_title),
            str(R.string.clear_log_text),
            str(R.string.clear),
        ) {
            viewModelScope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        if (missed) ctx.contentResolver.delete(android.provider.CallLog.Calls.CONTENT_URI, android.provider.CallLog.Calls.TYPE + "=?", arrayOf(android.provider.CallLog.Calls.MISSED_TYPE.toString()))
                        else ctx.contentResolver.delete(android.provider.CallLog.Calls.CONTENT_URI, null, null)
                        true
                    }.getOrDefault(false)
                }
                say(str(if (ok) R.string.log_cleared else R.string.log_clear_failed))
                reload()
            }
        }
    }

    /** Из уведомления о пропущенном: карточка человека или журнал пропущенных. */
    private var pendingMissed: String? = null
    fun openMissed(number: String?) {
        if (number != null && !ready) { pendingMissed = number; openRecents(missed = true); return }
        val p = number?.let { byNumber[Numbers.key(it)] }
        if (p != null) openPerson(p.id) else openRecents(missed = true)
    }

    fun callNumber(number: String) { closeKeypad(); Launcher.call(ctx, number)?.let { say(it) } }
    private var settingsFrom = Screen.HOME
    fun openSettings() {
        closeKeypad(); simForCalls = prefs.simForCalls
        viewModelScope.launch { sims = withContext(Dispatchers.IO) { Launcher.sims(ctx) } }
        if (screen != Screen.SETTINGS) settingsFrom = screen
        screen = Screen.SETTINGS
    }
    fun leaveSettings() { screen = settingsFrom }

    /** SIM-карты для настройки «SIM для звонков». */
    var sims by mutableStateOf<List<Pair<String, String>>>(emptyList()); private set
    var simForCalls by mutableStateOf<String?>(null); private set
    fun chooseSimForCalls(id: String?) { prefs.simForCalls = id; simForCalls = id }
    /** Номер основной SIM в списке: выбранная для звонков, иначе первая. Подписываем только звонки не с неё. */
    val mainSimIndex: Int get() = sims.indexOfFirst { it.first == simForCalls }.coerceAtLeast(0)
    val hasMissed: Boolean get() = recentCalls.any { it.first.missed }

    /** Полный журнал: каждая строка — звонок, с именем, если номер есть в контактах. */
    var recentCalls by mutableStateOf<List<Pair<CallRecord, Person?>>>(emptyList()); private set

    /** Тап в журнале: человеку — его обычным способом, незнакомому — звонок. */
    fun callBack(r: CallRecord, p: Person?) {
        if (p != null) { perform(p, defaultOf(p)); return }
        closeKeypad(); Launcher.call(ctx, r.number)?.let { say(it) }
    }

    fun openRecentMenu(r: CallRecord, p: Person?) {
        menu = MenuState(if (p != null) HomeEntry(p.key, p, null, r.date, null) else HomeEntry("n:" + Numbers.key(r.number), null, r.number, r.date, null), MenuContext.SEARCH, r)
    }

    /** Смахнутый звонок: строка уже скрыта, из системного журнала удалим, когда пройдёт «Вернуть». */
    private var pendingRecord: CallRecord? = null
    private var pendingCalls: List<Pair<CallRecord, Person?>>? = null

    fun swipeDeleteRecord(r: CallRecord) {
        commitPendingRecord()
        pendingCalls = recentCalls
        pendingRecord = r
        recentCalls = recentCalls.filterNot { it.first == r }
        say(str(R.string.call_deleted), undo = true)
    }

    private fun commitPendingRecord() {
        val r = pendingRecord ?: return
        val before = pendingCalls
        pendingRecord = null; pendingCalls = null
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.delete(
                        android.provider.CallLog.Calls.CONTENT_URI,
                        android.provider.CallLog.Calls.DATE + "=? AND " + android.provider.CallLog.Calls.NUMBER + "=?",
                        arrayOf(r.date.toString(), r.number),
                    ) > 0
                }.getOrDefault(false)
            }
            if (!ok && before != null) { recentCalls = before; say(str(R.string.log_delete_failed)) }
        }
    }

    /** Убрать одну строку журнала — быстрая уборка без «очистить всё». */
    fun deleteRecord(r: CallRecord) {
        menu = null
        val before = recentCalls
        recentCalls = recentCalls.filterNot { it.first == r }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.delete(
                        android.provider.CallLog.Calls.CONTENT_URI,
                        android.provider.CallLog.Calls.DATE + "=? AND " + android.provider.CallLog.Calls.NUMBER + "=?",
                        arrayOf(r.date.toString(), r.number),
                    ) > 0
                }.getOrDefault(false)
            }
            if (!ok) { recentCalls = before; say(str(R.string.log_delete_failed)) }
        }
    }

    fun toggleSilenceUnknown() {
        silenceUnknown = !silenceUnknown
        prefs.silenceUnknown = silenceUnknown
        rebuildHome()
    }

    fun callUnknown(number: String) { Launcher.call(ctx, number)?.let { say(it) } }
    fun openUnknownMenu(number: String) { menu = MenuState(HomeEntry("n:" + Numbers.key(number), null, number, 0L, null), MenuContext.SEARCH) }

    /** «Как связаться с Мамой» по-русски, иначе нейтрально. */
    fun reachTitle(p: Person): String {
        if (Locale.getDefault().language == "ru") {
            Declension.instrumental(p.name)?.let { return str(R.string.how_to_reach_named, Declension.withPreposition(it)) }
        }
        return str(R.string.how_to_reach)
    }
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
        get() = confirm != null || syncHelp != null || sheet != null || menu != null || keypadOpen || screen != Screen.HOME

    fun back(): Boolean = when {
        confirm != null -> { confirm = null; true }
        screen == Screen.EDIT -> { leaveEdit(); true }
        screen == Screen.SEARCH -> { closeTextSearch(); true }
        syncHelp != null -> { syncHelp = null; true }
        sheet != null -> { sheet = null; true }
        menu != null -> { menu = null; true }
        keypadOpen -> { closeKeypad(); true }
        screen == Screen.PERSON -> { leavePerson(); true }
        screen == Screen.RECENTS && recentsFromHome -> { screen = Screen.HOME; true }
        screen == Screen.SETTINGS -> { leaveSettings(); true }
        screen == Screen.BLOCKED || screen == Screen.UNKNOWN -> { screen = listFrom; true }
        // журнал из уведомления о пропущенном: назад — на главную, а не на экран, куда иначе не попасть
        screen == Screen.RECENTS -> { screen = recentsFrom; true }
        screen == Screen.CONTACTS -> { screen = Screen.HOME; true }
        else -> false
    }

    fun leavePerson() {
        screen = from
        if (fromSearch) keypadOpen = true
    }

    // ---------- клавиатура = поиск ----------
    fun openKeypad(prefill: String = "") { keypad = cleanNumber(prefill); keypadOpen = true; refreshHits() }

    /** Цифры и служебные символы; запятая и точка с запятой — паузы для добавочного. */
    private fun cleanNumber(s: String): String = s.filter { it.isDigit() || it in "+*#,;" }.take(32)

    /** Вставить номер из буфера обмена. */
    fun paste(text: String?) {
        val n = cleanNumber(text.orEmpty())
        if (n.isEmpty()) return
        keypad = n; refreshHits()
    }

    fun callVoicemail() { closeKeypad(); Launcher.voicemail(ctx)?.let { say(it) } }
    fun closeKeypad() { keypadOpen = false; keypad = "" }
    fun type(ch: Char) { if (keypad.length < 32) { keypad += ch; refreshHits() } }
    fun backspace() { keypad = keypad.dropLast(1); refreshHits() }
    fun clearKeypad() { keypad = ""; refreshHits() }

    private fun refreshHits() {
        hits = if (PhoneFormat.digits(keypad).isEmpty()) search.suggestions() else search.find(keypad)
    }

    /** Насколько часто и недавно вы общались: звонки и действия за 90 дней, избранные выше. */
    private fun buildSearch(l: Loaded): PeopleSearch {
        val now = System.currentTimeMillis()
        val window = 90L * 24 * 3600 * 1000
        val byNum = buildMap { l.people.forEach { p -> p.phones.forEach { put(Numbers.key(it.number), p.key) } } }
        val score = HashMap<String, Double>()
        fun add(key: String?, at: Long) {
            if (key == null) return
            val age = now - at
            if (age < 0 || age > window) return
            score[key] = (score[key] ?: 0.0) + 1.0 - age.toDouble() / window * 0.7
        }
        l.calls.forEach { add(byNum[Numbers.key(it.number)], it.date) }
        l.interactions.forEach { add(it.key, it.at) }
        scores = l.people.associate { p -> p.key to (score[p.key] ?: 0.0) + if (p.starred) 3.0 else 0.0 }
        return PeopleSearch.build(l.people, { p -> scores[p.key] ?: 0.0 })
    }

    // ---------- поиск по имени (стопку потянули вверх) ----------
    @Volatile private var scores: Map<String, Double> = emptyMap()
    var textQuery by mutableStateOf(""); private set
    var textHits by mutableStateOf<List<com.khizrim.dialer.data.TextHit>>(emptyList()); private set
    private var searchFrom = Screen.HOME
    /** Подсказка «Потяните стопку вверх» — первые 5 запусков, пока поиском не воспользовались. */
    fun countLaunch() { prefs.searchHintShows = prefs.searchHintShows + 1 }

    fun openTextSearch() {
        closeKeypad(); menu = null
        if (!prefs.searchUsed) prefs.searchUsed = true
        searchFrom = if (screen == Screen.SEARCH) Screen.HOME else screen
        textQuery = ""; textHits = emptyList(); screen = Screen.SEARCH
    }
    fun closeTextSearch() { textQuery = ""; screen = searchFrom }
    fun typeText(q: String) {
        textQuery = q
        textHits = if (q.isBlank()) emptyList() else com.khizrim.dialer.data.TextSearch.find(people, q, { scores[it.key] ?: 0.0 })
    }
    fun tapHit(p: Person) { closeTextSearch(); perform(p, defaultOf(p)) }

    fun callTyped() {
        if (PhoneFormat.digits(keypad).isEmpty()) {
            // пустое поле: кнопка вызова подставляет последний набранный номер, второй тап звонит
            calls.firstOrNull { it.type == android.provider.CallLog.Calls.OUTGOING_TYPE }?.let { keypad = cleanNumber(it.number); refreshHits() }
            return
        }
        val number = keypad
        closeKeypad()
        Launcher.call(ctx, number)?.let { say(it) }
    }

    fun addTypedToContacts() { addToContacts(keypad) }

    // ---------- действия ----------
    @Suppress("UNUSED_EXPRESSION")
    fun defaultOf(p: Person): Method? {
        defaultsVersion // читаем, чтобы Compose перерисовал после смены способа
        val saved = prefs.defaultFor(p.lookupKey)
        if (saved == Prefs.ASK) return null
        saved?.let { s -> runCatching { Method.valueOf(s) }.getOrNull()?.takeIf { it in p.methods }?.let { return it } }
        return if (Method.PHONE in p.methods) Method.PHONE else p.methods.firstOrNull()
    }

    /** Номер для звонка и SMS: выбранный в «Другом способе», если он ещё есть в контакте, иначе основной. */
    fun numberFor(p: Person): String? {
        defaultsVersion
        val saved = prefs.numberFor(p.lookupKey)?.let { s -> p.phones.firstOrNull { Numbers.key(it.number) == Numbers.key(s) } }
        return saved?.number ?: p.mainNumber
    }

    fun isAsk(p: Person): Boolean { defaultsVersion; return prefs.defaultFor(p.lookupKey) == Prefs.ASK }

    /** Выполнить способ связи. Без способа по умолчанию показываем выбор. */
    fun perform(p: Person, method: Method?, number: String? = null) {
        if (method == null) { menu = null; sheet = SheetState(SheetMode.OTHER, p.id); return }
        sheet = null; menu = null
        closeKeypad()
        val error = Launcher.perform(ctx, p, method, number ?: numberFor(p))
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

    /** Тап по номеру в карточке: звонок именно на этот номер. */
    fun callNumberOf(p: Person, number: String) = perform(p, Method.PHONE, number)

    fun copyNumber(number: String) {
        val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
        cm?.setPrimaryClip(android.content.ClipData.newPlainText(number, number))
        // на Android 13+ система сама показывает, что скопировано
        if (android.os.Build.VERSION.SDK_INT < 33) say(str(R.string.number_copied))
    }

    fun pickInSheet(m: Method, number: String? = null) {
        val s = sheet ?: return
        val p = person(s.personId) ?: return
        if (s.mode == SheetMode.DEFAULT) {
            prefs.setDefault(p.lookupKey, m)
            if (number != null) prefs.setNumber(p.lookupKey, number)
            defaultsVersion++
            sheet = null
            say(str(R.string.now_on_tap, m.label(ctx)))
        } else {
            performOther(p, m, number)
        }
    }

    /** Связаться не основным способом; когда человек вернётся в приложение, тихо предложим сделать способ основным. */
    fun performOther(p: Person, m: Method, number: String? = null) {
        val before = defaultOf(p)
        perform(p, m, number)
        if (m != before) suggestDefault = p.id to m
    }

    private var suggestDefault: Pair<Long, Method>? = null
    private var toastAction: (() -> Unit)? = null

    private fun offerDefault() {
        val (id, m) = suggestDefault ?: return
        suggestDefault = null
        val p = person(id) ?: return
        if (defaultOf(p) == m) return
        say(m.label(ctx), action = str(R.string.make_main)) {
            prefs.setDefault(p.lookupKey, m)
            defaultsVersion++
            say(str(R.string.now_on_tap, m.label(ctx)))
        }
    }

    /** Кнопка в тосте: «Вернуть» или своё действие. */
    fun toastTap() {
        val a = toastAction
        if (toast?.undo == true || a == null) { undo(); return }
        toastAction = null; toastJob?.cancel(); toast = null
        a()
    }

    fun sheetFooter() {
        val s = sheet ?: return
        val p = person(s.personId) ?: return
        if (s.mode == SheetMode.DEFAULT) {
            prefs.setDefault(p.lookupKey, null)
            defaultsVersion++
            sheet = null
            say(str(R.string.now_ask))
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
                rebuildHome()
                byId = people.associateBy { it.id }
                say(str(if (next) R.string.added_favorite else R.string.removed_favorite))
            } else {
                say(str(R.string.favorite_failed))
            }
        }
    }

    fun history(p: Person): List<HistoryEntry> {
        val keys = p.phones.map { Numbers.key(it.number) }.toSet()
        val events = ArrayList<Triple<Long, String, Boolean>>()
        // у обычного разговора только время, подпись — у пропущенного и несостоявшегося
        val tf = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
        calls.filter { Numbers.key(it.number) in keys }.take(300).forEach { events += Triple(it.date, it.note(res) ?: tf.format(java.util.Date(it.date)), it.missed) }
        interactions.filter { it.key == p.key }.takeLast(300).forEach { events += Triple(it.at, it.method.label(ctx), false) }
        // старые сверху, свежие у имени; карточка открывается у свежих
        return events.sortedBy { it.first }.map { HistoryEntry(Dates.day(res, it.first), it.second, it.third) }
    }

    fun smsTo(number: String) { Launcher.sms(ctx, number)?.let { say(it) } }

    // ---------- свой редактор контакта: имя и номера ----------
    var editDraft by mutableStateOf<com.khizrim.dialer.data.EditDraft?>(null); private set
    private var editBefore: com.khizrim.dialer.data.EditDraft? = null
    private var editReturn = Screen.HOME
    private var editPerson: Person? = null

    /** Изменение и добавление — системным редактором Контактов (решение пользователя). */
    fun editContact(p: Person) { menu = null; sheet = null; Launcher.editContact(ctx, p)?.let { say(it) } }
    fun addToContacts(number: String) { menu = null; sheet = null; closeKeypad(); Launcher.addContact(ctx, number)?.let { say(it) } }

    private fun editContactInApp(p: Person) {
        menu = null; sheet = null
        editPerson = p
        viewModelScope.launch {
            val d = withContext(Dispatchers.IO) { com.khizrim.dialer.data.ContactEditor.load(ctx, p.id, p.name) }
            editBefore = d; editDraft = d; editReturn = screen; screen = Screen.EDIT
        }
    }

    private fun addToContactsInApp(number: String) {
        menu = null; sheet = null; closeKeypad()
        editPerson = null
        val d = com.khizrim.dialer.data.ContactEditor.blank(number)
        editBefore = d; editDraft = d; editReturn = if (screen == Screen.EDIT) Screen.HOME else screen; screen = Screen.EDIT
    }

    fun updateDraft(d: com.khizrim.dialer.data.EditDraft) { editDraft = d }

    fun cancelEdit() { editDraft = null; screen = editReturn }

    /** Назад из редактора: если что-то поменяли, спрашиваем, чтобы не потерять. */
    fun leaveEdit() {
        val d = editDraft ?: return cancelEdit()
        if (d == editBefore) return cancelEdit()
        confirm = ConfirmState(str(R.string.edit_discard_title), str(R.string.edit_discard_text), str(R.string.edit_discard)) { cancelEdit() }
    }

    fun askDeleteContact() {
        val p = editPerson ?: return
        confirm = ConfirmState(str(R.string.edit_delete_title, p.name), str(R.string.edit_delete_text), str(R.string.delete)) {
            viewModelScope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching { ctx.contentResolver.delete(android.provider.ContactsContract.Contacts.getLookupUri(p.id, p.lookupKey), null, null) > 0 }.getOrDefault(false)
                }
                if (!ok) { say(str(R.string.edit_failed)); return@launch }
                editDraft = null
                screen = if (editReturn == Screen.PERSON) from else editReturn
                say(str(R.string.edit_deleted, p.name))
                reload()
            }
        }
    }

    fun saveEdit() {
        val d = editDraft ?: return
        if (d.name.isBlank() && d.phones.all { it.number.isBlank() }) { cancelEdit(); return }
        val before = editBefore?.takeIf { it.contactId != null }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { com.khizrim.dialer.data.ContactEditor.save(ctx, before, d) }
            if (!ok) { say(str(R.string.edit_failed)); return@launch }
            editDraft = null
            screen = editReturn
            say(str(R.string.edit_saved))
            reload()
        }
    }

    /** Фото, почта и всё остальное — в системных Контактах. */
    fun editInSystem() { editPerson?.let { Launcher.editContact(ctx, it)?.let { e -> say(e) } } }

    // ---------- быстрая уборка ----------
    fun removeFromHome(key: String, now: Boolean = false) {
        val at = home.indexOfFirst { it.key == key }
        if (at < 0) return
        val entry = home[at]
        menu = null
        viewModelScope.launch {
            // сначала уходит лист меню, потом гаснет строка, потом соседи съезжают; смахнутая строка уже ушла
            if (!now) {
                delay(Motion.MOVE.toLong())
                leaving = key
                delay(Motion.FADE.toLong())
            }
            home.removeAt(at)
            leaving = null
            prefs.hide(listOf(key))
            removed = listOf(at to entry)
            tidy = true
            say(str(if (entry.person != null && Declension.isFeminine(entry.title)) R.string.removed_from_home_f else R.string.removed_from_home, entry.title), undo = true)
        }
    }


    fun undo() {
        pendingCalls?.let { recentCalls = it }
        pendingRecord = null; pendingCalls = null
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
            str(R.string.clear_home_title),
            str(R.string.clear_home_text),
            str(R.string.hide),
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
        if (!isDefaultDialer) { say(str(R.string.block_need_role)); return }
        val shown = PhoneFormat.pretty(number)
        confirm = ConfirmState(
            str(R.string.block_title, shown),
            str(R.string.block_text),
            str(R.string.block),
        ) {
            viewModelScope.launch {
                val ok = withContext(Dispatchers.IO) { Blocklist.block(ctx, number) }
                say(if (ok) str(R.string.blocked_done, shown) else str(R.string.block_failed))
                if (ok) { prefs.hide(listOf("n:" + Numbers.key(number))); reload() }
            }
        }
    }

    fun unblock(number: String) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { Blocklist.unblock(ctx, number) }
            if (ok) blocked = blocked - number
            say(if (ok) str(R.string.unblocked_done, PhoneFormat.pretty(number)) else str(R.string.unblock_failed))
        }
    }

    // ---------- перенос ----------
    fun exportTo(uri: android.net.Uri) {
        val list = people
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { com.khizrim.dialer.data.Transfer.export(ctx, uri, list) }
            say(str(if (ok) R.string.transfer_saved else R.string.transfer_failed))
        }
    }

    fun importFrom(uri: android.net.Uri) {
        val list = people
        viewModelScope.launch {
            val n = withContext(Dispatchers.IO) { com.khizrim.dialer.data.Transfer.import(ctx, uri, list) }
            if (n == null) { say(str(R.string.transfer_bad_file)); return@launch }
            defaultsVersion++
            say(ctx.resources.getQuantityString(R.plurals.transfer_restored, n, n))
            reload()
        }
    }

    // ---------- тост ----------
    fun say(text: String, undo: Boolean = false, action: String? = null, onAction: (() -> Unit)? = null) {
        toastJob?.cancel()
        if (toast?.undo == true && pendingRecord != null) commitPendingRecord()
        toastAction = onAction
        toast = ToastState(text, undo, action)
        toastJob = viewModelScope.launch {
            delay(if (undo || action != null) 6000 else 2600)  // «Вернуть» живёт дольше: его надо успеть найти глазами
            toast = null
            if (undo) { tidy = false; removed = emptyList(); commitPendingRecord() }
        }
    }

    /** Intent для «Исправить»; null — экрана нет, показываем настройки приложения. */
    fun alertIntent(): android.content.Intent? {
        val a = callAlert ?: return null
        return com.khizrim.dialer.telecom.CallReadiness.fixIntent(ctx, a)
    }

    fun alertOpened() {
        if (callAlert == com.khizrim.dialer.telecom.CallAlert.XIAOMI) { prefs.xiaomiHintSeen = true; callAlert = null }
    }

    fun dismissAlert() {
        if (callAlert?.dismissable == true) { prefs.xiaomiHintSeen = true; callAlert = null }
    }

    companion object {
        const val HOME_SIZE = 6
        /** Сколько людей держит лента главной (это и есть журнал). */
        const val FEED_SIZE = 400
    }
}
