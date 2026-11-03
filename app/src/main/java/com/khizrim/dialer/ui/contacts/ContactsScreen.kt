package com.khizrim.dialer.ui.contacts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.khizrim.dialer.model.Person
import com.khizrim.dialer.ui.components.BottomBar
import com.khizrim.dialer.ui.components.GhostIconButton
import com.khizrim.dialer.ui.components.KeypadFab
import com.khizrim.dialer.ui.components.TextAction
import com.khizrim.dialer.ui.components.press
import com.khizrim.dialer.ui.theme.LocalOz
import com.khizrim.dialer.ui.theme.OzType
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

private sealed interface ListRow
private data class Header(val letter: Char) : ListRow
private data class Item(val person: Person) : ListRow
private data object Footer : ListRow

private fun sectionOf(name: String): Char {
    val c = name.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() ?: '#'
    return if (c.isLetter()) c else '#'
}

/** Все контакты: алфавит, тап по имени открывает человека, значок справа связывает сразу. */
@Composable
fun ContactsScreen(vm: DialerViewModel) {
    val oz = LocalOz.current
    val people = vm.people
    val rows = remember(people) {
        val collator = Collator.getInstance(Locale("ru"))
        val sorted = people.sortedWith { a, b -> collator.compare(a.name, b.name) }
        buildList<ListRow> {
            var last: Char? = null
            sorted.forEach { p ->
                val l = sectionOf(p.name)
                if (l != last) { add(Header(l)); last = l }
                add(Item(p))
            }
            add(Footer)
        }
    }
    val letters = remember(rows) { rows.filterIsInstance<Header>().map { it.letter } }
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            LazyColumn(state = state, modifier = Modifier.fillMaxSize().padding(start = 12.dp, end = 36.dp)) {
                if (people.isEmpty() && vm.ready) {
                    item(key = "empty") {
                        Column(Modifier.padding(12.dp)) {
                            Text("Контактов пока нет", style = OzType.nameSearch, color = oz.fg)
                            Text(
                                if (vm.hasContacts) "Добавьте людей в приложении «Контакты», и они появятся здесь."
                                else "Разрешите доступ к контактам, чтобы видеть своих людей.",
                                style = OzType.body, color = oz.fg2, modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
                items(rows, key = { r ->
                    when (r) {
                        is Header -> "h${r.letter}"
                        is Item -> "p${r.person.id}"
                        Footer -> "footer"
                    }
                }) { r ->
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
                        Footer -> TextAction(
                            "Заблокированные номера", oz.fg3, vm::openBlocked, style = OzType.small,
                            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                        )
                    }
                }
            }
            // алфавитный указатель у правого края: только буквы, которые есть
            if (letters.size > 1) {
                Column(
                    Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(28.dp).padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    letters.forEach { l ->
                        val at = rows.indexOfFirst { it is Header && it.letter == l } + if (people.isEmpty() && vm.ready) 1 else 0
                        Text(
                            l.toString(),
                            style = OzType.tiny.copy(letterSpacing = OzType.small.letterSpacing),
                            color = oz.fg2,
                            modifier = Modifier
                                .press({ scope.launch { state.scrollToItem(at) } }, label = "Перейти к букве $l")
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }
        BottomBar {
            GhostIconButton(R.drawable.ic_oz_back, "Вернуться на главную", vm::goHome)
            KeypadFab { vm.openKeypad() }
        }
    }
}

/** Заблокированные номера: системный чёрный список. */
@Composable
fun BlockedScreen(vm: DialerViewModel) {
    val oz = LocalOz.current
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.Bottom) {
            item(key = "title") {
                Text("Заблокированные", style = OzType.namePerson, color = oz.fg, modifier = Modifier.padding(bottom = 16.dp).semantics { heading() })
            }
            if (!vm.isDefaultDialer) {
                item(key = "role") {
                    Text(
                        "Список доступен, когда Ozero Phone выбран звонилкой по умолчанию.",
                        style = OzType.body, color = oz.fg2, modifier = Modifier.padding(bottom = 16.dp),
                    )
                }
            } else if (vm.blocked.isEmpty()) {
                item(key = "empty") {
                    Text("Никого не блокировали. Заблокировать номер можно прямо во время звонка или долгим нажатием на главной.", style = OzType.body, color = oz.fg2, modifier = Modifier.padding(bottom = 16.dp))
                }
            }
            items(vm.blocked, key = { it }) { n ->
                Row(Modifier.fillMaxWidth().height(56.dp).animateItem(), verticalAlignment = Alignment.CenterVertically) {
                    Text(com.khizrim.dialer.data.PhoneFormat.pretty(n), style = OzType.list, color = oz.fg, modifier = Modifier.weight(1f))
                    TextAction("Разблокировать", oz.accent, { vm.unblock(n) }, style = OzType.small)
                }
            }
        }
        BottomBar {
            GhostIconButton(R.drawable.ic_oz_back, "Назад", vm::openContacts)
            Box(Modifier)
        }
    }
}
