package com.khizrim.dialer.ui.card

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.khizrim.dialer.DialerViewModel
import com.khizrim.dialer.MenuContext
import com.khizrim.dialer.R
import com.khizrim.dialer.SheetMode
import com.khizrim.dialer.data.PhoneFormat
import com.khizrim.dialer.model.Method
import com.khizrim.dialer.ui.kit.MainButton
import com.khizrim.dialer.ui.kit.Opt
import com.khizrim.dialer.ui.kit.QuietButton
import com.khizrim.dialer.ui.kit.Sheet
import com.khizrim.dialer.ui.kit.SheetText
import com.khizrim.dialer.ui.kit.SheetTitle
import com.khizrim.dialer.ui.kit.SimBadge
import com.khizrim.dialer.ui.kit.rememberLast
import com.khizrim.dialer.ui.nav.Layer
import com.khizrim.dialer.ui.nav.LocalNav
import com.khizrim.dialer.ui.openCard
import com.khizrim.dialer.ui.theme.LocalOz

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
            Opt(stringResource(R.string.open_card), { vm.closeMenu(); nav.openCard(p.id) }, icon = R.drawable.ic_oz_user)
            Opt(stringResource(R.string.other_way), { vm.openSheet(p, SheetMode.OTHER) }, icon = R.drawable.ic_oz_swap)
            if (p.methods.size > 1) Opt(stringResource(R.string.on_tap_title), { vm.openSheet(p, SheetMode.DEFAULT) }, icon = R.drawable.ic_oz_sliders)
            Opt(
                stringResource(if (p.starred) R.string.remove_favorite else R.string.add_favorite),
                { vm.closeMenu(); vm.toggleFavorite(p) },
                icon = if (p.starred) R.drawable.ic_oz_star_on else R.drawable.ic_oz_star,
            )
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
