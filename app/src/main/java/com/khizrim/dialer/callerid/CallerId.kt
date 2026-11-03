package com.khizrim.dialer.callerid

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.TelephonyManager
import com.khizrim.dialer.R
import com.khizrim.dialer.data.ContactsSource
import com.khizrim.dialer.data.Prefs
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Что делать со звонком, похожим на спам. */
enum class SpamAction { SILENCE, REJECT, LABEL }

class CallerIdPrefs(context: Context) {
    private val sp = context.getSharedPreferences("ozero_callerid", Context.MODE_PRIVATE)

    /** Человек сам выбрал режим (а не живёт на значении по умолчанию). */
    val explicit: Boolean get() = sp.contains("spamAction")

    var action: SpamAction
        get() = runCatching { SpamAction.valueOf(sp.getString("spamAction", null) ?: "") }.getOrDefault(SpamAction.SILENCE)
        set(v) { sp.edit().putString("spamAction", v.name).apply() }
}

/**
 * АОН для незнакомых номеров. Всё считается на телефоне: номер никуда не отправляется,
 * у приложения даже нет доступа в интернет.
 *
 * Функции не смотрят в контакты (кроме [shouldSilence]): звать их стоит для номеров,
 * которых в контактах нет.
 */
object CallerId {
    private const val ASSET = "callerid/ru.bin"

    @Volatile private var db: NumberDb? = null
    @Volatile private var dbFailed = false

    /** Номера, которые сеть пометила как не прошедшие проверку, пока жив процесс. */
    private val spoofed: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

    fun db(context: Context): NumberDb? {
        db?.let { return it }
        if (dbFailed) return null
        synchronized(this) {
            db?.let { return it }
            val loaded = runCatching { context.applicationContext.assets.open(ASSET).use { NumberDb.read(it) } }.getOrNull()
            if (loaded == null) dbFailed = true
            db = loaded
            return loaded
        }
    }

    /** Загрузить базу заранее, чтобы первый звонок не ждал. */
    fun warmUp(context: Context) {
        if (db != null || dbFailed) return
        val app = context.applicationContext
        Thread({ db(app) }, "callerid-warmup").start()
    }

    fun facts(context: Context, number: String?, verificationFailed: Boolean = false): CallerFacts {
        val key = number.orEmpty().filter { it.isDigit() }
        if (verificationFailed && key.isNotEmpty()) spoofed += key
        val parsed = CallerNumber.parse(number, homeIso(context))
        return Classifier.classify(parsed, db(context), verificationFailed || (key.isNotEmpty() && key in spoofed))
    }

    /** Имена от сети (CNAP) по номеру, пока жив процесс: их видит экранирование звонка. */
    private val networkNames = ConcurrentHashMap<String, String>()

    /**
     * Запомнить имя, которое сеть передала вместе с входящим (Call.Details.callerDisplayName
     * при PRESENTATION_ALLOWED). Это единственная «системная» подпись, доступная сторонней звонилке:
     * её дают оператор и его антиспам («МТС Защитник», «Билайн Антиспам» и т. п.).
     */
    fun rememberNetworkName(number: String?, name: String?) {
        val key = number.orEmpty().filter { it.isDigit() }
        val usable = NetworkName.usable(name) ?: return
        if (key.isNotEmpty()) networkNames[key] = usable
    }

    fun networkName(number: String?): String? = networkNames[number.orEmpty().filter { it.isDigit() }]

    /** Подпись под номером на экране звонка: имя от сети, «МТС · Москва и область», «Похоже на спам». */
    fun labelFor(context: Context, number: String?): String? {
        val ours = label(context, facts(context, number))
        val network = networkName(number) ?: return ours
        if (NetworkName.looksLikeSpam(network)) return network
        val region = shortLabel(context, facts(context, number))
        return if (region.isNullOrBlank() || region == network) network else context.getString(R.string.cid_join, network, region)
    }

    /** Короткая подпись для списков: «Москва и область», «Беларусь», «Похоже на спам». */
    fun shortLabelFor(context: Context, number: String?): String? = shortLabel(context, facts(context, number))

    fun isSuspicious(context: Context, number: String?): Boolean =
        facts(context, number).suspicious || NetworkName.looksLikeSpam(networkName(number))

    /**
     * Глушить ли входящий: «Не беспокоить незнакомым» или подозрительный номер при режиме «Глушить».
     * Номера из контактов не глушим никогда.
     */
    fun shouldSilence(context: Context, number: String?): Boolean {
        val n = number.orEmpty()
        if (n.isNotBlank() && ContactsSource.nameFor(context, n) != null) return false
        if (isRecentContact(context, n)) return false
        if (Prefs(context).silenceUnknownActive(context)) return true
        val prefs = CallerIdPrefs(context)
        val strong = actsOn(context, facts(context, n), prefs) || NetworkName.looksLikeSpam(networkName(n))
        return prefs.action == SpamAction.SILENCE && strong
    }

    /** Сами звонили на этот номер за неделю или с него звонят повторно за 3 минуты (см. [RecentContact]). */
    fun isRecentContact(context: Context, number: String?): Boolean = runCatching {
        val log = com.khizrim.dialer.data.CallLogSource.load(context, 200).map { RecentContact.Entry(it.number, it.type, it.date) }
        RecentContact.exempt(number, log)
    }.getOrDefault(false)

    /** Глушить или сбрасывать этот подозрительный звонок, а не только подписать (см. [SpamPolicy]). */
    fun actsOn(context: Context, facts: CallerFacts, prefs: CallerIdPrefs = CallerIdPrefs(context)): Boolean =
        SpamPolicy.acts(facts.suspicion, prefs.explicit, dbAgeDays(context))

    /** Возраст базы номеров в днях. */
    fun dbAgeDays(context: Context, now: Long = System.currentTimeMillis()): Long? {
        val raw = db(context)?.updated?.takeIf { it.isNotBlank() } ?: return null
        val date = runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(raw) }.getOrNull() ?: return null
        return (now - date.time) / 86_400_000L
    }

    /** Поиск номера в интернете: открывается браузер, сам номер приложение никуда не шлёт. */
    fun webSearchIntent(number: String): Intent {
        val query = "\"" + number.filter { it.isDigit() || it == '+' } + "\""
        return Intent(Intent.ACTION_VIEW, Uri.parse("https://yandex.ru/search/?text=" + Uri.encode(query)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun label(context: Context, f: CallerFacts): String? {
        val res = context.resources
        fun join(a: String, b: String?) = if (b.isNullOrBlank()) a else res.getString(R.string.cid_join, a, b)
        when (f.suspicion) {
            CallerFacts.Suspicion.SPOOFED, CallerFacts.Suspicion.UNALLOCATED -> return res.getString(R.string.cid_spoofed)
            CallerFacts.Suspicion.RISKY_COUNTRY -> return join(res.getString(R.string.cid_spam), country(context, f.countryIso))
            null -> {}
        }
        return when (f.kind) {
            CallerFacts.Kind.HIDDEN -> res.getString(R.string.cid_hidden)
            CallerFacts.Kind.TOLL_FREE -> res.getString(R.string.cid_toll_free)
            CallerFacts.Kind.MOBILE -> f.operator?.let { join(it, f.region) } ?: f.region
            CallerFacts.Kind.LANDLINE -> join(res.getString(R.string.cid_landline), f.region)
            CallerFacts.Kind.VIRTUAL -> join(res.getString(R.string.cid_virtual), f.region)
            CallerFacts.Kind.FOREIGN -> country(context, f.countryIso) ?: res.getString(R.string.cid_foreign)
            CallerFacts.Kind.SERVICE, CallerFacts.Kind.UNALLOCATED, CallerFacts.Kind.UNKNOWN -> null
        }
    }

    fun shortLabel(context: Context, f: CallerFacts): String? {
        val res = context.resources
        if (f.suspicious) return res.getString(if (f.suspicion == CallerFacts.Suspicion.RISKY_COUNTRY) R.string.cid_spam else R.string.cid_spoofed)
        return when (f.kind) {
            CallerFacts.Kind.HIDDEN -> res.getString(R.string.cid_hidden)
            CallerFacts.Kind.TOLL_FREE -> res.getString(R.string.cid_toll_free)
            CallerFacts.Kind.VIRTUAL -> res.getString(R.string.cid_virtual)
            CallerFacts.Kind.MOBILE, CallerFacts.Kind.LANDLINE -> f.region ?: f.operator
            CallerFacts.Kind.FOREIGN -> country(context, f.countryIso) ?: res.getString(R.string.cid_foreign)
            else -> null
        }
    }

    /** Дата реестра для подписи в настройках: «26 октября 2026». */
    fun registryDate(context: Context): String? {
        val raw = db(context)?.updated?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(raw)!!
            SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(date)
        }.getOrDefault(raw)
    }

    private fun country(context: Context, iso: String?): String? {
        if (iso.isNullOrBlank()) return null
        val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
        return Locale("", iso).getDisplayCountry(locale).takeIf { it.isNotBlank() && it != iso }
    }

    private fun homeIso(context: Context): String {
        val tm = context.getSystemService(TelephonyManager::class.java)
        val iso = runCatching { tm?.simCountryIso?.takeIf { it.isNotBlank() } ?: tm?.networkCountryIso }.getOrNull()
        return (iso?.takeIf { it.isNotBlank() } ?: Locale.getDefault().country).uppercase(Locale.ROOT)
    }
}
