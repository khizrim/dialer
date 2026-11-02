package com.khizrim.dialer.ui.contacts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhoneCallback
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.khizrim.dialer.model.CommMethod
import com.khizrim.dialer.model.Contact
import com.khizrim.dialer.model.IncomingScenario
import com.khizrim.dialer.ui.components.Avatar
import com.khizrim.dialer.ui.components.color
import com.khizrim.dialer.ui.components.icon

@Composable
fun ContactsScreen(
    contacts: List<Contact>,
    onOpenContact: (Contact) -> Unit,
    onQuickAction: (Contact) -> Unit,
    onSimulateIncoming: (IncomingScenario) -> Unit,
    contentPadding: PaddingValues,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(contacts.toList(), query) {
        val q = query.trim()
        contacts
            .filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) || it.phone.filter(Char::isDigit).contains(q.filter(Char::isDigit).ifEmpty { "\u0000" }) }
            .sortedBy { it.name }
    }
    val favorites = contacts.filter { it.favorite }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item {
            Header(onSimulateIncoming)
        }
        item {
            SearchField(query, onChange = { query = it })
        }
        if (query.isBlank() && favorites.isNotEmpty()) {
            item {
                SectionTitle("Избранное")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(favorites, key = { it.id }) { contact ->
                        FavoriteTile(contact, onClick = { onQuickAction(contact) }, onLongClick = { onOpenContact(contact) })
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        val grouped = filtered.groupBy { it.name.first().uppercaseChar() }
        grouped.forEach { (letter, group) ->
            item(key = "h_$letter") { SectionTitle(letter.toString()) }
            items(group, key = { it.id }) { contact ->
                ContactRow(contact, onClick = { onOpenContact(contact) }, onAction = { onQuickAction(contact) })
            }
        }
        if (filtered.isEmpty()) {
            item {
                Text(
                    "Никого не нашли",
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Header(onSimulateIncoming: (IncomingScenario) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Контакты",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Rounded.PhoneCallback, contentDescription = "Демо входящего звонка")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                Text(
                    "Демо входящего звонка",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
                IncomingScenario.entries.forEach { scenario ->
                    DropdownMenuItem(
                        text = { Text(scenario.title) },
                        onClick = {
                            menu = false
                            onSimulateIncoming(scenario)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text("Имя или номер") },
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) { Icon(Icons.Rounded.Close, "Очистить") }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteTile(contact: Contact, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(76.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 6.dp),
    ) {
        Box {
            Avatar(contact.initials, contact.name, size = 60.dp)
            contact.defaultMethod?.let { method ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 2.dp, y = 2.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(method.channel.color),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(method.icon, null, tint = Color.White, modifier = Modifier.size(12.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            contact.name.substringBefore(' '),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ContactRow(contact: Contact, onClick: () -> Unit, onAction: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(contact.initials, contact.name)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(contact.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                text = contact.defaultMethod?.shortTitle ?: "Спрашивать каждый раз",
                style = MaterialTheme.typography.bodySmall,
                color = contact.defaultMethod?.channel?.color ?: MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        QuickActionButton(contact.defaultMethod, onAction)
    }
}

@Composable
private fun QuickActionButton(method: CommMethod?, onClick: () -> Unit) {
    val color = method?.channel?.color ?: MaterialTheme.colorScheme.onSurfaceVariant
    FilledTonalIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = color.copy(alpha = 0.14f),
            contentColor = color,
        ),
    ) {
        Icon(
            imageVector = method?.icon ?: Icons.AutoMirrored.Rounded.HelpOutline,
            contentDescription = method?.title ?: "Выбрать способ связи",
        )
    }
}
