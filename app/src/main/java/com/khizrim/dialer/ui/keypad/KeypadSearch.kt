package com.khizrim.dialer.ui.keypad

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.nestedscroll.nestedScroll
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khizrim.dialer.DialerViewModel
import com.khizrim.dialer.R
import com.khizrim.dialer.data.PhoneFormat
import com.khizrim.dialer.ui.components.GhostIconButton
import com.khizrim.dialer.ui.components.OzIcon
import com.khizrim.dialer.ui.components.RoundButton
import com.khizrim.dialer.ui.components.press
import com.khizrim.dialer.ui.theme.LocalOz
import com.khizrim.dialer.ui.theme.LocalReducedMotion
import com.khizrim.dialer.ui.theme.Motion
import com.khizrim.dialer.ui.theme.OzType

private val KEYS_RU = listOf(
    '1' to "", '2' to "АБВГ", '3' to "ДЕЖЗ", '4' to "ИЙКЛ", '5' to "МНОП", '6' to "РСТУ",
    '7' to "ФХЦЧ", '8' to "ШЩЪЫ", '9' to "ЬЭЮЯ", '*' to "", '0' to "+", '#' to "",
)
private val KEYS_LATIN = listOf(
    '1' to "", '2' to "ABC", '3' to "DEF", '4' to "GHI", '5' to "JKL", '6' to "MNO",
    '7' to "PQRS", '8' to "TUV", '9' to "WXYZ", '*' to "", '0' to "+", '#' to "",
)

/** Буквы на клавишах по языку интерфейса; поиск понимает обе раскладки. */
private val KEYS: List<Pair<Char, String>>
    get() = if (java.util.Locale.getDefault().language == "ru") KEYS_RU else KEYS_LATIN

/** Отфильтрованные люди над клавиатурой. Живут в верхней половине экрана. */
@Composable
fun SearchResults(vm: DialerViewModel, modifier: Modifier = Modifier, drag: PanelDrag? = null) {
    val oz = LocalOz.current
    val reduced = LocalReducedMotion.current
    // лучшее совпадение ближе к клавиатуре, под большим пальцем
    val results = vm.hits.asReversed()
    val showAdd = results.isEmpty() && PhoneFormat.digits(vm.keypad).length >= 3
    // подсказки и клавиатура — один лист: тянут вниз дальше края списка — весь слой идёт за пальцем 1:1,
    // отпустили — доезжает со скоростью пальца или возвращается (MOTION-v2)
    val pullDown = remember(vm, drag) {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            var pulled = 0f
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                if (drag == null || pulled <= 0f || source != androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput) return androidx.compose.ui.geometry.Offset.Zero
                val step = available.y.coerceAtLeast(-pulled)
                pulled += step; drag.by(step)
                return androidx.compose.ui.geometry.Offset(0f, available.y)
            }
            override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                if (drag == null || source != androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput || available.y <= 0f) return androidx.compose.ui.geometry.Offset.Zero
                if (pulled <= 0f) drag.start()
                pulled += available.y; drag.by(available.y)
                return androidx.compose.ui.geometry.Offset(0f, available.y)
            }
            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                if (drag == null || pulled <= 0f) return androidx.compose.ui.unit.Velocity.Zero
                pulled = 0f
                drag.end(available.y)
                return available
            }
        }
    }
    LazyColumn(modifier.nestedScroll(pullDown).padding(horizontal = 12.dp), verticalArrangement = Arrangement.Bottom) {
        items(results, key = { it.person.id }) { hit ->
            val p = hit.person
            val method = vm.defaultOf(p)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier.weight(1f)
                        .press(
                            onClick = { vm.openPerson(p.id) },
                            onLongClick = { vm.openMenu(p) },
                            label = "${p.name}, ${hit.phone}",
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    if (p.nameIsNumber) {
                        // имя — это номер: одна строка с номером, без дубля
                        Text(highlight(hit.phone, hit.phoneRange, oz.accent), style = OzType.list.copy(fontSize = OzType.nameSearch.fontSize), color = oz.fg, maxLines = 1)
                    } else {
                        Text(highlight(p.name, hit.nameRange, oz.accent), style = OzType.nameSearch, color = oz.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(highlight(hit.phone, hit.phoneRange, oz.accent), style = OzType.small, color = oz.fg3)
                    }
                }
                com.khizrim.dialer.ui.components.ReachButton(method, p.name) { vm.perform(p, method) }
            }
        }
        // в буфере номер — первая подсказка у клавиатуры: «Вставить +7 916 …»; есть, только пока может помочь
        val clip = vm.clipNumber
        if (vm.keypad.isEmpty() && clip != null) {
            item(key = "paste") {
                Row(
                    Modifier.fillMaxWidth().press(onClick = { vm.paste(clip) }).padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OzIcon(R.drawable.ic_oz_copy, oz.fg2)
                    Text(stringResource(R.string.paste_number, PhoneFormat.formatTyping(clip, vm.region)), style = OzType.body, color = oz.fg2, maxLines = 1)
                }
            }
        }
        if (showAdd) {
            item(key = "add") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .press(onClick = { vm.addTypedToContacts() })
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OzIcon(R.drawable.ic_oz_user_plus, oz.fg2)
                    Text(stringResource(R.string.add_number_to_contacts), style = OzType.body, color = oz.fg2)
                }
            }
        }
    }
}

private fun highlight(text: String, range: IntRange?, color: androidx.compose.ui.graphics.Color): AnnotatedString =
    buildAnnotatedString {
        if (range == null || range.first >= text.length) { append(text); return@buildAnnotatedString }
        val end = minOf(range.last + 1, text.length)
        append(text.substring(0, range.first))
        withStyle(SpanStyle(color = color)) { append(text.substring(range.first, end)) }
        append(text.substring(end))
    }

/** Жест «тянуть вниз» для слоя, в котором лежит панель. */
interface PanelDrag {
    fun start()
    fun by(dy: Float)
    fun end(velocityPx: Float)
}

/** Нижняя половина: номер с маской, клавиши без заливки, вызов. */
@Composable
fun KeypadPanel(vm: DialerViewModel, drag: PanelDrag? = null) {
    val oz = LocalOz.current
    // панель тянут вниз — весь слой клавиатуры идёт за пальцем 1:1, отпустили — доезжает со скоростью пальца
    val tracker = remember { androidx.compose.ui.input.pointer.util.VelocityTracker() }
    // слой едет за пальцем, поэтому свои координаты касания почти не меняются — считаем путь в координатах окна
    val coords = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
    fun windowOf(p: androidx.compose.ui.geometry.Offset) = coords[0]?.takeIf { it.isAttached }?.localToWindow(p) ?: p
    Column(
        Modifier.fillMaxWidth()
            .onGloballyPositioned { coords[0] = it }
            .pointerInput(drag) {
                if (drag == null) return@pointerInput
                // слушаем раньше клавиш (Initial): тянуть можно с любой точки панели, и с клавиши тоже;
                // после вертикального порога движение наше, нажатие клавиши отменяется
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var dx = 0f; var dy = 0f; var dragging = false
                    var last = windowOf(down.position)
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: run { if (dragging) drag.end(0f); null } ?: break
                        if (!ch.pressed) {
                            if (dragging) { ch.consume(); drag.end(tracker.calculateVelocity().y) }
                            break
                        }
                        val w = windowOf(ch.position)
                        val step = w.y - last.y
                        dx += w.x - last.x; dy += step; last = w
                        // горизонтальный жест — стирание номера, его не трогаем
                        if (!dragging && kotlin.math.abs(dx) > slop && kotlin.math.abs(dx) > kotlin.math.abs(dy)) break
                        if (!dragging && dy > slop) { dragging = true; tracker.resetTracking(); drag.start() }
                        if (dragging) { ch.consume(); tracker.addPosition(ch.uptimeMillis, w); drag.by(step) }
                    }
                }
            },
    ) {
        TypedNumber(vm)
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            KEYS.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { (d, letters) -> Key(d, letters, vm, Modifier.weight(1f)) }
                }
            }
        }
        val twoSims = vm.sims.size > 1
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = if (twoSims) 0.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                GhostIconButton(R.drawable.ic_oz_chevron_down, stringResource(R.string.hide_keypad), vm::closeKeypad, tint = oz.fg2, size = 56.dp)
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                val label = if (vm.keypad.isEmpty()) stringResource(R.string.call) else stringResource(R.string.call_number, PhoneFormat.formatTyping(vm.keypad, vm.region))
                RoundButton(R.drawable.ic_oz_phone, label, oz.accentFill, vm::callTyped)
            }
            Box(Modifier.weight(1f).size(56.dp), contentAlignment = Alignment.Center) {
                if (vm.keypad.isNotEmpty()) {
                    GhostIconButton(
                        R.drawable.ic_oz_backspace, stringResource(R.string.delete_digit), vm::backspace,
                        tint = oz.fg2, size = 56.dp, onLongClick = vm::clearKeypad,
                    )
                }
            }
        }
        // две SIM: под кнопкой вызова тихо названа та, с которой уйдёт звонок; нажатие переключает
        if (twoSims) {
            val sim = vm.simLabel(vm.keypadSimId) ?: stringResource(R.string.sim_ask)
            Box(Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
                Text(
                    sim, style = OzType.small, color = oz.fg3, maxLines = 1,
                    modifier = Modifier
                        .press({ vm.cycleKeypadSim() }, shape = com.khizrim.dialer.ui.theme.OzShape.text, label = stringResource(R.string.sim_switch))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TypedNumber(vm: DialerViewModel) {
    val oz = LocalOz.current
    val reduced = LocalReducedMotion.current
    val formatted = PhoneFormat.formatTyping(vm.keypad, vm.region)
    val noNumber = stringResource(R.string.no_number)
    val size = when {
        formatted.length > 16 -> 24.sp
        formatted.length > 13 -> 28.sp
        else -> 32.sp
    }
    // последняя цифра появляется с подъёмом на 4 px за 100 мс
    val cut = if (vm.keypad.isEmpty()) 0 else PhoneFormat.digitPos(formatted, PhoneFormat.digits(vm.keypad).length - 1)
        .let { if (vm.keypad.last().isDigit()) it else formatted.length - 1 }
    val head = formatted.take(cut.coerceIn(0, formatted.length))
    val tail = formatted.drop(cut.coerceIn(0, formatted.length))
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(start = 24.dp, end = 24.dp, top = 4.dp)
            // смахнуть номер влево — стереть цифру, как на калькуляторе
            .pointerInput(vm) {
                var dx = 0f
                val step = 48.dp.toPx()
                detectHorizontalDragGestures(onDragStart = { dx = 0f }) { change, amount ->
                    change.consume(); dx += amount
                    if (dx < -step) { vm.backspace(); dx = 0f }
                }
            }
            // долгое нажатие на поле вставляет номер из буфера
            .combinedClickable(interactionSource = null, indication = null, onClick = {}, onLongClick = { vm.paste(clipboard.getText()?.text) })
            .semantics { liveRegion = LiveRegionMode.Polite; contentDescription = formatted.ifEmpty { noNumber } },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val style = OzType.digit.copy(fontSize = size)
        // пустое поле: серым последний набранный номер, нажатие подставляет его
        val last = vm.lastDialed
        if (vm.keypad.isEmpty() && last != null) {
            Text(
                PhoneFormat.formatTyping(last, vm.region), style = OzType.digit.copy(fontSize = 24.sp), color = oz.fg3, maxLines = 1,
                modifier = Modifier.press({ vm.fillLast() }, shape = com.khizrim.dialer.ui.theme.OzShape.text, label = stringResource(R.string.call_number, PhoneFormat.formatTyping(last, vm.region))).padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        Text(head, style = style, color = oz.fg, maxLines = 1)
        key(vm.keypad.length) {
            val shift = with(LocalDensity.current) { 4.dp.toPx() }
            val a = remember { Animatable(1f) }
            LaunchedEffect(Unit) { a.animateTo(1f, Motion.enter(Motion.PRESS)) }
            Text(
                tail, style = style, color = oz.fg, maxLines = 1,
                modifier = Modifier.graphicsLayer { alpha = a.value; translationY = (1f - a.value) * shift },
            )
        }
    }
}

/** Клавиша без заливки: при нажатии цифра становится полупрозрачной на 100 мс. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Key(d: Char, letters: String, vm: DialerViewModel, modifier: Modifier) {
    val oz = LocalOz.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val alpha by animateFloatAsState(if (pressed) 0.45f else 1f, Motion.enter(Motion.PRESS), label = "key")
    val dialZero = stringResource(R.string.dial_zero)
    val dialDigit = stringResource(R.string.dial_digit, d.toString())
    val view = androidx.compose.ui.platform.LocalView.current
    Column(
        modifier
            .height(66.dp)
            .combinedClickable(
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onLongClick = when (d) {
                    '0' -> ({ vm.type('+') })
                    '1' -> ({ vm.callVoicemail() })
                    else -> null
                },
                onClick = {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    vm.type(d)
                },
            )
            .semantics { contentDescription = if (d == '0') dialZero else dialDigit }
            .graphicsLayer { this.alpha = alpha },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(d.toString(), style = OzType.digit, color = oz.fg)
        Text(letters, style = OzType.tiny, color = oz.fg3, modifier = Modifier.heightIn(min = 14.dp))
    }
}

