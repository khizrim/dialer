package com.khizrim.dialer.ui.components

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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Velocity
import com.khizrim.dialer.ui.Edge
import com.khizrim.dialer.ui.LocalScreenStack
import com.khizrim.dialer.ui.ScreenStack

/** Сдвиг пальца «к краю, откуда пришёл экран»: для журнала это вверх, для остальных — вниз. */
private fun toEdge(stack: ScreenStack, dy: Float) = if (stack.top?.edge == Edge.TOP) -dy else dy

/**
 * Список докрутили до края, а палец тянет дальше — экран идёт за пальцем к своему краю и открывает тот, что под ним.
 * Отпустили — доезжает со скоростью пальца или возвращается. Пока экран тянут, список не прокручивается.
 */
@Composable
fun Modifier.dismissOnOverscroll(onClose: () -> Unit): Modifier {
    val stack = LocalScreenStack.current ?: return this
    val close by rememberUpdatedState(onClose)
    val connection = remember(stack) {
        object : NestedScrollConnection {
            var pulled = 0f
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (pulled <= 0f || source != NestedScrollSource.UserInput) return Offset.Zero
                // экран уже тянут: всё движение пальца — ему, в обе стороны, пока не вернётся на место
                pulled = (pulled + toEdge(stack, available.y)).coerceAtLeast(0f)
                stack.dismissBy(pulled)
                return Offset(0f, available.y)
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val d = toEdge(stack, available.y)
                if (d <= 0f) return Offset.Zero
                pulled += d
                stack.dismissBy(pulled)
                return Offset(0f, available.y)
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pulled <= 0f && !stack.dismissing) return Velocity.Zero
                val wasPulled = pulled > 0f
                pulled = 0f
                stack.dismissEnd(toEdge(stack, available.y)) { close() }
                return if (wasPulled) available else Velocity.Zero
            }
        }
    }
    return this.nestedScroll(connection)
}

/** Поверхность без прокрутки (карточка, поле поиска): тянут к краю экрана — экран идёт за пальцем. */
@Composable
fun Modifier.dismissOnDrag(onClose: () -> Unit): Modifier {
    val stack = LocalScreenStack.current ?: return this
    val close by rememberUpdatedState(onClose)
    return this.pointerInput(stack) {
        val tracker = VelocityTracker()
        var pulled = 0f
        detectVerticalDragGestures(
            onDragStart = { tracker.resetTracking(); pulled = 0f },
            onDragEnd = { stack.dismissEnd(toEdge(stack, tracker.calculateVelocity().y)) { close() }; pulled = 0f },
            onDragCancel = { stack.dismissEnd(0f) { }; pulled = 0f },
        ) { change, amount ->
            change.consume()
            tracker.addPosition(change.uptimeMillis, change.position)
            pulled = (pulled + toEdge(stack, amount)).coerceAtLeast(0f)
            stack.dismissBy(pulled)
        }
    }
}
