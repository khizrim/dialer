package com.khizrim.dialer.ui.kit

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.khizrim.dialer.R
import com.khizrim.dialer.model.Method
import com.khizrim.dialer.telecom.CallOrigin
import com.khizrim.dialer.ui.theme.LocalOz
import com.khizrim.dialer.ui.theme.OzType
import com.khizrim.dialer.ui.theme.on
import com.khizrim.dialer.ui.theme.raisedFill

@Composable
fun OzIcon(@DrawableRes id: Int, tint: Color, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    Icon(painterResource(id), contentDescription = null, tint = tint, modifier = modifier.size(size))
}

/**
 * Нажатие: тихая подсветка формы, пока палец на месте, и долгое нажатие с отдачей.
 * Если палец поехал, жест забирает родитель (лента, лист, док), и нажатие отменяется само.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.press(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(16.dp),
    pressColor: Color? = null,
    label: String? = null,
    role: Role = Role.Button,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val fill = pressColor ?: LocalOz.current.press
    val haptic = LocalHapticFeedback.current
    // где лежит нажатое: если нажатие начнёт звонок, экран звонка вырастет отсюда
    val at = remember { Bounds() }
    return this
        .track(at)
        .clip(shape)
        .drawWithContent {
            drawContent()
            if (pressed) drawRect(fill)
        }
        .combinedClickable(
            interactionSource = source,
            indication = null,
            role = role,
            onClickLabel = label,
            onLongClick = onLongClick?.let { long -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); CallOrigin.set(at.rect); long() } },
            onClick = { CallOrigin.set(at.rect); onClick() },
        )
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
        modifier.heightIn(min = 44.dp).press(onClick, shape = RoundedCornerShape(12.dp)).padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) { Text(text, style = style, color = color) }
}

/** Значок способа и, если это мессенджер, его короткое имя мелко слева: «WA», «TG». У обычного звонка подписи нет. */
@Composable
fun MethodMark(method: Method?, tint: Color = LocalOz.current.fg3, size: Dp = 24.dp) {
    val tag = method?.tag
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (tag != null) Text(tag, style = OzType.channel, color = LocalOz.current.fg3, modifier = Modifier.padding(end = 4.dp))
        OzIcon(method?.icon ?: R.drawable.ic_oz_swap, tint, size)
    }
}

/** Круглая кнопка в акценте, приподнятая: звонок, ответ, клавиатура. */
@Composable
fun AccentRound(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    iconSize: Dp = 24.dp,
    fill: Color = LocalOz.current.accentFill,
    onLongClick: (() -> Unit)? = null,
) {
    Box(
        modifier
            .size(size)
            .raisedFill(fill, CircleShape)
            .press(onClick, onLongClick, CircleShape, pressColor = Color.White.copy(alpha = .14f), label = label),
        contentAlignment = Alignment.Center,
    ) { OzIcon(icon, LocalOz.current.on(fill), iconSize) }
}

/** Где на экране лежит элемент: отсюда вырастает экран звонка. */
class Bounds { var rect: Rect = Rect.Zero }

@Composable
fun rememberBounds(): Bounds = remember { Bounds() }

fun Modifier.track(b: Bounds): Modifier = onGloballyPositioned { b.rect = it.boundsInWindow() }
