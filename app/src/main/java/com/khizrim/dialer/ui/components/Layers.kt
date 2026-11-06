package com.khizrim.dialer.ui.components

import com.khizrim.dialer.ui.theme.lift
import com.khizrim.dialer.ui.theme.raisedFill
import com.khizrim.dialer.ui.theme.OzShape
import com.khizrim.dialer.ui.theme.Gap
import androidx.compose.ui.draw.clip

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/**
 * Затемнение и лист снизу: вход 300 мс, уход 200 мс, оба с мягким торможением.
 * Лист тянут вниз — он идёт за пальцем 1:1, затемнение светлеет вместе с ним; отпустили — лист доезжает
 * с той же скоростью (или возвращается), и только потом закрывается: без рывка и без повторной анимации.
 */
@Composable
fun BoxScope.SheetLayer(visible: Boolean, title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val oz = LocalOz.current
    val reduced = LocalReducedMotion.current
    val drag = remember { androidx.compose.animation.core.Animatable(0f) }
    val sheetHeight = remember { floatArrayOf(1f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(visible) { if (visible) drag.snapTo(0f) }
    AnimatedVisibility(visible, enter = fadeIn(Motion.move(Motion.SCRIM)), exit = fadeOut(Motion.move(Motion.SCRIM))) {
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = 1f - (drag.value / sheetHeight[0]).coerceIn(0f, 1f) }
                .background(oz.scrim)
                .clickable(remember { MutableInteractionSource() }, null, onClickLabel = stringResource(R.string.close), onClick = onDismiss),
        )
    }
    AnimatedVisibility(
        visible,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = if (reduced) fadeIn(Motion.enter(Motion.FADE)) else slideInVertically(Motion.move(Motion.SHEET)) { it },
        exit = if (reduced) fadeOut(Motion.exit(Motion.FADE)) else slideOutVertically(Motion.move(Motion.SHEET)) { it },
    ) {
        val tracker = remember { androidx.compose.ui.input.pointer.util.VelocityTracker() }
        // как лист Ozero Mail: не шире 560dp, не выше 88% экрана, цвет страницы, без тени — его отделяет затемнение
        val maxHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp * .88f
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .onSizeChanged { sheetHeight[0] = it.height.toFloat().coerceAtLeast(1f) }
                .graphicsLayer { translationY = drag.value }
                .pointerInput(onDismiss) {
                    detectVerticalDragGestures(
                        onDragStart = { tracker.resetTracking() },
                        onDragEnd = {
                            val v = tracker.calculateVelocity().y
                            val h = sheetHeight[0]
                            // решаем после того, как применились все сдвиги пальца
                            scope.launch {
                                if (Motion.commits(drag.value / h, v, h)) {
                                    // доезжает за край со скоростью пальца, затем закрывается (уход уже за экраном)
                                    drag.animateTo(h, Motion.settle(), v)
                                    onDismiss()
                                } else drag.animateTo(0f, Motion.settle(), v)
                            }
                        },
                        onDragCancel = { scope.launch { drag.animateTo(0f, Motion.settle()) } },
                    ) { change, amount ->
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        scope.launch { drag.snapTo((drag.value + amount).coerceAtLeast(0f)) }
                    }
                }
                .clip(OzShape.sheetTop)
                .background(oz.bg)
                .clickable(remember { MutableInteractionSource() }, null, onClick = {})
                .semantics { paneTitle = title }
                .navigationBarsPadding()
                .padding(top = 10.dp, bottom = 16.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp).size(36.dp, 4.dp).background(oz.handle, RoundedCornerShape(2.dp)))
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
    val neutral = stringResource(R.string.how_to_reach)
    // «Как связаться с Мамой», если имя склоняется; иначе имя и подпись под ним
    val reach = p?.let { vm.reachTitle(it) } ?: neutral
    val title = if (setMode) stringResource(R.string.on_tap_title) else reach
    SheetLayer(vm.sheet != null, title, vm::closeSheet) {
        if (p == null) return@SheetLayer
        val current = vm.defaultOf(p)
        if (!setMode && reach != neutral) {
            Text(reach, style = OzType.strong, color = oz.fg, modifier = Modifier.padding(start = Gap.sheet, end = Gap.sheet, top = 4.dp, bottom = 8.dp))
        } else {
            Text(p.name, style = OzType.nameSearch, color = oz.fg, modifier = Modifier.padding(start = Gap.sheet, end = Gap.sheet, top = 4.dp))
            Text(title, style = OzType.small, color = oz.fg3, modifier = Modifier.padding(start = Gap.sheet, end = Gap.sheet, top = 4.dp, bottom = 12.dp))
        }
        // у телефона и SMS по строке на каждый номер: «Позвонить» и под ним «мобильный  +7 …»
        val chosen = vm.numberFor(p)
        val many = p.phones.size > 1
        p.methods.forEach { m ->
            val perNumber = m == Method.PHONE || m == Method.SMS
            val numbers: List<com.khizrim.dialer.model.Phone?> = if (perNumber && p.phones.isNotEmpty()) p.phones else listOf(null)
            numbers.forEach { ph ->
                val sub = ph?.let { (if (many) it.label + "  " else "") + com.khizrim.dialer.data.PhoneFormat.pretty(it.number) }
                val selected = m == current && (ph == null || !many || ph.number == chosen)
                MethodRow(m, stringResource(m.title), selected, sub) { vm.pickInSheet(m, ph?.number) }
            }
        }
        // мессенджер дал только чат: объясняем, откуда возьмутся звонок и видео
        val chatOnly = when {
            Method.TG_CHAT in p.methods && Method.TG_CALL !in p.methods -> "Telegram"
            Method.WA_CHAT in p.methods && Method.WA_CALL !in p.methods -> "WhatsApp"
            else -> null
        }
        if (!setMode && chatOnly != null) {
            Row(Modifier.fillMaxWidth().padding(start = Gap.sheet, end = 12.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OzIcon(R.drawable.ic_oz_swap, oz.fg3, size = 18.dp, modifier = Modifier.padding(top = 2.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.sync_hint, chatOnly), style = OzType.small, color = oz.fg3)
                    TextAction(stringResource(R.string.sync_how), oz.accent, { vm.openSyncHelp(chatOnly) }, style = OzType.small, modifier = Modifier.padding(start = 0.dp))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        SheetRow(if (setMode) stringResource(R.string.ask_every_time) else stringResource(R.string.make_default), oz.fg3, vm::sheetFooter)
    }
}

@Composable
private fun MethodRow(m: Method, label: String, selected: Boolean, sub: String? = null, onClick: () -> Unit) {
    val oz = LocalOz.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .press(onClick, shape = RoundedCornerShape(0.dp), role = Role.RadioButton)
            .semantics { this.selected = selected }
            .padding(horizontal = Gap.sheet),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Gap.afterSlot),
    ) {
        OzIcon(m.icon, oz.fg2)
        Column(Modifier.weight(1f).padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = OzType.body, color = oz.fg)
            if (sub != null) Text(sub, style = OzType.small, color = oz.fg3, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
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
        Modifier.fillMaxWidth().heightIn(min = 52.dp).press(onClick, shape = RoundedCornerShape(0.dp)).padding(horizontal = Gap.sheet),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Gap.afterSlot),
    ) {
        if (icon != null) OzIcon(icon, oz.fg3)
        Text(text, style = OzType.body, color = color)
    }
}

/** Долгое нажатие на трубку при двух SIM: две крупные строки с операторами у большого пальца, выбор на один звонок. */
@Composable
fun BoxScope.SimPickSheet(vm: DialerViewModel) {
    val oz = LocalOz.current
    val pick = rememberLast(vm.simPick)
    SheetLayer(vm.simPick != null, pick?.who ?: "", vm::closeSimPick) {
        if (pick == null) return@SheetLayer
        Text(
            pick.who, style = OzType.nameSearch, color = oz.fg, maxLines = 1,
            modifier = Modifier.padding(start = Gap.sheet, end = Gap.sheet, top = 4.dp, bottom = 8.dp),
        )
        val usual = vm.usualSim(pick.number)
        vm.sims.forEach { (id, label) ->
            val carrier = label.substringAfter(" · ", "").ifBlank { label }
            val slot = label.substringBefore(" · ")
            Column(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).press({ vm.callWithSim(id) }, shape = RoundedCornerShape(0.dp), label = label)
                    .padding(horizontal = Gap.sheet, vertical = 10.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(carrier, style = OzType.body.copy(fontSize = 18.sp), color = oz.fg, maxLines = 1)
                Text(
                    if (id == usual) stringResource(R.string.sim_usual, slot) else slot,
                    style = OzType.small, color = oz.fg3, maxLines = 1,
                )
            }
        }
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
            style = if (p != null) OzType.nameSearch else OzType.number,
            color = oz.fg,
            modifier = Modifier.padding(start = Gap.sheet, end = Gap.sheet, top = 4.dp, bottom = 8.dp),
        )
        if (p != null) {
            SheetRow(stringResource(R.string.other_way), oz.fg, { vm.openSheet(p, SheetMode.OTHER) }, R.drawable.ic_oz_swap)
            SheetRow(
                stringResource(if (p.starred) R.string.remove_favorite else R.string.add_favorite), oz.fg,
                { vm.closeMenu(); vm.toggleFavorite(p) }, if (p.starred) R.drawable.ic_oz_star_on else R.drawable.ic_oz_star,
            )
        } else {
            val number = e.number.orEmpty()
            SheetRow(stringResource(R.string.add_to_contacts), oz.fg, { vm.addToContacts(number) }, R.drawable.ic_oz_user_plus)
            SheetRow(stringResource(R.string.send_sms), oz.fg, { vm.closeMenu(); vm.smsTo(number) }, R.drawable.ic_oz_sms)
            SheetRow(stringResource(R.string.block), oz.fg, { vm.askBlock(number) }, R.drawable.ic_oz_ban)
            SheetRow(stringResource(R.string.cid_search_web), oz.fg, { vm.searchWeb(number) }, R.drawable.ic_oz_search)
        }
        val rec = m?.record
        if (rec != null && vm.canClearLog) {
            SheetRow(stringResource(R.string.delete_from_log), oz.fg, { vm.deleteRecord(rec) }, R.drawable.ic_oz_trash)
        }
        if (m?.context == MenuContext.HOME) {
            SheetRow(stringResource(R.string.remove_from_home), oz.fg, { vm.removeFromHome(e.key) }, R.drawable.ic_oz_hide)
            // массовое действие живёт только здесь, а не рядом с «Вернуть»
            SheetRow(stringResource(R.string.home_clear), oz.fg2, { vm.closeMenu(); vm.askClearHome() }, R.drawable.ic_oz_hide)
        }
    }
}

@Composable
fun BoxScope.ConfirmLayer(vm: DialerViewModel) {
    val oz = LocalOz.current
    val c: ConfirmState? = rememberLast(vm.confirm)
    val reduced = LocalReducedMotion.current
    AnimatedVisibility(vm.confirm != null, enter = fadeIn(Motion.enter(Motion.MOVE)), exit = fadeOut(Motion.exit(Motion.FADE))) {
        Box(Modifier.fillMaxSize().background(oz.scrim).clickable(remember { MutableInteractionSource() }, null, onClick = vm::cancelConfirm))
    }
    AnimatedVisibility(
        vm.confirm != null,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = fadeIn(Motion.enter(Motion.MOVE)) + slideInVertically(Motion.move(Motion.MOVE)) { if (reduced) 0 else it / 8 },
        exit = fadeOut(Motion.exit(Motion.FADE)),
    ) {
        if (c == null) return@AnimatedVisibility
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                .fillMaxWidth()
                .lift(OzShape.sheet)
                .background(oz.sheet, OzShape.sheet)
                .clickable(remember { MutableInteractionSource() }, null, onClick = {})
                .semantics { paneTitle = c.title }
                .padding(24.dp),
        ) {
            Text(c.title, style = OzType.title, color = oz.fg)
            Text(c.description, style = OzType.small, color = oz.fg3, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.End) {
                TextAction(stringResource(R.string.cancel), oz.fg, vm::cancelConfirm, style = OzType.small.copy(fontWeight = FontWeight.SemiBold))
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .heightIn(min = 44.dp)
                        .press(vm::runConfirm, shape = RoundedCornerShape(12.dp), pressColor = oz.dangerFill)
                        .background(oz.dangerFill, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(c.verb, style = OzType.small.copy(fontWeight = FontWeight.SemiBold), color = Color.White) }
            }
        }
    }
}

/** Плавающий тост для всего, кроме «Вернуть» (тот живёт в нижней панели главной). */
@Composable
fun BoxScope.FloatToast(toast: ToastState?, bottom: androidx.compose.ui.unit.Dp, end: androidx.compose.ui.unit.Dp = Gap.bar, undoHere: Boolean = false, onUndo: () -> Unit = {}) {
    val oz = LocalOz.current
    // «Вернуть» на главной живёт в нижней панели, на остальных экранах — в этом тосте
    val visible = toast != null && (!toast.undo || undoHere)
    val t = rememberLast(if (visible) toast else null)
    val density = androidx.compose.ui.platform.LocalDensity.current
    AnimatedVisibility(
        visible,
        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = Gap.bar, end = end, bottom = bottom),
        // как тост Ozero Mail: проявляется и поднимается на 16dp
        enter = fadeIn(Motion.enter(Motion.MOVE)) + slideInVertically(Motion.move(Motion.NOTE)) { with(density) { 16.dp.roundToPx() } },
        exit = fadeOut(Motion.exit(Motion.FADE)),
    ) {
        Row(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .lift(OzShape.row).background(oz.toastBg, OzShape.row)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .padding(start = 16.dp, end = if (t?.undo == true || t?.action != null) 6.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(t?.text ?: "", style = OzType.small, color = oz.toastFg, modifier = Modifier.weight(1f).padding(vertical = 14.dp))
            if (t?.undo == true || t?.action != null) TextAction(t.action ?: stringResource(R.string.undo), oz.toastAct, onUndo, style = OzType.small.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}


/** Как включить звонки через мессенджер: он должен сохранить контакты в телефон. */
@Composable
fun BoxScope.SyncHelpSheet(vm: DialerViewModel) {
    val oz = LocalOz.current
    val app = rememberLast(vm.syncHelp)
    val context = androidx.compose.ui.platform.LocalContext.current
    SheetLayer(vm.syncHelp != null, stringResource(R.string.sync_title, app.orEmpty()), vm::closeSyncHelp) {
        if (app == null) return@SheetLayer
        Text(stringResource(R.string.sync_title, app), style = OzType.nameSearch, color = oz.fg, modifier = Modifier.padding(start = Gap.sheet, end = Gap.sheet, top = 4.dp, bottom = 12.dp))
        val steps = if (app == "Telegram") listOf(R.string.sync_tg_1, R.string.sync_tg_2, R.string.sync_tg_3, R.string.sync_back)
        else listOf(R.string.sync_wa_1, R.string.sync_wa_2, R.string.sync_wa_3, R.string.sync_back)
        steps.forEachIndexed { i, s ->
            Row(Modifier.fillMaxWidth().padding(horizontal = Gap.sheet, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("${i + 1}", style = OzType.body, color = oz.fg3)
                Text(stringResource(s), style = OzType.body, color = oz.fg, modifier = Modifier.weight(1f))
            }
        }
        val pkg = if (app == "Telegram") "org.telegram.messenger" else "com.whatsapp"
        val launch = remember(pkg) { context.packageManager.getLaunchIntentForPackage(pkg) }
        if (launch != null) {
            Box(
                Modifier.padding(start = Gap.sheet, end = Gap.sheet, top = 16.dp).fillMaxWidth().height(56.dp)
                    .raisedFill(oz.accentFill, OzShape.main)
                    .press({ vm.closeSyncHelp(); runCatching { context.startActivity(launch) } }, shape = OzShape.main, pressColor = Color.White.copy(alpha = .12f)),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.open_app, app), style = OzType.strong, color = Color.White) }
        }
    }
}
