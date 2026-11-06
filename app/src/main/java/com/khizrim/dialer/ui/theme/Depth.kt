package com.khizrim.dialer.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Четыре радиуса: текстовые кнопки, строки, главная кнопка, верх листа. */
object OzShape {
    val text = RoundedCornerShape(12.dp)
    val row = RoundedCornerShape(16.dp)
    val main = RoundedCornerShape(18.dp)
    val sheet = RoundedCornerShape(28.dp)
    val sheetTop = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
}

/** Лист, тост, карточка: едва заметно приподняты над фоном. */
@Composable
fun Modifier.lift(shape: Shape): Modifier {
    val oz = LocalOz.current
    return if (oz.isDark) shadow(14.dp, shape, ambientColor = Color.Black.copy(alpha = .5f), spotColor = Color.Black.copy(alpha = .7f))
    else shadow(12.dp, shape, ambientColor = oz.fg.copy(alpha = .08f), spotColor = oz.fg.copy(alpha = .22f))
}

/**
 * Круглая или главная кнопка, как кнопки в акценте у Ozero Mail: ровная заливка, мягкая тень цвета заливки
 * и полоска света по верхнему краю. Ночью тень почти не видна, поэтому кнопку держит свет.
 */
@Composable
fun Modifier.raisedFill(fill: Color, shape: Shape = CircleShape): Modifier {
    val lit = Color.White.copy(alpha = if (LocalOz.current.isDark) .07f else .35f)
    return this
    .shadow(3.dp, shape, ambientColor = fill.copy(alpha = .3f), spotColor = fill.copy(alpha = .7f))
    .clip(shape)
    .background(fill)
    .drawWithContent {
        drawContent()
        drawLine(lit, Offset(0f, .5.dp.toPx()), Offset(size.width, .5.dp.toPx()), 1.dp.toPx())
    }
}

/** «Лунка»: включённая кнопка звонка, переключатель, удержанный звонок. */
@Composable
fun Modifier.well(shape: Shape, on: Boolean = true): Modifier {
    if (!on) return this
    val oz = LocalOz.current
    val shade = if (oz.isDark) Color.Black.copy(alpha = .45f) else oz.fg.copy(alpha = .10f)
    return this
        .clip(shape)
        .background(oz.soft)
        .drawWithContent {
            drawRect(Brush.verticalGradient(0f to shade, 1f to Color.Transparent, startY = 0f, endY = 3.dp.toPx()))
            drawContent()
        }
}
