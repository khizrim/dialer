package com.khizrim.dialer.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Движение «тихо и спокойно» (MOTION.md):
 * 100 / 150 / 200 / 300 мс, вход out-expo, уход in-expo, только сдвиг и прозрачность.
 */
object Motion {
    val EnterEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    // уход: мягкое ускорение (in-expo «замирало», а потом обрывалось)
    val ExitEasing = CubicBezierEasing(0.4f, 0f, 1f, 1f)

    const val PRESS = 100
    const val FADE = 150
    const val MOVE = 200
    const val SHEET = 300

    fun <T> enter(ms: Int, delay: Int = 0): FiniteAnimationSpec<T> = tween(ms, delay, EnterEasing)
    fun <T> exit(ms: Int): FiniteAnimationSpec<T> = tween(ms, 0, ExitEasing)
}

/** true, если пользователь отключил анимации: тогда оставляем только прозрачность до 150 мс. */
val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
