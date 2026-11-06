package com.khizrim.dialer.ui.home

import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.height
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.res.pluralStringResource
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
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
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
import com.khizrim.dialer.ui.components.swipeAway
import com.khizrim.dialer.ui.theme.LocalOz
import com.khizrim.dialer.ui.theme.dock
import com.khizrim.dialer.ui.theme.LocalReducedMotion
import com.khizrim.dialer.ui.theme.Motion
import com.khizrim.dialer.ui.theme.OzType
import com.khizrim.dialer.ui.theme.Inter
import com.khizrim.dialer.ui.theme.lift
import com.khizrim.dialer.model.Method
import kotlinx.coroutines.launch

/** Главная: стопка имён, ближайший человек внизу. Тап связывает, долгое нажатие открывает меню. */
@Composable
fun HomeScreen(vm: DialerViewModel) {
    val oz = LocalOz.current
    val reduced = LocalReducedMotion.current
    // лента — это и журнал, её просто листают. Контакты с поиском тянут вверх за «Причал» и нижнюю панель.
    // Жест ловим раньше чипов и ссылок (Initial): после порога сдвига поглощаем касание, и нажатие отменяется — свайп не звонит.
    val stack = com.khizrim.dialer.ui.LocalScreenStack.current
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    val gestures = Modifier.pointerInput(vm, stack) {
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
            tracker.addPointerInputChange(down)
            var dy = 0f
            var dx = 0f
            var dragging = false
            var released = false
            val contacts = com.khizrim.dialer.ui.Route(com.khizrim.dialer.Screen.SEARCH)
            while (true) {
                val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                tracker.addPointerInputChange(ch)
                dy += ch.position.y - ch.previousPosition.y
                dx += ch.position.x - ch.previousPosition.x
                if (!dragging) {
                    // горизонтальный жест — листать чипы избранного; вниз — ничего; вверх — контакты
                    if (kotlin.math.abs(dx) > slop && kotlin.math.abs(dx) > kotlin.math.abs(dy)) break
                    if (dy > slop) break
                    if (dy < -slop) dragging = true
                }
                if (dragging) {
                    ch.consume()
                    val h = stack?.height ?: 1f
                    stack?.previewTo(contacts, com.khizrim.dialer.ui.Edge.BOTTOM, (-dy / h).coerceAtLeast(0f))
                }
                if (!ch.pressed) {
                    released = true
                    if (dragging) {
                        val v = tracker.calculateVelocity().y
                        if (stack == null) { if (dy < -threshold) vm.openTextSearch() }
                        else stack.previewEnd(-v) { vm.openTextSearch() }
                    }
                    break
                }
            }
            if (dragging && !released) stack?.previewCancel()
        }
    }
    val searchLabel = stringResource(R.string.search_by_name)
    val res = androidx.compose.ui.platform.LocalContext.current.resources
    // свежие внизу (reverseLayout), подпись дня — над строками своего дня
    val feed = vm.homeStack
    val rows = remember(feed) {
        buildList<Any> {
            var day: String? = null
            feed.asReversed().forEach { e ->
                // строки раскрытой группы «без звука» идут подряд над заголовком, дни их не делят
                if (e.inSilentGroup) { add(e); return@forEach }
                val d = if (e.lastAt > 0L) com.khizrim.dialer.data.Dates.day(res, e.lastAt) else null
                if (day != null && d != day) add(FeedDay(day!!))
                day = d
                add(e)
            }
            day?.let { add(FeedDay(it)) }
        }
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth()
                    .semantics { customActions = listOf(androidx.compose.ui.semantics.CustomAccessibilityAction(searchLabel) { vm.openTextSearch(); true }) }
                    .padding(horizontal = 8.dp),
                state = list,
                reverseLayout = true,
            ) {
                if (feed.isEmpty() && vm.favorites.isEmpty()) {
                    item(key = "empty") {
                        Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 24.dp).animateItem()) {
                            Text(stringResource(R.string.home_empty_title), style = OzType.nameHome, color = oz.fg)
                            Text(
                                stringResource(R.string.home_empty_text),
                                style = OzType.body, color = oz.fg2, modifier = Modifier.padding(top = 8.dp).widthIn(max = 320.dp),
                            )
                        }
                    }
                }
                items(rows, key = { r -> if (r is FeedDay) "d" + r.text else (r as com.khizrim.dialer.model.HomeEntry).key }) { r ->
                    if (r is FeedDay) {
                        // подпись дня — тихий разделитель с линией
                        Row(
                            Modifier.animateItem(fadeInSpec = Motion.enter(Motion.FADE), placementSpec = if (reduced) null else Motion.enter(Motion.MOVE), fadeOutSpec = Motion.exit(Motion.FADE))
                                .fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 20.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // подпись дня — без линии: хватает воздуха над ней
                            Text(r.text, style = OzType.meta.copy(fontWeight = FontWeight.Medium), color = oz.fg3, modifier = Modifier.semantics { heading() })
                        }
                        return@items
                    }
                    val e = r as com.khizrim.dialer.model.HomeEntry
                    if (e.silent > 0) {
                        // незнакомые без звука — одной тихой строкой; нажатие раскрывает их прямо в ленте
                        Row(
                            Modifier.animateItem().fillMaxWidth().heightIn(min = 56.dp)
                                .press(vm::toggleSilent, label = pluralStringResource(R.plurals.silent_unknown, e.silent, e.silent))
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OzIcon(R.drawable.ic_oz_moon, oz.fg3, 18.dp)
                            Text(pluralStringResource(R.plurals.silent_unknown, e.silent, e.silent), style = OzType.body, color = oz.fg2, modifier = Modifier.padding(start = 12.dp).weight(1f))
                            // раскрыта — стрелка смотрит вверх, туда, где лежат номера; нажатие сворачивает
                            OzIcon(R.drawable.ic_oz_chevron_down, oz.fg3, 18.dp, Modifier.graphicsLayer { rotationZ = if (vm.silentOpen) 180f else -90f })
                        }
                        return@items
                    }
                    val p = e.person
                    val method = p?.let { vm.defaultOf(it) } ?: if (p == null) Method.PHONE else null
                    val note = e.missedNote
                    val isLeaving = vm.leaving == e.key
                    val shift = with(LocalDensity.current) { (-24).dp.toPx() }
                    val progress by animateFloatAsState(
                        targetValue = if (isLeaving) 1f else 0f,
                        animationSpec = Motion.exit(Motion.FADE), label = "leave",
                    )
                    val quiet = vm.isSilenced(e)
                    // время серым под именем — только за сегодня и вчера, дальше хватает подписи дня
                    val time = com.khizrim.dialer.data.Dates.recentTime(e.lastAt)
                    Box(
                        Modifier
                            .animateItem(
                                fadeInSpec = Motion.enter(Motion.FADE),
                                // соседи съезжают на место убранной строки, а не перескакивают
                                placementSpec = if (reduced) null else Motion.enter(Motion.MOVE),
                                fadeOutSpec = Motion.exit(Motion.FADE),
                            )
                            // смахнуть влево — скрыть до следующего контакта, с «Вернуть»
                            .then(if (vm.leaving == null) Modifier.swipeAway(hint = stringResource(R.string.hide)) { vm.removeFromHome(e.key, now = true) } else Modifier)
                            .graphicsLayer {
                                alpha = 1f - progress
                                translationX = if (reduced) 0f else shift * progress
                            }
                            .fillMaxWidth(),
                    ) {
                        // всё, что правее имени (но не меньше 104 dp у края), на всю высоту строки — «связаться»
                        Box(
                            Modifier.matchParentSize()
                                .press(
                                    onClick = { vm.tap(e) },
                                    // долгое нажатие на трубку при двух SIM — выбор SIM на этот звонок, иначе меню
                                    onLongClick = { if (!(if (p != null) vm.askSim(p) else vm.askSim(e.number, e.title))) vm.openMenu(e, MenuContext.HOME) },
                                    label = stringResource(method?.title ?: R.string.choose_method) + ": " + e.title,
                                ),
                        )
                        Row(Modifier.fillMaxWidth().padding(end = 120.dp), verticalAlignment = Alignment.CenterVertically) {
                            // нажатие на имя — карточка (у незнакомого номера — такая же карточка номера)
                            Column(
                                Modifier.weight(1f, fill = false).heightIn(min = 52.dp)
                                    .press(
                                        onClick = { if (p != null) vm.openPerson(p.id) else e.number?.let(vm::openNumber) ?: vm.openMenu(e, MenuContext.HOME) },
                                        onLongClick = { vm.openMenu(e, MenuContext.HOME) },
                                        label = e.title + ((note ?: e.note)?.let { ", $it" } ?: ""),
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    e.title,
                                    style = if (p != null && !p.nameIsNumber) OzType.row else OzType.rowNumber,
                                    color = if (quiet) oz.fg2 else oz.fg, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                // в раскрытой группе дни не показаны, поэтому время рядом с «без звука»
                                val second = when {
                                    quiet -> stringResource(R.string.silenced) + (if (e.inSilentGroup) " · " + com.khizrim.dialer.data.Dates.whenText(res, e.lastAt) else "")
                                    note != null -> note
                                    else -> e.note // время стоит справа, у края строки
                                }
                                if (second != null) {
                                    Text(second, style = OzType.meta, color = if (note != null && !quiet) oz.danger else oz.fg3, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                                }
                            }
                        }
                        // у края: время серым (сегодня и вчера) и способ — значок, у мессенджера ещё «WA» / «TG»; нажатие ловит вся правая зона
                        Row(Modifier.align(Alignment.CenterEnd).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (time != null && !e.inSilentGroup) Text(time, style = OzType.meta, color = oz.fg3, modifier = Modifier.padding(end = 12.dp))
                            com.khizrim.dialer.ui.components.MethodMark(method)
                        }
                    }
                }
            }
            // идёт звонок: плашка встроена в разметку, стопка поднимается над ней, ничего не перекрыто
            com.khizrim.dialer.ui.components.CallStrip(Modifier.fillMaxWidth().padding(start = com.khizrim.dialer.ui.theme.Gap.bar, end = com.khizrim.dialer.ui.theme.Gap.bar, top = 8.dp, bottom = 4.dp))
            // избранные: один тихий ряд имён со значком способа, без заголовка и без заливок
            // «Причал»: избранные лежат на приподнятом крае над панелью — одна нижняя поверхность, лента уходит под неё
            // контакты вытягиваются за «Причал» и нижнюю панель: они не листаются, поэтому жест работает всегда
            // нижняя панель — причал, как у Ozero Mail: всегда одна поверхность цвета страницы с тенью над краем
            Column(Modifier.fillMaxWidth().dock().then(gestures)) {
                // ручка: за неё вытягивают контакты, и она выше системной полосы жестов
                Box(Modifier.padding(top = 6.dp).align(Alignment.CenterHorizontally).width(36.dp).height(4.dp).background(oz.handle, RoundedCornerShape(2.dp)))
                val favs = vm.favorites
                if (favs.isNotEmpty()) {
                    // избранные — ступенью выше подписей: тот же размер, что у имён в списках, и отдельной полосой над панелью
                    // край справа гаснет, только пока ряд можно прокрутить дальше
                    val favRow = androidx.compose.foundation.lazy.rememberLazyListState()
                    val fadeEdge by animateFloatAsState(if (favRow.canScrollForward) 1f else 0f, Motion.enter(Motion.FADE), label = "favFade")
                    androidx.compose.foundation.lazy.LazyRow(
                        Modifier.fillMaxWidth().padding(top = 6.dp)
                            .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                if (fadeEdge > 0f) {
                                    val w = 48.dp.toPx()
                                    drawRect(
                                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                                            listOf(Color.Black, Color.Black.copy(alpha = 1f - fadeEdge)), startX = size.width - w, endX = size.width,
                                        ),
                                        topLeft = androidx.compose.ui.geometry.Offset(size.width - w, 0f),
                                        size = androidx.compose.ui.geometry.Size(w, size.height),
                                        blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                                    )
                                }
                            },
                        state = favRow,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(favs, key = { "f" + it.id }) { p ->
                            val m = vm.defaultOf(p)
                            Row(
                                Modifier.heightIn(min = 56.dp)
                                    .press({ vm.perform(p, m) }, onLongClick = { if (!vm.askSim(p)) vm.openPerson(p.id) }, shape = com.khizrim.dialer.ui.theme.OzShape.text,
                                        label = stringResource(R.string.favorite_label, p.name, stringResource(m?.title ?: R.string.choose_method)))
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // избранные — кнопки быстрого набора: значок способа у всех, трубка тоже
                                OzIcon(m?.icon ?: R.drawable.ic_oz_swap, oz.fg3, 16.dp, modifier = Modifier.padding(end = 6.dp))
                                Text(vm.favLabel(p), style = OzType.row, color = oz.fg, maxLines = 1)
                            }
                        }
                    }
                }
                // необязательные строки появляются и уходят, раздвигая место плавно: стопка над ними не прыгает
                val alert = com.khizrim.dialer.ui.components.rememberLast(vm.callAlert)
                QuietRow(vm.callAlert != null) { alert?.let { CallAlertRow(vm, it) } }
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
                                    .background(oz.toastBg, com.khizrim.dialer.ui.theme.OzShape.row)
                                    .padding(start = 16.dp, end = 6.dp)
                                    .semantics { liveRegion = LiveRegionMode.Polite },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(t?.text ?: "", style = OzType.small, color = oz.toastFg, modifier = Modifier.weight(1f))
                                TextAction(stringResource(R.string.undo), oz.toastAct, vm::undo, style = OzType.small.copy(fontWeight = FontWeight.SemiBold))
                            }
                        }
                        // ссылки и тост делят одно место: ссылки гаснут быстрее, чем проявляется тост, — без наложения и без рывка
                        androidx.compose.animation.AnimatedVisibility(
                            visible = t?.undo != true,
                            enter = fadeIn(Motion.enter(Motion.MOVE, delay = Motion.PRESS)),
                            exit = fadeOut(Motion.exit(Motion.PRESS)),
                        ) {
                            Row {
                                // пропущенные и так видны красным в ленте; «Все» — только если лента открыта из уведомления о пропущенных
                                if (vm.missedOnly) TextAction(stringResource(R.string.filter_all), oz.fg2, { vm.showMissedOnly(false) }, modifier = Modifier.heightIn(min = 48.dp))
                                TextAction(stringResource(R.string.contacts), oz.fg2, vm::openTextSearch, modifier = Modifier.heightIn(min = 48.dp))
                            }
                        }
                    }
                    // настройки нужны редко и живут в «Контактах»; здесь одна кнопка — клавиатура
                    KeypadFab { vm.openKeypad() }
                }
            }
        }
    }
}


/** Строка, которая то есть, то нет: раздвигает место и проявляется, уходит обратно. */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.QuietRow(visible: Boolean, content: @Composable () -> Unit) {
    val reduced = LocalReducedMotion.current
    androidx.compose.animation.AnimatedVisibility(
        visible,
        enter = if (reduced) fadeIn(Motion.enter(Motion.FADE)) else androidx.compose.animation.expandVertically(Motion.move(Motion.MOVE), expandFrom = Alignment.Top) + fadeIn(Motion.enter(Motion.MOVE, delay = 50)),
        exit = if (reduced) fadeOut(Motion.exit(Motion.FADE)) else fadeOut(Motion.exit(Motion.PRESS)) + androidx.compose.animation.shrinkVertically(Motion.move(Motion.MOVE), shrinkTowards = Alignment.Top),
    ) { content() }
}

/** Одна тихая строка, если входящие могут не показаться. Исчезает, когда всё исправлено. */
@Composable
private fun CallAlertRow(vm: DialerViewModel, alert: com.khizrim.dialer.telecom.CallAlert) {
    val oz = LocalOz.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
    ) { vm.reload() }
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(alert.text), style = OzType.small, color = oz.warn, modifier = Modifier.weight(1f))
        TextAction(stringResource(alert.action), oz.accent, {
            val intent = vm.alertIntent()
            vm.alertOpened()
            val ok = intent != null && runCatching { launcher.launch(intent) }.isSuccess
            if (!ok) runCatching { context.startActivity(com.khizrim.dialer.telecom.CallReadiness.fallbackIntent(context)) }
        }, style = OzType.small)
        if (alert.dismissable) {
            GhostIconButton(R.drawable.ic_oz_x, stringResource(R.string.close), vm::dismissAlert, tint = oz.fg3, iconSize = 18.dp)
        }
    }
}

/** Подпись дня в ленте. */
private data class FeedDay(val text: String)

