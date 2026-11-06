package com.khizrim.dialer.ui.call

import androidx.compose.ui.semantics.disabled

import androidx.compose.ui.graphics.graphicsLayer

import com.khizrim.dialer.ui.theme.raisedFill
import com.khizrim.dialer.ui.theme.well
import com.khizrim.dialer.ui.theme.lift
import com.khizrim.dialer.ui.theme.OzShape

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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.gestures.draggable
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
    fun endAndAnswer()
    fun toggleHold()
    fun swap()
    fun chooseSim(index: Int, remember: Boolean)
    fun setRoute(route: Int)
    fun merge()
    fun addCall()
    fun message()
    fun addContact()
    fun callAgain()
    fun dismiss()
}

/** Входящий, исходящий и идущий звонок. Без подсказки про канал. */
@Composable
fun CallScreen(c: CallState, actions: CallActions, locked: Boolean = false) {
    val oz = LocalOz.current
    val reduced = LocalReducedMotion.current
    var confirmBlock by remember { mutableStateOf(false) }
    var routePicker by remember { mutableStateOf(false) }
    var moreSheet by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(c.phase) {
        while (c.phase == CallPhase.ACTIVE) { now = System.currentTimeMillis(); delay(500) }
    }
    val known = c.name != null
    // имя-номер (контакт без имени, CNAP с цифрами) показываем с маской, как сам номер
    val title = c.name?.let { n -> if (n.isNotBlank() && n.all { it.isDigit() || it in "+ ()-" }) PhoneFormat.pretty(n) else n } ?: c.number.takeIf { it.isNotEmpty() }?.let { PhoneFormat.pretty(it) } ?: stringResource(R.string.hidden_number)
    val subBase = when (c.phase) {
        CallPhase.RINGING -> if (known) (c.label ?: "").replaceFirstChar { it.uppercase() } else c.label ?: ""
        CallPhase.DIALING -> if (c.simChoice != null) stringResource(R.string.choose_sim) else stringResource(R.string.calling)
        CallPhase.ACTIVE -> if (c.onHold) stringResource(R.string.on_hold) else
            CallManager.timer(((now - c.connectedAt).coerceAtLeast(0)) / 1000)
        CallPhase.ENDED -> if (c.failed) stringResource(R.string.call_failed_text) else c.endText
    }
    val sub = if (c.sim != null && c.phase != CallPhase.ENDED && c.simChoice == null) "$subBase  ${c.sim}" else subBase
    val inCall = c.phase == CallPhase.ACTIVE || c.phase == CallPhase.DIALING

    // «Похоже на спам»: тёплый фон сверху, спокойно, без тревожного красного
    val bgBrush = if (c.suspicious) androidx.compose.ui.graphics.Brush.verticalGradient(0f to oz.warnSoft, .7f to oz.bg) else androidx.compose.ui.graphics.SolidColor(oz.bg)
    Box(Modifier.fillMaxSize().background(bgBrush)) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            c.other?.let { Box(Modifier.padding(top = 16.dp)) { OtherLineRow(it, c.phase, actions) } }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (c.simChoice != null) SimChoice(c.simChoice, actions)
                else if (inCall && c.dtmf != null) Dtmf(c.dtmf, actions)
            }
            // имя слева прямо над кнопками: прочитал и нажал, глаза не бегают через весь экран.
            // На входящем, в разговоре и после него оно стоит на одном месте — после «Ответить» экран не прыгает
            Column(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 32.dp)) {
                Text(
                    title,
                    style = if (known) OzType.nameCall else OzType.callNumber,
                    maxLines = if (known) 2 else 1,
                    softWrap = known,
                    color = oz.fg,
                    modifier = Modifier.semantics { heading() },
                )
                Text(sub, style = OzType.time, color = if (c.suspicious && c.phase == CallPhase.RINGING) oz.warn else oz.fg3, modifier = Modifier.padding(top = 6.dp).semantics { liveRegion = LiveRegionMode.Polite })
            }
            // после «Ответить» кнопки гаснут за 150 мс, управление поднимается на 8 px за 200 мс
            AnimatedContent(
                targetState = when (c.phase) { CallPhase.RINGING -> 0; CallPhase.ENDED -> 2; else -> 1 },
                transitionSpec = {
                    // кнопки гаснут за 100 мс, новое управление проявляется с подъёмом 8 dp; пустой паузы между ними нет
                    (fadeIn(Motion.enter(Motion.MOVE, delay = 60)) + slideInVertically(Motion.move(Motion.MOVE)) { it / 12 }) togetherWith
                        fadeOut(Motion.exit(Motion.PRESS)) using
                        androidx.compose.animation.SizeTransform(clip = false) { _, _ -> Motion.move(Motion.MOVE) }
                },
                contentAlignment = Alignment.BottomCenter,
                label = "call-phase",
            ) { phase ->
                when (phase) {
                    0 -> Ringing(actions, known, secondLine = c.other != null, locked = locked, onBlock = { confirmBlock = true })
                    1 -> Active(c, actions, onRoutes = { routePicker = true }, onMore = { moreSheet = true })
                    else -> Ended(c, actions, onBlock = { confirmBlock = true })
                }
            }
        }
        if (moreSheet) {
            MoreSheet(c, actions, onDone = { moreSheet = false })
        }
        if (routePicker) {
            RoutePicker(c, onPick = { routePicker = false; actions.setRoute(it) }, onCancel = { routePicker = false })
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
private fun Ringing(actions: CallActions, known: Boolean, secondLine: Boolean, locked: Boolean, onBlock: () -> Unit) {
    val oz = LocalOz.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // быстрые действия — кнопки со значком над тем кругом, к которому они ближе: SMS над «Отклонить», напомнить — над «Ответить»
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 20.dp), horizontalArrangement = if (secondLine) Arrangement.Center else Arrangement.SpaceBetween) {
            if (secondLine) {
                // второй звонок во время разговора: «Ответить» удержит текущий
                TextAction(stringResource(R.string.end_and_answer), oz.fg2, actions::endAndAnswer, style = OzType.small)
            } else {
                Toggle(R.drawable.ic_oz_sms, stringResource(R.string.send_sms), false, actions::quickSms, switch = false, modifier = Modifier.widthIn(min = 96.dp))
                if (known) Toggle(R.drawable.ic_oz_remind, stringResource(R.string.remind), false, actions::remind, switch = false, modifier = Modifier.widthIn(min = 96.dp))
                else Toggle(R.drawable.ic_oz_ban, stringResource(R.string.block), false, onBlock, switch = false, modifier = Modifier.widthIn(min = 96.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            // на заблокированном экране круги тянут: «Отклонить» вниз, «Ответить» вверх — в кармане случайно не сработает
            if (locked) {
                SlideRound(R.drawable.ic_oz_end_call, stringResource(R.string.decline), oz.dangerFill, up = false, onCommit = actions::decline)
                SlideRound(R.drawable.ic_oz_phone, stringResource(if (secondLine) R.string.answer_hold else R.string.answer), oz.accentFill, up = true, onCommit = actions::answer)
            } else {
                LabeledRound(R.drawable.ic_oz_end_call, stringResource(R.string.decline), oz.dangerFill, actions::decline)
                LabeledRound(R.drawable.ic_oz_phone, stringResource(if (secondLine) R.string.answer_hold else R.string.answer), oz.accentFill, actions::answer)
            }
        }
    }
}

/**
 * Круг, который тянут по вертикали (заблокированный экран): идёт за пальцем 1:1, на трети дорожки (≈50 dp)
 * или после броска в свою сторону срабатывает, отпустил раньше — возвращается. Дорожка видна, только пока палец на круге.
 */
@Composable
private fun SlideRound(icon: Int, label: String, fill: Color, up: Boolean, onCommit: () -> Unit) {
    val oz = LocalOz.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val track = with(density) { 150.dp.toPx() }
    val threshold = with(density) { 50.dp.toPx() }
    val fling = with(density) { 900.dp.toPx() }
    val offset = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var held by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    val sign = if (up) -1f else 1f
    val trackAlpha by androidx.compose.animation.core.animateFloatAsState(if (held) 1f else 0f, Motion.enter(Motion.FADE), label = "track")
    val drag = androidx.compose.foundation.gestures.rememberDraggableState { d ->
        // только в свою сторону и не дальше дорожки
        val next = (offset.value + d).let { if (up) it.coerceIn(-track, 0f) else it.coerceIn(0f, track) }
        scope.launch { offset.snapTo(next) }
    }
    Column(
        Modifier.widthIn(min = 104.dp).padding(8.dp)
            .semantics { contentDescription = label; onClick(label) { onCommit(); true } },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(64.dp)
                .drawBehind {
                    if (trackAlpha > 0f) {
                        val r = size.width / 2
                        val top = if (up) -track else 0f
                        drawRoundRect(
                            fill.copy(alpha = .16f * trackAlpha),
                            topLeft = androidx.compose.ui.geometry.Offset(0f, top),
                            size = androidx.compose.ui.geometry.Size(size.width, size.height + track),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                        )
                    }
                }
                .graphicsLayer { translationY = offset.value }
                // тап по кругу на заблокированном экране не отвечает, а показывает, куда его тянуть: круг с дорожкой подаётся в свою сторону и возвращается
                .pointerInput(Unit) {
                    detectTapGestures {
                        if (done) return@detectTapGestures
                        scope.launch {
                            held = true
                            offset.animateTo(sign * threshold * 0.6f, Motion.enter(Motion.MOVE))
                            offset.animateTo(0f, Motion.settle())
                            held = false
                        }
                    }
                }
                .draggable(
                    drag, androidx.compose.foundation.gestures.Orientation.Vertical,
                    enabled = !done,
                    onDragStarted = { held = true },
                    onDragStopped = { v ->
                        held = false
                        if (offset.value * sign >= threshold || v * sign >= fling) { done = true; onCommit() }
                        else offset.animateTo(0f, Motion.settle(), v)
                    },
                )
                .raisedFill(fill, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            OzIcon(icon, Color.White, 28.dp)
        }
        Text(label, style = OzType.small, color = oz.fg2)
    }
}

@Composable
private fun LabeledRound(icon: Int, label: String, fill: Color, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    val oz = LocalOz.current
    Column(
        // поле нажатия шире круга: подпись и тень не обрезаются по его краю
        Modifier.widthIn(min = 104.dp).press(onClick, onLongClick, shape = RoundedCornerShape(16.dp), pressColor = Color.Transparent).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(64.dp).raisedFill(fill, CircleShape), contentAlignment = Alignment.Center) {
            OzIcon(icon, Color.White, 28.dp)
        }
        Text(label, style = OzType.small, color = oz.fg2)
    }
}

@Composable
private fun Active(c: CallState, actions: CallActions, onRoutes: () -> Unit, onMore: () -> Unit) {
    val oz = LocalOz.current
    val two = c.other != null && !c.other.ringing
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // одна линия: то, что нужно в каждом разговоре. Удержать, добавить и написать — в «Ещё».
        // Идёт второй звонок — «Поменять» и «Объединить» встают в линию сами, пока они нужны
        Row(Modifier.fillMaxWidth().padding(bottom = 28.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            val cell = Modifier.weight(1f)
            // микрофон: мгновенная смена иконки и цвета, без анимации формы
            Toggle(if (c.muted) R.drawable.ic_oz_mic_off else R.drawable.ic_oz_mic, stringResource(if (c.muted) R.string.call_mic_off else R.string.call_mic), c.muted, actions::toggleMute, modifier = cell)
            if (c.hasRouteChoice) {
                // есть наушники или Bluetooth: подпись говорит, куда идёт звук, тап открывает выбор
                Toggle(R.drawable.ic_oz_speaker, routeLabel(c, c.route), c.route != CallState.ROUTE_EARPIECE, onRoutes, modifier = cell)
            } else {
                Toggle(R.drawable.ic_oz_speaker, stringResource(R.string.speaker), c.speaker, actions::toggleSpeaker, modifier = cell)
            }
            if (two) {
                Toggle(R.drawable.ic_oz_swap, stringResource(R.string.swap), false, actions::swap, switch = false, modifier = cell)
                if (c.canMerge) Toggle(R.drawable.ic_oz_users, stringResource(R.string.merge), false, actions::merge, switch = false, modifier = cell)
            } else {
                Toggle(R.drawable.ic_oz_grid, stringResource(R.string.call_digits), c.dtmf != null, actions::toggleDtmf, modifier = cell)
            }
            // на удержании «Ещё» светится: там кнопка «Продолжить»
            Toggle(R.drawable.ic_oz_more, stringResource(R.string.call_more), c.onHold, onMore, switch = false, modifier = cell)
        }
        Box(
            Modifier
                .padding(bottom = 40.dp)
                .size(64.dp)
                .raisedFill(oz.dangerFill, CircleShape)
                .press(actions::hangup, shape = CircleShape, pressColor = Color.White.copy(alpha = .12f), label = stringResource(R.string.end_call)),
            contentAlignment = Alignment.Center,
        ) { OzIcon(R.drawable.ic_oz_end_call, Color.White, 28.dp) }
    }
}

/** «Ещё» в разговоре: редкие действия словами, лист снизу. Недоступное не показываем. */
@Composable
private fun MoreSheet(c: CallState, actions: CallActions, onDone: () -> Unit) {
    val oz = LocalOz.current
    val two = c.other != null && !c.other.ringing
    val rows = buildList {
        if (two) add(Triple(R.drawable.ic_oz_grid, R.string.call_digits, actions::toggleDtmf))
        if (c.canHold && c.phase == CallPhase.ACTIVE && !two) add(Triple(R.drawable.ic_oz_pause, if (c.onHold) R.string.resume else R.string.hold, actions::toggleHold))
        if (!c.onHold && c.phase == CallPhase.ACTIVE && !two) add(Triple(R.drawable.ic_oz_plus, R.string.add_call, actions::addCall))
        if (c.number.isNotEmpty()) add(Triple(R.drawable.ic_oz_sms, R.string.write, actions::message))
    }
    Box(Modifier.fillMaxSize().background(oz.scrim).press(onDone, shape = RoundedCornerShape(0.dp), pressColor = oz.scrim)) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(12.dp)
                .fillMaxWidth()
                .lift(OzShape.sheet)
                .press({}, shape = OzShape.sheet, pressColor = oz.sheet)
                .background(oz.sheet, OzShape.sheet)
                .padding(vertical = 12.dp),
        ) {
            rows.forEach { (icon, label, act) ->
                Row(
                    Modifier.fillMaxWidth().height(56.dp).press({ onDone(); act() }, shape = RoundedCornerShape(16.dp)).padding(horizontal = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OzIcon(icon, oz.fg2, 22.dp)
                    Text(stringResource(label), style = OzType.list, color = oz.fg, modifier = Modifier.padding(start = 16.dp))
                }
            }
        }
    }
}

/** Кнопка звонка: круг 64dp без заливки, включённая — в «лунке» и цветом. Недоступная — бледная. */
@Composable
private fun Toggle(icon: Int, label: String, on: Boolean, onClick: () -> Unit, enabled: Boolean = true, switch: Boolean = true, modifier: Modifier = Modifier.width(96.dp)) {
    val oz = LocalOz.current
    val color = if (on) oz.accent else oz.fg
    val stateText = stringResource(if (on) R.string.state_on else R.string.state_off)
    Column(
        modifier
            .graphicsLayer { alpha = if (enabled) 1f else .38f }
            .press(if (enabled) onClick else ({}), shape = OzShape.row, role = if (switch) Role.Switch else Role.Button, label = label)
            .let { m -> if (switch) m.semantics { stateDescription = stateText } else m }
            .let { m -> if (!enabled) m.semantics { disabled() } else m }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(64.dp).well(CircleShape, on), contentAlignment = Alignment.Center) { OzIcon(icon, color, 26.dp) }
        Text(label, style = OzType.label, color = color, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

@Composable
private fun Dtmf(typed: String, actions: CallActions) {
    val oz = LocalOz.current
    val big = OzType.digit.copy(fontSize = OzType.nameHome.fontSize)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(typed.ifEmpty { " " }, style = big, color = oz.fg, maxLines = 1, modifier = Modifier.heightIn(min = 40.dp))
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            "123456789*0#".chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { d ->
                        Box(
                            Modifier.weight(1f).height(56.dp).press({ actions.tone(d) }, shape = RoundedCornerShape(16.dp), label = stringResource(R.string.tone, d.toString())),
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
                .lift(OzShape.sheet)
                .press({}, shape = OzShape.sheet, pressColor = oz.sheet)
                .background(oz.sheet, OzShape.sheet)
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
                        .press(onBlock, shape = RoundedCornerShape(12.dp), pressColor = oz.dangerFill)
                        .background(oz.dangerFill, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) { Text(stringResource(R.string.block), style = OzType.small.copy(fontWeight = FontWeight.SemiBold), color = Color.White) }
            }
        }
    }
}

@Composable
private fun routeLabel(c: CallState, route: Int): String = when (route) {
    CallState.ROUTE_BLUETOOTH -> c.bluetoothName ?: stringResource(R.string.route_bluetooth)
    CallState.ROUTE_WIRED -> stringResource(R.string.route_wired)
    CallState.ROUTE_SPEAKER -> stringResource(R.string.speaker)
    else -> stringResource(R.string.route_earpiece)
}

/** Вторая линия под именем: на удержании или входящий. Тап меняет линии местами. */
@Composable
private fun OtherLineRow(o: com.khizrim.dialer.model.OtherLine, phase: CallPhase, actions: CallActions) {
    val oz = LocalOz.current
    if (o.ringing) return // входящий второй показан целиком, текущий виден в подписи ниже
    val text = stringResource(R.string.line_on_hold, o.title)
    Row(
        Modifier
            .padding(top = 16.dp)
            .press(actions::swap, shape = RoundedCornerShape(16.dp), label = text + ". " + stringResource(R.string.swap))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = OzType.small, color = oz.fg2)
        if (phase == CallPhase.ACTIVE) {
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.swap), style = OzType.small, color = oz.accent)
        }
    }
}

/** Система спрашивает SIM: крупные строки у большого пальца. */
@Composable
private fun SimChoice(labels: List<String>, actions: CallActions) {
    val oz = LocalOz.current
    var remember by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        labels.forEachIndexed { i, label ->
            Row(
                Modifier.fillMaxWidth().height(56.dp).press({ actions.chooseSim(i, remember) }, shape = RoundedCornerShape(16.dp)).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OzIcon(R.drawable.ic_oz_sim, oz.fg2)
                Text(label, style = OzType.list, color = oz.fg)
            }
        }
        // «Запомнить для этого номера»: в следующий раз позвоним с той же SIM без вопроса
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .press({ remember = !remember }, shape = RoundedCornerShape(16.dp), role = androidx.compose.ui.semantics.Role.Checkbox)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.size(22.dp).border(1.5.dp, if (remember) oz.accent else oz.fg3, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                if (remember) OzIcon(R.drawable.ic_oz_check, oz.accent, 16.dp)
            }
            Text(stringResource(R.string.sim_remember), style = OzType.body, color = oz.fg2)
        }
    }
}

/** Выбор, куда идёт звук: телефон, динамик, наушники, Bluetooth. */
@Composable
private fun RoutePicker(c: CallState, onPick: (Int) -> Unit, onCancel: () -> Unit) {
    val oz = LocalOz.current
    val options = listOf(CallState.ROUTE_BLUETOOTH, CallState.ROUTE_WIRED, CallState.ROUTE_EARPIECE, CallState.ROUTE_SPEAKER)
        .filter { c.routes and it != 0 }
    Box(Modifier.fillMaxSize().background(oz.scrim).press(onCancel, shape = RoundedCornerShape(0.dp), pressColor = oz.scrim)) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(12.dp)
                .fillMaxWidth()
                .lift(OzShape.sheet)
                .press({}, shape = OzShape.sheet, pressColor = oz.sheet)
                .background(oz.sheet, OzShape.sheet)
                .padding(vertical = 12.dp),
        ) {
            options.forEach { r ->
                val on = c.route == r
                Row(
                    Modifier.fillMaxWidth().height(56.dp).press({ onPick(r) }, shape = RoundedCornerShape(16.dp)).padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(routeLabel(c, r), style = OzType.list, color = if (on) oz.accent else oz.fg, modifier = Modifier.weight(1f))
                    if (on) OzIcon(R.drawable.ic_oz_check, oz.accent, 20.dp)
                }
            }
        }
    }
}

/** Конец звонка: с незнакомым — «Добавить в контакты», с ошибкой — «Попробовать снова». */
@Composable
private fun Ended(c: CallState, actions: CallActions, onBlock: () -> Unit) {
    val oz = LocalOz.current
    val unknown = c.name == null && c.number.isNotEmpty()
    Column(Modifier.fillMaxWidth().padding(bottom = 32.dp).heightIn(min = 144.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
        if (c.failed) {
            Text(stringResource(R.string.call_failed), style = OzType.title, color = oz.fg, modifier = Modifier.padding(bottom = 20.dp))
            Box(
                Modifier.fillMaxWidth().height(56.dp).raisedFill(oz.accentFill, OzShape.main)
                    .press(actions::callAgain, shape = OzShape.main, pressColor = Color.White.copy(alpha = .12f)),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.try_again), style = OzType.strong, color = Color.White) }
            TextAction(stringResource(R.string.cancel), oz.fg2, actions::dismiss, modifier = Modifier.padding(top = 8.dp))
        } else if (unknown && c.connectedAt > 0) {
            Box(
                Modifier.fillMaxWidth().height(56.dp).well(OzShape.main).press(actions::addContact, shape = OzShape.main),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.add_to_contacts), style = OzType.strong, color = oz.accent) }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceAround) {
                TextAction(stringResource(R.string.call_back), oz.fg2, actions::callAgain, style = OzType.small)
                TextAction(stringResource(R.string.block), oz.fg2, onBlock, style = OzType.small)
            }
        }
    }
}
