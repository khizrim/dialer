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
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            if (history.isNotEmpty()) {
                // все звонки с человеком — здесь, с прокруткой: отдельного журнала больше нет
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState(), reverseScrolling = true).padding(bottom = 40.dp)) {
                    val canDelete = vm.canClearLog
                    val deleteWord = stringResource(R.string.delete)
                    history.forEach { e ->
                        // звонок смахивают влево — он удаляется из журнала, пока виден тост, его можно вернуть
                        androidx.compose.runtime.key(e.record ?: e) {
                            Row(Modifier.fillMaxWidth().swipeAway(enabled = canDelete && e.record != null, hint = deleteWord) { e.record?.let(vm::swipeDeleteRecord) }.padding(vertical = 6.dp)) {
                                Text(e.day, style = OzType.time, color = oz.fg3, modifier = Modifier.width(56.dp))
                                Spacer(Modifier.width(16.dp))
                                Text(e.text, style = OzType.small, color = if (e.missed) oz.danger else oz.fg2)
                            }
                        }
                    }
                }
            }
            // фото только если оно есть, без кружков с буквами
            p.photo?.let { ContactPhoto(it, Modifier.padding(bottom = 16.dp)) }
            Text(p.shownName, style = if (p.nameIsNumber) OzType.numberHome else OzType.namePerson, color = oz.fg, modifier = Modifier.padding(bottom = 12.dp).semantics { heading() })
            // номера под именем: тип и номер серым, чтобы было видно, куда звоним
            // тип номера нужен, только чтобы различать несколько номеров
            if (!p.nameIsNumber || p.phones.size > 1) {
                val many = p.phones.size > 1
                // номеров несколько: тот, на который звонит кнопка, помечен «основной»; номера темнее — по ним нажимают
                val main = if (many) vm.numberFor(p) else null
                // тап по номеру — звонок именно на него, долгое нажатие копирует
                p.phones.take(3).forEach { ph ->
                    Text(
                        (if (many) ph.label.replaceFirstChar { it.uppercase() } + "  " else "") + com.khizrim.dialer.data.PhoneFormat.pretty(ph.number) +
                            (if (main != null && com.khizrim.dialer.data.PhoneFormat.digits(main) == com.khizrim.dialer.data.PhoneFormat.digits(ph.number)) "  · " + stringResource(R.string.main_number) else ""),
                        style = OzType.body.copy(fontFeatureSettings = "cv11, tnum"), color = if (many) oz.fg2 else oz.fg3, maxLines = 1,
                        modifier = Modifier
                            .offset(x = (-8).dp)
                            .press({ vm.callNumberOf(p, ph.number) }, onLongClick = { vm.copyNumber(ph.number) }, shape = OzShape.text,
                                label = stringResource(R.string.call_to, com.khizrim.dialer.data.PhoneFormat.pretty(ph.number)))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
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
            // две SIM и для человека запомнена не основная: тихая подпись под кнопкой, нажатие переключает или забывает
            val simNumber = vm.numberFor(p)
            vm.personSim(simNumber)?.let { id ->
                Text(
                    vm.simLabel(id).orEmpty(), style = OzType.small, color = oz.fg3, maxLines = 1,
                    modifier = Modifier.padding(top = 4.dp).offset(x = (-12).dp)
                        .press({ simNumber?.let(vm::cyclePersonSim) }, shape = OzShape.text, label = stringResource(R.string.sim_switch))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
            // остальные способы — строкой: значок и подпись, нажатие связывает сразу
            val others = p.methods.filter { it != method }
            if (others.isNotEmpty()) {
                Row(
                    Modifier.padding(top = 8.dp).offset(x = (-12).dp).horizontalScroll(rememberScrollState()),
                ) {
                    others.forEach { m ->
                        Row(
                            Modifier.heightIn(min = 48.dp).press({ vm.performOther(p, m) }, shape = OzShape.text).padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OzIcon(m.icon, oz.fg2, 18.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(m.title), style = OzType.small, color = oz.fg2, maxLines = 1)
                        }
                    }
                }
            }
            Row(Modifier.padding(top = 4.dp).offset(x = (-12).dp)) {
                TextAction(stringResource(R.string.edit_contact), oz.fg3, { vm.editContact(p) })
                TextAction(
                    stringResource(if (fav) R.string.remove_favorite else R.string.add_favorite),
                    if (fav) oz.accent else oz.fg3, { vm.toggleFavorite(p) },
                )
            }
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
