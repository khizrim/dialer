package com.khizrim.dialer.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.khizrim.dialer.DialerViewModel
import com.khizrim.dialer.MenuContext
import com.khizrim.dialer.R
import com.khizrim.dialer.ui.components.BottomBar
import com.khizrim.dialer.ui.components.GhostIconButton
import com.khizrim.dialer.ui.components.KeypadFab
import com.khizrim.dialer.ui.components.OzIcon
import com.khizrim.dialer.ui.components.TextAction
import com.khizrim.dialer.ui.components.press
import com.khizrim.dialer.ui.theme.LocalOz
import com.khizrim.dialer.ui.theme.LocalReducedMotion
import com.khizrim.dialer.ui.theme.Motion
import com.khizrim.dialer.ui.theme.OzType
import com.khizrim.dialer.ui.theme.Inter
import com.khizrim.dialer.ui.theme.lift
import com.khizrim.dialer.model.Method
import kotlinx.coroutines.launch

/** Главная: стопка имён, ближайший человек внизу. Тап связывает, долгое нажатие открывает меню. */
@Composable
fun HomeScreen(vm: DialerViewModel) {
    val oz = LocalOz.current
    val reduced = LocalReducedMotion.current
    // стопку тянут вверх — контакты с поиском, вниз — журнал. Жест ловим раньше строк (Initial):
    // после порога сдвига поглощаем касание, и нажатие на строку отменяется — свайп не звонит.
    val density = LocalDensity.current
    val threshold = with(density) { 72.dp.toPx() }
    // стопка идёт за пальцем с сопротивлением и пружинит обратно — жест виден, пока его делают
    val pull = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val gestures = Modifier.pointerInput(vm) {
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            var dy = 0f
            var dragging = false
            while (true) {
                val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                if (!ch.pressed) {
                    if (dragging) {
                        ch.consume()
                        if (dy < -threshold) vm.openTextSearch() else if (dy > threshold) vm.openRecents(fromHome = true)
                        scope.launch { pull.animateTo(0f, Motion.enter(Motion.MOVE)) }
                    }
                    break
                }
                dy += ch.position.y - ch.previousPosition.y
                if (!dragging && kotlin.math.abs(dy) > slop) dragging = true
                if (dragging) {
                    ch.consume()
                    if (!reduced) scope.launch { pull.snapTo(dy * 0.35f) }
                }
            }
        }
    }
    val searchLabel = stringResource(R.string.search_by_name)
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth()
                    .then(gestures)
                    .graphicsLayer { translationY = pull.value }
                    .semantics { customActions = listOf(androidx.compose.ui.semantics.CustomAccessibilityAction(searchLabel) { vm.openTextSearch(); true }) }
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.Bottom,
            ) {
                if (vm.homeStack.isEmpty() && vm.favorites.isEmpty()) {
                    item(key = "empty") {
                        Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 24.dp).animateItem()) {
                            Text(stringResource(R.string.home_empty_title), style = OzType.nameHome, color = oz.fg)
                            Text(
                                stringResource(R.string.home_empty_text),
                                style = OzType.body, color = oz.fg2, modifier = Modifier.padding(top = 8.dp).widthIn(max = 320.dp),
                            )
                        }
                    }
                }
                items(vm.homeStack, key = { it.key }) { e ->
                    val p = e.person
                    val method = p?.let { vm.defaultOf(it) } ?: if (p == null) Method.PHONE else null
                    val note = e.missedNote
                    val isLeaving = vm.leaving == e.key
                    val shift = with(LocalDensity.current) { (-24).dp.toPx() }
                    val progress by animateFloatAsState(
                        targetValue = if (isLeaving) 1f else 0f,
                        animationSpec = Motion.exit(Motion.FADE), label = "leave",
                    )
                    Row(
                        Modifier
                            .animateItem(
                                fadeInSpec = Motion.enter(Motion.FADE),
                                // соседи съезжают на место убранной строки, а не перескакивают
                                placementSpec = if (reduced) null else Motion.enter(Motion.MOVE),
                                fadeOutSpec = Motion.exit(Motion.FADE),
                            )
                            .graphicsLayer {
                                alpha = 1f - progress
                                translationX = if (reduced) 0f else shift * progress
                            }
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .press(
                                onClick = { vm.tap(e) },
                                onLongClick = { vm.openMenu(e, MenuContext.HOME) },
                                label = e.title + ": " + stringResource(method?.title ?: R.string.choose_method) + (note?.let { ", $it" } ?: ""),
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                e.title,
                                style = if (p != null && !p.nameIsNumber) OzType.nameHome else OzType.numberHome,
                                color = oz.fg, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            if (note != null) {
                                Text(note, style = OzType.small, color = oz.danger, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                        // значок только когда тап делает не обычный звонок: одинаковая трубка у всех ничего не сообщает
                        if (method != Method.PHONE) OzIcon(method?.icon ?: R.drawable.ic_oz_help, oz.fg3, modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
            // идёт звонок: плашка встроена в разметку, стопка поднимается над ней, ничего не перекрыто
            com.khizrim.dialer.ui.components.CallStrip(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp))
            // избранные: один тихий ряд имён со значком способа, без заголовка и без заливок
            val favs = vm.favorites
            if (favs.isNotEmpty()) {
                androidx.compose.foundation.lazy.LazyRow(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                ) {
                    items(favs, key = { "f" + it.id }) { p ->
                        val m = vm.defaultOf(p)
                        Row(
                            Modifier.heightIn(min = 48.dp)
                                .press({ vm.perform(p, m) }, onLongClick = { vm.openMenu(p) }, shape = com.khizrim.dialer.ui.theme.OzShape.text,
                                    label = stringResource(R.string.favorite_label, p.name, stringResource(m?.title ?: R.string.choose_method)))
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OzIcon(m?.icon ?: R.drawable.ic_oz_help, oz.fg3, 16.dp, modifier = Modifier.padding(end = 6.dp))
                            Text(p.name.substringBefore(' '), style = OzType.body.copy(fontFamily = com.khizrim.dialer.ui.theme.Literata), color = oz.fg, maxLines = 1)
                        }
                    }
                }
            }
            vm.callAlert?.let { CallAlertRow(vm, it) }
            if (vm.showSearchHint && vm.home.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().press(vm::openTextSearch, shape = com.khizrim.dialer.ui.theme.OzShape.text).padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OzIcon(R.drawable.ic_oz_search, oz.fg3, 16.dp)
                    Text(stringResource(R.string.search_hint), style = OzType.small, color = oz.fg3, modifier = Modifier.padding(start = 8.dp))
                }
            }
            if (vm.tidy) {
                TextAction(
                    stringResource(R.string.home_clear), oz.fg3, vm::askClearHome, style = OzType.small,
                    modifier = Modifier.padding(start = 12.dp, top = 4.dp),
                )
            }
            BottomBar {
                val t = vm.toast
                Box(Modifier.weight(1f).padding(end = 16.dp)) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = t?.undo == true,
                        enter = fadeIn(Motion.enter(Motion.MOVE)),
                        exit = fadeOut(Motion.exit(Motion.FADE)),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .lift(com.khizrim.dialer.ui.theme.OzShape.row).background(oz.fg, com.khizrim.dialer.ui.theme.OzShape.row)
                                .padding(start = 16.dp, end = 6.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(t?.text ?: "", style = OzType.small, color = oz.bg, modifier = Modifier.weight(1f))
                            TextAction(stringResource(R.string.undo), oz.toastAct, vm::undo, style = OzType.small.copy(fontWeight = FontWeight.SemiBold))
                        }
                    }
                    if (t?.undo != true) {
                        Row {
                            TextAction(stringResource(R.string.journal), oz.fg2, { vm.openRecents(fromHome = true) }, modifier = Modifier.heightIn(min = 48.dp))
                            TextAction(stringResource(R.string.contacts), oz.fg2, vm::openTextSearch, modifier = Modifier.heightIn(min = 48.dp))
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GhostIconButton(R.drawable.ic_oz_settings, stringResource(R.string.settings), vm::openSettings, tint = oz.fg2)
                    androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
                    KeypadFab { vm.openKeypad() }
                }
            }
        }
    }
}


/** Одна тихая строка, если входящие могут не показаться. Исчезает, когда всё исправлено. */
@Composable
private fun CallAlertRow(vm: DialerViewModel, alert: com.khizrim.dialer.telecom.CallAlert) {
    val oz = LocalOz.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
    ) { vm.reload() }
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(alert.text), style = OzType.small, color = oz.warn, modifier = Modifier.weight(1f))
        TextAction(stringResource(alert.action), oz.accent, {
            val intent = vm.alertIntent()
            vm.alertOpened()
            val ok = intent != null && runCatching { launcher.launch(intent) }.isSuccess
            if (!ok) runCatching { context.startActivity(com.khizrim.dialer.telecom.CallReadiness.fallbackIntent(context)) }
        }, style = OzType.small)
        if (alert.dismissable) {
            GhostIconButton(R.drawable.ic_oz_x, stringResource(R.string.close), vm::dismissAlert, tint = oz.fg3, iconSize = 18.dp)
        }
    }
}
