package com.khizrim.dialer.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Velocity
import com.khizrim.dialer.ui.Edge
import com.khizrim.dialer.ui.LocalScreenStack
import com.khizrim.dialer.ui.ScreenStack

/** Сдвиг пальца «к краю, откуда пришёл экран»: для журнала это вверх, для остальных — вниз. */
private fun toEdge(stack: ScreenStack, dy: Float) = if (stack.top?.edge == Edge.TOP) -dy else dy

/**
 * Палец в координатах окна. Слой, который тянут, сам едет за пальцем, поэтому в его локальных координатах
 * касание почти стоит на месте (палец и слой двигаются вместе) — медленный жест «не доезжал».
 * Считаем путь и скорость по окну, а не по слою.
 */
private class Finger {
    var coords: LayoutCoordinates? = null
    var y = 0f
    var down = false
    val tracker = VelocityTracker()
    fun window(p: Offset): Offset = coords?.takeIf { it.isAttached }?.localToWindow(p) ?: p
}

/** Следит за пальцем (не поглощая) раньше всех, чтобы список и жесты внутри видели свежую позицию. */
private fun Modifier.followFinger(f: Finger): Modifier = this
    .onGloballyPositioned { f.coords = it }
    .pointerInput(f) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            f.tracker.resetTracking(); f.down = true
            f.window(down.position).let { f.y = it.y; f.tracker.addPosition(down.uptimeMillis, it) }
            try {
                while (true) {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                    f.window(c.position).let { f.y = it.y; f.tracker.addPosition(c.uptimeMillis, it) }
                    if (!c.pressed) break
                }
            } finally { f.down = false }
        }
    }

/**
 * Список докрутили до края, а палец тянет дальше — экран идёт за пальцем к своему краю и открывает тот, что под ним.
 * Отпустили — доезжает со скоростью пальца или возвращается. Пока экран тянут, список не прокручивается.
 */
@Composable
fun Modifier.dismissOnOverscroll(onClose: () -> Unit): Modifier {
    val stack = LocalScreenStack.current ?: return this
    val close by rememberUpdatedState(onClose)
    val finger = remember { Finger() }
    val connection = remember(stack) {
        object : NestedScrollConnection {
            var pulled = 0f
            /** Положение пальца в окне, при котором экран стоял бы на месте (pulled = 0). */
            var anchor = 0f
            fun follow(): Float {
                pulled = toEdge(stack, finger.y - anchor).coerceAtLeast(0f)
                stack.dismissBy(pulled)
                return pulled
            }
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (pulled <= 0f || source != NestedScrollSource.UserInput) return Offset.Zero
                // экран уже тянут: он стоит там, где палец, в обе стороны, пока не вернётся на место
                follow()
                return Offset(0f, available.y)
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val d = toEdge(stack, available.y)
                if (d <= 0f) return Offset.Zero
                // список упёрся: остаток этого шага уже тянет экран, дальше — по окну
                anchor = finger.y - (if (stack.top?.edge == Edge.TOP) -d else d)
                follow()
                return Offset(0f, available.y)
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pulled <= 0f && !stack.dismissing) return Velocity.Zero
                val wasPulled = pulled > 0f
                pulled = 0f
                stack.dismissEnd(toEdge(stack, finger.tracker.calculateVelocity().y)) { close() }
                return if (wasPulled) available else Velocity.Zero
            }
        }
    }
    return this.followFinger(finger).nestedScroll(connection)
}

/** Поверхность без прокрутки (карточка, поле поиска): тянут к краю экрана — экран идёт за пальцем. */
@Composable
fun Modifier.dismissOnDrag(onClose: () -> Unit): Modifier {
    val stack = LocalScreenStack.current ?: return this
    val close by rememberUpdatedState(onClose)
    val finger = remember { Finger() }
    return this.followFinger(finger).pointerInput(stack) {
        var anchor = 0f
        detectVerticalDragGestures(
            // после slop: экран стоит на месте, пока палец там, где жест начался
            onDragStart = { anchor = finger.y },
            onDragEnd = { stack.dismissEnd(toEdge(stack, finger.tracker.calculateVelocity().y)) { close() } },
            onDragCancel = { stack.dismissEnd(0f) { } },
        ) { change, _ ->
            change.consume()
            stack.dismissBy(toEdge(stack, finger.y - anchor).coerceAtLeast(0f))
        }
    }
}
