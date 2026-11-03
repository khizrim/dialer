package com.khizrim.dialer.ui.person

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import com.khizrim.dialer.ui.theme.raisedFill
import com.khizrim.dialer.ui.theme.well
import com.khizrim.dialer.ui.theme.lift
import com.khizrim.dialer.ui.theme.OzShape

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.khizrim.dialer.DialerViewModel
import com.khizrim.dialer.R
import com.khizrim.dialer.model.Method
import androidx.compose.ui.unit.sp
import com.khizrim.dialer.SheetMode
import com.khizrim.dialer.ui.components.GhostIconButton
import com.khizrim.dialer.ui.components.OzIcon
import com.khizrim.dialer.ui.components.TextAction
import com.khizrim.dialer.ui.components.press
import com.khizrim.dialer.ui.components.swipeAway
import com.khizrim.dialer.ui.components.dismissOnDrag
import com.khizrim.dialer.ui.theme.LocalOz
import com.khizrim.dialer.ui.theme.Motion
import com.khizrim.dialer.ui.theme.OzType

/** Человек: редкая хронология сверху, имя, главная кнопка со способом по умолчанию. */
@Composable
fun PersonScreen(vm: DialerViewModel, id: Long) {
    val oz = LocalOz.current
    val p = vm.person(id) ?: return
    val method = vm.defaultOf(p)
    val fav = p.starred
    val history = remember(p, vm.ready, vm.recentCalls) { vm.history(p) }

    // карточку тянут вниз — весь экран идёт за пальцем 1:1 и открывает тот, что под ним; отпустили — доезжает или возвращается
    Column(
        Modifier.fillMaxSize().dismissOnDrag { vm.back() },
    ) {
        // вёрстка шагом 8: у всех блоков общий левый и правый край (поля 20dp), всё прижато к низу
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            if (history.isNotEmpty()) {
                // все звонки с человеком — тихая таблица: день, что было, длительность; смахнуть влево — удалить с «Вернуть»
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState(), reverseScrolling = true).padding(bottom = 32.dp)) {
                    val canDelete = vm.canClearLog
                    val deleteWord = stringResource(R.string.delete)
                    history.forEachIndexed { i, e ->
                        androidx.compose.runtime.key(e.record ?: e) {
                            Column(Modifier.fillMaxWidth().swipeAway(enabled = canDelete && e.record != null, hint = deleteWord) { e.record?.let(vm::swipeDeleteRecord) }) {
                                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(oz.line))
                                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(e.day, style = OzType.time.copy(fontSize = 13.sp), color = oz.fg3, maxLines = 1, modifier = Modifier.width(52.dp))
                                    Text(e.text, style = OzType.small.copy(fontSize = 13.sp), color = if (e.missed) oz.danger else oz.fg3, maxLines = 1, modifier = Modifier.weight(1f))
                                    if (e.duration.isNotEmpty()) Text(e.duration, style = OzType.time.copy(fontSize = 13.sp), color = oz.fg3, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
            // фото только если оно есть, без кружков с буквами
            p.photo?.let { ContactPhoto(it, Modifier.padding(bottom = 16.dp)) }
            // имя и справа звезда избранного по верху первой строки: залита, когда человек в избранном
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(
                    p.shownName, style = if (p.nameIsNumber) OzType.numberHome else OzType.namePerson.copy(fontSize = 30.sp, lineHeight = 36.sp), color = oz.fg,
                    modifier = Modifier.weight(1f).padding(top = 4.dp).semantics { heading() },
                )
                Box(Modifier.offset(x = 10.dp)) {
                    GhostIconButton(
                        if (fav) R.drawable.ic_oz_star_on else R.drawable.ic_oz_star,
                        stringResource(if (fav) R.string.remove_favorite else R.string.add_favorite),
                        { vm.toggleFavorite(p) }, tint = if (fav) oz.accent else oz.fg3, size = 44.dp, iconSize = 22.dp,
                    )
                }
            }
            // номера: номер слева, тип справа; нажатие звонит на этот номер, долгое нажатие копирует
            if (!p.nameIsNumber || p.phones.size > 1) {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    p.phones.take(3).forEach { ph ->
                        Row(
                            Modifier.fillMaxWidth()
                                .press({ vm.callNumberOf(p, ph.number) }, onLongClick = { vm.copyNumber(ph.number) }, shape = OzShape.text,
                                    label = stringResource(R.string.call_to, com.khizrim.dialer.data.PhoneFormat.pretty(ph.number)))
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                com.khizrim.dialer.data.PhoneFormat.pretty(ph.number),
                                style = OzType.body.copy(fontSize = 15.sp, fontFeatureSettings = "cv11, tnum"), color = oz.fg2, maxLines = 1, modifier = Modifier.weight(1f),
                            )
                            if (ph.label.isNotBlank()) Text(ph.label, style = OzType.small.copy(fontSize = 12.sp), color = oz.fg3, maxLines = 1)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            // главная кнопка из двух частей: слева — связаться, справа за разделителем — выбрать, что делать по нажатию
            Row(
                Modifier.fillMaxWidth().height(56.dp).raisedFill(oz.accentFill, OzShape.main),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f).fillMaxHeight()
                        .press({ vm.perform(p, method) }, shape = OzShape.main, pressColor = Color.White.copy(alpha = .12f)),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OzIcon(method?.icon ?: R.drawable.ic_oz_swap, Color.White)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(method?.title ?: R.string.choose_method), style = OzType.strong, color = Color.White, maxLines = 1)
                }
                Box(Modifier.width(1.dp).height(28.dp).background(Color.White.copy(alpha = .28f)))
                Box(
                    Modifier.width(52.dp).fillMaxHeight()
                        .press({ vm.openSheet(p, SheetMode.DEFAULT) }, shape = OzShape.main, pressColor = Color.White.copy(alpha = .12f), label = stringResource(R.string.on_tap_title)),
                    contentAlignment = Alignment.Center,
                ) { OzIcon(R.drawable.ic_oz_chevron_down, Color.White, 20.dp) }
            }
            // под кнопкой одна тихая строка: куда звоним и, при двух SIM, с какой (нажатие переключает)
            val target = vm.numberFor(p)
            val sim = vm.personSim(target)
            if (target != null || sim != null) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        target?.let { stringResource(R.string.card_to_number, com.khizrim.dialer.data.PhoneFormat.pretty(it)) }.orEmpty(),
                        style = OzType.small.copy(fontSize = 12.sp), color = oz.fg3, maxLines = 1, modifier = Modifier.weight(1f),
                    )
                    if (sim != null) Text(
                        vm.simLabel(sim).orEmpty(), style = OzType.small.copy(fontSize = 12.sp), color = oz.fg3, maxLines = 1,
                        modifier = Modifier.press({ target?.let(vm::cyclePersonSim) }, shape = OzShape.text, label = stringResource(R.string.sim_switch)),
                    )
                }
            }
            // остальные способы — между двумя линиями, от левого края до правого: значок над подписью, нажатие связывает сразу
            val others = p.methods.filter { it != method }
            if (others.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(oz.line))
                val short = others.map { stringResource(it.short) }
                val labels = others.mapIndexed { i, m -> if (short.count { it == short[i] } > 1 && m.brand != null && m != Method.SMS) m.brand else short[i] }
                val few = others.size <= 4
                Row(
                    Modifier.fillMaxWidth().then(if (few) Modifier else Modifier.horizontalScroll(rememberScrollState())),
                    horizontalArrangement = if (few && others.size > 1) Arrangement.SpaceBetween else Arrangement.spacedBy(24.dp),
                ) {
                    others.forEachIndexed { i, m ->
                        Column(
                            Modifier.press({ vm.performOther(p, m) }, shape = OzShape.text, label = stringResource(m.title)).padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            OzIcon(m.icon, oz.fg2, 22.dp)
                            Spacer(Modifier.height(4.dp))
                            Text(labels[i], style = OzType.small.copy(fontSize = 12.sp), color = oz.fg2, maxLines = 1)
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(oz.line))
            }
            // «Изменить контакт» — тихим словом ровно по левому краю, открывает системный редактор
            Text(
                stringResource(R.string.edit_contact), style = OzType.small, color = oz.fg3,
                modifier = Modifier.padding(top = 8.dp).press({ vm.editContact(p) }, shape = OzShape.text).padding(vertical = 8.dp),
            )
        }
        // пока виден тост, карточка приподнимается, чтобы он ничего не закрыл
        val room by androidx.compose.animation.core.animateDpAsState(if (vm.toast != null) 76.dp else 16.dp, Motion.enter(Motion.MOVE), label = "toast")
        Spacer(Modifier.height(room))
    }
}


// value присваивается; ложное срабатывание Lint на Kotlin 2 (ProduceStateDoesNotAssignValue)
@android.annotation.SuppressLint("ProduceStateDoesNotAssignValue")
@Composable
private fun ContactPhoto(uri: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            }.getOrNull()
        }
    }
    // место под фото занято сразу, а само фото проявляется, когда загрузится: имя под ним не подпрыгивает
    val alpha by androidx.compose.animation.core.animateFloatAsState(if (bitmap != null) 1f else 0f, Motion.enter(Motion.MOVE), label = "photo")
    Box(modifier.size(72.dp).graphicsLayer { this.alpha = alpha }) {
        bitmap?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(72.dp).clip(CircleShape)) }
    }
}
