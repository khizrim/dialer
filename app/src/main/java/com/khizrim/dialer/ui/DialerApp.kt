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
import com.khizrim.dialer.ui.call.CallScreen
import com.khizrim.dialer.ui.components.ConfirmLayer
import com.khizrim.dialer.ui.components.DemoMenu
import com.khizrim.dialer.ui.components.FloatToast
import com.khizrim.dialer.ui.components.MethodSheet
import com.khizrim.dialer.ui.components.PersonMenu
import com.khizrim.dialer.ui.components.rememberLast
import com.khizrim.dialer.ui.contacts.ContactsScreen
import com.khizrim.dialer.ui.home.HomeScreen
import com.khizrim.dialer.ui.keypad.KeypadPanel
import com.khizrim.dialer.ui.keypad.SearchResults
import com.khizrim.dialer.ui.person.PersonScreen
import com.khizrim.dialer.ui.theme.LocalOz
import com.khizrim.dialer.ui.theme.LocalReducedMotion
import com.khizrim.dialer.ui.theme.Motion
import com.khizrim.dialer.ui.theme.rememberReducedMotion

private data class Route(val screen: Screen, val personId: Int?)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun DialerApp(vm: DialerViewModel = viewModel()) {
    val oz = LocalOz.current
    val reduced = rememberReducedMotion()
    val rise = with(LocalDensity.current) { 8.dp.roundToPx() }
    BackHandler(enabled = vm.canGoBack) { vm.back() }

    CompositionLocalProvider(LocalReducedMotion provides reduced) {
        Box(Modifier.fillMaxSize().background(oz.bg)) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                // экраны: старый гаснет за 150 мс, новый проявляется с подъёмом 8 px за 200 мс
                AnimatedContent(
                    targetState = Route(vm.screen, vm.personId),
                    transitionSpec = {
                        val enter = if (reduced) fadeIn(Motion.enter(Motion.FADE)) else
                            fadeIn(Motion.enter(Motion.MOVE, delay = Motion.FADE)) +
                                slideInVertically(Motion.enter(Motion.MOVE, delay = Motion.FADE)) { rise }
                        enter togetherWith fadeOut(Motion.exit(Motion.FADE))
                    },
                    label = "screen",
                ) { r ->
                    when (r.screen) {
                        Screen.HOME -> HomeScreen(vm)
                        Screen.CONTACTS -> ContactsScreen(vm)
                        Screen.PERSON -> r.personId?.let { PersonScreen(vm, it) }
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
                                .animateEnterExit(enter = fadeIn(Motion.enter(Motion.MOVE)), exit = fadeOut(Motion.exit(Motion.FADE)))
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

            // звонок поверх всего: проявляется за 200 мс, гаснет за 150 мс
            val call = rememberLast(vm.call)
            AnimatedVisibility(
                vm.call != null,
                enter = fadeIn(Motion.enter(Motion.MOVE)),
                exit = fadeOut(Motion.exit(Motion.FADE)),
            ) {
                call?.let {
                    Box(Modifier.fillMaxSize().background(oz.bg).safeDrawingPadding()) { CallScreen(vm, it) }
                }
            }

            DemoMenu(vm)
            PersonMenu(vm)
            MethodSheet(vm)
            ConfirmLayer(vm)
            FloatToast(vm.toast, bottom = if (vm.keypadOpen) 380.dp else 96.dp)
        }
    }
}
