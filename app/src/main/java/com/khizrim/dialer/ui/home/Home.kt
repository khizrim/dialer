package com.khizrim.dialer.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.khizrim.dialer.DialerViewModel
import com.khizrim.dialer.MenuContext
import com.khizrim.dialer.R
import com.khizrim.dialer.SheetMode
import com.khizrim.dialer.data.Dates
import com.khizrim.dialer.model.Dir
import com.khizrim.dialer.model.HomeEntry
import com.khizrim.dialer.model.Method
import com.khizrim.dialer.ui.Dest
import com.khizrim.dialer.ui.kit.AccentRound
import com.khizrim.dialer.ui.kit.MethodMark
import com.khizrim.dialer.ui.kit.OzIcon
import com.khizrim.dialer.ui.kit.TextAction
import com.khizrim.dialer.ui.kit.press
import com.khizrim.dialer.ui.nav.Kind
import com.khizrim.dialer.ui.nav.Layer
import com.khizrim.dialer.ui.nav.LocalNav
import com.khizrim.dialer.ui.openCard
import com.khizrim.dialer.ui.theme.LocalGlass
import com.khizrim.dialer.ui.theme.LocalOz
import com.khizrim.dialer.ui.theme.LocalReducedMotion
import com.khizrim.dialer.ui.theme.Motion
import com.khizrim.dialer.ui.theme.OzType
import com.khizrim.dialer.ui.theme.frosted
import com.khizrim.dialer.ui.theme.glass
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

/** Подпись дня в ленте. */
private data class FeedDay(val text: String)

/** Строка «скрыть всех» над самыми старыми. */
private object FeedTop

/**
 * Главная — лента людей, свежие внизу, у большого пальца. Управление висит над ней на стекле:
 * избранные, поиск и клавиатура. Потянуть док вверх — контакты, ровно за пальцем.
 */
@Composable
fun Home(vm: DialerViewModel, onDock: (Int) -> Unit) {
    val oz = LocalOz.current
    val haze = LocalGlass.current ?: remember { HazeState() }
    val list = rememberLazyListState()
    var dockPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val res = LocalContext.current.resources
    val feed = vm.homeStack
    val rows = remember(feed) {
        buildList<Any> {
            var day: String? = null
            feed.asReversed().forEach { e ->
                if (e.inSilentGroup) { add(e); return@forEach }
                val d = if (e.lastAt > 0L) Dates.day(res, e.lastAt) else null
                if (day != null && d != day) add(FeedDay(day!!))
                day = d
                add(e)
            }
            day?.let { add(FeedDay(it)) }
            if (feed.size > 3) add(FeedTop)
        }
    }

    CompositionLocalProvider(LocalGlass provides haze) {
        Box(Modifier.fillMaxSize()) {
            Feed(vm, rows, list, bottom = with(density) { dockPx.toDp() } + 8.dp, Modifier.fillMaxSize().haze(haze).background(oz.bg))
            TopBand(rows, list)
            Dock(vm, Modifier.align(Alignment.BottomCenter).onSizeChanged { dockPx = it.height; onDock(it.height) })
        }
    }
}

@Composable
private fun Feed(vm: DialerViewModel, rows: List<Any>, list: androidx.compose.foundation.lazy.LazyListState, bottom: Dp, modifier: Modifier) {
    val oz = LocalOz.current
    val reduced = LocalReducedMotion.current
    val top = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() } + 52.dp
    LazyColumn(
        modifier.padding(horizontal = 8.dp),
        state = list,
        reverseLayout = true,
        contentPadding = PaddingValues(top = top, bottom = bottom),
    ) {
        if (rows.isEmpty()) {
            item(key = "empty") {
                Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 24.dp)) {
                    Text(stringResource(R.string.home_empty_title), style = OzType.nameHome, color = oz.fg)
                    Text(stringResource(R.string.home_empty_text), style = OzType.body, color = oz.fg3, modifier = Modifier.padding(top = 8.dp).widthIn(max = 320.dp))
                }
            }
        }
        items(rows, key = { r -> when (r) { is FeedDay -> "d" + r.text; is HomeEntry -> r.key; else -> "top" } }) { r ->
            val anim = Modifier.animateItem(
                fadeInSpec = Motion.enter(Motion.FADE),
                placementSpec = if (reduced) null else androidx.compose.animation.core.spring(1f, 380f, androidx.compose.ui.unit.IntOffset.VisibilityThreshold),
                fadeOutSpec = Motion.exit(Motion.FADE),
            )
            when (r) {
                is FeedDay -> Text(
                    r.text, style = OzType.meta.copy(fontWeight = FontWeight.Medium), color = oz.fg3,
                    modifier = anim.padding(start = 12.dp, top = 20.dp, bottom = 4.dp).semantics { heading() },
                )
                is HomeEntry -> if (r.silent > 0) SilentRow(vm, r, anim) else FeedRow(vm, r, anim)
                else -> Box(anim.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    TextAction(stringResource(R.string.home_clear), oz.fg3, vm::askClearHome, style = OzType.small)
                }
            }
        }
    }
}

/** Незнакомые без звука — одной тихой строкой; нажатие раскрывает их прямо в ленте. */
@Composable
private fun SilentRow(vm: DialerViewModel, e: HomeEntry, modifier: Modifier) {
    val oz = LocalOz.current
    val text = pluralStringResource(R.plurals.silent_unknown, e.silent, e.silent)
    Row(
        modifier.fillMaxWidth().height(64.dp).press(vm::toggleSilent, label = text).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text, style = OzType.row, color = oz.fg2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Dates.recentTime(e.lastAt)?.let { Text(it, style = OzType.meta, color = oz.fg3, modifier = Modifier.padding(top = 2.dp)) }
        }
        val turn by animateFloatAsState(if (vm.silentOpen) 180f else 0f, Motion.pixels(500f), label = "chevron")
        OzIcon(R.drawable.ic_oz_chevron_down, oz.fg3, 20.dp, Modifier.graphicsLayer { rotationZ = turn })
    }
}

/**
 * Строка ленты, 64 dp. Имя — карточка, значок справа — связаться (долгое — другой способ).
 * Смахнуть влево — скрыть до следующего контакта, с «Вернуть».
 */
@Composable
private fun FeedRow(vm: DialerViewModel, e: HomeEntry, modifier: Modifier) {
    val oz = LocalOz.current
    val nav = LocalNav.current
    val p = e.person
    val method = p?.let { vm.defaultOf(it) } ?: Method.PHONE.takeIf { p == null }
    val missed = e.dir == Dir.MISSED && !vm.isSilenced(e)
    val time = Dates.recentTime(e.lastAt)
    val w = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    var x by remember { mutableFloatStateOf(0f) }
    val drag = rememberDraggableState { d -> x = (x + d).coerceAtMost(0f) }
    val tag = if (p == null && e.number != null) com.khizrim.dialer.callerid.rememberCallerTag(e.number) else null
    val sub = buildString {
        append(e.missedNote ?: e.note ?: time ?: stringResource(if (e.dir == Dir.OUT) R.string.h_outgoing else R.string.h_incoming))
        if (e.missedNote != null && time != null) append(", ").append(time)
        if (tag != null) append(", ").append(tag)
    }
    val armed = x < -w * 0.35f

    Box(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .draggable(
                drag, Orientation.Horizontal,
                onDragStopped = { v ->
                    val go = (x < -w * 0.35f && v < 200f) || v < -0.6f * w
                    if (go) {
                        animate(x, -w - 40f, minOf(v, -1200f), Motion.pixels(300f)) { value, _ -> x = value }
                        vm.removeFromHome(e.key, now = true)
                    } else animate(x, 0f, v, Motion.pixels()) { value, _ -> x = value }
                },
            ),
    ) {
        // под строкой, справа: «Скрыть», ярче, когда отпустить уже значит скрыть
        Text(
            stringResource(R.string.hide), style = OzType.small.copy(fontWeight = FontWeight.Medium),
            color = if (armed) oz.danger else oz.fg3,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 20.dp).graphicsLayer { alpha = (-x / (w * 0.2f)).coerceIn(0f, 1f) },
        )
        Row(Modifier.fillMaxSize().graphicsLayer { translationX = x }.background(oz.bg), verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .press(
                        onClick = { if (p != null) nav.openCard(p.id) else e.number?.let(vm::openNumber) },
                        onLongClick = { vm.openMenu(e, MenuContext.HOME) },
                        label = e.title + ", " + sub,
                    )
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    e.title, style = if (p != null && !p.nameIsNumber) OzType.row else OzType.rowNumber,
                    color = if (missed) oz.danger else if (vm.isSilenced(e)) oz.fg2 else oz.fg, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (e.dir != null) {
                        OzIcon(if (e.dir == Dir.OUT) R.drawable.ic_oz_arr_out else R.drawable.ic_oz_arr_in, if (missed) oz.danger else oz.fg3, 14.dp, Modifier.padding(end = 4.dp))
                    }
                    Text(sub, style = OzType.meta, color = if (missed) oz.danger else oz.fg3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            // правая зона — связаться: на всю высоту строки, широкая, чтобы не промахнуться
            Box(
                Modifier
                    .width(96.dp)
                    .fillMaxHeight()
                    .press(
                        onClick = { vm.tap(e) },
                        onLongClick = {
                            when {
                                p != null && vm.askSim(p) -> Unit
                                p != null -> vm.openSheet(p, SheetMode.OTHER)
                                !vm.askSim(e.number, e.title) -> vm.openMenu(e, MenuContext.HOME)
                            }
                        },
                        label = stringResource(method?.title ?: R.string.choose_method) + ": " + e.title,
                    )
                    .padding(end = 14.dp),
                contentAlignment = Alignment.CenterEnd,
            ) { MethodMark(method, size = 24.dp) }
        }
    }
}

/** Полоса наверху появляется, только когда под ней что-то лежит, и называет день, если это не сегодня. */
@Composable
private fun TopBand(rows: List<Any>, list: androidx.compose.foundation.lazy.LazyListState) {
    val oz = LocalOz.current
    val res = LocalContext.current.resources
    val under by remember { derivedStateOf { list.canScrollForward } }
    val day by remember(rows) {
        derivedStateOf {
            val last = list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf null
            // ближайшая подпись дня выше (дальше по списку) — день самой верхней строки
            (last until rows.size).firstNotNullOfOrNull { (rows.getOrNull(it) as? FeedDay)?.text }
        }
    }
    val a by animateFloatAsState(if (under) 1f else 0f, Motion.enter(Motion.FADE), label = "band")
    if (a == 0f) return
    Box(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = a }
            .frosted()
            .statusBarsPadding()
            .height(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        val d = day
        if (d != null && d != res.getString(R.string.today)) Text(d, style = OzType.meta.copy(fontWeight = FontWeight.Medium), color = oz.fg2)
    }
}

/** Док: предупреждение (если звонки идут мимо), избранные на стекле, поиск и клавиатура. */
@Composable
private fun Dock(vm: DialerViewModel, modifier: Modifier) {
    val oz = LocalOz.current
    val nav = LocalNav.current
    val h = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val pulled = remember { arrayOfNulls<Layer>(1) }
    val pull = rememberDraggableState { d ->
        var l = pulled[0]
        if (l == null && d < 0f && nav.top == null) { l = nav.open(Dest.Contacts, Kind.RISE, animate = false); pulled[0] = l }
        if (l != null) { l.stop(); l.p = (l.p - d / h).coerceIn(0f, 1f) }
    }
    Column(
        modifier
            .fillMaxWidth()
            .draggable(pull, Orientation.Vertical, onDragStopped = { v -> pulled[0]?.let { nav.settle(it, v, h) }; pulled[0] = null })
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val alert = com.khizrim.dialer.ui.kit.rememberLast(vm.callAlert)
        AnimatedVisibility(
            vm.callAlert != null,
            enter = fadeIn(Motion.enter(Motion.FADE, delay = 60)) + expandVertically(Motion.size()),
            exit = fadeOut(Motion.exit(90)) + shrinkVertically(Motion.size()),
        ) {
            alert?.let { Notice(vm, it) }
        }
        val favs = rememberLastNonEmpty(vm.favorites)
        // первая звезда или последняя снятая: строка избранных раскрывается и сворачивается, док не прыгает
        AnimatedVisibility(
            vm.favorites.isNotEmpty(),
            enter = fadeIn(Motion.enter(Motion.FADE, delay = 60)) + expandVertically(Motion.size(), expandFrom = Alignment.Top),
            exit = fadeOut(Motion.exit(90)) + shrinkVertically(Motion.size(), shrinkTowards = Alignment.Top),
        ) {
            LazyRow(
                Modifier.fillMaxWidth().height(48.dp).glass(RoundedCornerShape(24.dp)),
                contentPadding = PaddingValues(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(favs, key = { it.id }) { p ->
                    val m = vm.defaultOf(p)
                    Row(
                        Modifier
                            .animateItem(fadeInSpec = Motion.enter(Motion.FADE), placementSpec = Motion.offset(), fadeOutSpec = Motion.exit(Motion.FADE))
                            .height(40.dp)
                            .press({ vm.perform(p, m) }, onLongClick = { nav.openCard(p.id) }, shape = RoundedCornerShape(20.dp),
                                label = stringResource(R.string.favorite_label, p.name, stringResource(m?.title ?: R.string.choose_method)))
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(vm.favLabel(p), style = OzType.row.copy(fontWeight = FontWeight.Medium), color = oz.fg, maxLines = 1)
                        Spacer(Modifier.width(6.dp))
                        MethodMark(m, size = 16.dp)
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val search = stringResource(R.string.search_by_name)
            Row(
                Modifier
                    .weight(1f)
                    .height(52.dp)
                    .glass(CircleShape)
                    .press({ if (nav.top == null) nav.open(Dest.Contacts, Kind.RISE) }, shape = CircleShape, label = search)
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OzIcon(R.drawable.ic_oz_search, oz.fg3, 20.dp)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.search_all), style = OzType.body, color = oz.fg3, maxLines = 1)
            }
            Spacer(Modifier.width(10.dp))
            AccentRound(R.drawable.ic_oz_grid, stringResource(R.string.open_keypad), { vm.openKeypad() })
        }
    }
}

/** Пока звонки идут мимо приложения или чего-то не хватает: одна строка на стекле и «Исправить». */
@Composable
private fun Notice(vm: DialerViewModel, alert: com.khizrim.dialer.telecom.CallAlert) {
    val oz = LocalOz.current
    val context = LocalContext.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
    ) { vm.reload() }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).glass(RoundedCornerShape(24.dp)).padding(start = 18.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(alert.text), style = OzType.small, color = oz.warn, modifier = Modifier.weight(1f).padding(vertical = 6.dp))
        TextAction(stringResource(alert.action), oz.accent, {
            val intent = vm.alertIntent()
            vm.alertOpened()
            val ok = intent != null && runCatching { launcher.launch(intent) }.isSuccess
            if (!ok) runCatching { context.startActivity(com.khizrim.dialer.telecom.CallReadiness.fallbackIntent(context)) }
        }, style = OzType.small)
        if (alert.dismissable) TextAction(stringResource(R.string.close), oz.fg3, vm::dismissAlert, style = OzType.small)
    }
}

/** Последний непустой список: пока строка сворачивается, в ней ещё видно, что в ней было. */
@Composable
private fun <T> rememberLastNonEmpty(list: List<T>): List<T> {
    val box = remember { arrayOf<List<T>>(emptyList()) }
    if (list.isNotEmpty()) box[0] = list
    return box[0]
}
