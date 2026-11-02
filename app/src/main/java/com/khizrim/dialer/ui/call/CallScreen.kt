package com.khizrim.dialer.ui.call

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.khizrim.dialer.DialerViewModel
import com.khizrim.dialer.R
import com.khizrim.dialer.model.CallKind
import com.khizrim.dialer.model.CallPhase
import com.khizrim.dialer.model.CallState
import com.khizrim.dialer.ui.components.OzIcon
import com.khizrim.dialer.ui.components.TextAction
import com.khizrim.dialer.ui.components.press
import com.khizrim.dialer.ui.theme.LocalOz
import com.khizrim.dialer.ui.theme.LocalReducedMotion
import com.khizrim.dialer.ui.theme.Motion
import com.khizrim.dialer.ui.theme.OzType

/** Входящий, неизвестный, спам и идущий звонок. Без подсказки про канал. */
@Composable
fun CallScreen(vm: DialerViewModel, c: CallState) {
    val oz = LocalOz.current
    val reduced = LocalReducedMotion.current
    val shift = with(LocalDensity.current) { 8.dp.roundToPx() }
    val person = c.personId?.let { vm.person(it) }
    val title = person?.name ?: c.number ?: ""
    val sub = when (c.phase) {
        CallPhase.RINGING -> if (person != null) "мобильный" else c.callerLabel ?: ""
        CallPhase.ACTIVE -> c.network + "  " + DialerViewModel.timer(c.seconds)
        CallPhase.ENDED -> c.endText
    }

    Column(
        Modifier.fillMaxSize().background(oz.bg).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(if (c.phase == CallPhase.RINGING) 72.dp else 160.dp))
        Text(
            title,
            style = if (person != null) OzType.nameCall else OzType.digit,
            color = oz.fg,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            sub, style = OzType.small, color = oz.fg3,
            modifier = Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (c.kind == CallKind.SPAM && c.phase == CallPhase.RINGING) {
            Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                OzIcon(R.drawable.ic_oz_alert, oz.warn, 18.dp)
                Spacer(Modifier.width(8.dp))
                Text("Похоже на спам, 128 жалоб", style = OzType.small.copy(fontWeight = FontWeight.Medium), color = oz.warn)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (c.phase == CallPhase.ACTIVE && c.dtmf != null) Dtmf(vm, c.dtmf)
        }
        // после «Ответить» кнопки гаснут за 150 мс, управление поднимается на 8 px за 200 мс
        AnimatedContent(
            targetState = c.phase,
            transitionSpec = {
                val enter = if (reduced) fadeIn(Motion.enter(Motion.FADE)) else
                    fadeIn(Motion.enter(Motion.MOVE, delay = Motion.FADE)) + slideInVertically(Motion.enter(Motion.MOVE, delay = Motion.FADE)) { shift }
                enter togetherWith fadeOut(Motion.exit(Motion.FADE))
            },
            label = "call-phase",
        ) { phase ->
            when (phase) {
                CallPhase.RINGING -> Ringing(vm, known = person != null)
                CallPhase.ACTIVE -> Active(vm, c)
                CallPhase.ENDED -> Spacer(Modifier.fillMaxWidth().height(144.dp))
            }
        }
    }
}

@Composable
private fun Ringing(vm: DialerViewModel, known: Boolean) {
    val oz = LocalOz.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(bottom = 28.dp), horizontalArrangement = Arrangement.SpaceAround) {
            TextAction("Написать SMS", oz.fg2, vm::quickSms, style = OzType.small)
            if (known) TextAction("Напомнить", oz.fg2, vm::remind, style = OzType.small)
            else TextAction("Заблокировать", oz.fg2, vm::askBlock, style = OzType.small)
        }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            LabeledRound(R.drawable.ic_oz_end_call, "Отклонить", oz.dangerFill, vm::decline)
            LabeledRound(R.drawable.ic_oz_phone, "Ответить", oz.accentFill, vm::answer)
        }
    }
}

@Composable
private fun LabeledRound(icon: Int, label: String, fill: Color, onClick: () -> Unit) {
    val oz = LocalOz.current
    Column(
        Modifier.press(onClick, shape = RoundedCornerShape(20.dp), pressColor = Color.Transparent),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(64.dp).background(fill, CircleShape), contentAlignment = Alignment.Center) {
            OzIcon(icon, Color.White, 28.dp)
        }
        Text(label, style = OzType.small, color = oz.fg2)
    }
}

@Composable
private fun Active(vm: DialerViewModel, c: CallState) {
    val oz = LocalOz.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(bottom = 32.dp), horizontalArrangement = Arrangement.SpaceAround) {
            // микрофон: мгновенная смена иконки и цвета, без анимации формы
            Toggle(if (c.muted) R.drawable.ic_oz_mic_off else R.drawable.ic_oz_mic, "Микрофон", c.muted, vm::toggleMute)
            Toggle(R.drawable.ic_oz_speaker, "Динамик", c.speaker, vm::toggleSpeaker)
            Toggle(R.drawable.ic_oz_grid, "Клавиши", c.dtmf != null, vm::toggleDtmf)
        }
        Box(
            Modifier
                .padding(bottom = 40.dp)
                .size(64.dp)
                .press(vm::hangup, shape = CircleShape, pressColor = oz.dangerFill, label = "Завершить звонок")
                .background(oz.dangerFill, CircleShape),
            contentAlignment = Alignment.Center,
        ) { OzIcon(R.drawable.ic_oz_end_call, Color.White, 28.dp) }
    }
}

@Composable
private fun Toggle(icon: Int, label: String, on: Boolean, onClick: () -> Unit) {
    val oz = LocalOz.current
    val color = if (on) oz.accent else oz.fg
    Column(
        Modifier
            .width(96.dp)
            .press(onClick, shape = RoundedCornerShape(16.dp), role = Role.Switch)
            .semantics { stateDescription = if (on) "включено" else "выключено" }
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OzIcon(icon, color, 26.dp)
        Text(label, style = OzType.small, color = color)
    }
}

@Composable
private fun Dtmf(vm: DialerViewModel, typed: String) {
    val oz = LocalOz.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(typed.ifEmpty { " " }, style = OzType.digit.copy(fontSize = OzType.nameHome.fontSize), color = oz.fg, modifier = Modifier.height(40.dp))
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            "123456789*0#".chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { d ->
                        Box(
                            Modifier.weight(1f).height(56.dp).press({ vm.dtmf(d) }, shape = RoundedCornerShape(14.dp), label = "Тон $d"),
                            contentAlignment = Alignment.Center,
                        ) { Text(d.toString(), style = OzType.digit.copy(fontSize = OzType.nameHome.fontSize), color = oz.fg) }
                    }
                }
            }
        }
    }
}
