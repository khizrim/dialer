package com.khizrim.dialer.ui.nav

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.khizrim.dialer.ui.theme.LocalOz
import com.khizrim.dialer.ui.theme.LocalReducedMotion
import com.khizrim.dialer.ui.theme.Motion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Откуда приходит слой и куда уходит. */
enum class Kind {
    /** Лист по содержимому снизу; под ним затемнение. */
    SHEET,
    /** Лист без затемнения: клавиатура набора, лента над ней видна, нажатие выше закрывает. */
    PANEL,
    /** Экран целиком снизу: контакты. */
    RISE,
    /** Страница справа: настройки. Жест «назад» ведёт её за пальцем. */
    PUSH,
}

/**
 * Один слой над главной. [p] — сколько он открыт (0…1). Им двигает либо палец (1:1), либо пружина,
 * которая начинает с того места и той скорости, где её отпустили.
 */
class Layer internal constructor(val dest: Any, val kind: Kind, val id: Int, private val scope: CoroutineScope, private val reduced: Boolean) {
    var p by mutableFloatStateOf(0f)
    /** Наклон предиктивного «назад» у листов: 0…1. */
    var back by mutableFloatStateOf(0f)
    var backDir by mutableIntStateOf(1)
    var closing by mutableStateOf(false)
        internal set
    /** Длина пути в пикселях: высота листа, высота или ширина экрана. NaN, пока лист не измерен. */
    var extent by mutableFloatStateOf(Float.NaN)
    /** Высота того, что экран держит у нижнего края (группа способов у экрана человека): тост встаёт над ней. */
    var floor by mutableFloatStateOf(Float.NaN)
    var onClosed: (() -> Unit)? = null
    private var job: Job? = null

    fun stop() { job?.cancel(); job = null }

    fun animateTo(target: Float, velocity: Float = 0f, then: (() -> Unit)? = null) {
        stop()
        job = scope.launch {
            val spec: androidx.compose.animation.core.AnimationSpec<Float> = if (reduced) Motion.enter(Motion.FADE) else Motion.layer()
            animate(p, target, if (reduced) 0f else velocity, spec) { v, _ -> p = v }
            then?.invoke()
        }
    }

    internal fun relaxBack() {
        val from = back
        if (from == 0f) return
        scope.launch { animate(from, 0f, 0f, Motion.layer()) { v, _ -> back = v } }
    }
}

/** Стопка слоёв над главной. Порядок — порядок открытия; закрывающийся слой ещё рисуется, пока уходит. */
class Nav internal constructor(private val scope: CoroutineScope, private val reduced: Boolean) {
    val layers = mutableStateListOf<Layer>()
    private var nextId = 0

    val top: Layer? get() = layers.lastOrNull { !it.closing }
    fun find(pred: (Any) -> Boolean): Layer? = layers.lastOrNull { !it.closing && pred(it.dest) }

    /** Открыть слой. [animate] = false — слой стоит закрытым, его ведёт палец (подтянули док вверх). */
    fun open(dest: Any, kind: Kind, animate: Boolean = true, onClosed: (() -> Unit)? = null): Layer {
        val l = Layer(dest, kind, nextId++, scope, reduced)
        l.onClosed = onClosed
        layers.add(l)
        if (animate) l.animateTo(1f)
        return l
    }

    fun close(l: Layer, velocity: Float = 0f, then: (() -> Unit)? = null) {
        if (l.closing || l !in layers) return
        l.closing = true
        l.relaxBack()
        l.animateTo(0f, velocity) {
            layers.remove(l)
            l.onClosed?.invoke()
            then?.invoke()
        }
    }

    /** Закрыть сразу, без движения: его уже накрыло что-то другое (экран звонка). */
    fun drop(l: Layer) {
        if (l !in layers) return
        l.stop(); l.closing = true
        layers.remove(l)
        l.onClosed?.invoke()
    }

    fun closeTop(): Boolean { val t = top ?: return false; close(t); return true }

    /** Решить при отпускании: остаться или закрыться, продолжив со скоростью пальца. [velocity] — px/с в сторону закрытия. */
    fun settle(l: Layer, velocity: Float, screenPx: Float) {
        val extent = l.extent.takeUnless { it.isNaN() || it <= 0f } ?: screenPx
        if (Motion.keeps(l.p, -velocity, screenPx)) l.animateTo(1f, -velocity / extent) else close(l, -velocity / extent)
    }
}

val LocalNav = staticCompositionLocalOf<Nav> { error("Nav не задан") }
val LocalLayer = staticCompositionLocalOf<Layer?> { null }

@Composable
fun rememberNav(): Nav {
    val scope = rememberCoroutineScope()
    val reduced = LocalReducedMotion.current
    return remember { Nav(scope, reduced) }
}

/**
 * Тянуть слой обратно туда, откуда он пришёл, 1:1 за пальцем. Работает и на том, что не листается,
 * и как продолжение списка: дотянули список до верха и тянете дальше — поехал слой.
 */
@Composable
fun Modifier.dismissDrag(l: Layer, horizontal: Boolean = false, enabled: Boolean = true): Modifier {
    val nav = LocalNav.current
    val density = LocalDensity.current
    val cfg = LocalConfiguration.current
    val screenPx = with(density) { (if (horizontal) cfg.screenWidthDp else cfg.screenHeightDp).dp.toPx() }
    fun extent() = l.extent.takeUnless { it.isNaN() || it <= 0f } ?: screenPx
    fun pull(delta: Float) { l.stop(); l.p = (l.p - delta / extent()).coerceIn(0f, 1f) }
    val connection = remember(l) {
        object : NestedScrollConnection {
            var pulled = false
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // слой уже поехал вниз, а палец пошёл вверх: сначала возвращается слой, потом листается список
                if (!enabled || horizontal || source != NestedScrollSource.UserInput || !pulled || available.y >= 0f || l.p >= 1f) return Offset.Zero
                val before = l.p
                pull(available.y)
                val used = (l.p - before) * extent()
                return Offset(0f, -used)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (!enabled || horizontal || source != NestedScrollSource.UserInput || available.y <= 0f || l.closing) return Offset.Zero
                pulled = true
                pull(available.y)
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!pulled) return Velocity.Zero
                pulled = false
                nav.settle(l, available.y, screenPx)
                return available
            }
        }
    }
    val state = rememberDraggableState { d -> pull(d) }
    return this
        .nestedScroll(connection)
        .draggable(
            state,
            if (horizontal) Orientation.Horizontal else Orientation.Vertical,
            enabled = enabled,
            onDragStarted = { l.stop() },
            onDragStopped = { v -> nav.settle(l, v, screenPx) },
        )
}

/**
 * Главная и слои над ней. Лист выезжает на свою высоту, экран — на высоту экрана, страница — на ширину;
 * под страницей предыдущий слой отъезжает на четверть влево и чуть темнеет.
 */
@Composable
fun LayerHost(nav: Nav, base: @Composable () -> Unit, content: @Composable (Layer) -> Unit) {
    val oz = LocalOz.current
    val reduced = LocalReducedMotion.current
    val lean = with(LocalDensity.current) { 14.dp.toPx() }

    PredictiveBackHandler(enabled = nav.top != null) { events ->
        val l = nav.top ?: return@PredictiveBackHandler
        l.stop()
        val p0 = l.p
        try {
            events.collect { e ->
                if (l.kind == Kind.PUSH) l.p = p0 * (1f - e.progress)
                else { l.back = e.progress; l.backDir = if (e.swipeEdge == BackEventCompat.EDGE_LEFT) 1 else -1 }
            }
            nav.close(l)
        } catch (e: CancellationException) {
            l.relaxBack()
            l.animateTo(1f)
            throw e
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        // сдвиг и тень под страницей: читаются в фазе рисования, без перекомпоновки на каждый кадр
        fun under(index: Int): Modifier = Modifier
            .graphicsLayer {
                val next = nav.layers.getOrNull(index + 1)
                translationX = if (next != null && next.kind == Kind.PUSH && !reduced) -0.25f * w * next.p else 0f
            }
            .drawWithContent {
                drawContent()
                val next = nav.layers.getOrNull(index + 1)
                if (next != null && next.kind == Kind.PUSH) drawRect(Color.Black, alpha = 0.12f * next.p)
            }

        Box(Modifier.fillMaxSize().then(under(-1))) { base() }

        nav.layers.forEachIndexed { i, l ->
            key(l.id) {
                CompositionLocalProvider(LocalLayer provides l) {
                    when (l.kind) {
                        Kind.SHEET, Kind.PANEL -> Box(Modifier.fillMaxSize()) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { alpha = if (l.kind == Kind.SHEET) l.p else 0f }
                                    .background(oz.scrim)
                                    .pointerInput(l) { detectTapGestures { nav.close(l) } },
                            )
                            Box(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .onSizeChanged { l.extent = it.height.toFloat() }
                                    .graphicsLayer {
                                        val e = l.extent.takeUnless { it.isNaN() } ?: h
                                        if (reduced) alpha = l.p else translationY = (1f - l.p) * e
                                        val b = l.back
                                        if (b > 0f) {
                                            transformOrigin = TransformOrigin(0.5f, 1f)
                                            scaleX = 1f - 0.08f * b; scaleY = scaleX
                                            translationX = b * lean * l.backDir
                                        }
                                    },
                            ) { content(l) }
                        }
                        Kind.RISE -> Box(Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxSize().graphicsLayer { alpha = 0.4f * l.p }.background(oz.scrim))
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .then(under(i))
                                    .graphicsLayer {
                                        l.extent = h
                                        if (reduced) alpha = l.p else translationY = (1f - l.p) * h
                                        val b = l.back
                                        if (b > 0f) {
                                            transformOrigin = TransformOrigin(0.5f, 1f)
                                            scaleX = 1f - 0.08f * b; scaleY = scaleX
                                            translationX = b * lean * l.backDir
                                        }
                                    },
                            ) { content(l) }
                        }
                        Kind.PUSH -> Box(
                            Modifier
                                .fillMaxSize()
                                .then(under(i))
                                .graphicsLayer {
                                    l.extent = w
                                    if (reduced) alpha = l.p else translationX = (1f - l.p) * w
                                },
                        ) { content(l) }
                    }
                }
            }
        }
    }
}
