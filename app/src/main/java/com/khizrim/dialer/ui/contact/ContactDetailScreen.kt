package com.khizrim.dialer.ui.contact

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.khizrim.dialer.model.CommMethod
import com.khizrim.dialer.model.Contact
import com.khizrim.dialer.ui.components.Avatar
import com.khizrim.dialer.ui.components.MethodBadge
import com.khizrim.dialer.ui.components.MethodPickerSheet
import com.khizrim.dialer.ui.components.PickerMode
import com.khizrim.dialer.ui.components.color
import com.khizrim.dialer.ui.components.icon
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailScreen(
    contact: Contact,
    onBack: () -> Unit,
    onSetDefault: (CommMethod?) -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var picker by remember { mutableStateOf<PickerMode?>(null) }

    fun perform(method: CommMethod) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar("${method.title}: ${contact.name} (демо)")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") }
                },
                actions = {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            if (contact.favorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                            contentDescription = "Избранное",
                            tint = if (contact.favorite) Color(0xFFFBBF24) else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Avatar(contact.initials, contact.name, size = 104.dp)
            Spacer(Modifier.height(16.dp))
            Text(contact.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "${contact.label} · ${contact.phone}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            contact.note?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))

            PrimaryActionButton(
                method = contact.defaultMethod,
                onClick = {
                    val method = contact.defaultMethod
                    if (method != null) perform(method) else picker = PickerMode.ONE_TIME
                },
            )
            Spacer(Modifier.height(16.dp))

            DefaultMethodCard(contact.defaultMethod, onClick = { picker = PickerMode.SET_DEFAULT })
            Spacer(Modifier.height(16.dp))

            AllMethodsCard(contact, onMethod = ::perform)
            Spacer(Modifier.height(16.dp))

            NotificationsCard()
            Spacer(Modifier.height(32.dp))
        }
    }

    picker?.let { mode ->
        MethodPickerSheet(
            contact = contact,
            mode = mode,
            onDismiss = { picker = null },
            onPick = { method ->
                picker = null
                if (mode == PickerMode.SET_DEFAULT) {
                    onSetDefault(method)
                    scope.launch {
                        snackbar.currentSnackbarData?.dismiss()
                        snackbar.showSnackbar(
                            if (method == null) "Теперь будем спрашивать каждый раз"
                            else "По умолчанию: ${method.title}",
                        )
                    }
                } else if (method != null) {
                    perform(method)
                }
            },
        )
    }
}

@Composable
private fun PrimaryActionButton(method: CommMethod?, onClick: () -> Unit) {
    val color = method?.channel?.color ?: MaterialTheme.colorScheme.primary
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
    ) {
        Icon(method?.icon ?: Icons.AutoMirrored.Rounded.HelpOutline, null, Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text = method?.title ?: "Выбрать, как связаться",
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) { content() }
}

@Composable
private fun CardTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun DefaultMethodCard(method: CommMethod?, onClick: () -> Unit) {
    SectionCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (method != null) {
                MethodBadge(method, size = 44.dp)
            } else {
                FilledTonalIconButton(onClick = onClick) { Icon(Icons.AutoMirrored.Rounded.HelpOutline, null) }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Способ связи по умолчанию", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    method?.title ?: "Спрашивать каждый раз",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = "Изменить", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AllMethodsCard(contact: Contact, onMethod: (CommMethod) -> Unit) {
    SectionCard {
        CardTitle("Все способы связи")
        val groups = contact.availableMethods.groupBy { it.channel }
        groups.entries.forEachIndexed { index, (channel, methods) ->
            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    channel.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    methods.forEach { method ->
                        val isDefault = method == contact.defaultMethod
                        FilledTonalIconButton(
                            onClick = { onMethod(method) },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = if (isDefault) channel.color else channel.color.copy(alpha = 0.14f),
                                contentColor = if (isDefault) Color.White else channel.color,
                            ),
                        ) {
                            Icon(method.icon, contentDescription = method.title)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun NotificationsCard() {
    var bypassDnd by rememberSaveable { mutableStateOf(false) }
    SectionCard {
        CardTitle("Уведомления · появятся в бете")
        SettingRow(Icons.Rounded.MusicNote, "Мелодия", "По умолчанию")
        SettingRow(
            Icons.Rounded.DoNotDisturbOn,
            "Звонить в режиме «Не беспокоить»",
            if (bypassDnd) "Да" else "Нет",
            trailing = { Switch(checked = bypassDnd, onCheckedChange = { bypassDnd = it }) },
        )
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    value: String,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}
