package com.khizrim.dialer.ui.card

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khizrim.dialer.DialerViewModel
import com.khizrim.dialer.R
import com.khizrim.dialer.SheetMode
import com.khizrim.dialer.data.PhoneFormat
import com.khizrim.dialer.model.HistoryEntry
import com.khizrim.dialer.model.Method
import com.khizrim.dialer.model.MethodKind
import com.khizrim.dialer.model.Person
import com.khizrim.dialer.ui.kit.OzIcon
import com.khizrim.dialer.ui.kit.press
import com.khizrim.dialer.ui.kit.rememberLast
import com.khizrim.dialer.ui.nav.Layer
import com.khizrim.dialer.ui.nav.LocalNav
import com.khizrim.dialer.ui.nav.dismissDrag
import com.khizrim.dialer.ui.theme.LocalGlass
import com.khizrim.dialer.ui.theme.LocalOz
import com.khizrim.dialer.ui.theme.OzType
import com.khizrim.dialer.ui.theme.glass
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

/*
 * Экран человека. Иерархия сверху вниз: история (тише всего) → имя (крупнее всего) → способы связи.
 * Всё, что про «как связаться», — одна стеклянная группа внизу: «Обычно», действия, приложения.
 * Сетка 4: текст по краю 20, группа по краю 12; внутри групп 4–8, между группами 16–28.
 */

private val Gutter = 20.dp
private val NameStyle get() = OzType.namePerson.copy(fontSize = 32.sp, lineHeight = 36.sp)
private val SubStyle get() = OzType.body.copy(fontSize = 15.sp, lineHeight = 20.sp)

/** Приложение, в котором лежит способ: телефон (звонок и SMS) или мессенджер. */
private fun appOf(m: Method): String = m.brand?.takeIf { it != "SMS" } ?: ""

/** Порядок действий внутри приложения: звонок, видео, сообщение. */
private fun orderOf(m: Method): Int = when {
    m.kind == MethodKind.CHAT -> 2
    m.icon == R.drawable.ic_oz_video -> 1
    else -> 0
}

@Composable
fun PersonScreen(vm: DialerViewModel, l: Layer, id: Long) {
    val nav = LocalNav.current
    val live = vm.person(id)
    val p = rememberLast(live)
    LaunchedEffect(live == null) { if (live == null) nav.close(l) }
    if (p == null) return
    val oz = LocalOz.current
    val density = LocalDensity.current
    val haze = remember { HazeState() }
    var dockPx by remember { mutableIntStateOf(0) }
    val unknown = vm.isNumberCard(p)
    val history = vm.history(p)
    val days = remember(history) { history.withIndex().groupBy({ it.value.day }, { it.value }).toList() }
    val statusTop = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val navBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val dockDp = with(density) { dockPx.toDp() }
    fun done(action: () -> Unit) { nav.close(l); action() }

    CompositionLocalProvider(LocalGlass provides haze) {
        Box(Modifier.fillMaxSize().background(oz.bg).dismissDrag(l)) {
            // снизу вверх: имя у способов связи, над ним свежая история, старая уходит вверх
            LazyColumn(
                Modifier.fillMaxSize().haze(haze),
                state = rememberLazyListState(),
                reverseLayout = true,
                contentPadding = PaddingValues(top = statusTop + 24.dp, bottom = navBottom + 12.dp + dockDp + 24.dp),
            ) {
                item(key = "who") { Who(vm, p, unknown, top = if (history.isEmpty()) 0.dp else 28.dp) }
                days.asReversed().forEachIndexed { di, (day, list) ->
                    list.asReversed().forEachIndexed { i, h -> item(key = "h$di:$i") { Event(h) } }
                    item(key = "d$di") { Day(day, first = di == days.size - 1) }
                }
            }
            // история уходит под строку состояния мягко, без резкого края
            Box(
                Modifier.fillMaxWidth().height(statusTop + 24.dp)
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(0.45f to oz.bg, 1f to oz.bg.copy(alpha = 0f))),
            )
            CompositionLocalProvider(LocalGlass provides haze) {
                Ways(
                    vm, p, unknown, ::done,
                    Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                        .onSizeChanged { dockPx = it.height; l.floor = it.height.toFloat() },
                )
            }
        }
    }
}

/** Имя крупно, под ним номер или компания; справа действия с самим человеком. */
@Composable
private fun Who(vm: DialerViewModel, p: Person, unknown: Boolean, top: androidx.compose.ui.unit.Dp) {
    val oz = LocalOz.current
    val number = p.mainNumber.orEmpty()
    val tag = if (unknown) com.khizrim.dialer.callerid.rememberCallerTag(number) else null
    Row(Modifier.fillMaxWidth().padding(start = Gutter, end = 8.dp, top = top), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f).padding(top = 2.dp)) {
            if (unknown) {
                Text(
                    PhoneFormat.pretty(number), style = NameStyle.copy(fontSize = 26.sp, fontFeatureSettings = "tnum"), color = oz.fg,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() },
                )
                if (tag != null) Text(tag, style = SubStyle, color = oz.warn, modifier = Modifier.padding(top = 4.dp))
            } else {
                Text(p.shownName, style = NameStyle, color = oz.fg, modifier = Modifier.semantics { heading() })
                val sub = if (p.phones.size == 1) PhoneFormat.pretty(p.phones.first().number)
                else p.nickname?.takeIf { it.isNotBlank() && it != p.name } ?: p.company?.takeIf { it.isNotBlank() }
                val copyable = p.phones.size == 1
                if (sub != null) Text(
                    sub, style = SubStyle.copy(fontFeatureSettings = "tnum"), color = oz.fg3, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp).then(
                        if (copyable) Modifier.press({ vm.copyNumber(p.phones.first().number) }, shape = RoundedCornerShape(6.dp), label = stringResource(R.string.copy_number)) else Modifier,
                    ),
                )
            }
        }
        if (unknown) {
            Act(R.drawable.ic_oz_user_plus, stringResource(R.string.add_to_contacts), oz.fg3) { vm.addToContacts(number) }
            Act(R.drawable.ic_oz_ban, stringResource(R.string.block), oz.fg3) { vm.askBlock(number) }
        } else {
            Act(
                if (p.starred) R.drawable.ic_oz_star_on else R.drawable.ic_oz_star,
                stringResource(if (p.starred) R.string.remove_favorite else R.string.add_favorite),
                if (p.starred) oz.accent else oz.fg3,
            ) { vm.toggleFavorite(p) }
            Act(R.drawable.ic_oz_edit, stringResource(R.string.edit_contact), oz.fg3) { vm.editContact(p) }
        }
    }
}

@Composable
private fun Act(@DrawableRes icon: Int, label: String, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).press(onClick, shape = CircleShape, label = label).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        OzIcon(icon, tint, 22.dp)
    }
}

@Composable
private fun Day(text: String, first: Boolean) {
    Text(
        text, style = OzType.meta.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = LocalOz.current.fg3,
        modifier = Modifier.padding(start = Gutter, end = Gutter, top = if (first) 0.dp else 24.dp, bottom = 4.dp).semantics { heading() },
    )
}

/** Строка истории: направление, что было (длительность серым после), справа всегда время. */
@Composable
private fun Event(h: HistoryEntry) {
    val oz = LocalOz.current
    val out = h.record == null || h.record.type == android.provider.CallLog.Calls.OUTGOING_TYPE
    val tint = if (h.missed) oz.danger else oz.fg3
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(horizontal = Gutter), verticalAlignment = Alignment.CenterVertically) {
        OzIcon(if (out) R.drawable.ic_oz_arr_out else R.drawable.ic_oz_arr_in, tint, 20.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            buildAnnotatedString {
                append(h.text)
                if (h.duration.isNotEmpty()) withStyle(SpanStyle(color = oz.fg3)) { append(" · " + h.duration) }
            },
            style = SubStyle, color = if (h.missed) oz.danger else oz.fg2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        if (h.time.isNotEmpty()) Text(h.time, style = OzType.meta.copy(fontSize = 13.sp), color = oz.fg3, modifier = Modifier.padding(start = 12.dp))
    }
}

/** Одно действие в группе: значок над подписью. Синий — основной способ, и только когда есть из чего выбирать. */
private data class Verb(val icon: Int, val label: String, val usual: Boolean, val onClick: () -> Unit, val onHold: (() -> Unit)?)

/**
 * Способы связи одной стеклянной группой: «Обычно — …» (если выбирать есть из чего), номера (если их несколько),
 * действия выбранного приложения, под ними сами приложения.
 */
@Composable
private fun Ways(vm: DialerViewModel, p: Person, unknown: Boolean, done: (() -> Unit) -> Unit, modifier: Modifier) {
    val oz = LocalOz.current
    val shape = RoundedCornerShape(28.dp)
    val number0 = p.mainNumber.orEmpty()

    if (unknown) {
        Column(modifier.fillMaxWidth().glass(shape).background(oz.fg.copy(alpha = 0.03f)).padding(4.dp)) {
            VerbRow(listOf(
                Verb(R.drawable.ic_oz_phone, stringResource(R.string.call), false, { done { vm.callNumber(number0) } }, null),
                Verb(R.drawable.ic_oz_sms, stringResource(R.string.way_sms), false, { done { vm.smsTo(number0) } }, null),
                Verb(R.drawable.ic_oz_copy, stringResource(R.string.card_copy), false, { vm.copyNumber(number0) }, null),
            ))
        }
        return
    }

    val default = vm.defaultOf(p)
    val apps = remember(p.methods) { p.methods.map(::appOf).distinct().sortedBy { if (it.isEmpty()) 0 else 1 } }
    val choice = apps.size > 1
    var app by remember(p.id) { mutableStateOf(default?.let(::appOf) ?: apps.firstOrNull().orEmpty()) }
    if (app !in apps) app = apps.firstOrNull().orEmpty()
    val usualNumber = vm.numberFor(p)
    var numIdx by remember(p.id) { mutableIntStateOf(p.phones.indexOfFirst { it.number == usualNumber }.coerceAtLeast(0)) }
    if (numIdx >= p.phones.size) numIdx = 0
    val number = p.phones.getOrNull(numIdx)?.number ?: number0
    val atUsualNumber = p.phones.size < 2 || number == usualNumber
    val sims = vm.sims
    val personSim = vm.personSim(number)

    // лёгкий оттенок поверх стекла: на белой бумаге граница группы иначе не видна
    Column(modifier.fillMaxWidth().glass(shape).background(oz.fg.copy(alpha = 0.03f)).padding(4.dp)) {
        if (choice) {
            val simText = if (default == Method.PHONE && sims.size > 1) vm.simLabel(personSim)?.let { " · $it" }.orEmpty() else ""
            Row(
                Modifier.fillMaxWidth().height(40.dp)
                    .press({ vm.openSheet(p, SheetMode.DEFAULT) }, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 12.dp, bottomEnd = 12.dp), label = stringResource(R.string.on_tap_title))
                    .padding(start = 4.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    buildAnnotatedString {
                        append(stringResource(R.string.card_usual) + " ")
                        withStyle(SpanStyle(color = oz.fg, fontWeight = FontWeight.Medium)) {
                            append((default?.let { stringResource(it.title) } ?: stringResource(R.string.ask_every_time)) + simText)
                        }
                    },
                    style = OzType.small, color = oz.fg3, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                // стрелка вплотную к значению: она про него, а не про край группы
                OzIcon(R.drawable.ic_oz_chevron_down, oz.fg3, 16.dp, Modifier.padding(start = 4.dp))
            }
        }

        if (app.isEmpty() && p.phones.size > 1) {
            NumberChips(p.phones.map { it.label.takeIf { l -> l.isNotBlank() } ?: PhoneFormat.pretty(it.number) }, numIdx) { numIdx = it }
        }

        val verbs = if (app.isEmpty()) buildList {
            if (Method.PHONE in p.methods) {
                if (sims.size > 1) sims.forEach { (simId, label) ->
                    val short = label.substringAfter(" · ", label)
                    add(Verb(
                        R.drawable.ic_oz_phone, short,
                        choice && default == Method.PHONE && atUsualNumber && simId == personSim,
                        { done { vm.callPerson(p, number, simId) } },
                        { vm.makeUsual(p, Method.PHONE, number.takeIf { p.phones.size > 1 }, simId) },
                    ))
                } else add(Verb(
                    R.drawable.ic_oz_phone, stringResource(R.string.call),
                    choice && default == Method.PHONE && atUsualNumber,
                    { done { vm.callPerson(p, number, null) } },
                    if (choice) ({ vm.makeUsual(p, Method.PHONE, number.takeIf { p.phones.size > 1 }) }) else null,
                ))
            }
            if (Method.SMS in p.methods) add(Verb(
                R.drawable.ic_oz_sms, stringResource(R.string.way_sms),
                choice && default == Method.SMS && atUsualNumber,
                { done { vm.performOther(p, Method.SMS, number) } },
                if (choice) ({ vm.makeUsual(p, Method.SMS, number.takeIf { p.phones.size > 1 }) }) else null,
            ))
        } else p.methods.filter { appOf(it) == app }.sortedBy(::orderOf).map { m ->
            Verb(m.icon, stringResource(m.short), choice && m == default, { done { vm.performOther(p, m) } }, { vm.makeUsual(p, m) })
        }
        VerbRow(verbs)

        if (choice) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).height(1.dp).background(oz.line))
            val names = apps.map { it.ifEmpty { stringResource(R.string.net_phone) } }
            Segments(names, apps.indexOf(app)) { app = apps[it] }
        }
    }
}

@Composable
private fun VerbRow(verbs: List<Verb>) {
    val oz = LocalOz.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        verbs.forEach { v ->
            val tint = if (v.usual) oz.accent else oz.fg
            Column(
                Modifier.weight(1f).height(64.dp).press(v.onClick, v.onHold, RoundedCornerShape(20.dp), label = v.label),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                OzIcon(v.icon, tint, 24.dp)
                Spacer(Modifier.height(4.dp))
                Text(v.label, style = OzType.meta.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = ""), color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Номера у телефона: выбранный обведён тонкой линией. Не заливка, чтобы не спорить с приложениями ниже. */
@Composable
private fun NumberChips(items: List<String>, selected: Int, onPick: (Int) -> Unit) {
    val oz = LocalOz.current
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEachIndexed { i, s ->
            val on = i == selected
            Box(
                Modifier.weight(1f).height(32.dp)
                    .then(if (on) Modifier.border(1.dp, oz.fg3.copy(alpha = 0.5f), CircleShape) else Modifier)
                    .press({ onPick(i) }, shape = CircleShape, label = s),
                contentAlignment = Alignment.Center,
            ) {
                Text(s, style = OzType.meta.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium), color = if (on) oz.fg else oz.fg3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Переключатель: выбранное — тихая заливка, остальное серым. */
@Composable
private fun Segments(items: List<String>, selected: Int, onPick: (Int) -> Unit) {
    val oz = LocalOz.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEachIndexed { i, s ->
            val on = i == selected
            Box(
                Modifier.weight(1f).height(44.dp)
                    .background(if (on) oz.fg.copy(alpha = 0.08f) else Color.Transparent, CircleShape)
                    .press({ onPick(i) }, shape = CircleShape, label = s),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    s, style = OzType.small.copy(fontWeight = FontWeight.Medium),
                    color = if (on) oz.fg else oz.fg3, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
