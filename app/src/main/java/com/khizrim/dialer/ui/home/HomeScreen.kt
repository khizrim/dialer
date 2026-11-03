package com.khizrim.dialer.ui.home

import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
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
    // лента — это и журнал: её листают вверх в прошлое. Тянут вверх у самого свежего — снизу поднимаются контакты с поиском.
    // Жест ловим раньше строк (Initial): после порога сдвига поглощаем касание, и нажатие на строку отменяется — свайп не звонит.
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
                    // горизонтальный жест — смахивание строки; вниз — листать ленту в прошлое; вверх не у края — тоже листать
                    if (kotlin.math.abs(dx) > slop && kotlin.math.abs(dx) > kotlin.math.abs(dy)) break
                    if (dy > slop) break
                    if (dy < -slop) { if (list.canScrollBackward) break else dragging = true }
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
                    .then(gestures)
                    .semantics { customActions = listOf(androidx.compose.ui.semantics.CustomAccessibilityAction(searchLabel) { vm.openTextSearch(); true }) }
                    .padding(horizontal = 12.dp),
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
                                .fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 24.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(r.text, style = OzType.small.copy(fontWeight = FontWeight.Medium), color = oz.fg3, modifier = Modifier.semantics { heading() })
                            Box(Modifier.padding(start = 12.dp).weight(1f).height(1.dp).background(oz.line))
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
                            OzIcon(R.drawable.ic_oz_chevron, oz.fg3, 18.dp)
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
                    Row(
                        Modifier
                            .animateItem(
                                fadeInSpec = Motion.enter(Motion.FADE),
                                // соседи съезжают на место убранной строки, а не перескакивают
                                placementSpec = if (reduced) null else Motion.enter(Motion.MOVE),
                                fadeOutSpec = Motion.exit(Motion.FADE),
                            )
                            // смахнуть влево — скрыть до следующего контакта, с «Вернуть»
                            .then(if (vm.leaving == null) Modifier.swipeAway { vm.removeFromHome(e.key, now = true) } else Modifier)
                            .graphicsLayer {
                                alpha = 1f - progress
                                translationX = if (reduced) 0f else shift * progress
                            }
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // нажатие на имя — карточка (у незнакомого номера — его меню), значок справа — связаться
                        Column(
                            Modifier.weight(1f).heightIn(min = 64.dp)
                                .press(
                                    onClick = { if (p != null) vm.openPerson(p.id) else vm.openMenu(e, MenuContext.HOME) },
                                    onLongClick = { vm.openMenu(e, MenuContext.HOME) },
                                    label = e.title + ((note ?: e.note)?.let { ", $it" } ?: ""),
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                e.title,
                                style = if (p != null && !p.nameIsNumber) OzType.nameHome else OzType.numberHome,
                                color = if (quiet) oz.fg2 else oz.fg, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            val second = if (quiet) stringResource(R.string.silenced) else note ?: e.note
                            if (second != null) {
                                Text(second, style = OzType.small, color = if (note != null && !quiet) oz.danger else oz.fg3, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                        com.khizrim.dialer.ui.components.ReachButton(method, e.title) { vm.tap(e) }
                    }
                }
            }
            // идёт звонок: плашка встроена в разметку, стопка поднимается над ней, ничего не перекрыто
            com.khizrim.dialer.ui.components.CallStrip(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp))
            // избранные: один тихий ряд имён со значком способа, без заголовка и без заливок
            // «Причал»: избранные лежат на приподнятом крае над панелью — одна нижняя поверхность, лента уходит под неё
            val docked = vm.favorites.isNotEmpty()
            Column(if (docked) Modifier.fillMaxWidth().dock() else Modifier.fillMaxWidth()) {
                val favs = vm.favorites
                if (favs.isNotEmpty()) {
                    // избранные — ступенью выше подписей: Literata 20, как имена в списках, и отдельной полосой над панелью
                    androidx.compose.foundation.lazy.LazyRow(
                        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(favs, key = { "f" + it.id }) { p ->
                            val m = vm.defaultOf(p)
                            Row(
                                Modifier.heightIn(min = 56.dp)
                                    .press({ vm.perform(p, m) }, onLongClick = { vm.openPerson(p.id) }, shape = com.khizrim.dialer.ui.theme.OzShape.text,
                                        label = stringResource(R.string.favorite_label, p.name, stringResource(m?.title ?: R.string.choose_method)))
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // избранные — кнопки быстрого набора: значок способа у всех, трубка тоже
                                OzIcon(m?.icon ?: R.drawable.ic_oz_swap, oz.fg3, 18.dp, modifier = Modifier.padding(end = 8.dp))
                                Text(vm.favLabel(p), style = OzType.nameList.copy(fontSize = 19.sp), color = oz.fg, maxLines = 1)
                            }
                        }
                    }
                }
                // необязательные строки появляются и уходят, раздвигая место плавно: стопка над ними не прыгает
                val alert = com.khizrim.dialer.ui.components.rememberLast(vm.callAlert)
                QuietRow(vm.callAlert != null) { alert?.let { CallAlertRow(vm, it) } }
                QuietRow(vm.tidy) {
                    TextAction(
                        stringResource(R.string.home_clear), oz.fg3, vm::askClearHome, style = OzType.small,
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
                                    .lift(com.khizrim.dialer.ui.theme.OzShape.row).background(oz.fg, com.khizrim.dialer.ui.theme.OzShape.row)
                                    .padding(start = 16.dp, end = 6.dp)
                                    .semantics { liveRegion = LiveRegionMode.Polite },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(t?.text ?: "", style = OzType.small, color = oz.bg, modifier = Modifier.weight(1f))
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
                                // «Пропущенные» — только когда они есть; во время фильтра на его месте «Все»
                                if (vm.missedOnly) TextAction(stringResource(R.string.filter_all), oz.fg2, { vm.showMissedOnly(false) }, modifier = Modifier.heightIn(min = 48.dp))
                                else if (vm.homeHasMissed) TextAction(stringResource(R.string.filter_missed), oz.danger, { vm.showMissedOnly(true) }, modifier = Modifier.heightIn(min = 48.dp))
                                TextAction(stringResource(R.string.contacts), oz.fg2, vm::openTextSearch, modifier = Modifier.heightIn(min = 48.dp))
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GhostIconButton(R.drawable.ic_oz_settings, stringResource(R.string.settings), vm::openSettings, tint = oz.fg2)
                        androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
                        KeypadFab { vm.openKeypad() }
                    }
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
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 4.dp),
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

/** Приподнятый край: фон листа, верх скруглён на 22dp, тонкая линия и мягкая тень вверх, на ленту. */
@Composable
private fun Modifier.dock(): Modifier {
    val oz = LocalOz.current
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    val shade = if (oz.isDark) Color.Black.copy(alpha = .35f) else oz.fg.copy(alpha = .07f)
    return this
        .drawBehind {
            val h = 16.dp.toPx()
            drawRect(
                androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, shade), startY = -h, endY = 0f),
                topLeft = androidx.compose.ui.geometry.Offset(0f, -h), size = androidx.compose.ui.geometry.Size(size.width, h),
            )
        }
        .clip(shape)
        .background(oz.sheet)
        .border(1.dp, oz.line, shape)
}
