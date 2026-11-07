package com.khizrim.dialer.ui.call

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khizrim.dialer.R
import com.khizrim.dialer.data.PhoneFormat
import com.khizrim.dialer.model.CallPhase
import com.khizrim.dialer.model.CallState
import com.khizrim.dialer.telecom.CallManager
import com.khizrim.dialer.ui.kit.Bounds
import com.khizrim.dialer.ui.kit.MainButton
import com.khizrim.dialer.ui.kit.Opt
import com.khizrim.dialer.ui.kit.OzIcon
import com.khizrim.dialer.ui.kit.QuietButton
import com.khizrim.dialer.ui.kit.Sheet
import com.khizrim.dialer.ui.kit.SheetText
import com.khizrim.dialer.ui.kit.SheetTitle
import com.khizrim.dialer.ui.kit.SimBadge
import com.khizrim.dialer.ui.kit.TextAction
import com.khizrim.dialer.ui.kit.press
import com.khizrim.dialer.ui.kit.rememberBounds
import com.khizrim.dialer.ui.kit.rememberLast
import com.khizrim.dialer.ui.kit.track
import com.khizrim.dialer.ui.nav.Kind
import com.khizrim.dialer.ui.nav.Layer
import com.khizrim.dialer.ui.nav.LayerHost
import com.khizrim.dialer.ui.nav.LocalNav
import com.khizrim.dialer.ui.nav.rememberNav
import com.khizrim.dialer.ui.theme.LocalOz
import com.khizrim.dialer.ui.theme.LocalReducedMotion
import com.khizrim.dialer.ui.theme.Motion
import com.khizrim.dialer.ui.theme.OzType
import com.khizrim.dialer.ui.theme.glass
import com.khizrim.dialer.ui.theme.raisedFill
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

interface CallActions {
    fun answer()
    fun decline()
    fun hangup()
    fun quickSms(text: String)
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

/** Капсула звонка наверху: туда сворачивается экран звонка и оттуда же разворачивается. */
object Capsule {
    val width = 200.dp
    val height = 40.dp
    val top = 6.dp

    fun rect(density: Density, screenWidthPx: Float, statusTopPx: Float): Rect = with(density) {
        val w = width.toPx(); val h = height.toPx()
        Rect(androidx.compose.ui.geometry.Offset(screenWidthPx / 2f - w / 2f, statusTopPx + top.toPx()), Size(w, h))
    }
}

private class MorphShape(val r: Rect, val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(r, CornerRadius(radius)))
}

private enum class CallSheet { AUDIO, MORE, REPLY, BLOCK }

/**
 * Экран звонка вырастает из того, что нажали (или из капсулы, если звонок пришёл сам),
 * сворачивается жестом вниз или «назад» в капсулу и в конце уходит в кнопку «Завершить».
 * [onMinimized] — свёрнут, звонок идёт; [onGone] — звонка больше нет.
 */
@Composable
fun CallHost(actions: CallActions, locked: Boolean, origin: Rect?, onMinimized: () -> Unit, onGone: () -> Unit) {
    val oz = LocalOz.current
    val reduced = LocalReducedMotion.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val live = CallManager.state
    val c = rememberLast(live)
    val endButton = rememberBounds()
    val nav = rememberNav()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val statusTop = WindowInsets.statusBars.getTop(density).toFloat()
        val capsule = remember(w, statusTop) { Capsule.rect(density, w, statusTop) }
        val full = Rect(0f, 0f, w, h)
        val screenRadius = with(density) { 36.dp.toPx() }
        var from by remember { mutableStateOf(origin ?: capsule) }
        var v by remember { mutableFloatStateOf(0f) }
        var leaving by remember { mutableStateOf(false) }

        fun run(target: Float, velocity: Float = 0f, then: () -> Unit = {}) {
            scope.launch {
                animate(v, target, if (reduced) 0f else velocity, if (reduced) Motion.enter(Motion.FADE) else Motion.layer()) { x, _ -> v = x }
                then()
            }
        }
        fun minimize(velocity: Float = 0f) {
            if (leaving) return
            leaving = true
            from = capsule
            run(0f, velocity) { onMinimized() }
        }

        LaunchedEffect(Unit) { if (CallManager.state == null) onGone() else run(1f) }
        // звонка нет: экран уходит в кнопку, которой его завершили
        LaunchedEffect(live == null) {
            if (live != null || leaving) return@LaunchedEffect
            leaving = true
            from = endButton.rect.takeIf { it.width > 0f } ?: Rect(androidx.compose.ui.geometry.Offset(w / 2f, h * 0.85f), 1f)
            run(0f) { onGone() }
        }

        val canMinimize = c != null && (c.phase == CallPhase.ACTIVE || c.phase == CallPhase.DIALING) && c.simChoice == null
        BackHandler { if (canMinimize) minimize() }
        val drag = rememberDraggableState { d -> if (!leaving) { from = capsule; v = (v - d / h).coerceIn(0f, 1f) } }

        CompositionLocalProvider(LocalNav provides nav) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (reduced) { alpha = v; return@graphicsLayer }
                        val r = androidx.compose.ui.geometry.lerp(from, full, v)
                        val rad = from.minDimension / 2f + (screenRadius - from.minDimension / 2f) * v
                        shape = MorphShape(r, rad)
                        clip = true
                    }
                    .background(if (c?.suspicious == true && c.phase == CallPhase.RINGING) oz.warnSoft else oz.bg)
                    .draggable(
                        drag, Orientation.Vertical, enabled = canMinimize && c?.dtmf == null,
                        onDragStopped = { vy -> if (Motion.keeps(1f - v, vy, h)) minimize(-vy / h) else run(1f, -vy / h) },
                    ),
            ) {
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (reduced) 1f else ((v - 0.35f) / 0.45f).coerceIn(0f, 1f) }) {
                    if (c != null) {
                        LayerHost(nav, base = { CallScreen(c, actions, locked, endButton) { nav.open(it, Kind.SHEET) } }) { l ->
                            CallSheets(c, actions, l)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CallScreen(c: CallState, actions: CallActions, locked: Boolean, endButton: Bounds, sheet: (CallSheet) -> Unit) {
    val oz = LocalOz.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(c.phase) { while (c.phase == CallPhase.ACTIVE) { now = System.currentTimeMillis(); delay(500) } }
    val title = callTitle(c)
    val known = c.name != null
    val sub = listOfNotNull(
        when (c.phase) {
            CallPhase.RINGING -> c.label?.replaceFirstChar { it.uppercase() }
            CallPhase.DIALING -> if (c.simChoice != null) stringResource(R.string.choose_sim) else null
            CallPhase.ACTIVE -> if (c.onHold) stringResource(R.string.on_hold) else c.label
            CallPhase.ENDED -> if (c.failed) stringResource(R.string.call_failed_text) else c.endText
        }?.takeIf { it.isNotBlank() },
        c.sim?.takeIf { c.phase != CallPhase.ENDED && c.simChoice == null },
    ).joinToString(" · ")
    val top = when (c.phase) {
        CallPhase.DIALING -> stringResource(R.string.calling) + "…"
        CallPhase.RINGING -> if (locked) "" else stringResource(R.string.incoming_call)
        CallPhase.ACTIVE -> if (c.connectedAt > 0) CallManager.timer(((now - c.connectedAt) / 1000).coerceAtLeast(0)) else ""
        CallPhase.ENDED -> ""
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Text(top, style = OzType.time, color = oz.fg3, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(20.dp))
        val other = c.other
        if (other != null && !other.ringing && c.phase != CallPhase.ENDED) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth().heightIn(min = 48.dp).glass(RoundedCornerShape(24.dp)).padding(start = 18.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.line_on_hold, other.title), style = OzType.small, color = oz.fg2, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (c.phase == CallPhase.ACTIVE) TextAction(stringResource(R.string.swap), oz.accent, actions::swap, style = OzType.small)
            }
        }
        Spacer(Modifier.weight(1f))
        // имя слева, над управлением: от звонка до разговора оно стоит на месте
        Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 20.dp)) {
            if (c.suspicious && c.phase == CallPhase.RINGING) Text(stringResource(R.string.cid_spam), style = OzType.small.copy(fontWeight = FontWeight.Medium), color = oz.warn, modifier = Modifier.padding(bottom = 4.dp))
            Text(
                title, style = OzType.nameCall.copy(fontSize = 36.sp, lineHeight = 40.sp, fontFeatureSettings = if (known) "" else "tnum"),
                color = oz.fg, maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
            if (sub.isNotEmpty()) Text(sub, style = OzType.time, color = if (c.suspicious && c.phase == CallPhase.RINGING) oz.warn else oz.fg3, modifier = Modifier.padding(top = 6.dp).semantics { liveRegion = LiveRegionMode.Polite })
        }
        Box(Modifier.fillMaxWidth().heightIn(min = 300.dp), contentAlignment = Alignment.BottomCenter) {
            when {
                c.phase == CallPhase.RINGING -> Ringing(c, actions, locked, known, sheet)
                c.phase == CallPhase.ENDED -> Ended(c, actions, known, sheet)
                c.simChoice != null -> SimChoice(c.simChoice, actions)
                c.dtmf != null -> Dtmf(c.dtmf, actions, endButton)
                else -> Active(c, actions, endButton, sheet)
            }
        }
        // второй входящий поверх разговора
        AnimatedVisibility(other?.ringing == true && c.phase == CallPhase.ACTIVE, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }) {
            val o = rememberLast(other)
            Column(Modifier.padding(12.dp).fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(vertical = 8.dp)) {
                Text(stringResource(R.string.incoming_call), style = OzType.meta, color = oz.fg3, modifier = Modifier.padding(start = 22.dp, top = 6.dp))
                Text(o?.title.orEmpty(), style = OzType.title, color = oz.fg, modifier = Modifier.padding(start = 22.dp, end = 22.dp, bottom = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Opt(stringResource(R.string.answer_hold), actions::answer, icon = R.drawable.ic_oz_pause, color = oz.accent)
                Opt(stringResource(R.string.end_and_answer), actions::endAndAnswer, icon = R.drawable.ic_oz_phone)
                Opt(stringResource(R.string.decline), actions::decline, icon = R.drawable.ic_oz_end_call, color = oz.danger)
            }
        }
    }
}

@Composable
private fun callTitle(c: CallState): String =
    c.name?.let { n -> if (n.isNotBlank() && n.all { it.isDigit() || it in "+ ()-" }) PhoneFormat.pretty(n) else n }
        ?: c.number.takeIf { it.isNotEmpty() }?.let { PhoneFormat.pretty(it) }
        ?: stringResource(R.string.hidden_number)

@Composable
private fun Ringing(c: CallState, actions: CallActions, locked: Boolean, known: Boolean, sheet: (CallSheet) -> Unit) {
    val oz = LocalOz.current
    Column(Modifier.fillMaxWidth().padding(bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (!locked) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Quick(R.drawable.ic_oz_chat, stringResource(R.string.write)) { sheet(CallSheet.REPLY) }
                if (known && c.number.isNotEmpty()) Quick(R.drawable.ic_oz_remind, stringResource(R.string.remind), actions::remind)
                else if (c.number.isNotEmpty()) Quick(R.drawable.ic_oz_ban, stringResource(R.string.block)) { sheet(CallSheet.BLOCK) }
            }
            Spacer(Modifier.height(28.dp))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 40.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            BigRound(R.drawable.ic_oz_end_call, stringResource(R.string.decline), oz.dangerFill, needsPull = locked, onCommit = actions::decline)
            BigRound(R.drawable.ic_oz_phone, stringResource(R.string.answer), oz.accentFill, needsPull = locked, onCommit = actions::answer)
        }
        if (locked) Text(stringResource(R.string.hold_to_answer), style = OzType.meta, color = oz.fg3, modifier = Modifier.padding(top = 14.dp))
    }
}

@Composable
private fun Quick(icon: Int, label: String, onClick: () -> Unit) {
    val oz = LocalOz.current
    Row(Modifier.heightIn(min = 48.dp).press(onClick, shape = CircleShape, label = label).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        OzIcon(icon, oz.fg2, 18.dp)
        Spacer(Modifier.width(8.dp))
        Text(label, style = OzType.small, color = oz.fg2, maxLines = 1)
    }
}

/**
 * Круглая кнопка 76 dp с подписью. На экране блокировки её тянут вверх: случайное касание в кармане ничего не делает,
 * нажатие только подсказывает движением.
 */
@Composable
private fun BigRound(icon: Int, label: String, fill: Color, needsPull: Boolean, onCommit: () -> Unit) {
    val oz = LocalOz.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var y by remember { mutableFloatStateOf(0f) }
    val far = with(density) { 150.dp.toPx() }
    val enough = with(density) { 110.dp.toPx() }
    val drag = rememberDraggableState { d -> y = (y + d).coerceIn(-far, 0f) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .offset { IntOffset(0, y.roundToInt()) }
                .size(76.dp)
                .raisedFill(fill, CircleShape)
                .draggable(drag, Orientation.Vertical, enabled = needsPull, onDragStopped = { v ->
                    if (y < -enough || v < -900f) { animate(y, -far, v, Motion.pixels()) { x, _ -> y = x }; onCommit(); y = 0f }
                    else animate(y, 0f, v, Motion.pixels(500f)) { x, _ -> y = x }
                })
                .press({
                    if (!needsPull) onCommit()
                    else scope.launch { animate(0f, -30f, 0f, Motion.pixels(400f)) { x, _ -> y = x }; animate(y, 0f, 0f, Motion.pixels(300f)) { x, _ -> y = x } }
                }, shape = CircleShape, pressColor = Color.White.copy(alpha = .14f), label = label),
            contentAlignment = Alignment.Center,
        ) { OzIcon(icon, Color.White, 30.dp) }
        Text(label, style = OzType.small, color = oz.fg2, modifier = Modifier.padding(top = 10.dp))
    }
}

/** Разговор: микрофон, звук, клавиши, «Ещё» и красная кнопка. */
@Composable
private fun Active(c: CallState, actions: CallActions, endButton: Bounds, sheet: (CallSheet) -> Unit) {
    val oz = LocalOz.current
    Column(Modifier.fillMaxWidth().padding(bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Toggle(if (c.muted) R.drawable.ic_oz_mic_off else R.drawable.ic_oz_mic, stringResource(if (c.muted) R.string.call_mic_off else R.string.call_mic), c.muted, actions::toggleMute)
            if (c.hasRouteChoice) Toggle(R.drawable.ic_oz_speaker, routeLabel(c, c.route), c.route != CallState.ROUTE_EARPIECE) { sheet(CallSheet.AUDIO) }
            else Toggle(R.drawable.ic_oz_speaker, stringResource(R.string.speaker), c.speaker, actions::toggleSpeaker)
            if (c.other != null) Toggle(R.drawable.ic_oz_swap, stringResource(R.string.swap), false, actions::swap)
            else Toggle(R.drawable.ic_oz_grid, stringResource(R.string.call_digits), false, actions::toggleDtmf)
            Toggle(R.drawable.ic_oz_more, stringResource(R.string.call_more), c.onHold) { sheet(CallSheet.MORE) }
        }
        Spacer(Modifier.height(28.dp))
        EndButton(actions, endButton)
    }
}

@Composable
private fun EndButton(actions: CallActions, endButton: Bounds) {
    Box(
        Modifier.size(76.dp).track(endButton).raisedFill(LocalOz.current.dangerFill, CircleShape)
            .press(actions::hangup, shape = CircleShape, pressColor = Color.White.copy(alpha = .14f), label = stringResource(R.string.end_call)),
        contentAlignment = Alignment.Center,
    ) { OzIcon(R.drawable.ic_oz_end_call, Color.White, 30.dp) }
}

/** Переключатель звонка: значок в круге 64 dp; включённый — в заливке цвета текста. */
@Composable
private fun Toggle(icon: Int, label: String, on: Boolean, onClick: () -> Unit) {
    val oz = LocalOz.current
    val state = stringResource(if (on) R.string.state_on else R.string.state_off)
    Column(
        Modifier.width(80.dp).press(onClick, shape = RoundedCornerShape(20.dp), label = "$label, $state").padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(64.dp).background(if (on) oz.fg else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) { OzIcon(icon, if (on) oz.bg else oz.fg, 26.dp) }
        Text(label, style = OzType.meta, color = oz.fg2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun Dtmf(typed: String, actions: CallActions, endButton: Bounds) {
    val oz = LocalOz.current
    Column(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(typed, style = OzType.digit, color = oz.fg, maxLines = 1, modifier = Modifier.padding(bottom = 8.dp).height(40.dp))
        listOf("123", "456", "789", "*0#").forEach { row ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                row.forEach { d ->
                    Box(
                        Modifier.weight(1f).height(60.dp).press({ actions.tone(d) }, shape = RoundedCornerShape(18.dp), label = stringResource(R.string.tone, d.toString())),
                        contentAlignment = Alignment.Center,
                    ) { Text(d.toString(), style = OzType.digit, color = oz.fg) }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            EndButton(actions, endButton)
            TextAction(stringResource(R.string.hide), oz.fg2, actions::toggleDtmf, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 28.dp))
        }
    }
}

/** Система спрашивает, с какой SIM звонить: выбор прямо на экране звонка. */
@Composable
private fun SimChoice(labels: List<String>, actions: CallActions) {
    val oz = LocalOz.current
    var remember by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
        labels.forEachIndexed { i, label -> Opt(label, { actions.chooseSim(i, remember) }, lead = { SimBadge(i + 1) }) }
        Opt(stringResource(R.string.sim_remember), { remember = !remember }, checked = remember, color = oz.fg2)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(64.dp).raisedFill(oz.dangerFill, CircleShape).press(actions::hangup, shape = CircleShape, label = stringResource(R.string.cancel)),
                contentAlignment = Alignment.Center,
            ) { OzIcon(R.drawable.ic_oz_end_call, Color.White, 26.dp) }
        }
    }
}

@Composable
private fun Ended(c: CallState, actions: CallActions, known: Boolean, sheet: (CallSheet) -> Unit) {
    val oz = LocalOz.current
    Column(Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
        when {
            c.failed -> {
                MainButton(stringResource(R.string.try_again), actions::callAgain, icon = R.drawable.ic_oz_phone)
                QuietButton(stringResource(R.string.cancel), actions::dismiss)
            }
            !known && c.number.isNotEmpty() -> {
                Opt(stringResource(R.string.add_to_contacts), actions::addContact, icon = R.drawable.ic_oz_user_plus)
                Opt(stringResource(R.string.call_back), actions::callAgain, icon = R.drawable.ic_oz_phone)
                Opt(stringResource(R.string.block), { sheet(CallSheet.BLOCK) }, icon = R.drawable.ic_oz_ban, color = oz.danger)
            }
            else -> Spacer(Modifier.height(76.dp))
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

@Composable
private fun CallSheets(c: CallState, actions: CallActions, l: Layer) {
    val oz = LocalOz.current
    val nav = LocalNav.current
    fun done(action: () -> Unit) { nav.close(l); action() }
    when (l.dest as CallSheet) {
        CallSheet.AUDIO -> Sheet(l) {
            SheetTitle(stringResource(R.string.call_audio_title))
            listOf(CallState.ROUTE_EARPIECE, CallState.ROUTE_SPEAKER, CallState.ROUTE_BLUETOOTH, CallState.ROUTE_WIRED)
                .filter { it and c.routes != 0 }
                .forEach { r ->
                    Opt(routeLabel(c, r), { done { actions.setRoute(r) } }, icon = if (r == CallState.ROUTE_EARPIECE) R.drawable.ic_oz_phone else R.drawable.ic_oz_speaker, checked = c.route == r)
                }
        }
        CallSheet.MORE -> Sheet(l) {
            if (c.canHold && c.phase == CallPhase.ACTIVE) Opt(stringResource(if (c.onHold) R.string.resume else R.string.hold), { done(actions::toggleHold) }, icon = R.drawable.ic_oz_pause)
            if (c.other != null) Opt(stringResource(R.string.call_digits), { done(actions::toggleDtmf) }, icon = R.drawable.ic_oz_grid)
            if (!c.onHold && c.phase == CallPhase.ACTIVE && c.other == null) Opt(stringResource(R.string.add_call), { done(actions::addCall) }, icon = R.drawable.ic_oz_plus)
            if (c.canMerge && c.other != null) Opt(stringResource(R.string.merge), { done(actions::merge) }, icon = R.drawable.ic_oz_users)
            if (c.number.isNotEmpty()) Opt(stringResource(R.string.write), { done(actions::message) }, icon = R.drawable.ic_oz_chat)
            Spacer(Modifier.height(8.dp))
        }
        CallSheet.REPLY -> Sheet(l) {
            SheetTitle(stringResource(R.string.reply_title))
            listOf(R.string.quick_reply, R.string.quick_reply_later, R.string.quick_reply_write).forEach { id ->
                val text = stringResource(id)
                Opt(text, { done { actions.quickSms(text) } }, icon = R.drawable.ic_oz_send)
            }
            Spacer(Modifier.height(8.dp))
        }
        CallSheet.BLOCK -> Sheet(l) {
            SheetTitle(stringResource(R.string.block_title, PhoneFormat.pretty(c.number)))
            SheetText(stringResource(R.string.block_text))
            MainButton(stringResource(R.string.block), { done(actions::block) }, fill = oz.dangerFill)
            QuietButton(stringResource(R.string.cancel), { nav.close(l) })
        }
    }
}

/** Капсула идущего звонка в приложении: имя и время на стекле; нажатие разворачивает звонок. */
@Composable
fun CallCapsule(modifier: Modifier = Modifier) {
    val oz = LocalOz.current
    val context = LocalContext.current
    val s = CallManager.state
    val live = s != null && s.phase != CallPhase.ENDED
    val last = rememberLast(s)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(live) { while (live) { now = System.currentTimeMillis(); delay(500) } }
    AnimatedVisibility(
        live, modifier = modifier.statusBarsPadding().padding(top = Capsule.top),
        enter = fadeIn(Motion.enter(Motion.FADE)), exit = fadeOut(Motion.exit(Motion.FADE)),
    ) {
        val c = last ?: return@AnimatedVisibility
        val time = if (c.phase == CallPhase.ACTIVE && c.connectedAt > 0) CallManager.timer(((now - c.connectedAt) / 1000).coerceAtLeast(0)) else stringResource(R.string.calling)
        val name = callTitle(c)
        Row(
            Modifier
                .size(Capsule.width, Capsule.height)
                .glass(CircleShape)
                .press({ runCatching { context.startActivity(com.khizrim.dialer.telecom.CallActivity.intent(context)) } }, shape = CircleShape,
                    label = stringResource(R.string.back_to_call, name))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).background(if (c.onHold) oz.fg3 else oz.accentFill, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(name, style = OzType.small.copy(fontWeight = FontWeight.Medium), color = oz.fg, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(time, style = OzType.time, color = oz.fg3, modifier = Modifier.padding(start = 8.dp))
        }
    }
}
