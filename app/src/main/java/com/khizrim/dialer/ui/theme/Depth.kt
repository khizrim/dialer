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
import androidx.compose.ui.graphics.lerp
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

/** Круглая или главная кнопка: мягкий градиент, блик сверху и тень цвета заливки. */
fun Modifier.raisedFill(fill: Color, shape: Shape = CircleShape): Modifier = this
    .shadow(6.dp, shape, ambientColor = fill.copy(alpha = .3f), spotColor = fill.copy(alpha = .55f))
    .clip(shape)
    .background(Brush.verticalGradient(0f to lerp(fill, Color.White, .12f), .6f to fill, 1f to fill))
    .drawWithContent {
        drawContent()
        // блик 1dp у верхнего края
        drawLine(Color.White.copy(alpha = .22f), Offset(size.width * .2f, 1.dp.toPx() / 2), Offset(size.width * .8f, 1.dp.toPx() / 2), 1.dp.toPx())
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
