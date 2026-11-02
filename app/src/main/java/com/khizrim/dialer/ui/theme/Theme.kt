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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.khizrim.dialer.R

/** Палитра «Озеро». Значения совпадают с HTML-прототипом. */
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
    val press: Color,
    val scrim: Color,
    val toastAct: Color,
    val isDark: Boolean,
)

val LightOz = OzColors(
    bg = Color(0xFFFFFFFF), sheet = Color(0xFFFFFFFF), soft = Color(0xFFF2F5FA),
    fg = Color(0xFF142036), fg2 = Color(0xFF3D4A63), fg3 = Color(0xFF5B6680), line = Color(0xFFE3E9F1),
    accent = Color(0xFF1766D4), accentFill = Color(0xFF1A6FE0),
    danger = Color(0xFFC2453F), dangerFill = Color(0xFFC2453F), warn = Color(0xFFA8551B),
    press = Color(0x12142036), scrim = Color(0x52142036), toastAct = Color(0xFF6AB2FF), isDark = false,
)

val DarkOz = OzColors(
    bg = Color(0xFF141923), sheet = Color(0xFF1F2532), soft = Color(0xFF1F2532),
    fg = Color(0xFFE5E9F1), fg2 = Color(0xFFB9C2D3), fg3 = Color(0xFF8792A7), line = Color(0xFF2A3140),
    accent = Color(0xFF6AB2FF), accentFill = Color(0xFF1A6FE0),
    danger = Color(0xFFF08A84), dangerFill = Color(0xFFC2453F), warn = Color(0xFFF0A46B),
    press = Color(0x14FFFFFF), scrim = Color(0x8C000000), toastAct = Color(0xFF1766D4), isDark = true,
)

val LocalOz = staticCompositionLocalOf { LightOz }

/** Inter для интерфейса, Literata для имён людей. */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)
val Literata = FontFamily(
    Font(R.font.literata_regular, FontWeight.Normal),
    Font(R.font.literata_medium, FontWeight.Medium),
)

object OzType {
    private const val CV11 = "cv11, tnum"
    val body = TextStyle(fontFamily = Inter, fontSize = 16.sp, lineHeight = 24.sp, fontFeatureSettings = CV11)
    val small = TextStyle(fontFamily = Inter, fontSize = 14.sp, lineHeight = 20.sp, fontFeatureSettings = CV11)
    val tiny = TextStyle(fontFamily = Inter, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.sp)
    val list = TextStyle(fontFamily = Inter, fontSize = 17.sp, lineHeight = 24.sp, fontFeatureSettings = CV11)
    val digit = TextStyle(fontFamily = Inter, fontSize = 32.sp, lineHeight = 36.sp, fontFeatureSettings = CV11)
    val title = TextStyle(fontFamily = Inter, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
    val strong = body.copy(fontWeight = FontWeight.SemiBold)

    private val name = TextStyle(fontFamily = Literata, fontWeight = FontWeight.Medium)
    val nameHome = name.copy(fontSize = 28.sp, lineHeight = 34.sp)
    val nameSearch = name.copy(fontSize = 24.sp, lineHeight = 30.sp)
    val namePerson = name.copy(fontSize = 36.sp, lineHeight = 41.sp, letterSpacing = (-0.3).sp)
    val nameCall = name.copy(fontSize = 42.sp, lineHeight = 46.sp, letterSpacing = (-0.4).sp)
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
