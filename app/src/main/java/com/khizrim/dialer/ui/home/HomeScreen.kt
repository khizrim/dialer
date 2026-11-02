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

/** Главная: стопка имён, ближайший человек внизу. Тап связывает, долгое нажатие открывает меню. */
@Composable
fun HomeScreen(vm: DialerViewModel) {
    val oz = LocalOz.current
    val reduced = LocalReducedMotion.current
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.Bottom,
            ) {
                if (vm.home.isEmpty()) {
                    item(key = "empty") {
                        Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 24.dp).animateItem()) {
                            Text("Пусто и тихо", style = OzType.nameHome, color = oz.fg)
                            Text(
                                "Здесь появятся люди, с которыми вы связываетесь. Наберите номер или откройте все контакты.",
                                style = OzType.body, color = oz.fg2, modifier = Modifier.padding(top = 8.dp).widthIn(max = 320.dp),
                            )
                        }
                    }
                }
                items(vm.home, key = { it }) { id ->
                    val p = vm.person(id)
                    val method = vm.defaultOf(p)
                    val note = vm.missedNote(p)
                    val isLeaving = vm.leaving == id
                    val shift = with(LocalDensity.current) { (-24).dp.toPx() }
                    val progress by animateFloatAsState(
                        targetValue = if (isLeaving) 1f else 0f,
                        animationSpec = Motion.exit(Motion.FADE), label = "leave",
                    )
                    Row(
                        Modifier
                            .animateItem(
                                fadeInSpec = Motion.enter(Motion.FADE),
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
                                onClick = { vm.perform(p, method) },
                                onLongClick = { vm.openMenu(p, MenuContext.HOME) },
                                label = p.name + ": " + (method?.title ?: "Выбрать способ") + (note?.let { ", $it" } ?: ""),
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = OzType.nameHome, color = oz.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (note != null) {
                                Text(note, style = OzType.small, color = oz.danger, modifier = Modifier.padding(top = 2.dp))
                            }
                        }
                        OzIcon(method?.icon ?: R.drawable.ic_oz_help, oz.fg3, modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
            if (vm.tidy) {
                TextAction(
                    "Очистить главную", oz.fg3, vm::askClearHome, style = OzType.small,
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
                                .background(oz.fg, RoundedCornerShape(14.dp))
                                .padding(start = 16.dp, end = 6.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(t?.text ?: "", style = OzType.small, color = oz.bg, modifier = Modifier.weight(1f))
                            TextAction("Вернуть", oz.toastAct, vm::undo, style = OzType.small.copy(fontWeight = FontWeight.SemiBold))
                        }
                    }
                    if (t?.undo != true) {
                        TextAction("Все контакты", oz.fg2, vm::openContacts, modifier = Modifier.heightIn(min = 48.dp))
                    }
                }
                KeypadFab(vm::openKeypad)
            }
        }
        // демо входящих звонков
        Box(Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 16.dp)) {
            GhostIconButton(R.drawable.ic_oz_incoming, "Показать демо входящего звонка", vm::toggleDemo, tint = oz.fg3, size = 44.dp, iconSize = 22.dp)
        }
    }
}

