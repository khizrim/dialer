package com.khizrim.dialer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import com.khizrim.dialer.ui.keypad.keypadPull
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.khizrim.dialer.DialerViewModel
import com.khizrim.dialer.Screen
import com.khizrim.dialer.ui.components.ConfirmLayer
import com.khizrim.dialer.ui.components.FloatToast
import com.khizrim.dialer.ui.components.MethodSheet
import com.khizrim.dialer.ui.components.PersonMenu
import com.khizrim.dialer.ui.components.SimPickSheet
import com.khizrim.dialer.ui.components.SyncHelpSheet
import com.khizrim.dialer.ui.contacts.BlockedScreen
import com.khizrim.dialer.ui.onboarding.Onboarding
import com.khizrim.dialer.ui.home.HomeScreen
import com.khizrim.dialer.ui.keypad.KeypadPanel
import com.khizrim.dialer.ui.keypad.SearchResults
import com.khizrim.dialer.ui.person.PersonScreen
import com.khizrim.dialer.ui.theme.LocalOz
import com.khizrim.dialer.ui.theme.LocalReducedMotion
import com.khizrim.dialer.ui.theme.Motion
import com.khizrim.dialer.ui.theme.lift
import com.khizrim.dialer.ui.theme.rememberReducedMotion
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch


@OptIn(ExperimentalAnimationApi::class)
@Composable
fun DialerApp(vm: DialerViewModel = viewModel()) {
    val oz = LocalOz.current
    val reduced = rememberReducedMotion()
    BackHandler(enabled = vm.canGoBack) { vm.back() }

    CompositionLocalProvider(LocalReducedMotion provides reduced) {
        if (!vm.ready) { Box(Modifier.fillMaxSize().background(oz.bg)); return@CompositionLocalProvider }
        if (vm.needsOnboarding) { Onboarding(vm); return@CompositionLocalProvider }
        Box(Modifier.fillMaxSize().background(oz.bg)) {
            // экраны: непрозрачные слои, новый приходит со своей стороны поверх неподвижного старого (ScreenStack)
            // касание, начатое до смены экрана, на новом (и на уходящем) уже ничего не нажимает
            androidx.compose.runtime.LaunchedEffect(vm.screen, vm.personId, vm.keypadOpen) { com.khizrim.dialer.ui.components.TapGuard.gen++ }
            val stack = rememberScreenStack()
            ScreenStackHost(stack, Route(vm.screen, vm.personId), reduced) { r ->
                when (r.screen) {
                    Screen.HOME -> HomeScreen(vm)
                    Screen.PERSON -> r.personId?.let { PersonScreen(vm, it) }
                    Screen.BLOCKED -> BlockedScreen(vm)
                    Screen.SETTINGS -> com.khizrim.dialer.ui.settings.SettingsScreen(vm)
                    Screen.EDIT -> com.khizrim.dialer.ui.person.EditContactScreen(vm)
                    Screen.SEARCH -> com.khizrim.dialer.ui.home.NameSearchScreen(vm)
                }
            }

            // клавиатура = поиск: такой же непрозрачный слой снизу; идёт за пальцем, когда её тянут вниз
            KeypadLayer(vm, reduced)

            // идёт звонок, а человек в приложении: на главной плашка встроена в разметку (над избранными),
            // на остальных экранах — тихо внизу, над нижней панелью
            val live = com.khizrim.dialer.telecom.CallManager.state
            val hasLive = live != null && live.phase != com.khizrim.dialer.model.CallPhase.ENDED
            if (hasLive && (vm.screen != Screen.HOME || vm.keypadOpen)) {
                com.khizrim.dialer.ui.components.CallStrip(
                    Modifier
                        .align(androidx.compose.ui.Alignment.BottomCenter)
                        .fillMaxWidth()
                        .safeDrawingPadding()
                        .padding(start = 12.dp, end = 12.dp, bottom = floatBottom(vm)),
                )
            }

            PersonMenu(vm)
            SimPickSheet(vm)
            MethodSheet(vm)
            SyncHelpSheet(vm)
            ConfirmLayer(vm)
            // тост встаёт над плашкой звонка, а не на неё
            // тост не перекрывает содержимое: в панели слева от кнопки набора, над клавиатурой или в самом низу
            FloatToast(
                vm.toast,
                bottom = when { vm.keypadOpen -> 380.dp; barScreen(vm) -> 18.dp; else -> 16.dp },
                end = if (barScreen(vm)) 84.dp else 12.dp,
                undoHere = vm.screen != Screen.HOME || vm.keypadOpen,
                onUndo = vm::toastTap,
            )
        }
    }
}

@Composable
private fun KeypadLayer(vm: DialerViewModel, reduced: Boolean) {
    val oz = LocalOz.current
    val shown = remember { androidx.compose.animation.core.Animatable(if (vm.keypadOpen) 1f else 0f, visibilityThreshold = 0.0002f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val velocity = remember { floatArrayOf(0f) }
    val dragging = remember { booleanArrayOf(false) }
    androidx.compose.runtime.LaunchedEffect(vm.keypadOpen) {
        if (dragging[0]) return@LaunchedEffect
        val v = velocity[0]; velocity[0] = 0f
        val spec: androidx.compose.animation.core.AnimationSpec<Float> = when {
            reduced -> Motion.enter(Motion.FADE)
            v != 0f -> Motion.settle()
            else -> Motion.move(Motion.SHEET)
        }
        shown.animateTo(if (vm.keypadOpen) 1f else 0f, spec, v)
    }
    if (!vm.keypadOpen && shown.value == 0f) return
    val panel = remember { floatArrayOf(Float.MAX_VALUE, 0f) } // верх панели в окне и её высота
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val h = with(LocalDensity.current) { maxHeight.toPx() }
        val drag = remember(h) {
            object : com.khizrim.dialer.ui.keypad.PanelDrag {
                var total = 0f
                override fun start() { dragging[0] = true; total = 0f }
                override fun by(dy: Float) {
                    total = (total + dy).coerceAtLeast(0f)
                    if (!reduced) scope.launch { shown.snapTo(1f - total / h) }
                }
                override fun end(velocityPx: Float) {
                    dragging[0] = false
                    // доля считается от высоты панели клавиш: протянул треть панели — закрылась
                    if (Motion.commits(total / panel[1].coerceAtLeast(1f), velocityPx, h)) { velocity[0] = -velocityPx / h; vm.closeKeypad() }
                    else scope.launch { shown.animateTo(1f, Motion.settle(), -velocityPx / h) }
                }
            }
        }
        Column(
            Modifier
                .fillMaxSize()
                .keypadPull(drag) { panel[0] }
                .graphicsLayer { if (reduced) alpha = shown.value else translationY = (1f - shown.value) * h }
                .background(oz.bg)
                .safeDrawingPadding(),
        ) {
            SearchResults(vm, Modifier.weight(1f).fillMaxWidth(), drag)
            // верх панели в покое: вычитаем текущий сдвиг слоя, чтобы граница не ехала вместе с ним
            KeypadPanel(vm, onTop = { y -> val top = y - (if (reduced) 0f else (1f - shown.value) * h); panel[0] = top; panel[1] = h - top })
        }
    }
}

/** Где висит плашка звонка: над клавиатурой или над нижней панелью. */
private fun floatBottom(vm: DialerViewModel): androidx.compose.ui.unit.Dp = when {
    vm.keypadOpen -> 380.dp
    vm.screen == Screen.HOME && vm.favorites.isNotEmpty() -> 156.dp
    else -> 96.dp
}

/** Экраны с нижней панелью и кнопкой набора: тост ложится в панель слева от кнопки и не закрывает содержимое. */
private fun barScreen(vm: DialerViewModel) = !vm.keypadOpen && vm.screen in setOf(Screen.HOME)
