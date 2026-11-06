package com.khizrim.dialer.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Отступы и радиусы — те же, что у Ozero Mail (Design.kt): поля экрана 20, лист 22,
 * значок в ячейке 24 и 14 до текста; радиусы 10 / 14 / 16 / 20 / 26.
 */
object Gap {
    val screen = 20.dp
    val sheet = 22.dp
    val bar = 12.dp
    val barTop = 10.dp
    val barBottom = 12.dp
    val card = 16.dp
    val slot = 24.dp
    val afterSlot = 14.dp
}

object Radius {
    val small = 10.dp
    val medium = 14.dp
    val tile = 16.dp
    val dialog = 20.dp
    val sheet = 26.dp
}

/** Формы на этих радиусах: текстовые кнопки, строки и тосты, главная кнопка, окно, верх листа. */
object OzShape {
    val text = RoundedCornerShape(Radius.small)
    val row = RoundedCornerShape(Radius.medium)
    val main = RoundedCornerShape(Radius.tile)
    val sheet = RoundedCornerShape(Radius.dialog)
    val sheetTop = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet)
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

/**
 * Нижняя панель, как причал у Ozero Mail: цвет страницы чуть насквозь, мягкая тень над верхним краем
 * и волосяная линия. Без скруглений и без рамки — лента уходит под неё.
 */
@Composable
fun Modifier.dock(): Modifier {
    val oz = LocalOz.current
    val shade = if (oz.isDark) Color.Black.copy(alpha = .3f) else oz.fg.copy(alpha = .04f)
    val hair = oz.line.copy(alpha = .6f)
    return this
        .background(oz.bg.copy(alpha = .94f))
        .drawWithCache {
            val fall = 10.dp.toPx()
            val brush = Brush.verticalGradient(listOf(Color.Transparent, shade), startY = -fall, endY = 0f)
            onDrawBehind { drawRect(brush, Offset(0f, -fall), Size(size.width, fall)) }
        }
        .drawWithContent {
            drawContent()
            drawLine(hair, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
        }
}
