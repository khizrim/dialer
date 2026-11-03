package com.khizrim.dialer.ui.home

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
@Composable
fun NameSearchScreen(vm: DialerViewModel) {
    val oz = LocalOz.current
    val focus = remember { FocusRequester() }
    // клавиатуру поднимаем, только когда контакты точно открыли (жест дотянули или нажали), а не пока их тянут
    val active = vm.screen == com.khizrim.dialer.Screen.SEARCH
    LaunchedEffect(active) { if (active) runCatching { focus.requestFocus() } }
    val q = vm.textQuery
    val digits = q.filter { it.isDigit() }
    val numeric = digits.length >= 3 && q.none { it.isLetter() }
    // пустой запрос — все контакты: избранные ближе к полю, дальше по алфавиту, без имени в конце
    val all = remember(vm.people) {
        val collator = java.text.Collator.getInstance(java.util.Locale("ru"))
        vm.people.sortedWith { a, b ->
            when {
                a.starred != b.starred -> if (a.starred) -1 else 1
                a.nameIsNumber != b.nameIsNumber -> if (a.nameIsNumber) 1 else -1
                else -> collator.compare(a.name, b.name)
            }
        }
    }
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
        // докрутили до самого верха и тянут дальше вниз — контакты уезжают за пальцем
        LazyColumn(Modifier.weight(1f).fillMaxWidth().dismissOnOverscroll { vm.closeTextSearch() }.padding(horizontal = 12.dp), state = listState, reverseLayout = true) {
            if (q.isBlank()) {
                items(all, key = { "a" + it.id }) { p ->
                    HitRow(vm, com.khizrim.dialer.data.TextHit(p, null, MatchField.NAME, null, MatchVia.DIRECT, 0))
                }
            }
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
            if (q.isNotBlank() && vm.textHits.isEmpty() && !numeric) {
                item(key = "none") { Text(stringResource(R.string.search_nothing), style = OzType.body, color = oz.fg3, modifier = Modifier.padding(12.dp)) }
            }
        }
        // поле 52dp: обводка accent, радиус 26, лупа слева, крестик справа. Поле тянут вниз — весь экран идёт за пальцем
        Row(
            Modifier
                .dismissOnDrag { vm.closeTextSearch() }
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp).fillMaxWidth().height(52.dp)
                .border(1.5.dp, oz.accent, RoundedCornerShape(26.dp)).padding(start = 16.dp, end = 4.dp),
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
            if (q.isNotEmpty()) GhostIconButton(R.drawable.ic_oz_x, stringResource(R.string.clear), { vm.typeText("") })
            else GhostIconButton(R.drawable.ic_oz_chevron_down, stringResource(R.string.back), vm::closeTextSearch)
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
            Modifier.weight(1f).heightIn(min = 60.dp)
                .press({ vm.openPerson(p.id) }, onLongClick = { vm.openMenu(p) }, label = p.name + (where?.let { ", $it" } ?: ""))
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (p.nameIsNumber) Text(p.shownName, style = OzType.number, color = oz.fg, maxLines = 1)
            else Text(name, style = OzType.nameSearch, color = oz.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (where != null) Text(where, style = OzType.small, color = oz.fg3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        com.khizrim.dialer.ui.components.ReachButton(method, p.name) { vm.tapHit(p) }
    }
}
