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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
fun SearchResults(vm: DialerViewModel, modifier: Modifier = Modifier) {
    val oz = LocalOz.current
    val reduced = LocalReducedMotion.current
    // лучшее совпадение ближе к клавиатуре, под большим пальцем
    val results = vm.hits.asReversed()
    val showAdd = results.isEmpty() && PhoneFormat.digits(vm.keypad).length >= 3
    LazyColumn(modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.Bottom) {
        items(results, key = { it.person.id }) { hit ->
            val p = hit.person
            val method = vm.defaultOf(p)
            Row(
                Modifier
                    .fillMaxWidth()
                    .press(
                        onClick = { vm.perform(p, method) },
                        onLongClick = { vm.openMenu(p) },
                        label = "${p.name}, ${hit.phone}: ${stringResource(method?.title ?: R.string.choose_method)}",
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    if (p.nameIsNumber) {
                        // имя — это номер: одна строка с номером, без дубля
                        Text(highlight(hit.phone, hit.phoneRange, oz.accent), style = OzType.list.copy(fontSize = OzType.nameSearch.fontSize), color = oz.fg, maxLines = 1)
                    } else {
                        Text(highlight(p.name, hit.nameRange, oz.accent), style = OzType.nameSearch, color = oz.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(highlight(hit.phone, hit.phoneRange, oz.accent), style = OzType.small, color = oz.fg3)
                    }
                }
                OzIcon(method?.icon ?: R.drawable.ic_oz_help, oz.fg3, modifier = Modifier.padding(start = 16.dp))
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

/** Нижняя половина: номер с маской, клавиши без заливки, вызов. */
@Composable
fun KeypadPanel(vm: DialerViewModel) {
    val oz = LocalOz.current
    // панель тянут вниз — клавиатура закрывается; панель идёт за пальцем
    val pull = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val limit = with(LocalDensity.current) { 72.dp.toPx() }
    Column(
        Modifier.fillMaxWidth()
            .pointerInput(vm) {
                detectVerticalDragGestures(
                    onDragEnd = { if (pull.value > limit) vm.closeKeypad(); scope.launch { pull.animateTo(0f, Motion.enter(Motion.MOVE)) } },
                    onDragCancel = { scope.launch { pull.animateTo(0f) } },
                ) { change, amount -> change.consume(); scope.launch { pull.snapTo((pull.value + amount).coerceAtLeast(0f)) } }
            }
            .graphicsLayer { translationY = pull.value }
            .background(oz.bg),
    ) {
        TypedNumber(vm)
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            KEYS.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { (d, letters) -> Key(d, letters, vm, Modifier.weight(1f)) }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 16.dp),
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

