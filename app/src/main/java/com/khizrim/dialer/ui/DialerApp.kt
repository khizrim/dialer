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
    val rise = with(LocalDensity.current) { 8.dp.roundToPx() }
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

            // идёт звонок, а человек в приложении (например, нажал «Добавить»): тихая плашка внизу, под пальцем
            val live = com.khizrim.dialer.telecom.CallManager.state
            if (live != null && live.phase != com.khizrim.dialer.model.CallPhase.ENDED) {
                val context = androidx.compose.ui.platform.LocalContext.current
                Box(
                    Modifier
                        .align(androidx.compose.ui.Alignment.BottomCenter)
                        .fillMaxWidth()
                        .safeDrawingPadding()
                        .padding(start = 12.dp, end = 12.dp, bottom = if (vm.keypadOpen) 380.dp else 96.dp),
                ) {
                    androidx.compose.material3.Text(
                        androidx.compose.ui.res.stringResource(com.khizrim.dialer.R.string.back_to_call, live.name ?: com.khizrim.dialer.data.PhoneFormat.pretty(live.number)),
                        style = com.khizrim.dialer.ui.theme.OzType.small,
                        color = oz.accent,
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .lift(com.khizrim.dialer.ui.theme.OzShape.row)
                        .background(oz.sheet, com.khizrim.dialer.ui.theme.OzShape.row)
                            .clickable { context.startActivity(com.khizrim.dialer.telecom.CallActivity.intent(context)) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                }
            }

            PersonMenu(vm)
            MethodSheet(vm)
            SyncHelpSheet(vm)
            ConfirmLayer(vm)
            FloatToast(vm.toast, bottom = if (vm.keypadOpen) 380.dp else 96.dp)
        }
    }
}
