package com.khizrim.dialer.ui.kit

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import com.khizrim.dialer.ui.theme.Motion
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.khizrim.dialer.ui.nav.Layer
import com.khizrim.dialer.ui.nav.dismissDrag
import com.khizrim.dialer.ui.theme.LocalOz
import com.khizrim.dialer.ui.theme.OzShape
import com.khizrim.dialer.ui.theme.OzType
import com.khizrim.dialer.ui.theme.raisedFill

/** Последнее непустое значение: лист дорисовывает своё содержимое, пока уходит. */
@Composable
fun <T> rememberLast(value: T?): T? {
    val box = remember { arrayOfNulls<Any>(1) }
    if (value != null) box[0] = value
    @Suppress("UNCHECKED_CAST")
    return box[0] as T?
}

/** Ручка листа: за неё (и за весь лист) его тянут вниз. */
@Composable
fun Grab(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.width(36.dp).height(4.dp).background(LocalOz.current.handle, RoundedCornerShape(2.dp)))
    }
}

/**
 * Лист снизу по содержимому. Тянется вниз весь, а если внутри список — сначала докручивается список.
 * Не выше 90% экрана: сверху всегда видно, откуда он пришёл.
 */
@Composable
fun Sheet(
    l: Layer,
    modifier: Modifier = Modifier,
    scroll: Boolean = true,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val oz = LocalOz.current
    val max = LocalConfiguration.current.screenHeightDp.dp * 0.9f
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(max = max)
            // содержимое листа поменялось (раскрылся выбор, пришёл номер): лист плавно меняет высоту, а не прыгает
            .animateContentSize(Motion.size())
            .background(oz.sheet, OzShape.sheetTop)
            .dismissDrag(l)
            .navigationBarsPadding()
            .padding(bottom = 10.dp),
    ) {
        Grab()
        if (scroll) Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())) { content() }
        else content()
        // низ листа (главная кнопка) стоит на месте, что бы ни раскрылось над ним
        footer?.invoke(this)
    }
}

@Composable
fun SheetTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = OzType.title, color = LocalOz.current.fg,
        modifier = modifier.padding(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 10.dp).semantics { heading() },
    )
}

@Composable
fun SheetText(text: String) {
    Text(text, style = OzType.body, color = LocalOz.current.fg3, modifier = Modifier.padding(start = 22.dp, end = 22.dp, bottom = 12.dp))
}

/** Строка выбора в листе: значок, текст, пояснение мелко, отметка у выбранного. */
@Composable
fun Opt(
    title: String,
    onClick: () -> Unit,
    @DrawableRes icon: Int? = null,
    sub: String? = null,
    checked: Boolean = false,
    color: Color = LocalOz.current.fg,
    onLongClick: (() -> Unit)? = null,
    lead: (@Composable () -> Unit)? = null,
) {
    val oz = LocalOz.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .heightIn(min = 56.dp)
            .press(onClick, onLongClick, OzShape.row, label = title)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            lead != null -> { lead(); Spacer(Modifier.width(16.dp)) }
            icon != null -> { OzIcon(icon, if (color == oz.fg) oz.fg2 else color, 24.dp); Spacer(Modifier.width(16.dp)) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = OzType.body, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, style = OzType.meta, color = oz.fg3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (checked) OzIcon(com.khizrim.dialer.R.drawable.ic_oz_check, oz.accent, 22.dp, Modifier.padding(start = 12.dp))
    }
}

/** Широкая главная кнопка листа: «Позвонить», «Заблокировать». Одна на лист. */
@Composable
fun MainButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    fill: Color = LocalOz.current.accentFill,
    content: Color = Color.White,
    onLongClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(56.dp)
            .then(if (fill == Color.Transparent) Modifier else Modifier.raisedFill(fill, shape))
            .press(onClick, onLongClick, shape, pressColor = Color.White.copy(alpha = .14f), label = text),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { OzIcon(icon, content, 22.dp); Spacer(Modifier.width(10.dp)) }
        Text(text, style = OzType.strong.copy(fontWeight = FontWeight.SemiBold), color = content)
    }
}

/** Тихая вторая кнопка под главной: «Отмена». */
@Composable
fun QuietButton(text: String, onClick: () -> Unit, color: Color = LocalOz.current.fg3) {
    Box(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(52.dp).press(onClick, shape = RoundedCornerShape(18.dp), label = text),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = OzType.strong, color = color) }
}

/** Номер SIM в рамке: «1», «2». */
@Composable
fun SimBadge(n: Int, color: Color = LocalOz.current.fg2) {
    Box(
        Modifier.size(28.dp).background(Color.Transparent, RoundedCornerShape(8.dp))
            .then(Modifier.padding(0.dp)),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(28.dp)) {
            drawRoundRect(color.copy(alpha = .6f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
        }
        Text(n.toString(), style = OzType.small.copy(fontWeight = FontWeight.Bold), color = color)
    }
}
