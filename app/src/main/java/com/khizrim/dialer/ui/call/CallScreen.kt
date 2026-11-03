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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import com.khizrim.dialer.R
import com.khizrim.dialer.data.PhoneFormat
import com.khizrim.dialer.model.CallPhase
import com.khizrim.dialer.model.CallState
import com.khizrim.dialer.telecom.CallManager
import com.khizrim.dialer.ui.components.OzIcon
import com.khizrim.dialer.ui.components.TextAction
import com.khizrim.dialer.ui.components.press
import com.khizrim.dialer.ui.theme.LocalOz
import com.khizrim.dialer.ui.theme.LocalReducedMotion
import com.khizrim.dialer.ui.theme.Motion
import com.khizrim.dialer.ui.theme.OzType
import kotlinx.coroutines.delay

interface CallActions {
    fun answer()
    fun decline()
    fun hangup()
    fun quickSms()
    fun remind()
    fun block()
    fun toggleMute()
    fun toggleSpeaker()
    fun toggleDtmf()
    fun tone(d: Char)
}

/** Входящий, исходящий и идущий звонок. Без подсказки про канал. */
@Composable
fun CallScreen(c: CallState, actions: CallActions) {
    val oz = LocalOz.current
    val reduced = LocalReducedMotion.current
    val shift = with(LocalDensity.current) { 8.dp.roundToPx() }
    var confirmBlock by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(c.phase) {
        while (c.phase == CallPhase.ACTIVE) { now = System.currentTimeMillis(); delay(500) }
    }
    val known = c.name != null
    val title = c.name ?: c.number.takeIf { it.isNotEmpty() }?.let { PhoneFormat.pretty(it) } ?: stringResource(R.string.hidden_number)
    val sub = when (c.phase) {
        CallPhase.RINGING -> if (known) stringResource(R.string.mobile) else c.label ?: stringResource(R.string.not_in_contacts)
        CallPhase.DIALING -> stringResource(R.string.calling)
        CallPhase.ACTIVE -> if (c.onHold) stringResource(R.string.on_hold) else
            stringResource(R.string.net_phone) + "  " + CallManager.timer(((now - c.connectedAt).coerceAtLeast(0)) / 1000)
        CallPhase.ENDED -> c.endText
    }
    val inCall = c.phase == CallPhase.ACTIVE || c.phase == CallPhase.DIALING

    Box(Modifier.fillMaxSize().background(oz.bg)) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(if (c.phase == CallPhase.RINGING) 72.dp else 160.dp))
            Text(
                title,
                style = if (known) OzType.nameCall else OzType.digit,
                color = oz.fg,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(sub, style = OzType.small, color = oz.fg3, modifier = Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite })
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (inCall && c.dtmf != null) Dtmf(c.dtmf, actions)
            }
            // после «Ответить» кнопки гаснут за 150 мс, управление поднимается на 8 px за 200 мс
            AnimatedContent(
                targetState = when (c.phase) { CallPhase.RINGING -> 0; CallPhase.ENDED -> 2; else -> 1 },
                transitionSpec = {
                    val enter = if (reduced) fadeIn(Motion.enter(Motion.FADE)) else
                        fadeIn(Motion.enter(Motion.MOVE, delay = Motion.FADE)) + slideInVertically(Motion.enter(Motion.MOVE, delay = Motion.FADE)) { shift }
                    enter togetherWith fadeOut(Motion.exit(Motion.FADE))
                },
                label = "call-phase",
            ) { phase ->
                when (phase) {
                    0 -> Ringing(actions, known, onBlock = { confirmBlock = true })
                    1 -> Active(c, actions)
                    else -> Spacer(Modifier.fillMaxWidth().height(144.dp))
                }
            }
        }
        if (confirmBlock) {
            BlockConfirm(
                number = PhoneFormat.pretty(c.number),
                onCancel = { confirmBlock = false },
                onBlock = { confirmBlock = false; actions.block() },
            )
        }
    }
}

@Composable
private fun Ringing(actions: CallActions, known: Boolean, onBlock: () -> Unit) {
    val oz = LocalOz.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(bottom = 28.dp), horizontalArrangement = Arrangement.SpaceAround) {
            TextAction(stringResource(R.string.send_sms), oz.fg2, actions::quickSms, style = OzType.small)
            if (known) TextAction(stringResource(R.string.remind), oz.fg2, actions::remind, style = OzType.small)
            else TextAction(stringResource(R.string.block), oz.fg2, onBlock, style = OzType.small)
        }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            LabeledRound(R.drawable.ic_oz_end_call, stringResource(R.string.decline), oz.dangerFill, actions::decline)
            LabeledRound(R.drawable.ic_oz_phone, stringResource(R.string.answer), oz.accentFill, actions::answer)
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
private fun Active(c: CallState, actions: CallActions) {
    val oz = LocalOz.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(bottom = 32.dp), horizontalArrangement = Arrangement.SpaceAround) {
            // микрофон: мгновенная смена иконки и цвета, без анимации формы
            Toggle(if (c.muted) R.drawable.ic_oz_mic_off else R.drawable.ic_oz_mic, stringResource(R.string.mute), c.muted, actions::toggleMute)
            Toggle(R.drawable.ic_oz_speaker, stringResource(R.string.speaker), c.speaker, actions::toggleSpeaker)
            Toggle(R.drawable.ic_oz_grid, stringResource(R.string.keypad), c.dtmf != null, actions::toggleDtmf)
        }
        Box(
            Modifier
                .padding(bottom = 40.dp)
                .size(64.dp)
                .press(actions::hangup, shape = CircleShape, pressColor = oz.dangerFill, label = stringResource(R.string.end_call))
                .background(oz.dangerFill, CircleShape),
            contentAlignment = Alignment.Center,
        ) { OzIcon(R.drawable.ic_oz_end_call, Color.White, 28.dp) }
    }
}

@Composable
private fun Toggle(icon: Int, label: String, on: Boolean, onClick: () -> Unit) {
    val oz = LocalOz.current
    val color = if (on) oz.accent else oz.fg
    val stateText = stringResource(if (on) R.string.state_on else R.string.state_off)
    Column(
        Modifier
            .width(96.dp)
            .press(onClick, shape = RoundedCornerShape(16.dp), role = Role.Switch)
            .semantics { stateDescription = stateText }
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OzIcon(icon, color, 26.dp)
        Text(label, style = OzType.small, color = color)
    }
}

@Composable
private fun Dtmf(typed: String, actions: CallActions) {
    val oz = LocalOz.current
    val big = OzType.digit.copy(fontSize = OzType.nameHome.fontSize)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(typed.ifEmpty { " " }, style = big, color = oz.fg, modifier = Modifier.height(40.dp))
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            "123456789*0#".chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { d ->
                        Box(
                            Modifier.weight(1f).height(56.dp).press({ actions.tone(d) }, shape = RoundedCornerShape(14.dp), label = stringResource(R.string.tone, d.toString())),
                            contentAlignment = Alignment.Center,
                        ) { Text(d.toString(), style = big, color = oz.fg) }
                    }
                }
            }
        }
    }
}

/** Подтверждение внизу, у большого пальца. */
@Composable
private fun BlockConfirm(number: String, onCancel: () -> Unit, onBlock: () -> Unit) {
    val oz = LocalOz.current
    Box(Modifier.fillMaxSize().background(oz.scrim).press(onCancel, shape = RoundedCornerShape(0.dp), pressColor = oz.scrim)) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(12.dp)
                .fillMaxWidth()
                .press({}, shape = RoundedCornerShape(24.dp), pressColor = oz.sheet)
                .background(oz.sheet, RoundedCornerShape(24.dp))
                .padding(24.dp),
        ) {
            Text(stringResource(R.string.block_title, number), style = OzType.title, color = oz.fg)
            Text(
                stringResource(R.string.block_text),
                style = OzType.small, color = oz.fg3, modifier = Modifier.padding(top = 8.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.End) {
                TextAction(stringResource(R.string.cancel), oz.fg, onCancel, style = OzType.small.copy(fontWeight = FontWeight.SemiBold))
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .press(onBlock, shape = RoundedCornerShape(22.dp), pressColor = oz.dangerFill)
                        .background(oz.dangerFill, RoundedCornerShape(22.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) { Text(stringResource(R.string.block), style = OzType.small.copy(fontWeight = FontWeight.SemiBold), color = Color.White) }
            }
        }
    }
}
