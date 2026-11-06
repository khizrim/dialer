package com.khizrim.dialer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.khizrim.dialer.R

/**
 * Палитра «Озеро», общая с Ozero Mail: белая бумага, прохладные серые, один яркий утренний синий
 * для кнопок ([accentFill], одинаковый в обеих темах) и его тёмный тон для текста ([accent]).
 */
@Immutable
data class OzColors(
    val bg: Color,
    val sheet: Color,
    val soft: Color,
    val fg: Color,
    val fg2: Color,
    val fg3: Color,
    val line: Color,
    val accent: Color,
    val accentFill: Color,
    val danger: Color,
    val dangerFill: Color,
    val warn: Color,
    /** Тёплый фон экрана «Похоже на спам». */
    val warnSoft: Color,
    val press: Color,
    val scrim: Color,
    val toastAct: Color,
    /** Тост, как у Mail: тёмная плашка днём, светлая ночью. */
    val toastBg: Color,
    val toastFg: Color,
    /** Ручка листа. */
    val handle: Color,
    val isDark: Boolean,
)

val LightOz = OzColors(
    bg = Color(0xFFFFFFFF), sheet = Color(0xFFFFFFFF), soft = Color(0xFFF2F5FA),
    fg = Color(0xFF142036), fg2 = Color(0xFF3D4A63), fg3 = Color(0xFF66728A), line = Color(0xFFE9EEF5),
    accent = Color(0xFF1A6FE0), accentFill = Color(0xFF2F8CFF),
    danger = Color(0xFFC94452), dangerFill = Color(0xFFDF5260), warn = Color(0xFFA8551B), warnSoft = Color(0xFFFBF1E8),
    press = Color(0x12142036), scrim = Color(0x570A1428), toastAct = Color(0xFF8CC2FF),
    toastBg = Color(0xFF1B2639), toastFg = Color(0xFFF2F5FA), handle = Color(0xFFE3E9F2), isDark = false,
)

val DarkOz = OzColors(
    bg = Color(0xFF141923), sheet = Color(0xFF1F2633), soft = Color(0xFF1A202C),
    fg = Color(0xFFE5E9F1), fg2 = Color(0xFFB9C2D3), fg3 = Color(0xFF8792A7), line = Color(0xFF242B39),
    accent = Color(0xFF6AB2FF), accentFill = Color(0xFF2F8CFF),
    // на кнопке «Сбросить» лежит белая трубка, поэтому заливка та же, что днём, а не светлый текстовый тон
    danger = Color(0xFFEE6672), dangerFill = Color(0xFFDF5260), warn = Color(0xFFF0A46B), warnSoft = Color(0xFF2A2320),
    press = Color(0x14FFFFFF), scrim = Color(0x8C000000), toastAct = Color(0xFF1A6FE0),
    toastBg = Color(0xFFE5E9F1), toastFg = Color(0xFF141923), handle = Color(0xFF2B3343), isDark = true,
)

val LocalOz = staticCompositionLocalOf { LightOz }

@OptIn(ExperimentalTextApi::class)
private fun variable(weights: List<Int>, optical: TextUnit? = null) = FontFamily(
    weights.map { w ->
        val axes = listOfNotNull(FontVariation.weight(w), optical?.let { FontVariation.opticalSizing(it) })
        Font(R.font.inter, FontWeight(w), variationSettings = FontVariation.Settings(*axes.toTypedArray()))
    },
)

/** Inter везде, как в Ozero Mail: переменный шрифт рисует каждую насыщенность точно. */
val Inter = variable(listOf(400, 500, 600, 700))

/** Inter для крупных размеров: плотнее, с тонкими деталями. Имя на карточке и на экране звонка. */
val InterDisplay = variable(listOf(600, 700), optical = 32.sp)

/**
 * Шесть размеров с шагом около 1,25: 14 · 16 · 20 · 28 · 32 · 40 (и 32 для клавиш).
 * Всё набрано Inter, как в Ozero Mail; крупные имена — Inter Display. Моноширинные цифры только во времени и номерах.
 */
object OzType {
    private const val NUM = "tnum"
    val body = TextStyle(fontFamily = Inter, fontSize = 16.sp, lineHeight = 24.sp)
    val small = TextStyle(fontFamily = Inter, fontSize = 14.sp, lineHeight = 20.sp)
    /** Время, длительность, номер в подписи: цифры одной ширины, столбец не пляшет. */
    val time = small.copy(fontFeatureSettings = NUM)
    val tiny = TextStyle(fontFamily = Inter, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.08.em)
    /** Строка списка без имени (настройки, номер): тот же 16, что и текст, — 17 рядом с 16 читалось как ошибка. */
    val list = body
    /** Номер вместо имени в списке. */
    val number = body.copy(fontFeatureSettings = NUM)
    val digit = TextStyle(fontFamily = Inter, fontSize = 32.sp, lineHeight = 36.sp, fontFeatureSettings = NUM)
    val strong = body.copy(fontWeight = FontWeight.Medium)
    /** Подписи кнопок звонка и групп настроек. */
    val label = small
    /** Номер на экране звонка: в одну строку. */
    val callNumber = TextStyle(fontFamily = Inter, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.017).em, fontFeatureSettings = NUM)

    /** Имя в списке: журнал, контакты, поиск, листы. Как отправитель в списке Ozero Mail, на ступень крупнее. */
    val nameList = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-0.006).em)
    val nameSearch = nameList
    val nameHome = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.017).em)
    /** Номер вместо имени на главной: тот же вид, цифры одной ширины. */
    val numberHome = nameHome.copy(fontFeatureSettings = NUM)
    val namePerson = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.022).em)
    /** Заголовок диалога и экрана, как заголовки листов в Ozero Mail. */
    val title = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 25.sp, letterSpacing = (-0.01).em)
    val nameCall = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.Bold, fontSize = 38.sp, lineHeight = 44.sp, letterSpacing = (-0.022).em)
}

@Composable
fun DialerTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val oz = if (dark) DarkOz else LightOz
    val scheme = if (dark) {
        darkColorScheme(background = oz.bg, surface = oz.sheet, onBackground = oz.fg, onSurface = oz.fg, primary = oz.accentFill)
    } else {
        lightColorScheme(background = oz.bg, surface = oz.sheet, onBackground = oz.fg, onSurface = oz.fg, primary = oz.accentFill)
    }
    CompositionLocalProvider(LocalOz provides oz) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
