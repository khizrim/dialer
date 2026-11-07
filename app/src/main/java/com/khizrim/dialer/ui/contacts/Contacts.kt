package com.khizrim.dialer.ui.contacts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khizrim.dialer.DialerViewModel
import com.khizrim.dialer.R
import com.khizrim.dialer.SheetMode
import com.khizrim.dialer.data.MatchField
import com.khizrim.dialer.data.PhoneFormat
import com.khizrim.dialer.data.TextHit
import com.khizrim.dialer.model.Person
import com.khizrim.dialer.ui.kit.MethodMark
import com.khizrim.dialer.ui.kit.OzIcon
import com.khizrim.dialer.ui.kit.TextAction
import com.khizrim.dialer.ui.kit.press
import com.khizrim.dialer.ui.nav.Layer
import com.khizrim.dialer.ui.nav.LocalNav
import com.khizrim.dialer.ui.nav.dismissDrag
import com.khizrim.dialer.ui.openCard
import com.khizrim.dialer.ui.theme.LocalGlass
import com.khizrim.dialer.ui.theme.LocalOz
import com.khizrim.dialer.ui.theme.Motion
import com.khizrim.dialer.ui.theme.OzType
import com.khizrim.dialer.ui.theme.frosted
import com.khizrim.dialer.ui.theme.glass
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

private sealed interface CItem {
    val key: String
    data object Hero : CItem { override val key = "hero" }
    data class Label(val text: String, override val key: String) : CItem
    /** «Избранные» с «Изменить» справа. */
    data object FavHeader : CItem { override val key = "favhead" }
    /** Избранные в режиме «Изменить»: убрать, переставить, способ по нажатию. */
    data object FavEditor : CItem { override val key = "favedit" }
    data class Letter(val letter: String) : CItem { override val key = "L$letter" }
    data class Row(val p: Person, val sub: String?, override val key: String) : CItem
    data class Hit(val h: TextHit, override val key: String) : CItem
    data object Empty : CItem { override val key = "none" }
    data object Create : CItem { override val key = "create" }
}

private fun letterOf(p: Person): String = p.shownName.firstOrNull { !it.isWhitespace() }?.takeIf { it.isLetter() }?.uppercaseChar()?.toString() ?: "#"

/**
 * Контакты — экран снизу, с поиском у большого пальца: поле на стекле внизу, клавиатура открыта сразу.
 * Листнули — клавиатура спряталась. Дотянули список до верха и тянете дальше — экран уходит вниз.
 */
@Composable
fun ContactsScreen(vm: DialerViewModel, l: Layer) {
    val oz = LocalOz.current
    val nav = LocalNav.current
    val haze = remember { HazeState() }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val q = vm.textQuery
    val collator = remember { Collator.getInstance(Locale.getDefault()) }
    var editFav by remember { mutableStateOf(false) }
    val sorted = remember(vm.people) { vm.people.distinctBy { it.id }.sortedWith { a, b -> collator.compare(a.shownName, b.shownName) } }
    val res = androidx.compose.ui.platform.LocalContext.current.resources

    val favs = vm.favorites
    LaunchedEffect(favs.isEmpty()) { if (favs.isEmpty()) editFav = false }
    val items = remember(sorted, q, vm.textHits, favs, editFav) {
        buildList {
            add(CItem.Hero)
            if (q.isBlank()) {
                if (favs.isNotEmpty()) {
                    add(CItem.FavHeader)
                    if (editFav) add(CItem.FavEditor) else favs.distinctBy { it.id }.forEach { add(CItem.Row(it, null, "f" + it.id)) }
                }
                // по буквам: «Ё» и «Е», «É» и «E» сортировщик ставит вперемешку, поэтому сначала группы, потом строки,
                // иначе одна буква встречается дважды и ключи списка совпадают.
                // Сначала алфавит, на котором записано больше людей; второй отделён тихой подписью «A–Z» или «А–Я»,
                // иначе латинская «A» и кириллическая «А» выглядят одной буквой, встреченной дважды
                val groups = sorted.groupBy(::letterOf)
                fun script(l0: String) = when (l0.firstOrNull()) { null -> 2; in 'A'..'Z', in 'À'..'ž' -> 1; else -> if (l0 == "#") 3 else 0 }
                val cyr = groups.filterKeys { script(it) == 0 }.values.sumOf { it.size }
                val lat = groups.filterKeys { script(it) == 1 }.values.sumOf { it.size }
                fun rank(l0: String) = script(l0).let { if (it <= 1 && lat > cyr) 1 - it else it }
                var lastRank = -1
                groups.entries
                    .sortedWith(compareBy<Map.Entry<String, List<Person>>> { rank(it.key) }.thenComparator { a, b -> collator.compare(a.key, b.key) })
                    .forEach { (l0, ps) ->
                        val r = rank(l0)
                        if (lastRank == 0 && r == 1) add(CItem.Label(if (script(l0) == 1) "A–Z" else "А–Я", "script"))
                        lastRank = r
                        add(CItem.Letter(l0))
                        ps.forEach { p -> add(CItem.Row(p, p.company?.takeIf { it.isNotBlank() }, "p" + p.id)) }
                    }
                if (sorted.isNotEmpty()) add(CItem.Create)
            } else {
                val hits = vm.textHits
                if (hits.isEmpty()) add(CItem.Empty) else hits.distinctBy { it.person.id }.forEach { add(CItem.Hit(it, "h" + it.person.id)) }
            }
        }
    }
    val letters = remember(items) { items.mapIndexedNotNull { i, it -> (it as? CItem.Letter)?.let { l0 -> l0.letter to i } } }

    // экран дошёл — открываем клавиатуру; уходит — прячем, запрос забываем
    LaunchedEffect(l) {
        snapshotFlow { l.p > 0.98f && !l.closing }.first { it }
        runCatching { focus.requestFocus() }
    }
    LaunchedEffect(l.closing) { if (l.closing) focusManager.clearFocus() }
    DisposableEffect(Unit) { onDispose { vm.typeText("") } }
    // листнули пальцем — клавиатура уходит
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress }.collect { if (it) focusManager.clearFocus() }
    }

    val imeBottom = with(density) { WindowInsets.ime.getBottom(this).toDp() }
    val navBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val statusTop = with(density) { WindowInsets.statusBars.getTop(this).toDp() }

    CompositionLocalProvider(LocalGlass provides haze) {
        Box(Modifier.fillMaxSize().background(oz.bg).dismissDrag(l)) {
            LazyColumn(
                Modifier.fillMaxSize().haze(haze).background(oz.bg),
                state = list,
                contentPadding = PaddingValues(top = statusTop, bottom = maxOf(imeBottom, navBottom) + 96.dp),
            ) {
                items(items, key = { it.key }) { item ->
                    when (item) {
                        // стекло внутри того, что оно размывает, рисовало бы само себя: здесь только вуаль
                        CItem.Hero -> CompositionLocalProvider(LocalGlass provides null) { Hero(onNew = { vm.addNamedContact(q) }, onSettings = { focusManager.clearFocus(); vm.openSettings() }) }
                        CItem.FavHeader -> FavHeader(editFav) { focusManager.clearFocus(); editFav = !editFav }
                        CItem.FavEditor -> FavEditor(vm, favs)
                        is CItem.Label -> Text(item.text, style = OzType.meta.copy(fontWeight = FontWeight.Medium), color = oz.fg3, modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp).semantics { heading() })
                        is CItem.Letter -> Text(item.letter, style = OzType.title.copy(fontSize = 20.sp), color = oz.fg3, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 2.dp).semantics { heading() })
                        is CItem.Row -> PersonRow(vm, item.p, AnnotatedString(item.p.shownName), item.sub) { focusManager.clearFocus(); nav.openCard(item.p.id) }
                        is CItem.Hit -> PersonRow(vm, item.h.person, mark(item.h.person.shownName, item.h.nameRange), why(item.h, res)) { focusManager.clearFocus(); nav.openCard(item.h.person.id) }
                        CItem.Empty -> NothingFound(vm, q)
                        CItem.Create -> Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                            TextAction(stringResource(R.string.edit_new_title), oz.fg3, { vm.addNamedContact("") }, style = OzType.small)
                        }
                    }
                }
            }

            // маленький заголовок, когда большой ушёл под строку состояния
            val mini by remember { derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 120 } }
            val a by animateFloatAsState(if (mini) 1f else 0f, Motion.enter(Motion.FADE), label = "mini")
            if (a > 0f) {
                Box(Modifier.fillMaxWidth().graphicsLayer { alpha = a }.frosted().statusBarsPadding().height(44.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.contacts), style = OzType.strong, color = oz.fg)
                }
            }

            if (q.isBlank() && letters.size > 1) {
                Rail(letters.map { it.first }, Modifier.align(Alignment.CenterEnd).padding(end = 2.dp)) { i ->
                    focusManager.clearFocus()
                    scope.launch { list.scrollToItem(letters[i].second) }
                }
            }

            SearchPill(
                q, vm::typeText, focus,
                Modifier.align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = (imeBottom - navBottom).coerceAtLeast(0.dp))
                    .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
            ) { focusManager.clearFocus() }
        }
    }
}

/** «Избранные» и справа «Изменить» / «Готово». */
@Composable
private fun FavHeader(editing: Boolean, onToggle: () -> Unit) {
    val oz = LocalOz.current
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.favorites), style = OzType.meta.copy(fontWeight = FontWeight.Medium), color = oz.fg3, modifier = Modifier.weight(1f).semantics { heading() })
        TextAction(stringResource(if (editing) R.string.fav_done else R.string.fav_edit), oz.accent, onToggle, style = OzType.small)
    }
}

/**
 * Избранные в режиме «Изменить»: за ручку слева — переставить (порядок тот же, что в доке),
 * справа — что делает нажатие (меняется тут же) и крестик «убрать», с «Вернуть» в тосте.
 */
@Composable
private fun FavEditor(vm: DialerViewModel, favs: List<Person>) {
    val oz = LocalOz.current
    val rowPx = with(LocalDensity.current) { 60.dp.toPx() }
    var dragging by remember { mutableStateOf<Long?>(null) }
    var offset by remember { mutableStateOf(0f) }
    val list by androidx.compose.runtime.rememberUpdatedState(favs)
    Column(Modifier.fillMaxWidth()) {
        favs.forEach { p ->
            androidx.compose.runtime.key(p.id) {
                val m = vm.defaultOf(p)
                val lifted = dragging == p.id
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .graphicsLayer { if (lifted) { translationY = offset; shadowElevation = 12f } }
                        .background(oz.bg)
                        .padding(start = 4.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val drag = stringResource(R.string.fav_drag)
                    Box(
                        Modifier
                            .size(48.dp)
                            .semantics { contentDescription = drag }
                            .pointerInput(p.id) {
                                detectVerticalDragGestures(
                                    onDragStart = { dragging = p.id; offset = 0f },
                                    onDragEnd = { dragging = null; offset = 0f },
                                    onDragCancel = { dragging = null; offset = 0f },
                                ) { c, dy ->
                                    c.consume()
                                    offset += dy
                                    val at = list.indexOfFirst { it.id == p.id }
                                    // переехали за половину соседней строки — меняемся с ней местами
                                    if (offset > rowPx / 2 && at < list.size - 1) { vm.moveFavorite(at, at + 1); offset -= rowPx }
                                    else if (offset < -rowPx / 2 && at > 0) { vm.moveFavorite(at, at - 1); offset += rowPx }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) { OzIcon(R.drawable.ic_oz_grip, oz.fg3, 20.dp) }
                    Text(p.shownName, style = OzType.row.copy(fontWeight = FontWeight.Normal), color = oz.fg, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 4.dp))
                    if (p.methods.size > 1) {
                        Row(
                            Modifier.height(44.dp).press({ vm.openSheet(p, SheetMode.DEFAULT) }, shape = RoundedCornerShape(14.dp), label = stringResource(R.string.on_tap_title)).padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MethodMark(m, size = 18.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(m?.short ?: R.string.ask_every_time), style = OzType.small, color = oz.accent, maxLines = 1)
                        }
                    } else {
                        Box(Modifier.padding(horizontal = 10.dp)) { MethodMark(m, size = 18.dp) }
                    }
                    val remove = stringResource(R.string.remove_favorite)
                    Box(Modifier.size(44.dp).press({ vm.toggleFavorite(p) }, shape = CircleShape, label = remove).semantics { contentDescription = remove }, contentAlignment = Alignment.Center) {
                        OzIcon(R.drawable.ic_oz_x, oz.fg3, 18.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun Hero(onNew: () -> Unit, onSettings: () -> Unit) {
    val oz = LocalOz.current
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 40.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.contacts), style = OzType.namePerson.copy(fontSize = 32.sp), color = oz.fg, modifier = Modifier.weight(1f).semantics { heading() })
        GlassRound(R.drawable.ic_oz_plus, stringResource(R.string.edit_new_title), onNew)
        Spacer(Modifier.width(8.dp))
        GlassRound(R.drawable.ic_oz_settings, stringResource(R.string.settings), onSettings)
    }
}

@Composable
private fun GlassRound(icon: Int, label: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).glass(CircleShape).press(onClick, shape = CircleShape, label = label), contentAlignment = Alignment.Center) {
        OzIcon(icon, LocalOz.current.fg2, 22.dp)
    }
}

/** Строка человека: имя — карточка, значок справа — связаться основным способом, долгое — другим. */
@Composable
private fun PersonRow(vm: DialerViewModel, p: Person, name: AnnotatedString, sub: String?, onCard: () -> Unit) {
    val oz = LocalOz.current
    val m = vm.defaultOf(p)
    Row(Modifier.fillMaxWidth().height(if (sub != null) 60.dp else 52.dp).padding(start = 8.dp, end = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier.weight(1f).fillMaxHeight().press(onCard, shape = RoundedCornerShape(14.dp), label = p.shownName).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(name, style = if (p.nameIsNumber) OzType.rowNumber else OzType.row.copy(fontWeight = FontWeight.Normal), color = oz.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, style = OzType.meta, color = oz.fg3, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
        Box(
            Modifier.width(72.dp).fillMaxHeight()
                .press({ vm.perform(p, m) }, onLongClick = { if (!vm.askSim(p)) vm.openSheet(p, SheetMode.OTHER) }, shape = RoundedCornerShape(14.dp),
                    label = stringResource(m?.title ?: R.string.choose_method) + ": " + p.shownName),
            contentAlignment = Alignment.CenterEnd,
        ) { Box(Modifier.padding(end = 6.dp)) { MethodMark(m, size = 22.dp) } }
    }
}

@Composable
private fun NothingFound(vm: DialerViewModel, q: String) {
    val oz = LocalOz.current
    val digits = q.filter { it.isDigit() || it == '+' }
    Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.search_nothing), style = OzType.body, color = oz.fg3)
        if (PhoneFormat.digits(digits).length >= 3) {
            TextAction(stringResource(R.string.call_number, PhoneFormat.pretty(digits)), oz.accent, { vm.callNumber(digits) }, modifier = Modifier.padding(top = 8.dp))
        }
        TextAction(stringResource(R.string.create_named, q.trim()), oz.fg3, { vm.addNamedContact(q) }, style = OzType.small, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun SearchPill(q: String, onChange: (String) -> Unit, focus: FocusRequester, modifier: Modifier, onDone: () -> Unit) {
    val oz = LocalOz.current
    Row(
        modifier.fillMaxWidth().height(52.dp).glass(CircleShape).padding(start = 18.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OzIcon(R.drawable.ic_oz_search, oz.fg3, 20.dp)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (q.isEmpty()) Text(stringResource(R.string.search_placeholder), style = OzType.body, color = oz.fg3, maxLines = 1)
            BasicTextField(
                q, onChange,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                singleLine = true,
                textStyle = OzType.body.copy(color = oz.fg),
                cursorBrush = SolidColor(oz.accentFill),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onDone() }),
            )
        }
        if (q.isNotEmpty()) {
            Box(Modifier.size(44.dp).press({ onChange("") }, shape = CircleShape, label = stringResource(R.string.clear)), contentAlignment = Alignment.Center) {
                OzIcon(R.drawable.ic_oz_x, oz.fg3, 18.dp)
            }
        }
    }
}

/** Алфавит справа: ведёт список за пальцем. */
@Composable
private fun Rail(letters: List<String>, modifier: Modifier, onLetter: (Int) -> Unit) {
    val oz = LocalOz.current
    var h by remember { mutableIntStateOf(1) }
    var at by remember { mutableIntStateOf(-1) }
    fun pick(y: Float) {
        val i = (y / h * letters.size).toInt().coerceIn(0, letters.size - 1)
        if (i != at) { at = i; onLetter(i) }
    }
    Column(
        modifier
            .width(22.dp)
            .onSizeChanged { h = it.height.coerceAtLeast(1) }
            .pointerInput(letters) { detectTapGestures { pick(it.y); at = -1 } }
            .pointerInput(letters) { detectVerticalDragGestures(onDragEnd = { at = -1 }, onDragCancel = { at = -1 }) { c, _ -> pick(c.position.y) } },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEachIndexed { i, s ->
            Text(s, style = OzType.channel.copy(fontSize = 11.sp, lineHeight = 15.sp), color = if (i == at) oz.accent else oz.fg3)
        }
    }
}

private fun mark(text: String, range: IntRange?): AnnotatedString = buildAnnotatedString {
    if (range == null || range.isEmpty() || range.first < 0 || range.last >= text.length) { append(text); return@buildAnnotatedString }
    append(text.substring(0, range.first))
    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(text.substring(range.first, range.last + 1)) }
    append(text.substring(range.last + 1))
}

/** Почему человек нашёлся, если не по имени: «компания: …», «набрано в другой раскладке». */
private fun why(h: TextHit, res: android.content.res.Resources): String? {
    val field = when (h.field) {
        MatchField.NAME -> null
        MatchField.NICKNAME -> h.fieldText?.let { res.getString(R.string.found_nickname, it) }
        MatchField.COMPANY -> h.fieldText?.let { res.getString(R.string.found_company, it) }
        MatchField.TITLE -> h.fieldText?.let { res.getString(R.string.found_title, it) }
        MatchField.PHONE -> h.fieldText?.let { PhoneFormat.pretty(it) }
    }
    // как именно нашлось (раскладка, транслит) человеку не важно: он видит, что нашёлся тот, кого искал
    return field
}
