package com.khizrim.dialer.ui.card

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.khizrim.dialer.DialerViewModel
import com.khizrim.dialer.MenuContext
import com.khizrim.dialer.R
import com.khizrim.dialer.SheetMode
import com.khizrim.dialer.data.PhoneFormat
import com.khizrim.dialer.model.HistoryEntry
import com.khizrim.dialer.model.Method
import com.khizrim.dialer.model.Person
import com.khizrim.dialer.ui.kit.MainButton
import com.khizrim.dialer.ui.kit.OzIcon
import com.khizrim.dialer.ui.kit.Opt
import com.khizrim.dialer.ui.kit.QuietButton
import com.khizrim.dialer.ui.kit.Sheet
import com.khizrim.dialer.ui.kit.SheetText
import com.khizrim.dialer.ui.kit.SheetTitle
import com.khizrim.dialer.ui.kit.SimBadge
import com.khizrim.dialer.ui.kit.press
import com.khizrim.dialer.ui.kit.rememberLast
import com.khizrim.dialer.ui.nav.Layer
import com.khizrim.dialer.ui.nav.LocalNav
import com.khizrim.dialer.ui.theme.LocalOz
import com.khizrim.dialer.ui.theme.OzShape
import com.khizrim.dialer.ui.theme.OzType

/**
 * Карточка человека — лист снизу. Сверху имя, ниже способы по приложениям (основной — в акценте),
 * «Обычно: …», история по нажатию и одна широкая кнопка основного способа у большого пальца.
 * Любое действие закрывает карточку: человек уже там, куда шёл.
 */
@Composable
fun CardSheet(vm: DialerViewModel, l: Layer, id: Long) {
    val nav = LocalNav.current
    val live = vm.person(id)
    val p = rememberLast(live)
    LaunchedEffect(live == null) { if (live == null) nav.close(l) }
    if (p == null) return
    if (vm.isNumberCard(p)) { NumberCard(vm, l, p); return }
    val oz = LocalOz.current
    val default = vm.defaultOf(p)
    fun done(action: () -> Unit) { nav.close(l); action() }

    Sheet(l) {
        Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 10.dp, top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.shownName, style = OzType.namePerson, color = oz.fg, modifier = Modifier.semantics { heading() })
                val sub = p.nickname?.takeIf { it.isNotBlank() && it != p.name } ?: p.company?.takeIf { it.isNotBlank() }
                if (sub != null) Text(sub, style = OzType.small, color = oz.fg3, modifier = Modifier.padding(top = 2.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconSlot(if (p.starred) R.drawable.ic_oz_star_on else R.drawable.ic_oz_star, stringResource(if (p.starred) R.string.remove_favorite else R.string.add_favorite), if (p.starred) oz.accent else oz.fg3) { vm.toggleFavorite(p) }
            IconSlot(R.drawable.ic_oz_edit, stringResource(R.string.edit_contact), oz.fg3) { vm.editContact(p) }
        }

        // несколько номеров: каждый — звонок именно на него, долгое — скопировать
        if (p.phones.size > 1) {
            p.phones.forEach { ph ->
                Opt(
                    PhoneFormat.pretty(ph.number), { done { vm.callNumberOf(p, ph.number) } },
                    sub = ph.label.takeIf { it.isNotBlank() },
                    onLongClick = { vm.copyNumber(ph.number) },
                    checked = p.phones.size > 1 && ph.number == vm.numberFor(p) && default == Method.PHONE,
                )
            }
            Spacer(Modifier.height(4.dp))
        }

        // способы по приложениям: «Телефон  ☎ ✉», «WhatsApp  ☎ ▶ ✉»; основной — в акценте
        val groups = p.methods.groupBy { it.brand?.takeIf { b -> b != "SMS" } }
        groups.forEach { (brand, methods) ->
            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 10.dp).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(brand ?: stringResource(R.string.net_phone), style = OzType.body, color = oz.fg2, modifier = Modifier.weight(1f), maxLines = 1)
                methods.forEach { m ->
                    IconSlot(m.icon, stringResource(m.title), if (m == default) oz.accent else oz.fg2, size = 52, onLongClick = { vm.makeDefault(p, m) }) {
                        done { vm.performOther(p, m) }
                    }
                }
            }
        }

        if (p.methods.size > 1) {
            Line(
                left = stringResource(R.string.card_usual, default?.let { stringResource(it.title) } ?: stringResource(R.string.ask_every_time)),
                right = stringResource(R.string.card_change),
            ) { vm.openSheet(p, SheetMode.DEFAULT) }
        }

        History(vm.history(p))

        Spacer(Modifier.height(10.dp))
        MainButton(
            text = default?.let { stringResource(it.title) } ?: stringResource(R.string.choose_method),
            icon = default?.icon ?: R.drawable.ic_oz_swap,
            onClick = { if (default == null) vm.perform(p, null) else done { vm.perform(p, default) } },
            onLongClick = { if (!vm.askSim(p)) vm.openSheet(p, SheetMode.OTHER) },
        )
        val number = vm.numberFor(p)
        val line = buildList {
            if (default == Method.PHONE || default == Method.SMS) {
                if (p.phones.size > 1) p.phones.firstOrNull { it.number == number }?.label?.takeIf { it.isNotBlank() }?.let(::add)
                else number?.let { add(PhoneFormat.pretty(it)) }
            }
            if (default == Method.PHONE && vm.sims.size > 1) vm.simLabel(vm.personSim(number))?.let(::add)
        }.joinToString(" · ")
        Text(
            line, style = OzType.meta, color = oz.fg3,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp).height(16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** Незнакомый номер: такая же карточка, только действия другие. */
@Composable
private fun NumberCard(vm: DialerViewModel, l: Layer, p: Person) {
    val oz = LocalOz.current
    val nav = LocalNav.current
    val number = p.mainNumber.orEmpty()
    val tag = com.khizrim.dialer.callerid.rememberCallerTag(number)
    fun done(action: () -> Unit) { nav.close(l); action() }
    Sheet(l) {
        Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 4.dp, bottom = 8.dp)) {
            Text(PhoneFormat.pretty(number), style = OzType.namePerson.copy(fontFeatureSettings = "tnum"), color = oz.fg, modifier = Modifier.semantics { heading() })
            if (tag != null) Text(tag, style = OzType.small, color = oz.fg3, modifier = Modifier.padding(top = 2.dp))
        }
        History(vm.history(p), open = true)
        Opt(stringResource(R.string.add_to_contacts), { done { vm.addToContacts(number) } }, icon = R.drawable.ic_oz_user_plus)
        Opt(stringResource(R.string.send_sms), { done { vm.smsTo(number) } }, icon = R.drawable.ic_oz_sms)
        Opt(stringResource(R.string.copy_number), { vm.copyNumber(number) }, icon = R.drawable.ic_oz_copy)
        Opt(stringResource(R.string.block), { vm.askBlock(number) }, icon = R.drawable.ic_oz_ban, color = oz.danger)
        Spacer(Modifier.height(10.dp))
        MainButton(stringResource(R.string.call), { done { vm.callNumber(number) } }, icon = R.drawable.ic_oz_phone,
            onLongClick = { vm.askSim(number, PhoneFormat.pretty(number)) })
        Spacer(Modifier.height(12.dp))
    }
}

/** «История, 12 звонков» — раскрывается на месте; свежие сверху. */
@Composable
private fun History(entries: List<HistoryEntry>, open: Boolean = false) {
    if (entries.isEmpty()) return
    val oz = LocalOz.current
    var shown by remember { mutableStateOf(open) }
    val calls = entries.count { it.record != null }
    if (!open) {
        Line(
            left = stringResource(R.string.card_history) + if (calls > 0) ", " + pluralStringResource(R.plurals.card_calls, calls, calls) else "",
            right = null, chevron = if (shown) 180f else 0f,
        ) { shown = !shown }
    }
    AnimatedVisibility(shown, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Column(Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
            entries.asReversed().take(200).forEach { h ->
                val out = h.record?.type == android.provider.CallLog.Calls.OUTGOING_TYPE
                Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (h.record != null) OzIcon(if (out) R.drawable.ic_oz_arr_out else R.drawable.ic_oz_arr_in, if (h.missed) oz.danger else oz.fg3, 15.dp, Modifier.padding(end = 8.dp))
                    Text(h.day + ", " + h.text, style = OzType.small, color = if (h.missed) oz.danger else oz.fg2, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (h.duration.isNotEmpty()) Text(h.duration, style = OzType.time, color = oz.fg3)
                }
            }
        }
    }
}

/** Тихая строка-действие: слева текст, справа значение или стрелка. */
@Composable
private fun Line(left: String, right: String?, chevron: Float? = null, onClick: () -> Unit) {
    val oz = LocalOz.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).heightIn(min = 48.dp).press(onClick, shape = OzShape.row, label = left).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(left, style = OzType.small, color = oz.fg2, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (right != null) Text(right, style = OzType.small.copy(fontWeight = FontWeight.Medium), color = oz.accent)
        if (chevron != null) OzIcon(R.drawable.ic_oz_chevron_down, oz.fg3, 18.dp, Modifier.graphicsLayer { rotationZ = chevron })
    }
}

@Composable
private fun IconSlot(icon: Int, label: String, tint: androidx.compose.ui.graphics.Color, size: Int = 48, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    Box(Modifier.size(size.dp).press(onClick, onLongClick, CircleShape, label = label), contentAlignment = Alignment.Center) { OzIcon(icon, tint, 24.dp) }
}

// ---------- листы выбора ----------

/** «Связаться другим способом» и «что делать по нажатию». С двумя SIM звонок — по строке на каждую. */
@Composable
fun WaySheet(vm: DialerViewModel, l: Layer) {
    val s = rememberLast(vm.sheet) ?: return
    val p = vm.person(s.personId) ?: return
    val default = vm.defaultOf(p)
    Sheet(l) {
        if (s.mode == SheetMode.OTHER) {
            SheetTitle(vm.reachTitle(p))
            p.methods.forEach { m ->
                if (m == Method.PHONE && vm.sims.size > 1) {
                    vm.sims.forEachIndexed { i, (simId, label) ->
                        Opt(stringResource(R.string.call_with_sim, label), { vm.closeSheet(); vm.numberFor(p)?.let { vm.callNumber(it, simId) } },
                            lead = { SimBadge(i + 1) })
                    }
                } else if ((m == Method.PHONE || m == Method.SMS) && p.phones.size > 1) {
                    p.phones.forEach { ph -> Opt(stringResource(m.title), { vm.pickInSheet(m, ph.number) }, icon = m.icon, sub = PhoneFormat.pretty(ph.number) + (ph.label.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")) }
                } else Opt(stringResource(m.title), { vm.pickInSheet(m) }, icon = m.icon, checked = m == default)
            }
            QuietButton(stringResource(R.string.change_default), vm::sheetFooter, color = LocalOz.current.accent)
        } else {
            SheetTitle(stringResource(R.string.on_tap_title))
            p.methods.forEach { m -> Opt(stringResource(m.title), { vm.pickInSheet(m) }, icon = m.icon, checked = m == default) }
            Opt(stringResource(R.string.ask_every_time), vm::sheetFooter, icon = R.drawable.ic_oz_swap, checked = default == null)
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** С какой SIM позвонить на этот раз. Обычная SIM отмечена; три звонка подряд с другой — она запомнится сама. */
@Composable
fun SimSheet(vm: DialerViewModel, l: Layer) {
    val pick = rememberLast(vm.simPick) ?: return
    val usual = vm.usualSim(pick.number)
    Sheet(l) {
        SheetTitle(stringResource(R.string.choose_sim))
        SheetText(pick.who)
        vm.sims.forEachIndexed { i, (id, label) ->
            Opt(label, { vm.callWithSim(id) }, lead = { SimBadge(i + 1) }, checked = id == usual)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun ConfirmSheet(vm: DialerViewModel, l: Layer) {
    val c = rememberLast(vm.confirm) ?: return
    val oz = LocalOz.current
    Sheet(l) {
        SheetTitle(c.title)
        SheetText(c.description)
        MainButton(c.verb, vm::runConfirm, fill = oz.dangerFill)
        Spacer(Modifier.height(4.dp))
        QuietButton(stringResource(R.string.cancel), vm::cancelConfirm)
    }
}

/** Долгое нажатие на строку: всё, что можно сделать с человеком или номером. */
@Composable
fun MenuSheet(vm: DialerViewModel, l: Layer) {
    val m = rememberLast(vm.menu) ?: return
    val oz = LocalOz.current
    val nav = LocalNav.current
    val e = m.entry
    val p = e.person
    val number = e.number ?: p?.let { vm.numberFor(it) }
    Sheet(l) {
        SheetTitle(e.title)
        if (p != null) {
            Opt(stringResource(R.string.open_card), { vm.closeMenu(); nav.open(com.khizrim.dialer.ui.Dest.Card(p.id), com.khizrim.dialer.ui.nav.Kind.SHEET) }, icon = R.drawable.ic_oz_user)
            Opt(stringResource(R.string.other_way), { vm.openSheet(p, SheetMode.OTHER) }, icon = R.drawable.ic_oz_swap)
            Opt(stringResource(R.string.edit_contact), { vm.editContact(p) }, icon = R.drawable.ic_oz_edit)
        } else if (number != null) {
            Opt(stringResource(R.string.call), { vm.closeMenu(); vm.callNumber(number) }, icon = R.drawable.ic_oz_phone)
            Opt(stringResource(R.string.send_sms), { vm.closeMenu(); vm.smsTo(number) }, icon = R.drawable.ic_oz_sms)
            Opt(stringResource(R.string.add_to_contacts), { vm.addToContacts(number) }, icon = R.drawable.ic_oz_user_plus)
            Opt(stringResource(R.string.cid_search_web), { vm.searchWeb(number) }, icon = R.drawable.ic_oz_search)
        }
        if (number != null) Opt(stringResource(R.string.copy_number), { vm.closeMenu(); vm.copyNumber(number) }, icon = R.drawable.ic_oz_copy)
        if (m.context == MenuContext.HOME) Opt(stringResource(R.string.remove_from_home), { vm.removeFromHome(e.key) }, icon = R.drawable.ic_oz_hide)
        if (p == null && number != null) Opt(stringResource(R.string.block), { vm.askBlock(number) }, icon = R.drawable.ic_oz_ban, color = oz.danger)
        Spacer(Modifier.height(8.dp))
    }
}
