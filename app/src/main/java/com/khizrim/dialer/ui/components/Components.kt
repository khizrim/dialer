package com.khizrim.dialer.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduced) 0.98f else 1f,
        animationSpec = Motion.enter(Motion.PRESS),
        label = "press",
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(shape)
        .background(if (pressed) pressColor ?: oz.press else Color.Transparent)
        .combinedClickable(
            interactionSource = source,
            indication = null,
            role = role,
            onClickLabel = null,
            onLongClickLabel = if (onLongClick != null) stringResource(R.string.menu) else null,
            onLongClick = onLongClick,
            onClick = onClick,
        )
        .let { if (label != null) it.semantics { contentDescription = label } else it }
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
            .press(onClick, shape = CircleShape, pressColor = fill, label = label)
            .background(fill, CircleShape),
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
