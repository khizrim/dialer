package com.khizrim.dialer.ui.keypad

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khizrim.dialer.DialerViewModel
import com.khizrim.dialer.R
import com.khizrim.dialer.data.PhoneFormat
import com.khizrim.dialer.data.SearchHit
import com.khizrim.dialer.ui.kit.AccentRound
import com.khizrim.dialer.ui.kit.Grab
import com.khizrim.dialer.ui.kit.OzIcon
import com.khizrim.dialer.ui.kit.TextAction
import com.khizrim.dialer.ui.kit.press
import com.khizrim.dialer.ui.nav.Layer
import com.khizrim.dialer.ui.nav.LocalNav
import com.khizrim.dialer.ui.nav.dismissDrag
import com.khizrim.dialer.ui.openCard
import com.khizrim.dialer.ui.theme.LocalOz
import com.khizrim.dialer.ui.theme.Motion
import com.khizrim.dialer.ui.theme.OzShape
import com.khizrim.dialer.ui.theme.OzType
import com.khizrim.dialer.ui.theme.glass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.LocalDensity
import java.util.Locale

/** Сколько надо протянуть палец по клавишам, чтобы лист поехал вниз, а не засчиталось нажатие. */
private val KEY_SLOP = 40.dp

/** Та же конфигурация, но с порогом жеста побольше — только для перетаскивания листа поверх клавиш. */
private class CalmSlop(private val base: ViewConfiguration, private val slop: Float) : ViewConfiguration by base {
    override val touchSlop: Float get() = slop
}

private val KEYS = listOf('1', '2', '3', '4', '5', '6', '7', '8', '9', '*', '0', '#')
private val LATIN = mapOf('2' to "ABC", '3' to "DEF", '4' to "GHI", '5' to "JKL", '6' to "MNO", '7' to "PQRS", '8' to "TUV", '9' to "WXYZ", '0' to "+")
private val CYRILLIC = mapOf('2' to "АБВГ", '3' to "ДЕЖЗ", '4' to "ИЙКЛ", '5' to "МНОП", '6' to "РСТУ", '7' to "ФХЦЧ", '8' to "ШЩЪЫ", '9' to "ЬЭЮЯ", '0' to "+")

/**
 * Клавиатура набора — панель снизу без затемнения: лента над ней видна, нажатие выше закрывает.
 * Над панелью на стекле — до трёх людей по цифрам и буквам. Две SIM и «спрашивать» — две кнопки вызова.
 */
@Composable
fun KeypadPanel(vm: DialerViewModel, l: Layer) {
    val oz = LocalOz.current
    val nav = LocalNav.current
    val typed = vm.keypad
    val digits = PhoneFormat.digits(typed)
    val hits = if (digits.isNotEmpty()) vm.hits.take(3) else emptyList()

    Column(Modifier.fillMaxWidth()) {
        AnimatedVisibility(
            hits.isNotEmpty(),
            enter = fadeIn(Motion.enter(Motion.FADE)) + slideInVertically(Motion.enter(Motion.MOVE)) { it / 4 },
            exit = fadeOut(Motion.exit(Motion.FADE)) + slideOutVertically(Motion.exit(Motion.FADE)) { it / 4 },
        ) {
            Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp).fillMaxWidth().animateContentSize(Motion.size()).glass(RoundedCornerShape(22.dp)).padding(vertical = 4.dp)) {
                vm.hits.take(3).forEach { h -> HitRow(h, onCard = { nav.openCard(h.person.id) }, onCall = { vm.callNumber(h.phone) }) }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(oz.sheet, OzShape.sheetTop)
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
        ) {
            // за ручку и поле номера лист тянется сразу, как обычно
            Column(Modifier.fillMaxWidth().dismissDrag(l)) {
                Grab()
                NumberField(vm, typed)
            }
            // по клавишам — только явным длинным движением вниз: быстрый набор с дрожью пальца
            // не должен превращаться в жест закрытия и съедать нажатия
            val vc = LocalViewConfiguration.current
            val density = LocalDensity.current
            val calm = remember(vc, density) { CalmSlop(vc, with(density) { KEY_SLOP.toPx() }) }
            CompositionLocalProvider(LocalViewConfiguration provides calm) {
                Column(Modifier.fillMaxWidth().dismissDrag(l)) {
                    CompositionLocalProvider(LocalViewConfiguration provides vc) {
                        Keys(vm)
                        Foot(vm, digits.isNotEmpty(), typed.isNotEmpty())
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberField(vm: DialerViewModel, typed: String) {
    val oz = LocalOz.current
    val shown = if (typed.isEmpty()) "" else PhoneFormat.formatTyping(typed, vm.region)
    Box(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
        val clip = vm.clipNumber
        if (typed.isEmpty() && clip != null) {
            Row(
                Modifier.heightIn(min = 40.dp).press({ vm.paste(clip) }, shape = CircleShape).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OzIcon(R.drawable.ic_oz_copy, oz.fg3, 16.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.paste_number, PhoneFormat.pretty(clip)), style = OzType.small, color = oz.fg2, maxLines = 1)
            }
        } else {
            Text(
                shown,
                style = OzType.digit.copy(fontSize = if (shown.length > 16) 26.sp else 32.sp),
                color = oz.fg, maxLines = 1, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
    // кто это: подпись определителя и «Добавить в контакты», если номера нет среди людей
    val digits = PhoneFormat.digits(typed)
    val known = vm.hits.any { PhoneFormat.digits(it.phone).endsWith(digits.takeLast(10)) && digits.length >= 7 }
    Box(Modifier.fillMaxWidth().height(28.dp).padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
        androidx.compose.animation.AnimatedVisibility(
            digits.length >= 7 && !known,
            enter = fadeIn(Motion.enter(Motion.FADE)), exit = fadeOut(Motion.exit(Motion.FADE)),
        ) {
            val tag = com.khizrim.dialer.callerid.rememberCallerTag(typed)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (tag != null) Text(tag, style = OzType.meta, color = oz.fg3, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 4.dp))
                TextAction(stringResource(R.string.add_to_contacts), oz.accent, vm::addTypedToContacts, style = OzType.meta.copy(fontWeight = FontWeight.Medium))
            }
        }
    }
}

@Composable
private fun Keys(vm: DialerViewModel) {
    val oz = LocalOz.current
    val haptic = LocalHapticFeedback.current
    val letters = if (Locale.getDefault().language == "ru") CYRILLIC else LATIN
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
        KEYS.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { k ->
                    val label = if (k == '0') stringResource(R.string.dial_zero) else stringResource(R.string.dial_digit, k.toString())
                    Column(
                        Modifier
                            .weight(1f)
                            .height(68.dp)
                            .press(
                                onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); vm.type(k) },
                                onLongClick = when (k) {
                                    '0' -> ({ vm.type('+') })
                                    '1' -> ({ vm.callVoicemail() })
                                    else -> null
                                },
                                shape = RoundedCornerShape(20.dp),
                                label = label,
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(k.toString(), style = OzType.digit, color = oz.fg)
                        Text(letters[k] ?: "", style = OzType.tiny, color = oz.fg3, modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Foot(vm: DialerViewModel, hasDigits: Boolean, hasText: Boolean) {
    val oz = LocalOz.current
    val two = vm.sims.size > 1 && vm.simForCalls == null
    Row(Modifier.fillMaxWidth().height(104.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.weight(1f))
        if (two) {
            vm.sims.take(2).forEachIndexed { i, (id, label) ->
                if (i > 0) Spacer(Modifier.width(28.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box {
                        AccentRound(R.drawable.ic_oz_phone, stringResource(R.string.call_with_sim, label), { if (hasDigits) vm.callNumber(vm.keypad, id) }, size = 64.dp, iconSize = 26.dp)
                        Box(
                            Modifier.align(Alignment.TopEnd).size(20.dp).background(oz.sheet, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Text((i + 1).toString(), style = OzType.channel.copy(fontSize = 11.sp), color = oz.fg) }
                    }
                    Text(label.substringAfter(" · ", label), style = OzType.meta, color = oz.fg3, maxLines = 1, modifier = Modifier.padding(top = 4.dp).width(88.dp), textAlign = TextAlign.Center)
                }
            }
        } else {
            AccentRound(R.drawable.ic_oz_phone, stringResource(R.string.call), vm::callTyped, size = 72.dp, iconSize = 28.dp)
        }
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            // стереть — только когда есть что стирать, в том числе одни «*» и «#»
            val a by animateFloatAsState(if (hasText) 1f else 0f, Motion.enter(Motion.FADE), label = "bs")
            if (a > 0f) {
                Box(
                    Modifier.size(56.dp).graphicsLayer { alpha = a }
                        .press(vm::backspace, onLongClick = vm::clearKeypad, shape = CircleShape, label = stringResource(R.string.delete_digit)),
                    contentAlignment = Alignment.Center,
                ) { OzIcon(R.drawable.ic_oz_backspace, oz.fg2, 26.dp) }
            }
        }
    }
}

/** Человек, найденный по набранному: имя — карточка, трубка — звонок на найденный номер. */
@Composable
private fun HitRow(h: SearchHit, onCard: () -> Unit, onCall: () -> Unit) {
    val oz = LocalOz.current
    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier.weight(1f).fillMaxHeight().press(onCard, shape = RoundedCornerShape(18.dp), label = h.person.shownName).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(mark(h.person.shownName, h.nameRange, oz.fg), style = OzType.row.copy(fontWeight = FontWeight.Normal), color = oz.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(PhoneFormat.pretty(h.phone), style = OzType.meta, color = oz.fg3, maxLines = 1)
        }
        Box(
            Modifier.width(72.dp).fillMaxHeight().press(onCall, shape = RoundedCornerShape(18.dp), label = stringResource(R.string.call_number, h.person.shownName)),
            contentAlignment = Alignment.Center,
        ) { OzIcon(R.drawable.ic_oz_phone, oz.fg2, 22.dp) }
    }
}

private fun mark(text: String, range: IntRange?, strong: androidx.compose.ui.graphics.Color): AnnotatedString = buildAnnotatedString {
    if (range == null || range.first < 0 || range.last >= text.length || range.isEmpty()) { append(text); return@buildAnnotatedString }
    append(text.substring(0, range.first))
    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = strong)) { append(text.substring(range.first, range.last + 1)) }
    append(text.substring(range.last + 1))
}
