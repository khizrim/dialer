package com.khizrim.dialer.ui.person

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.khizrim.dialer.DialerViewModel
import com.khizrim.dialer.R
import com.khizrim.dialer.SheetMode
import com.khizrim.dialer.ui.components.GhostIconButton
import com.khizrim.dialer.ui.components.OzIcon
import com.khizrim.dialer.ui.components.TextAction
import com.khizrim.dialer.ui.components.press
import com.khizrim.dialer.ui.theme.LocalOz
import com.khizrim.dialer.ui.theme.Motion
import com.khizrim.dialer.ui.theme.OzType

/** Человек: редкая хронология сверху, имя, главная кнопка со способом по умолчанию. */
@Composable
fun PersonScreen(vm: DialerViewModel, id: Int) {
    val oz = LocalOz.current
    val p = vm.person(id)
    val method = vm.defaultOf(p)
    val fav = vm.isFavorite(p)
    // свежий пропущенный уже показан, когда человек на главной; после связи он уходит из хронологии
    val history = if (p.missedNote != null && !vm.hasMissed(p)) p.history.drop(1) else p.history

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            if (history.isNotEmpty()) {
                Column(Modifier.padding(bottom = 40.dp)) {
                    history.forEachIndexed { i, e ->
                        val a = remember { Animatable(0f) }
                        LaunchedEffect(Unit) { a.animateTo(1f, Motion.enter(Motion.MOVE, delay = Motion.FADE + i * 30)) }
                        Row(Modifier.padding(vertical = 6.dp).graphicsLayer { alpha = a.value }) {
                            Text(e.day, style = OzType.small, color = oz.fg3, modifier = Modifier.width(48.dp))
                            Spacer(Modifier.width(16.dp))
                            Text(e.text, style = OzType.small, color = if (e.missed) oz.danger else oz.fg2)
                        }
                    }
                }
            }
            Text(p.name, style = OzType.namePerson, color = oz.fg, modifier = Modifier.padding(bottom = 24.dp).semantics { heading() })
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .press({ vm.perform(p, method) }, shape = RoundedCornerShape(18.dp), pressColor = oz.accentFill)
                    .background(oz.accentFill, RoundedCornerShape(18.dp)),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OzIcon(method?.icon ?: R.drawable.ic_oz_help, Color.White)
                Spacer(Modifier.width(10.dp))
                Text(method?.title ?: "Выбрать способ", style = OzType.strong, color = Color.White)
            }
            Column(Modifier.padding(top = 12.dp).offset(x = (-12).dp)) {
                TextAction("Другой способ", oz.accent, { vm.openSheet(p, SheetMode.OTHER) })
                TextAction("Изменить по умолчанию", oz.fg3, { vm.openSheet(p, SheetMode.DEFAULT) })
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(12.dp, 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            GhostIconButton(R.drawable.ic_oz_back, "Назад", vm::leavePerson)
            GhostIconButton(
                if (fav) R.drawable.ic_oz_star_on else R.drawable.ic_oz_star,
                if (fav) "Убрать из избранного" else "Добавить в избранное",
                { vm.toggleFavorite(p) },
            )
        }
    }
}

