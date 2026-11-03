package com.khizrim.dialer.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Движение «тихо и спокойно», версия 2 (MOTION-v2.md).
 *
 * Пространство: главная — поверхность озера. Журнал лежит над ней (его стягивают вниз),
 * всё остальное — под ней и поднимается снизу (контакты, карточка, настройки, клавиатура, листы).
 * Экран всегда непрозрачный и приходит целиком со своей стороны поверх неподвижного старого;
 * уходит туда же, откуда пришёл. Два экрана никогда не просвечивают друг через друга
 * и между ними не бывает пустого фона.
 *
 * Жест ведёт слой 1:1 за пальцем; после отпускания слой продолжает движение с той же скоростью
 * (пружина без отскока), а не начинает анимацию заново.
 */
object Motion {
    /** Сдвиг слоёв, строк и листов: мягкий старт и долгое спокойное торможение. */
    val MoveEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    /** Появление мелочей (тост, галочка, строка): сразу видно, плавно доходит. */
    val EnterEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    /** Исчезновение: начинает сразу, без «замирания». */
    val ExitEasing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    const val PRESS = 100
    const val FADE = 150
    const val MOVE = 200
    const val SHEET = 300

    fun <T> enter(ms: Int, delay: Int = 0): FiniteAnimationSpec<T> = tween(ms, delay, EnterEasing)
    fun <T> exit(ms: Int): FiniteAnimationSpec<T> = tween(ms, 0, ExitEasing)
    fun <T> move(ms: Int = SHEET): FiniteAnimationSpec<T> = tween(ms, 0, MoveEasing)

    /** Досвод после жеста: критическое затухание (без отскока), принимает скорость пальца. */
    fun <T> settle(): AnimationSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 420f)

    /** Порог решения после отпускания: прошли треть пути или бросили быстрее 1000 px/с. */
    fun commits(fraction: Float, velocityPx: Float, heightPx: Float): Boolean {
        val v = velocityPx / heightPx.coerceAtLeast(1f) // доли экрана в секунду
        return when {
            v > 0.6f -> true
            v < -0.6f -> false
            else -> fraction > 0.33f
        }
    }
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
