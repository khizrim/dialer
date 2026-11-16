package com.khizrim.dialer.ui.kit

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.khizrim.dialer.R
import com.khizrim.dialer.ui.theme.LocalOz
import com.khizrim.dialer.ui.theme.Motion
import com.khizrim.dialer.ui.theme.OzType

/**
 * Смахнуть влево — удалить. Одинаково в ленте, тихой группе и истории карточки: под строкой справа
 * проступает «Удалить», краснеет, когда отпустить уже значит удалить. «Вернуть» показывает тост.
 */
@Composable
fun SwipeToDelete(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.delete_swipe),
    content: @Composable BoxScope.() -> Unit,
) {
    val oz = LocalOz.current
    val w = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    var x by remember { mutableFloatStateOf(0f) }
    val drag = rememberDraggableState { d -> x = (x + d).coerceAtMost(0f) }
    val armed = x < -w * 0.35f
    Box(
        modifier
            .fillMaxWidth()
            .draggable(
                drag, Orientation.Horizontal,
                onDragStopped = { v ->
                    val go = (x < -w * 0.35f && v < 200f) || v < -0.6f * w
                    if (go) {
                        animate(x, -w - 40f, minOf(v, -1200f), Motion.pixels(300f)) { value, _ -> x = value }
                        onDelete()
                        x = 0f
                    } else animate(x, 0f, v, Motion.pixels()) { value, _ -> x = value }
                },
            ),
    ) {
        Text(
            label, style = OzType.small.copy(fontWeight = FontWeight.Medium),
            color = if (armed) oz.danger else oz.fg3,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 20.dp).graphicsLayer { alpha = (-x / (w * 0.2f)).coerceIn(0f, 1f) },
        )
        Box(Modifier.fillMaxWidth().graphicsLayer { translationX = x }.background(oz.bg), content = content)
    }
}
