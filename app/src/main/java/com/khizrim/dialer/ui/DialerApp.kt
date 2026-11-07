package com.khizrim.dialer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.khizrim.dialer.DialerViewModel
import com.khizrim.dialer.R
import com.khizrim.dialer.Screen
import com.khizrim.dialer.ui.call.CallCapsule
import com.khizrim.dialer.ui.card.CardSheet
import com.khizrim.dialer.ui.card.ConfirmSheet
import com.khizrim.dialer.ui.card.MenuSheet
import com.khizrim.dialer.ui.card.SimSheet
import com.khizrim.dialer.ui.card.WaySheet
import com.khizrim.dialer.ui.contacts.ContactsScreen
import com.khizrim.dialer.ui.home.Home
import com.khizrim.dialer.ui.keypad.KeypadPanel
import com.khizrim.dialer.ui.kit.TextAction
import com.khizrim.dialer.ui.kit.rememberLast
import com.khizrim.dialer.ui.nav.Kind
import com.khizrim.dialer.ui.nav.LayerHost
import com.khizrim.dialer.ui.nav.LocalNav
import com.khizrim.dialer.ui.nav.Nav
import com.khizrim.dialer.ui.nav.rememberNav
import com.khizrim.dialer.ui.onboarding.Onboarding
import com.khizrim.dialer.ui.settings.BlockedPage
import com.khizrim.dialer.ui.settings.SettingsPage
import com.khizrim.dialer.ui.theme.LocalOz
import com.khizrim.dialer.ui.theme.LocalReducedMotion
import com.khizrim.dialer.ui.theme.Motion
import com.khizrim.dialer.ui.theme.OzType
import com.khizrim.dialer.ui.theme.rememberReducedMotion

/** Куда ведёт слой над главной. */
sealed interface Dest {
    data class Card(val id: Long) : Dest
    data object Keypad : Dest
    data object Contacts : Dest
    data object Settings : Dest
    data object Blocked : Dest
    /** «Другой способ» и «что по нажатию»: лист из модели (vm.sheet). */
    data object Way : Dest
    data object Sim : Dest
    data object Confirm : Dest
    data object Menu : Dest
}

/** Открыть карточку человека, если она ещё не сверху. */
fun Nav.openCard(id: Long) {
    if ((top?.dest as? Dest.Card)?.id == id) return
    open(Dest.Card(id), Kind.SHEET)
}

@Composable
fun DialerApp(vm: DialerViewModel) {
    val oz = LocalOz.current
    val reduced = rememberReducedMotion()
    CompositionLocalProvider(LocalReducedMotion provides reduced) {
        if (!vm.ready) { Box(Modifier.fillMaxSize().background(oz.bg)); return@CompositionLocalProvider }
        if (vm.needsOnboarding) { Onboarding(vm); return@CompositionLocalProvider }
        val nav = rememberNav()
        var dock by remember { mutableIntStateOf(0) }
        // лента главной просвечивает сквозь всё стекло над ней: док, подсказки клавиатуры, капсулу звонка
        val feedGlass = remember { dev.chrisbanes.haze.HazeState() }
        CompositionLocalProvider(LocalNav provides nav, com.khizrim.dialer.ui.theme.LocalGlass provides feedGlass) {
            Bridge(vm, nav)
            Box(Modifier.fillMaxSize().background(oz.bg)) {
                LayerHost(nav, base = { Home(vm) { dock = it } }) { l ->
                    when (val d = l.dest) {
                        is Dest.Card -> CardSheet(vm, l, d.id)
                        Dest.Keypad -> KeypadPanel(vm, l)
                        Dest.Contacts -> ContactsScreen(vm, l)
                        Dest.Settings -> SettingsPage(vm, l)
                        Dest.Blocked -> BlockedPage(vm, l)
                        Dest.Way -> WaySheet(vm, l)
                        Dest.Sim -> SimSheet(vm, l)
                        Dest.Confirm -> ConfirmSheet(vm, l)
                        Dest.Menu -> MenuSheet(vm, l)
                    }
                }
                ToastHost(vm, nav, dock)
                CallCapsule(Modifier.align(Alignment.TopCenter))
            }
        }
    }
}

/**
 * Модель по-прежнему решает, когда открыть клавиатуру, выбор способа, SIM или подтверждение
 * (из уведомлений, tel:-ссылок, после действий). Здесь её флаги становятся слоями, а закрытие
 * слоя жестом — вызовом модели.
 */
@Composable
private fun Bridge(vm: DialerViewModel, nav: Nav) {
    Mirror(nav, vm.keypadOpen, Dest.Keypad, Kind.PANEL) { vm.closeKeypad() }
    Mirror(nav, vm.sheet != null, Dest.Way, Kind.SHEET) { vm.closeSheet() }
    Mirror(nav, vm.simPick != null, Dest.Sim, Kind.SHEET) { vm.closeSimPick() }
    Mirror(nav, vm.menu != null, Dest.Menu, Kind.SHEET) { vm.closeMenu() }
    Mirror(nav, vm.confirm != null, Dest.Confirm, Kind.SHEET) { vm.cancelConfirm() }
    // экраны, которые модель открывает сама (пропущенный из уведомления, карточка номера, заблокированные)
    LaunchedEffect(vm.screen, vm.personId) {
        when (vm.screen) {
            Screen.PERSON -> { vm.personId?.let { nav.openCard(it) }; vm.goHome() }
            Screen.SETTINGS -> { if (nav.find { it == Dest.Settings } == null) nav.open(Dest.Settings, Kind.PUSH); vm.goHome() }
            Screen.BLOCKED -> { if (nav.find { it == Dest.Blocked } == null) nav.open(Dest.Blocked, Kind.PUSH); vm.goHome() }
            Screen.SEARCH -> { if (nav.find { it == Dest.Contacts } == null) nav.open(Dest.Contacts, Kind.RISE); vm.closeTextSearch(); vm.goHome() }
            else -> Unit
        }
    }
}

@Composable
private fun Mirror(nav: Nav, on: Boolean, dest: Dest, kind: Kind, close: () -> Unit) {
    val closeNow by rememberUpdatedState(close)
    LaunchedEffect(on) {
        val l = nav.find { it == dest }
        if (on && l == null) nav.open(dest, kind, onClosed = { closeNow() })
        else if (!on && l != null) { l.onClosed = null; nav.close(l) }
    }
}

/**
 * Тост, как у Mail: тёмная плашка, одно действие. Висит над тем, что сейчас снизу:
 * над доком, над листом, над клавиатурой, и едет вместе с ними.
 */
@Composable
private fun ToastHost(vm: DialerViewModel, nav: Nav, dockPx: Int) {
    val oz = LocalOz.current
    val t = rememberLast(vm.toast)
    val gap = with(LocalDensity.current) { 12.dp.toPx() }
    val rise = with(LocalDensity.current) { 84.dp.toPx() }
    val navPx = WindowInsets.navigationBars.getBottom(LocalDensity.current).toFloat()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = vm.toast != null,
            enter = fadeIn(Motion.enter(Motion.FADE)) + slideInVertically(Motion.enter(Motion.MOVE)) { it / 2 },
            exit = fadeOut(Motion.exit(Motion.FADE)) + slideOutVertically(Motion.exit(Motion.FADE)) { it / 3 },
            modifier = Modifier
                .graphicsLayer {
                    val top = nav.top
                    translationY = -when (top?.kind) {
                        null -> dockPx + gap
                        Kind.SHEET, Kind.PANEL -> {
                            val e = top.extent.takeUnless { it.isNaN() } ?: 0f
                            maxOf(dockPx.toFloat() * (1f - top.p), e * top.p) + gap
                        }
                        Kind.RISE -> navPx + rise
                        Kind.PUSH -> navPx + gap
                    }
                }
                .padding(horizontal = 16.dp),
        ) {
            val shown = t ?: return@AnimatedVisibility
            Row(
                Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .background(oz.toastBg, RoundedCornerShape(18.dp))
                    .padding(start = 18.dp, end = 4.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(shown.text, style = OzType.small, color = oz.toastFg, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
                val act = shown.action ?: if (shown.undo) stringResource(R.string.undo) else null
                if (act != null) TextAction(act, oz.toastAct, vm::toastTap, style = OzType.small)
            }
        }
    }
}
