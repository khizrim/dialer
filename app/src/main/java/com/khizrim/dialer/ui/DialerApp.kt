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
                AnimatedContent(
                    targetState = Route(vm.screen, vm.personId),
                    transitionSpec = {
                        // спокойная смена экранов: перекрёстное растворение без паузы и без подпрыгивания
                        // «через фон»: старый уходит за 90 мс, новый проявляется после — без двойной экспозиции
                        (fadeIn(androidx.compose.animation.core.tween(210, 90, Motion.EnterEasing)) togetherWith fadeOut(androidx.compose.animation.core.tween(90, 0, Motion.ExitEasing)))
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
            FloatToast(vm.toast, bottom = floatBottom(vm) + if (hasLive) 60.dp else 0.dp, undoHere = vm.screen != Screen.HOME || vm.keypadOpen, onUndo = vm::undo)
        }
    }
}

/** Где висят тост и плашка звонка: над клавиатурой, над рядом избранных и подсказкой или над нижней панелью. */
private fun floatBottom(vm: DialerViewModel): androidx.compose.ui.unit.Dp = when {
    vm.keypadOpen -> 380.dp
    vm.screen == Screen.HOME && (vm.favorites.isNotEmpty() || vm.showSearchHint) -> 156.dp
    else -> 96.dp
}
