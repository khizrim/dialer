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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.res.pluralStringResource
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
// value присваивается; ложное срабатывание Lint на Kotlin 2 (ProduceStateDoesNotAssignValue)
@android.annotation.SuppressLint("ProduceStateDoesNotAssignValue")
@Composable
fun PersonScreen(vm: DialerViewModel, id: Long) {
    val oz = LocalOz.current
    val p = vm.person(id) ?: return
    val method = vm.defaultOf(p)
    val fav = p.starred
    val unknown = vm.isNumberCard(p)
    val history = remember(p, vm.ready, vm.recentCalls) { vm.history(p) }

    var historyOpen by remember(id) { mutableStateOf(false) }
    // карточка — лист по содержимому поверх ленты: пустого верха нет, за листом видно, откуда пришли.
    // Нажатие мимо листа закрывает его, лист тянут вниз — он идёт за пальцем 1:1
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { vm.back() } })
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(.92f).wrapContentHeight(Alignment.Bottom)
                .dismissOnDrag { vm.back() }
                .background(oz.bg, OzShape.sheetTop)
                .pointerInput(Unit) { detectTapGestures { } }
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            Box(Modifier.padding(top = 8.dp, bottom = 12.dp).align(Alignment.CenterHorizontally).width(36.dp).height(4.dp).background(oz.handle, RoundedCornerShape(2.dp)))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                // фото только если оно есть, без кружков с буквами
                p.photo?.let { ContactPhoto(it, Modifier.padding(bottom = 16.dp)) }
                // имя и справа звезда избранного: залита, когда человек в избранном
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Text(
                        p.shownName, style = if (p.nameIsNumber) OzType.namePerson.copy(fontFeatureSettings = "tnum") else OzType.namePerson, color = oz.fg,
                        modifier = Modifier.weight(1f).padding(top = 4.dp).semantics { heading() },
                    )
                    if (!unknown) Box(Modifier.offset(x = 10.dp)) {
                        GhostIconButton(
                            if (fav) R.drawable.ic_oz_star_on else R.drawable.ic_oz_star,
                            stringResource(if (fav) R.string.remove_favorite else R.string.add_favorite),
                            { vm.toggleFavorite(p) }, tint = if (fav) oz.accent else oz.fg3, size = 44.dp, iconSize = 22.dp,
                        )
                    }
                }
                // у незнакомого номера под ним метка АОН, если она есть
                if (unknown) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val cid by produceState<String?>(null, p.mainNumber) {
                        value = withContext(Dispatchers.IO) { runCatching { com.khizrim.dialer.callerid.CallerId.labelFor(context, p.mainNumber) }.getOrNull() }
                    }
                    cid?.let { Text(it, style = OzType.small, color = oz.fg3, modifier = Modifier.padding(top = 2.dp)) }
                }
                // номера: номер слева, тип справа; нажатие звонит на этот номер, долгое нажатие копирует
                if (!p.nameIsNumber || p.phones.size > 1) {
                    Column(Modifier.padding(top = 2.dp)) {
                        p.phones.take(3).forEach { ph ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .press({ vm.callNumberOf(p, ph.number) }, onLongClick = { vm.copyNumber(ph.number) }, shape = OzShape.text,
                                        label = stringResource(R.string.call_to, com.khizrim.dialer.data.PhoneFormat.pretty(ph.number)))
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(com.khizrim.dialer.data.PhoneFormat.pretty(ph.number), style = OzType.time, color = oz.fg3, maxLines = 1, modifier = Modifier.weight(1f))
                                if (ph.label.isNotBlank() && p.phones.size > 1) Text(ph.label, style = OzType.meta, color = oz.fg3, maxLines = 1)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                // одна главная кнопка: способ по умолчанию. Долгое нажатие при двух SIM — выбор SIM на этот звонок
                Row(
                    Modifier.fillMaxWidth().height(52.dp).raisedFill(oz.accentFill, OzShape.main)
                        .press({ vm.perform(p, method) }, onLongClick = { vm.askSim(p) }, shape = OzShape.main, pressColor = Color.White.copy(alpha = .12f)),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OzIcon(method?.icon ?: R.drawable.ic_oz_swap, Color.White, 20.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(method?.title ?: R.string.choose_method), style = OzType.strong, color = Color.White, maxLines = 1)
                }
                // под кнопкой одна тихая строка: куда звоним (если номеров несколько) и, при двух SIM, с какой
                val target = vm.numberFor(p)
                val shownTarget = target?.takeIf { p.phones.size > 1 }
                val sim = vm.personSim(target)
                if (shownTarget != null || sim != null) {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            shownTarget?.let { stringResource(R.string.card_to_number, com.khizrim.dialer.data.PhoneFormat.pretty(it)) }.orEmpty(),
                            style = OzType.meta, color = oz.fg3, maxLines = 1, modifier = Modifier.weight(1f),
                        )
                        if (sim != null) Text(
                            vm.simLabel(sim).orEmpty(), style = OzType.meta, color = oz.fg3, maxLines = 1,
                            modifier = Modifier.press({ target?.let(vm::cyclePersonSim) }, shape = OzShape.text, label = stringResource(R.string.sim_switch)),
                        )
                    }
                }
                // другие способы: значок над подписью, нажатие связывает сразу
                val others = p.methods.filter { it != method }
                if (others.isNotEmpty()) {
                    val short = others.map { stringResource(it.short) }
                    val labels = others.mapIndexed { i, m -> if (short.count { it == short[i] } > 1 && m.brand != null && m != Method.SMS) m.brand else short[i] }
                    val few = others.size <= 4
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp).then(if (few) Modifier else Modifier.horizontalScroll(rememberScrollState())),
                        horizontalArrangement = if (few && others.size > 1) Arrangement.SpaceBetween else Arrangement.spacedBy(24.dp),
                    ) {
                        others.forEachIndexed { i, m ->
                            Column(
                                Modifier.press({ vm.performOther(p, m) }, shape = OzShape.text, label = stringResource(m.title)).padding(horizontal = 4.dp, vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                OzIcon(m.icon, oz.fg2, 22.dp)
                                Spacer(Modifier.height(4.dp))
                                Text(labels[i], style = OzType.meta, color = oz.fg2, maxLines = 1)
                            }
                        }
                    }
                }
                // ниже — тихие строки во всю ширину между волосяными линиями
                if (!unknown) {
                    CardRow(
                        stringResource(R.string.card_usual, stringResource(method?.title ?: R.string.choose_method)),
                        action = stringResource(R.string.card_change),
                    ) { vm.openSheet(p, SheetMode.DEFAULT) }
                }
                if (history.isNotEmpty()) {
                    val last = history.last()
                    CardRow(
                        stringResource(R.string.card_history),
                        value = pluralStringResource(R.plurals.card_calls, history.size, history.size) + " · " + last.day,
                        chevron = if (historyOpen) 90f else 0f,
                    ) { historyOpen = !historyOpen }
                    // раскрытая история: свежие сверху; смахнуть влево — удалить с «Вернуть»
                    androidx.compose.animation.AnimatedVisibility(historyOpen) {
                        Column(Modifier.padding(bottom = 8.dp)) {
                            val canDelete = vm.canClearLog
                            val deleteWord = stringResource(R.string.delete)
                            history.asReversed().take(50).forEach { e ->
                                androidx.compose.runtime.key(e.record ?: e) {
                                    Row(
                                        Modifier.fillMaxWidth().swipeAway(enabled = canDelete && e.record != null, hint = deleteWord) { e.record?.let(vm::swipeDeleteRecord) }.padding(vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(e.day, style = OzType.meta, color = oz.fg3, maxLines = 1, modifier = Modifier.width(64.dp))
                                        Text(e.text, style = OzType.meta, color = if (e.missed) oz.danger else oz.fg2, maxLines = 1, modifier = Modifier.weight(1f))
                                        if (e.duration.isNotEmpty()) Text(e.duration, style = OzType.meta, color = oz.fg3, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
                // «Изменить контакт» открывает системный редактор. У незнакомого номера — добавить, заблокировать, найти
                if (unknown) {
                    val n = p.mainNumber.orEmpty()
                    CardRow(stringResource(R.string.add_to_contacts), chevron = 0f) { vm.addToContacts(n) }
                    CardRow(stringResource(R.string.block), chevron = 0f) { vm.askBlock(n) }
                    CardRow(stringResource(R.string.cid_search_web), chevron = 0f) { vm.searchWeb(n) }
                } else {
                    CardRow(stringResource(R.string.edit_contact), chevron = 0f) { vm.editContact(p) }
                }
            }
            // пока виден тост, лист приподнимается, чтобы он ничего не закрыл
            val room by androidx.compose.animation.core.animateDpAsState(if (vm.toast != null) 72.dp else 8.dp, Motion.enter(Motion.MOVE), label = "toast")
            Spacer(Modifier.height(room))
        }
    }
}

/** Тихая строка листа во всю ширину: слева что это, справа значение, слово-действие или стрелка. */
@Composable
private fun CardRow(title: String, value: String? = null, action: String? = null, chevron: Float? = null, onClick: () -> Unit) {
    val oz = LocalOz.current
    Box(Modifier.fillMaxWidth().height(1.dp).background(oz.line))
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).press(onClick, shape = OzShape.text),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = OzType.body.copy(fontSize = 15.sp), color = oz.fg, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (value != null) Text(value, style = OzType.meta, color = oz.fg3, maxLines = 1, modifier = Modifier.padding(start = 12.dp))
        if (action != null) Text(action, style = OzType.small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), color = oz.accent, modifier = Modifier.padding(start = 12.dp))
        if (chevron != null) OzIcon(R.drawable.ic_oz_chevron, oz.fg3, 16.dp, Modifier.padding(start = 8.dp).graphicsLayer { rotationZ = chevron })
    }
}


// value присваивается; ложное срабатывание Lint на Kotlin 2 (ProduceStateDoesNotAssignValue)
@android.annotation.SuppressLint("ProduceStateDoesNotAssignValue")
@Composable
fun PersonScreen(vm: DialerViewModel, id: Long) {
    val oz = LocalOz.current
    val p = vm.person(id) ?: return
    val method = vm.defaultOf(p)
    val fav = p.starred
    val unknown = vm.isNumberCard(p)
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
                if (!unknown) Box(Modifier.offset(x = 10.dp)) {
                    GhostIconButton(
                        if (fav) R.drawable.ic_oz_star_on else R.drawable.ic_oz_star,
                        stringResource(if (fav) R.string.remove_favorite else R.string.add_favorite),
                        { vm.toggleFavorite(p) }, tint = if (fav) oz.accent else oz.fg3, size = 44.dp, iconSize = 22.dp,
                    )
                }
            }
            // у незнакомого номера под ним метка АОН, если она есть
            if (unknown) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val cid by produceState<String?>(null, p.mainNumber) {
                    value = withContext(Dispatchers.IO) { runCatching { com.khizrim.dialer.callerid.CallerId.labelFor(context, p.mainNumber) }.getOrNull() }
                }
                cid?.let { Text(it, style = OzType.small, color = oz.fg3, modifier = Modifier.padding(top = 4.dp)) }
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
                        .press({ vm.perform(p, method) }, onLongClick = { vm.askSim(p) }, shape = OzShape.main, pressColor = Color.White.copy(alpha = .12f)),
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
            // «на номер» нужно, только когда номеров несколько; с одним он и так виден строкой выше
            val target = vm.numberFor(p)
            val shownTarget = target?.takeIf { p.phones.size > 1 }
            val sim = vm.personSim(target)
            if (shownTarget != null || sim != null) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        shownTarget?.let { stringResource(R.string.card_to_number, com.khizrim.dialer.data.PhoneFormat.pretty(it)) }.orEmpty(),
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
            // «Изменить контакт» — тихим словом ровно по левому краю, открывает системный редактор.
            // У незнакомого номера здесь «Добавить в контакты», «Заблокировать», «Найти в интернете».
            if (unknown) {
                val n = p.mainNumber.orEmpty()
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    listOf(
                        R.string.add_to_contacts to { vm.addToContacts(n) },
                        R.string.block to { vm.askBlock(n) },
                        R.string.cid_search_web to { vm.searchWeb(n) },
                    ).forEach { (t, act) ->
                        Text(stringResource(t), style = OzType.small, color = oz.fg3, maxLines = 1,
                            modifier = Modifier.press(act, shape = OzShape.text).padding(vertical = 8.dp))
                    }
                }
            } else Text(
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
