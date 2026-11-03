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
import com.khizrim.dialer.ui.components.SyncHelpSheet
import com.khizrim.dialer.ui.contacts.BlockedScreen
import com.khizrim.dialer.ui.contacts.ContactsScreen
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

private data class Route(val screen: Screen, val personId: Long?)

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
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                // экраны: старый гаснет за 150 мс, новый проявляется с подъёмом 8 px за 200 мс
                // касание, начатое до смены экрана, на новом (и на уходящем) уже ничего не нажимает
                androidx.compose.runtime.LaunchedEffect(vm.screen, vm.personId, vm.keypadOpen) { com.khizrim.dialer.ui.components.TapGuard.gen++ }
                AnimatedContent(
                    targetState = Route(vm.screen, vm.personId),
                    transitionSpec = {
                        // спокойная смена экранов: перекрёстное растворение без паузы и без подпрыгивания
                        // «через фон»: старый уходит за 90 мс, новый проявляется после — без двойной экспозиции
                        val from = initialState.screen; val to = targetState.screen
                        // журнал живёт над главной (его стягивают вниз), контакты — под ней (их вытягивают вверх):
                        // такие переходы — сдвигом по той же оси, что и жест; новый экран въезжает целиком,
                        // старый отъезжает на четверть и гаснет, чтобы тексты не накладывались
                        val dir = when {
                            reduced -> 0
                            from == Screen.HOME && to == Screen.RECENTS -> -1   // журнал сверху
                            from == Screen.RECENTS && to == Screen.HOME -> 1
                            from == Screen.HOME && to == Screen.SEARCH -> 1     // контакты снизу
                            from == Screen.SEARCH && to == Screen.HOME -> -1
                            else -> 0
                        }
                        val move = androidx.compose.animation.core.tween<androidx.compose.ui.unit.IntOffset>(Motion.SHEET, 0, Motion.EnterEasing)
                        when {
                            dir == -1 && to != Screen.HOME -> slideInVertically(move) { -it } togetherWith
                                (slideOutVertically(move) { it / 4 } + fadeOut(androidx.compose.animation.core.tween(Motion.FADE, 0, Motion.ExitEasing)))
                            dir == 1 && to == Screen.HOME -> (slideInVertically(move) { it / 4 } + fadeIn(androidx.compose.animation.core.tween(Motion.MOVE, 60, Motion.EnterEasing))) togetherWith
                                (slideOutVertically(move) { -it } + fadeOut(androidx.compose.animation.core.tween(Motion.MOVE, 0, Motion.ExitEasing)))
                            dir == 1 -> slideInVertically(move) { it } togetherWith
                                (slideOutVertically(move) { -it / 4 } + fadeOut(androidx.compose.animation.core.tween(Motion.FADE, 0, Motion.ExitEasing)))
                            dir == -1 -> (slideInVertically(move) { -it / 4 } + fadeIn(androidx.compose.animation.core.tween(Motion.MOVE, 60, Motion.EnterEasing))) togetherWith
                                (slideOutVertically(move) { it } + fadeOut(androidx.compose.animation.core.tween(Motion.MOVE, 0, Motion.ExitEasing)))
                            else -> fadeIn(androidx.compose.animation.core.tween(210, 90, Motion.EnterEasing)) togetherWith fadeOut(androidx.compose.animation.core.tween(90, 0, Motion.ExitEasing))
                        }
                            .using(androidx.compose.animation.SizeTransform(clip = false) { _, _ -> androidx.compose.animation.core.snap() })
                    },
                    label = "screen",
                    // якорь у низа: списки прижаты к большому пальцу, при смене экрана ничего не прыгает
                    contentAlignment = androidx.compose.ui.Alignment.BottomCenter,
                ) { r ->
                    when (r.screen) {
                        Screen.HOME -> HomeScreen(vm)
                        Screen.CONTACTS -> ContactsScreen(vm)
                        Screen.PERSON -> r.personId?.let { PersonScreen(vm, it) }
                        Screen.BLOCKED -> BlockedScreen(vm)
                        Screen.UNKNOWN -> com.khizrim.dialer.ui.contacts.UnknownScreen(vm)
                        Screen.RECENTS -> com.khizrim.dialer.ui.recents.RecentsScreen(vm)
                        Screen.SETTINGS -> com.khizrim.dialer.ui.settings.SettingsScreen(vm)
                        Screen.EDIT -> com.khizrim.dialer.ui.person.EditContactScreen(vm)
                        Screen.SEARCH -> com.khizrim.dialer.ui.home.NameSearchScreen(vm)
                    }
                }

                // клавиатура = поиск: выезжает снизу за 300 мс, уходит за 200 мс
                AnimatedVisibility(vm.keypadOpen, enter = EnterTransition.None, exit = ExitTransition.None) {
                    Column(Modifier.fillMaxSize()) {
                        SearchResults(
                            vm,
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .animateEnterExit(enter = fadeIn(Motion.enter(Motion.MOVE)), exit = fadeOut(Motion.exit(Motion.MOVE)))
                                .background(oz.bg),
                        )
                        Box(
                            Modifier.animateEnterExit(
                                enter = if (reduced) fadeIn(Motion.enter(Motion.FADE)) else slideInVertically(Motion.enter(Motion.SHEET)) { it },
                                exit = if (reduced) fadeOut(Motion.exit(Motion.FADE)) else slideOutVertically(Motion.exit(Motion.MOVE)) { it },
                            ),
                        ) { KeypadPanel(vm) }
                    }
                }
            }

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
                onUndo = vm::undo,
            )
        }
    }
}

/** Где висит плашка звонка: над клавиатурой или над нижней панелью. */
private fun floatBottom(vm: DialerViewModel): androidx.compose.ui.unit.Dp = when {
    vm.keypadOpen -> 380.dp
    vm.screen == Screen.HOME && (vm.favorites.isNotEmpty() || vm.showSearchHint) -> 156.dp
    else -> 96.dp
}

/** Экраны с нижней панелью и кнопкой набора: тост ложится в панель слева от кнопки и не закрывает содержимое. */
private fun barScreen(vm: DialerViewModel) = !vm.keypadOpen && vm.screen in setOf(Screen.HOME, Screen.RECENTS, Screen.CONTACTS)
