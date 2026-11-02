package com.khizrim.dialer.ui.contacts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.khizrim.dialer.DialerViewModel
import com.khizrim.dialer.R
import com.khizrim.dialer.data.People
import com.khizrim.dialer.model.Person
import com.khizrim.dialer.ui.components.BottomBar
import com.khizrim.dialer.ui.components.GhostIconButton
import com.khizrim.dialer.ui.components.KeypadFab
import com.khizrim.dialer.ui.components.press
import com.khizrim.dialer.ui.theme.LocalOz
import com.khizrim.dialer.ui.theme.OzType
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

private const val ALPHABET = "АБВГДЕЖЗИКЛМНОПРСТУФХЦЧШЭЮЯ"

private sealed interface ListRow
private data class Header(val letter: Char) : ListRow
private data class Item(val person: Person) : ListRow

/** Все контакты: алфавит, тап по имени открывает человека, значок справа связывает сразу. */
@Composable
fun ContactsScreen(vm: DialerViewModel) {
    val oz = LocalOz.current
    val rows = remember {
        val collator = Collator.getInstance(Locale("ru"))
        val sorted = People.all.sortedWith { a, b -> collator.compare(a.name, b.name) }
        buildList<ListRow> {
            var last: Char? = null
            sorted.forEach { p ->
                val l = p.name.first().uppercaseChar()
                if (l != last) { add(Header(l)); last = l }
                add(Item(p))
            }
        }
    }
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            LazyColumn(state = state, modifier = Modifier.fillMaxSize().padding(start = 12.dp, end = 36.dp)) {
                items(rows, key = { r -> if (r is Header) "h${r.letter}" else "p${(r as Item).person.id}" }) { r ->
                    when (r) {
                        is Header -> Text(
                            r.letter.toString(),
                            style = OzType.small.copy(fontWeight = FontWeight.Medium),
                            color = oz.fg3,
                            modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
                        )
                        is Item -> {
                            val p = r.person
                            val method = vm.defaultOf(p)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .height(56.dp)
                                        .press({ vm.openPerson(p.id) }, shape = RoundedCornerShape(14.dp))
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    Text(p.name, style = OzType.list, color = oz.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                GhostIconButton(
                                    method?.icon ?: R.drawable.ic_oz_help,
                                    "${method?.title ?: "Выбрать способ"}: ${p.name}",
                                    { vm.perform(p, method) },
                                    tint = oz.fg3, iconSize = 22.dp,
                                )
                            }
                        }
                    }
                }
            }
            // алфавитный указатель у правого края
            Column(
                Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(28.dp).padding(vertical = 16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ALPHABET.forEach { l ->
                    val at = rows.indexOfFirst { it is Header && it.letter == l }
                    Text(
                        l.toString(),
                        style = OzType.tiny.copy(letterSpacing = OzType.small.letterSpacing),
                        color = if (at >= 0) oz.fg2 else oz.line,
                        modifier = Modifier
                            .press({ if (at >= 0) scope.launch { state.scrollToItem(at) } }, label = if (at >= 0) "Перейти к букве $l" else "Нет контактов на $l")
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
        }
        BottomBar {
            GhostIconButton(R.drawable.ic_oz_back, "Вернуться на главную", vm::goHome)
            KeypadFab(vm::openKeypad)
        }
    }
}

