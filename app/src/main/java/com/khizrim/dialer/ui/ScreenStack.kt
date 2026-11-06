package com.khizrim.dialer.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import com.khizrim.dialer.Screen
import com.khizrim.dialer.ui.theme.LocalOz
import com.khizrim.dialer.ui.theme.Motion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class Route(val screen: Screen, val personId: Long? = null)

/** С какой стороны экран приходит и куда уходит. */
enum class Edge(val sign: Float) { TOP(-1f), BOTTOM(1f) }

/** Журнал, открытый с главной, лежит над ней; всё остальное — под ней и поднимается снизу. */
fun edgeOf(from: Route?, to: Route): Edge =
    Edge.BOTTOM

@Stable
class Layer(val route: Route, val edge: Edge, shown: Float) {
    val id = next++
    /** 1 — экран на месте, 0 — целиком за своим краем. */
    // порог видимости в долях экрана: по умолчанию 0.01 — это 24 px, и слой «доскакивал» в конце
    val shown = Animatable(shown, visibilityThreshold = 0.0002f)
    var leaving = false

    private companion object { var next = 0L }
}

/**
 * Стопка экранов. Новый экран приходит поверх неподвижного старого со своей стороны, назад — уходит туда же.
 * Жест может вести верхний слой (или ещё не открытый) за пальцем; отпускание продолжает движение с той же скоростью.
 */
@Stable
class ScreenStack(private val scope: CoroutineScope) {
    val layers = mutableStateListOf<Layer>()
    /** Пройденный путь: если экран уже есть в нём, переход к нему — «назад». */
    private val trail = mutableListOf<Pair<Route, Edge>>()
    var height = 1f
    var reduced = false
    /** Верхний слой стоит на месте: можно поднимать клавиатуру и т. п. */
    var settled by mutableStateOf(true); private set
    private var job: Job? = null
    private var pendingVelocity = 0f
    private var gesture: Gesture? = null

    private sealed interface Gesture
    private class Preview(val layer: Layer) : Gesture { var fraction = 0f }
    private class Dismiss(val top: Layer, val beneath: Layer) : Gesture { var fraction = 0f }

    val top: Layer? get() = layers.lastOrNull()

    private fun spec(velocity: Float): androidx.compose.animation.core.AnimationSpec<Float> = when {
        reduced -> Motion.enter(Motion.FADE)
        velocity != 0f -> Motion.settle()
        else -> Motion.move(Motion.SHEET)
    }

    /** vm.screen поменялся: доводим стопку до него. */
    fun go(route: Route) {
        val v = pendingVelocity; pendingVelocity = 0f
        if (layers.isEmpty()) {
            layers += Layer(route, Edge.BOTTOM, 1f); trail.clear(); trail += route to Edge.BOTTOM; return
        }
        // чужой жест, который не довели: убираем его следы
        gesture = null
        layers.removeAll { it.leaving }
        val top = layers.last()
        val i = trail.indexOfFirst { it.first == route }
        job?.cancel()
        settled = false
        if (top.route == route) {
            // жест уже вытянул этот экран — доводим с его скоростью
            if (trail.lastOrNull()?.first != route) trail += route to top.edge
            job = scope.launch { top.shown.animateTo(1f, spec(v), v); trim() }
            return
        }
        val beneathIndex = layers.indexOfLast { it.route == route && it !== top }
        if (i >= 0 || beneathIndex >= 0) {
            // назад: верхний уходит за свой край, под ним уже лежит (или кладём) нужный экран
            val edge = if (i >= 0) trail[i].second else Edge.BOTTOM
            if (i >= 0) while (trail.size > i + 1) trail.removeAt(trail.lastIndex)
            if (beneathIndex < 0) layers.add(layers.lastIndex, Layer(route, edge, 1f))
            else { val keep = layers.take(beneathIndex + 1) + top; layers.clear(); layers.addAll(keep) }
            top.leaving = true
            job = scope.launch {
                layers.forEach { if (it !== top && it.shown.value < 1f) it.shown.snapTo(1f) }
                top.shown.animateTo(0f, spec(v), v)
                layers.remove(top); trim()
            }
        } else {
            // вперёд: новый экран приходит со своей стороны поверх неподвижного старого
            val edge = edgeOf(top.route, route)
            trail += route to edge
            val layer = Layer(route, edge, 0f)
            layers += layer
            job = scope.launch { layer.shown.animateTo(1f, spec(v), v); trim() }
        }
    }

    /** Под верхним, когда он встал на место, ничего не держим: старые экраны не рисуются и не считаются. */
    private fun trim() {
        val top = layers.lastOrNull() ?: return
        layers.removeAll { it !== top }
        settled = true
    }

    // ---------- жесты ----------

    /** Тянут ещё не открытый экран [route] со стороны [edge]: [fraction] — какая доля уже видна. */
    fun previewTo(route: Route, edge: Edge, fraction: Float) {
        if (!settled) return
        val g = gesture
        val layer = if (g is Preview && g.layer.route == route) g.layer else {
            if (g is Preview) layers.remove(g.layer)
            Layer(route, edge, 0f).also { layers += it; gesture = Preview(it) }
        }
        val f = fraction.coerceIn(0f, 1f)
        (gesture as? Preview)?.fraction = f
        scope.launch { layer.shown.snapTo(f) }
    }

    /** Палец отпустили. [velocity] — px/с в сторону открытия. true — открываем, и вызывающий меняет экран. */
    fun previewEnd(velocity: Float, open: () -> Unit) {
        val g = gesture as? Preview ?: return
        gesture = null
        if (Motion.commits(g.fraction, velocity, height)) {
            pendingVelocity = velocity / height; open()
        } else {
            g.layer.leaving = true
            scope.launch { g.layer.shown.animateTo(0f, spec(velocity / height), velocity / height); layers.remove(g.layer) }
        }
    }

    /** Отмена: убрать недотянутый экран (жест сменил направление и т. п.). */
    fun previewCancel() {
        val g = gesture as? Preview ?: return
        gesture = null; g.layer.leaving = true
        scope.launch { g.layer.shown.animateTo(0f, Motion.settle()); layers.remove(g.layer) }
    }

    /** Экран под верхним: куда вернёт «назад». */
    val backRoute: Route? get() = trail.getOrNull(trail.lastIndex - 1)?.first

    /** Верхний экран тянут к его краю на [px] (сумма с начала жеста, ≥ 0). */
    fun dismissBy(px: Float) {
        val top = layers.lastOrNull() ?: return
        var g = gesture
        if (g !is Dismiss) {
            if (!settled || reduced) return
            val back = backRoute ?: return
            val beneath = Layer(back, trail[trail.lastIndex - 1].second, 1f)
            layers.add(layers.lastIndex, beneath)
            g = Dismiss(top, beneath); gesture = g
        }
        g.fraction = (px / height).coerceIn(0f, 1f)
        val shown = 1f - g.fraction
        scope.launch { g.top.shown.snapTo(shown) }
    }

    val dismissing: Boolean get() = gesture is Dismiss

    /** [velocity] — px/с в сторону края. true — закрываем, вызывающий делает «назад». */
    fun dismissEnd(velocity: Float, close: () -> Unit) {
        val g = gesture as? Dismiss ?: return
        gesture = null
        if (Motion.commits(g.fraction, velocity, height)) {
            pendingVelocity = -velocity / height; close()
        } else {
            scope.launch {
                g.top.shown.animateTo(1f, Motion.settle(), -velocity / height)
                if (gesture == null && layers.lastOrNull() === g.top) layers.remove(g.beneath)
            }
        }
    }
}

val LocalScreenStack = staticCompositionLocalOf<ScreenStack?> { null }

@Composable
fun rememberScreenStack(): ScreenStack {
    val scope = rememberCoroutineScope()
    return remember { ScreenStack(scope) }
}

/** Рисует стопку: каждый экран непрозрачный, нижний чуть притеняется, пока его накрывают. */
@Composable
fun ScreenStackHost(stack: ScreenStack, route: Route, reduced: Boolean, content: @Composable (Route) -> Unit) {
    val oz = LocalOz.current
    stack.reduced = reduced
    remember(stack) { if (stack.layers.isEmpty()) stack.go(route); 0 }
    LaunchedEffect(route) { stack.go(route) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val h = with(LocalDensity.current) { maxHeight.toPx() }
        stack.height = h
        CompositionLocalProvider(LocalScreenStack provides stack) {
            val layers = stack.layers.toList()
            layers.forEachIndexed { index, layer ->
                key(layer.id) {
                    val above = layers.getOrNull(index + 1)
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val s = layer.shown.value
                                if (reduced) alpha = s
                                else translationY = layer.edge.sign * (1f - s) * h
                            }
                            .background(oz.bg)
                            .drawWithContent {
                                drawContent()
                                // накрытый экран притеняется не больше чем на треть затемнения листов: глубина без шума
                                val cover = above?.shown?.value ?: 0f
                                if (cover > 0f && !reduced) drawRect(oz.scrim.copy(alpha = oz.scrim.alpha * 0.35f * cover))
                            }
                            .safeDrawingPadding(),
                    ) { content(layer.route) }
                }
            }
        }
    }
}
