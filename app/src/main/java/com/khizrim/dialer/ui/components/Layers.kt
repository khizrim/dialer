package com.khizrim.dialer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.khizrim.dialer.ConfirmState
import com.khizrim.dialer.DialerViewModel
import com.khizrim.dialer.MenuContext
import com.khizrim.dialer.R
import com.khizrim.dialer.SheetMode
import com.khizrim.dialer.ToastState
import com.khizrim.dialer.model.Method
import com.khizrim.dialer.ui.theme.LocalOz
import com.khizrim.dialer.ui.theme.LocalReducedMotion
import com.khizrim.dialer.ui.theme.Motion
import com.khizrim.dialer.ui.theme.OzType

/** Держит последнее непустое значение, чтобы слой мог красиво уйти. */
@Composable
fun <T : Any> rememberLast(value: T?): T? {
    val holder = remember { arrayOfNulls<Any>(1) }
    if (value != null) holder[0] = value
    @Suppress("UNCHECKED_CAST")
    return holder[0] as T?
}

/** Затемнение 200 мс и лист снизу: вход 300 мс, уход 200 мс. */
@Composable
fun BoxScope.SheetLayer(visible: Boolean, title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val oz = LocalOz.current
    val reduced = LocalReducedMotion.current
    AnimatedVisibility(visible, enter = fadeIn(Motion.enter(Motion.MOVE)), exit = fadeOut(Motion.exit(Motion.MOVE))) {
        Box(
            Modifier.fillMaxSize().background(oz.scrim)
                .clickable(remember { MutableInteractionSource() }, null, onClickLabel = "Закрыть", onClick = onDismiss),
        )
    }
    AnimatedVisibility(
        visible,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = if (reduced) fadeIn(Motion.enter(Motion.FADE)) else slideInVertically(Motion.enter(Motion.SHEET)) { it },
        exit = if (reduced) fadeOut(Motion.exit(Motion.FADE)) else slideOutVertically(Motion.exit(Motion.MOVE)) { it },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(oz.sheet, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .clickable(remember { MutableInteractionSource() }, null, onClick = {})
                .semantics { paneTitle = title }
                .navigationBarsPadding()
                .padding(top = 12.dp, bottom = 16.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(36.dp, 4.dp).background(oz.line, RoundedCornerShape(2.dp)))
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun BoxScope.MethodSheet(vm: DialerViewModel) {
    val oz = LocalOz.current
    val s = rememberLast(vm.sheet)
    val p = s?.let { vm.person(it.personId) }
    val setMode = s?.mode == SheetMode.DEFAULT
    val title = if (setMode) "Что делать по нажатию на имя" else "Как связаться"
    SheetLayer(vm.sheet != null, title, vm::closeSheet) {
        if (p == null) return@SheetLayer
        val current = vm.defaultOf(p)
        Text(p.name, style = OzType.nameSearch, color = oz.fg, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp))
        Text(title, style = OzType.small, color = oz.fg3, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 2.dp, bottom = 8.dp))
        p.methods.forEachIndexed { i, m ->
            if (i > 0) HorizontalDivider(thickness = 1.dp, color = oz.line)
            MethodRow(m, if (m == Method.PHONE) "${m.title} ${com.khizrim.dialer.data.PhoneFormat.pretty(p.mainNumber.orEmpty())}" else m.title, m == current) { vm.pickInSheet(m) }
        }
        HorizontalDivider(Modifier.padding(top = 4.dp), thickness = 1.dp, color = oz.line)
        SheetRow(if (setMode) "Спрашивать каждый раз" else "Сделать способом по умолчанию", oz.fg3, vm::sheetFooter)
    }
}

@Composable
private fun MethodRow(m: Method, label: String, selected: Boolean, onClick: () -> Unit) {
    val oz = LocalOz.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .press(onClick, shape = RoundedCornerShape(0.dp), role = Role.RadioButton)
            .semantics { this.selected = selected }
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OzIcon(m.icon, oz.fg2)
        Text(label, style = OzType.body, color = oz.fg, modifier = Modifier.weight(1f))
        // галочка переходит прозрачностью за 150 мс
        AnimatedVisibility(selected, enter = fadeIn(Motion.enter(Motion.FADE)), exit = fadeOut(Motion.exit(Motion.FADE))) {
            OzIcon(R.drawable.ic_oz_check, oz.accent)
        }
    }
}

@Composable
private fun SheetRow(text: String, color: Color, onClick: () -> Unit, icon: Int? = null) {
    val oz = LocalOz.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).press(onClick, shape = RoundedCornerShape(0.dp)).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (icon != null) OzIcon(icon, oz.fg3)
        Text(text, style = OzType.body, color = color)
    }
}

/** Долгое нажатие на имя. */
@Composable
fun BoxScope.PersonMenu(vm: DialerViewModel) {
    val oz = LocalOz.current
    val m = rememberLast(vm.menu)
    val e = m?.entry
    val p = e?.person
    SheetLayer(vm.menu != null, e?.title ?: "", vm::closeMenu) {
        if (e == null) return@SheetLayer
        Text(
            e.title,
            style = if (p != null) OzType.nameSearch else OzType.nameSearch.copy(fontFamily = com.khizrim.dialer.ui.theme.Inter, fontWeight = FontWeight.Normal),
            color = oz.fg,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 8.dp),
        )
        if (p != null) {
            SheetRow("Открыть карточку", oz.fg, { vm.openPerson(p.id) }, R.drawable.ic_oz_user)
            SheetRow("Другой способ", oz.fg, { vm.openSheet(p, SheetMode.OTHER) }, R.drawable.ic_oz_swap)
        } else {
            val number = e.number.orEmpty()
            SheetRow("Добавить в контакты", oz.fg, { vm.addToContacts(number) }, R.drawable.ic_oz_user_plus)
            SheetRow("Написать SMS", oz.fg, { vm.closeMenu(); vm.smsTo(number) }, R.drawable.ic_oz_sms)
            SheetRow("Заблокировать", oz.fg, { vm.askBlock(number) }, R.drawable.ic_oz_ban)
        }
        if (m?.context == MenuContext.HOME) {
            SheetRow("Убрать с главной", oz.fg, { vm.removeFromHome(e.key) }, R.drawable.ic_oz_hide)
        }
    }
}

@Composable
fun BoxScope.ConfirmLayer(vm: DialerViewModel) {
    val oz = LocalOz.current
    val c: ConfirmState? = rememberLast(vm.confirm)
    AnimatedVisibility(vm.confirm != null, enter = fadeIn(Motion.enter(Motion.MOVE)), exit = fadeOut(Motion.exit(Motion.FADE))) {
        Box(Modifier.fillMaxSize().background(oz.scrim).clickable(remember { MutableInteractionSource() }, null, onClick = vm::cancelConfirm))
    }
    AnimatedVisibility(
        vm.confirm != null,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = fadeIn(Motion.enter(Motion.MOVE)), exit = fadeOut(Motion.exit(Motion.FADE)),
    ) {
        if (c == null) return@AnimatedVisibility
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                .fillMaxWidth()
                .background(oz.sheet, RoundedCornerShape(24.dp))
                .border(1.dp, oz.line, RoundedCornerShape(24.dp))
                .clickable(remember { MutableInteractionSource() }, null, onClick = {})
                .semantics { paneTitle = c.title }
                .padding(24.dp),
        ) {
            Text(c.title, style = OzType.title, color = oz.fg)
            Text(c.description, style = OzType.small, color = oz.fg3, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.End) {
                TextAction("Отмена", oz.fg, vm::cancelConfirm, style = OzType.small.copy(fontWeight = FontWeight.SemiBold))
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .heightIn(min = 44.dp)
                        .press(vm::runConfirm, shape = RoundedCornerShape(22.dp), pressColor = oz.dangerFill)
                        .background(oz.dangerFill, RoundedCornerShape(22.dp))
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(c.verb, style = OzType.small.copy(fontWeight = FontWeight.SemiBold), color = Color.White) }
            }
        }
    }
}

/** Плавающий тост для всего, кроме «Вернуть» (тот живёт в нижней панели главной). */
@Composable
fun BoxScope.FloatToast(toast: ToastState?, bottom: androidx.compose.ui.unit.Dp) {
    val oz = LocalOz.current
    val visible = toast != null && !toast.undo
    val t = rememberLast(if (visible) toast else null)
    AnimatedVisibility(
        visible,
        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = bottom),
        enter = fadeIn(Motion.enter(Motion.MOVE)), exit = fadeOut(Motion.exit(Motion.FADE)),
    ) {
        Text(
            t?.text ?: "",
            style = OzType.small,
            color = oz.bg,
            modifier = Modifier
                .fillMaxWidth()
                .background(oz.fg, RoundedCornerShape(14.dp))
                .semantics { liveRegion = LiveRegionMode.Polite }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}

