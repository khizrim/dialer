package com.khizrim.dialer.ui.home

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.khizrim.dialer.DialerViewModel
import com.khizrim.dialer.R
import com.khizrim.dialer.data.MatchField
import com.khizrim.dialer.data.MatchVia
import com.khizrim.dialer.data.PhoneFormat
import com.khizrim.dialer.data.TextHit
import com.khizrim.dialer.ui.components.GhostIconButton
import com.khizrim.dialer.ui.components.OzIcon
import com.khizrim.dialer.ui.components.press
import com.khizrim.dialer.ui.components.dismissOnDrag
import com.khizrim.dialer.ui.components.dismissOnOverscroll
import com.khizrim.dialer.ui.theme.LocalOz
import com.khizrim.dialer.ui.theme.OzType

/** Поиск по имени: поле внизу над клавиатурой, лучшее совпадение прямо над полем. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun NameSearchScreen(vm: DialerViewModel) {
    val oz = LocalOz.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    // клавиатуру поднимаем, только когда контакты точно открыли (жест дотянули или нажали), а не пока их тянут
    val active = vm.screen == com.khizrim.dialer.Screen.SEARCH
    LaunchedEffect(active) { if (active) runCatching { focus.requestFocus() } }
    val q = vm.textQuery
    val digits = q.filter { it.isDigit() }
    val numeric = digits.length >= 3 && q.none { it.isLetter() }
    // пустой запрос — «Указатель»: алфавит сверху вниз, по группам букв; избранных не повторяем, без имени — в конце
    val all = remember(vm.people) {
        val collator = java.text.Collator.getInstance(java.util.Locale("ru"))
        vm.people.filter { !it.starred }.sortedWith { a, b ->
            when {
                a.nameIsNumber != b.nameIsNumber -> if (a.nameIsNumber) 1 else -1
                else -> collator.compare(a.name, b.name)
            }
        }
    }
    val groups = remember(all) { all.groupBy { if (it.nameIsNumber) "#" else it.name.firstOrNull()?.uppercaseChar()?.takeIf { c -> c.isLetter() }?.toString() ?: "#" }.toList() }
    // начали листать — прячем клавиатуру, чтобы видеть список целиком
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    // шторку закрыли (назад, жест, тап): клавиатура уходит вместе с ней, а не остаётся висеть над лаунчером
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { keyboard?.hide(); focusManager.clearFocus(force = true) }
    }
    LaunchedEffect(active) { if (!active) { keyboard?.hide(); focusManager.clearFocus(force = true) } }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) { keyboard?.hide(); focusManager.clearFocus() }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        val indexState = androidx.compose.foundation.lazy.rememberLazyListState()
        LaunchedEffect(indexState.isScrollInProgress) {
            if (indexState.isScrollInProgress) { keyboard?.hide(); focusManager.clearFocus() }
        }
        if (q.isBlank()) Box(Modifier.weight(1f).fillMaxWidth()) {
            // докрутили до самого верха и тянут дальше вниз — контакты уезжают за пальцем
            // где начинается каждая группа: для колонки букв справа
            val starts = remember(groups) { var i = 0; groups.map { (l, ps) -> (l to i).also { i += 1 + ps.size } } }
            LazyColumn(Modifier.fillMaxSize().dismissOnOverscroll { vm.closeTextSearch() }.padding(horizontal = 8.dp), state = indexState) {
                groups.forEach { (letter, ps) ->
                    // буква серым над своей группой: вся ширина строки остаётся имени
                    item(key = "h$letter") {
                        Text(
                            letter, style = OzType.meta.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = oz.fg3,
                            modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 2.dp).semantics { heading() },
                        )
                    }
                    items(ps, key = { "a" + it.id }) { p -> IndexRow(vm, p) }
                }
            }
            // колонка букв для быстрой прокрутки — при 30+ контактах и только пока список листают или её держат
            var railHeld by remember { androidx.compose.runtime.mutableStateOf(false) }
            var railShown by remember { androidx.compose.runtime.mutableStateOf(false) }
            LaunchedEffect(indexState.isScrollInProgress, railHeld) {
                if (indexState.isScrollInProgress || railHeld) railShown = true
                else { kotlinx.coroutines.delay(1200); railShown = false }
            }
            if (all.size >= 30) androidx.compose.animation.AnimatedVisibility(
                railShown, Modifier.align(Alignment.CenterEnd),
                enter = androidx.compose.animation.fadeIn(com.khizrim.dialer.ui.theme.Motion.enter(com.khizrim.dialer.ui.theme.Motion.FADE)),
                exit = androidx.compose.animation.fadeOut(com.khizrim.dialer.ui.theme.Motion.exit(com.khizrim.dialer.ui.theme.Motion.FADE)),
            ) {
                LetterRail(starts.map { it.first }, Modifier, onHeld = { railHeld = it }) { i ->
                    scope.launch { indexState.scrollToItem(starts[i].second) }
                }
            }
        } else LazyColumn(Modifier.weight(1f).fillMaxWidth().dismissOnOverscroll { vm.closeTextSearch() }.padding(horizontal = 8.dp), state = listState, reverseLayout = true) {
            if (numeric) {
                item(key = "call") {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).press({ vm.closeTextSearch(); vm.callNumber(q.trim()) }).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.call_to, PhoneFormat.pretty(q.trim())), style = OzType.list, color = oz.accent, modifier = Modifier.weight(1f))
                        OzIcon(R.drawable.ic_oz_phone, oz.fg3)
                    }
                }
            }
            items(vm.textHits, key = { it.person.key }) { h -> HitRow(vm, h) }
            // ничего не нашлось: сразу предлагаем создать контакт с тем, что набрано
            if (q.isNotBlank() && vm.textHits.isEmpty()) {
                item(key = "create") {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp)
                            .press({ if (numeric) vm.addToContacts(q.trim()) else vm.addNamedContact(q) })
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OzIcon(R.drawable.ic_oz_user_plus, oz.fg2)
                        Text(
                            if (numeric) stringResource(R.string.create_with_number) else stringResource(R.string.create_named, q.trim()),
                            style = OzType.body, color = oz.fg2, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        // поле 48dp на мягкой заливке без рамки, справа от него настройки. Поле тянут вниз — весь экран идёт за пальцем
        Row(
            Modifier.dismissOnDrag { vm.closeTextSearch() }.fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        Row(
            Modifier.weight(1f).height(48.dp)
                .background(oz.soft, com.khizrim.dialer.ui.theme.OzShape.row).padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OzIcon(R.drawable.ic_oz_search, oz.fg3, 20.dp)
            val hint = stringResource(R.string.search_by_name)
            Box(Modifier.weight(1f).padding(start = 12.dp)) {
                BasicTextField(
                    value = q,
                    onValueChange = vm::typeText,
                    singleLine = true,
                    textStyle = OzType.list.copy(color = oz.fg),
                    cursorBrush = SolidColor(oz.accent),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.textHits.firstOrNull()?.let { vm.tapHit(it.person) } }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = hint },
                    decorationBox = { inner -> Box { if (q.isEmpty()) Text(hint, style = OzType.list, color = oz.fg3); inner() } },
                )
            }
            // крестик — только когда есть что стереть; закрывают свайпом вниз или жестом назад
            if (q.isNotEmpty()) GhostIconButton(R.drawable.ic_oz_x, stringResource(R.string.clear), { vm.typeText("") }, tint = oz.fg3)
        }
        GhostIconButton(R.drawable.ic_oz_settings, stringResource(R.string.settings), vm::openSettings, tint = oz.fg2)
        }
    }
}

@Composable
private fun HitRow(vm: DialerViewModel, h: TextHit) {
    val oz = LocalOz.current
    val p = h.person
    val method = vm.defaultOf(p)
    val name = buildAnnotatedString {
        val r = h.nameRange?.takeIf { it.first >= 0 && it.last < p.name.length }
        if (r == null) append(p.name) else {
            append(p.name.substring(0, r.first))
            withStyle(SpanStyle(color = oz.accent)) { append(p.name.substring(r.first, r.last + 1)) }
            append(p.name.substring(r.last + 1))
        }
    }
    val where = when {
        h.field == MatchField.COMPANY -> stringResource(R.string.found_company, h.fieldText.orEmpty())
        h.field == MatchField.TITLE -> stringResource(R.string.found_title, h.fieldText.orEmpty())
        h.field == MatchField.NICKNAME -> stringResource(R.string.found_nickname, h.fieldText.orEmpty())
        h.field == MatchField.PHONE -> PhoneFormat.pretty(h.fieldText.orEmpty())
        h.via == MatchVia.LAYOUT -> stringResource(R.string.found_layout)
        h.via == MatchVia.TRANSLIT -> stringResource(R.string.found_translit)
        else -> null
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier.weight(1f).heightIn(min = 52.dp)
                .press({ vm.openPerson(p.id) }, onLongClick = { vm.openMenu(p) }, label = p.name + (where?.let { ", $it" } ?: ""))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        ) {
            if (p.nameIsNumber) Text(p.shownName, style = OzType.rowNumber, color = oz.fg, maxLines = 1)
            else Text(name, style = OzType.row, color = oz.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (where != null) Text(where, style = OzType.meta, color = oz.fg3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        com.khizrim.dialer.ui.components.ReachButton(method, p.name) { vm.tapHit(p) }
    }
}

/** Строка указателя: имя на 48dp; нажатие — карточка, значок справа связывает. */
@Composable
private fun IndexRow(vm: DialerViewModel, p: com.khizrim.dialer.model.Person) {
    val oz = LocalOz.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.weight(1f).heightIn(min = 52.dp).press({ vm.openPerson(p.id) }, onLongClick = { vm.openMenu(p) }, label = p.name).padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(p.shownName, style = if (p.nameIsNumber) OzType.rowNumber else OzType.row, color = oz.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        com.khizrim.dialer.ui.components.ReachButton(vm.defaultOf(p), p.name) { vm.tapHit(p) }
    }
}

/** Колонка букв справа: тап или ведение пальцем прокручивает к группе, рядом крупно текущая буква. */
@Composable
private fun LetterRail(letters: List<String>, modifier: Modifier, onHeld: (Boolean) -> Unit = {}, onPick: (Int) -> Unit) {
    val oz = LocalOz.current
    var current by remember { androidx.compose.runtime.mutableStateOf<Int?>(null) }
    var railHeight by remember { androidx.compose.runtime.mutableStateOf(1) }
    fun pick(y: Float) {
        val i = ((y / railHeight) * letters.size).toInt().coerceIn(0, letters.lastIndex)
        if (i != current) { current = i; onPick(i) }
    }
    Box(modifier) {
        Column(
            Modifier.width(28.dp)
                .onSizeChanged { railHeight = it.height.coerceAtLeast(1) }
                .pointerInput(letters) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume(); onHeld(true); pick(down.position.y)
                        do {
                            val ev = awaitPointerEvent()
                            val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (c.pressed) { c.consume(); pick(c.position.y) }
                        } while (c.pressed)
                        current = null
                        onHeld(false)
                    }
                }
                .semantics { contentDescription = letters.joinToString(" ") },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            letters.forEach { l -> Text(l, style = OzType.tiny.copy(letterSpacing = 0.sp), color = oz.fg3, modifier = Modifier.padding(vertical = 1.dp)) }
        }
        current?.let { i ->
            Text(
                letters[i], style = OzType.nameList.copy(fontSize = 40.sp, lineHeight = 46.sp), color = oz.fg,
                modifier = Modifier.align(Alignment.CenterStart).offset(x = (-72).dp),
            )
        }
    }
}
