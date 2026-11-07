package com.khizrim.dialer.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs

/**
 * Движение Ozero Phone, переписанное с нуля (прототип «интерфейс с нуля», 2026-11-07).
 *
 * Пространство:
 * - главная — лента людей; управление висит над ней на стекле;
 * - карточка, клавиатура, контакты и все выборы приходят снизу и уходят вниз;
 * - настройки и их страницы сдвигаются справа, жест «назад» ведёт их за пальцем;
 * - звонок вырастает из нажатого, сворачивается в капсулу наверху и в конце уходит в кнопку «Завершить».
 *
 * Одна пружина без отскока на всё. Жест ведёт слой 1:1; после отпускания слой продолжает
 * со скоростью пальца, ничего не начинается заново. Решение — при отпускании: прошли треть пути
 * или бросили быстрее 0,6 экрана в секунду.
 */
object Motion {
    val Ease = CubicBezierEasing(0.25f, 0.1f, 0.1f, 1f)
    val EnterEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val ExitEasing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    const val PRESS = 100
    const val FADE = 150
    const val MOVE = 250
    const val NOTE = 400
    const val SHEET = 420

    /** Жёсткость пружины слоёв: критическое затухание, слой доходит примерно за 420 мс, как экран в Ozero Mail. */
    const val LAYER_STIFFNESS = 230f

    /** Слои двигаются в долях пути (0…1): порог, после которого пружина считается дошедшей. Со стандартным 0.01 слой в конце доскакивает. */
    const val THRESHOLD = 0.0002f

    fun layer(): SpringSpec<Float> = spring(dampingRatio = 1f, stiffness = LAYER_STIFFNESS, visibilityThreshold = THRESHOLD)

    /** То же для пикселей: строки, круги, сдвиги. */
    fun pixels(stiffness: Float = 420f): SpringSpec<Float> = spring(dampingRatio = 1f, stiffness = stiffness, visibilityThreshold = 0.5f)

    /** Блок меняет высоту (группа способов, лист, подсказки): та же пружина без отскока, чуть быстрее слоя. */
    fun size(): FiniteAnimationSpec<IntSize> = spring(dampingRatio = 1f, stiffness = 500f, visibilityThreshold = IntSize.VisibilityThreshold)

    /** Сдвиг внутри экрана в пикселях (ползунок выбора, строки): как [size]. */
    fun offset(): FiniteAnimationSpec<IntOffset> = spring(dampingRatio = 1f, stiffness = 500f, visibilityThreshold = IntOffset.VisibilityThreshold)

    /** Цвет отметки, значка, текста: короткий переход вместо щелчка. */
    fun <T> tint(): FiniteAnimationSpec<T> = tween(FADE, 0, Ease)

    fun <T> enter(ms: Int, delay: Int = 0): FiniteAnimationSpec<T> = tween(ms, delay, EnterEasing)
    fun <T> exit(ms: Int): FiniteAnimationSpec<T> = tween(ms, 0, ExitEasing)
    fun <T> move(ms: Int = SHEET): FiniteAnimationSpec<T> = tween(ms, 0, Ease)
    fun <T> settle(): AnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 700f)

    /**
     * Решение при отпускании. [progress] — пройденная доля пути в сторону «открыто»,
     * [velocityTowardOpen] — скорость пальца в ту же сторону, px/с, [screenPx] — высота экрана.
     * true — слой остаётся (или открывается).
     */
    fun keeps(progress: Float, velocityTowardOpen: Float, screenPx: Float): Boolean {
        if (abs(velocityTowardOpen) > 0.6f * screenPx) return velocityTowardOpen > 0f
        return progress > 1f / 3f
    }
}

/** true, если пользователь отключил анимации: тогда слои только проявляются. */
val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
