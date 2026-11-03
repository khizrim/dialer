package com.khizrim.dialer.ui.components

import com.khizrim.dialer.ui.theme.raisedFill
import com.khizrim.dialer.ui.theme.well
import com.khizrim.dialer.ui.theme.lift
import com.khizrim.dialer.ui.theme.OzShape

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.composed
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.khizrim.dialer.R
import com.khizrim.dialer.ui.theme.LocalOz
import com.khizrim.dialer.ui.theme.LocalReducedMotion
import com.khizrim.dialer.ui.theme.Motion
import com.khizrim.dialer.ui.theme.OzType

@Composable
fun OzIcon(@DrawableRes id: Int, tint: Color, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    Icon(painterResource(id), contentDescription = null, tint = tint, modifier = modifier.size(size))
}

/**
 * Тихая нажимаемая область: без ряби, лёгкая подложка при нажатии
 * и сжатие до 0.98 за 100 мс (без сжатия в режиме «меньше движения»).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.ozPressImpl(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    shape: Shape,
    pressColor: Color?,
    label: String?,
    role: Role,
): Modifier {
    val oz = LocalOz.current
    val reduced = LocalReducedMotion.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val guard = remember { TapGuard.Touch() }
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduced) 0.98f else 1f,
        animationSpec = Motion.enter(Motion.PRESS),
        label = "press",
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(shape)
        .background(if (pressed) pressColor ?: oz.press else Color.Transparent)
        .pointerInput(Unit) { trackTouch(guard) }
        .combinedClickable(
            interactionSource = source,
            indication = null,
            role = role,
            onClickLabel = null,
            onLongClickLabel = if (onLongClick != null) stringResource(R.string.menu) else null,
            onLongClick = onLongClick?.let { l -> { if (guard.sameScreen()) l() } },
            onClick = { if (guard.isTap()) onClick() },
        )
        .let { if (label != null) it.semantics { contentDescription = label } else it }
}

/**
 * Нажатие считается только чистым касанием: палец не уехал дальше touch slop,
 * никто выше (список, свайп стопки, шторка) не забрал движение и экран не сменился,
 * пока палец был на стекле. Любой жест с движением вызовом не заканчивается.
 */
object TapGuard {
    /** Растёт при каждой смене экрана; касание, начатое на прошлом экране, не нажимает. */
    @Volatile var gen = 0
    class Touch {
        var gen = 0; var moved = false; var active = false
        // нажатие из TalkBack приходит без касания (active = false) — его не трогаем
        fun isTap() = !active || (!moved && gen == TapGuard.gen)
        fun sameScreen() = !active || gen == TapGuard.gen
    }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.trackTouch(t: TapGuard.Touch) {
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        t.gen = TapGuard.gen; t.moved = false; t.active = true
        var dx = 0f; var dy = 0f
        try { while (true) {
            // Final — после всех: видно, если список или родительский жест забрал движение
            val ev = awaitPointerEvent(PointerEventPass.Final)
            val c = ev.changes.firstOrNull { it.id == down.id } ?: break
            if (!c.pressed) break
            dx += c.position.x - c.previousPosition.x; dy += c.position.y - c.previousPosition.y
            // первое событие здесь — тот же down в Final pass, его кнопка уже поглотила сама.
            // Любое следующее поглощённое событие значит, что движение забрал кто-то другой:
            // список, свайп стопки или шторка, которая едет вместе с пальцем (тогда своя позиция
            // строки не меняется, поэтому смотрим именно на поглощение, а не на сдвиг)
            if ((c.isConsumed && !c.changedToDownIgnoreConsumed()) || dx * dx + dy * dy > slop * slop) t.moved = true
        } } finally { t.active = false }
    }
}

/** Используйте этот модификатор внутри @Composable. */
@Composable
fun Modifier.press(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(16.dp),
    pressColor: Color? = null,
    label: String? = null,
    role: Role = Role.Button,
): Modifier = ozPressImpl(onClick, onLongClick, shape, pressColor, label, role)

/** Круглая залитая кнопка: клавиатура, вызов, ответ, сброс. */
@Composable
fun RoundButton(
    @DrawableRes icon: Int,
    label: String,
    fill: Color,
    onClick: () -> Unit,
    size: Dp = 64.dp,
    iconSize: Dp = 26.dp,
) {
    Box(
        Modifier
            .size(size)
            .raisedFill(fill, CircleShape)
            .press(onClick, shape = CircleShape, pressColor = Color.White.copy(alpha = .12f), label = label),
        contentAlignment = Alignment.Center,
    ) {
        OzIcon(icon, Color.White, iconSize)
    }
}

/** Круглая кнопка без заливки: «назад», звезда, стереть. */
@Composable
fun GhostIconButton(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    tint: Color = LocalOz.current.fg,
    size: Dp = 48.dp,
    iconSize: Dp = 24.dp,
    onLongClick: (() -> Unit)? = null,
) {
    Box(
        Modifier.size(size).press(onClick, onLongClick, CircleShape, label = label),
        contentAlignment = Alignment.Center,
    ) { OzIcon(icon, tint, iconSize) }
}

/** Тихая текстовая кнопка. */
@Composable
fun TextAction(
    text: String,
    color: Color,
    onClick: () -> Unit,
    style: TextStyle = OzType.body,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .heightIn(min = 44.dp)
            .press(onClick, shape = RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) { Text(text, style = style, color = color) }
}

@Composable
fun BottomBar(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 24.dp, top = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun KeypadFab(onClick: () -> Unit) {
    RoundButton(R.drawable.ic_oz_grid, stringResource(R.string.open_keypad), LocalOz.current.accentFill, onClick)
}


/** Плашка идущего звонка: «Звонок: X · Вернуться» или «Входящий: X · Ответить». Тап открывает экран звонка. */
@androidx.compose.runtime.Composable
fun CallStrip(modifier: Modifier = Modifier) {
    val oz = com.khizrim.dialer.ui.theme.LocalOz.current
    val live = com.khizrim.dialer.telecom.CallManager.state ?: return
    if (live.phase == com.khizrim.dialer.model.CallPhase.ENDED) return
    val context = androidx.compose.ui.platform.LocalContext.current
    val who = live.name ?: com.khizrim.dialer.data.PhoneFormat.pretty(live.number)
    androidx.compose.material3.Text(
        if (live.phase == com.khizrim.dialer.model.CallPhase.RINGING) androidx.compose.ui.res.stringResource(com.khizrim.dialer.R.string.incoming_strip, who)
        else androidx.compose.ui.res.stringResource(com.khizrim.dialer.R.string.back_to_call, who),
        style = com.khizrim.dialer.ui.theme.OzType.small,
        color = oz.accent,
        maxLines = 1,
        modifier = modifier
            .lift(OzShape.row)
            .background(oz.sheet, com.khizrim.dialer.ui.theme.OzShape.row)
            .clickable { context.startActivity(com.khizrim.dialer.telecom.CallActivity.intent(context)) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    )
}



/**
 * Строку смахивают влево: она идёт за пальцем и гаснет; дальше 96 dp — уходит совсем и вызывает onSwiped,
 * иначе пружинит обратно. Вправо не двигается: этот жест сознательно пустой.
 */
fun Modifier.swipeAway(enabled: Boolean = true, onRight: (() -> Unit)? = null, onSwiped: () -> Unit): Modifier = if (!enabled && onRight == null) this else composed {
    // порог в пикселях: по умолчанию 0.01 px, и пружина «доезжает» лишние полсекунды
    val offset = remember { androidx.compose.animation.core.Animatable(0f, visibilityThreshold = 0.5f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val threshold = with(density) { 96.dp.toPx() }
    val reduced = com.khizrim.dialer.ui.theme.LocalReducedMotion.current
    val tracker = remember { androidx.compose.ui.input.pointer.util.VelocityTracker() }
    val w = remember { floatArrayOf(1f) }
    this
        .onWidth { w[0] = it }
        .pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { tracker.resetTracking() },
                onDragEnd = {
                    val v = tracker.calculateVelocity().x
                    scope.launch {
                        val settle = com.khizrim.dialer.ui.theme.Motion.settle<Float>()
                        if (offset.value > threshold && onRight != null) {
                            // вправо — действие без удаления: строка спокойно возвращается на место
                            onRight()
                            offset.animateTo(0f, settle)
                        } else if (enabled && (offset.value < -threshold || (v < -1500f && offset.value < 0f))) {
                            // строка уезжает за край со скоростью пальца и там остаётся: её убирает список,
                            // соседи смыкаются. Назад на место её не возвращаем — иначе она мигнёт перед исчезновением
                            // список убирает её сразу: соседи смыкаются, пока она ещё уезжает, а не после
                            // долгого хвоста пружины (иначе ~1 с на её месте пусто, а потом всё прыгает)
                            onSwiped()
                            if (!reduced) offset.animateTo(-w[0], settle, v.coerceAtMost(0f))
                            // если строку так и не убрали (нет прав и т. п.) — через мгновение возвращаем
                            kotlinx.coroutines.delay(700)
                            offset.animateTo(0f, settle)
                        } else offset.animateTo(0f, settle, v)
                    }
                },
                onDragCancel = { scope.launch { offset.animateTo(0f, com.khizrim.dialer.ui.theme.Motion.settle()) } },
            ) { change, amount ->
                change.consume()
                tracker.addPosition(change.uptimeMillis, change.position)
                val max = if (onRight != null) threshold * 1.4f else 0f
                val min = if (enabled) Float.NEGATIVE_INFINITY else 0f
                scope.launch { offset.snapTo((offset.value + amount).coerceIn(min, max)) }
            }
        }
        .graphicsLayer {
            translationX = offset.value
            alpha = 1f - ((-offset.value).coerceAtLeast(0f) / w[0]).coerceIn(0f, 1f)
        }
}

private fun Modifier.onWidth(block: (Float) -> Unit): Modifier =
    this.then(Modifier.onSizeChanged { block(it.width.toFloat().coerceAtLeast(1f)) })
