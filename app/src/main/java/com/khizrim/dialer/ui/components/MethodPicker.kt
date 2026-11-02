package com.khizrim.dialer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.khizrim.dialer.model.Channel
import com.khizrim.dialer.model.CommMethod
import com.khizrim.dialer.model.Contact

enum class PickerMode {
    /** Выбор способа по умолчанию для контакта. */
    SET_DEFAULT,

    /** Разовый выбор, когда у контакта стоит «спрашивать каждый раз». */
    ONE_TIME,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MethodPickerSheet(
    contact: Contact,
    mode: PickerMode,
    onDismiss: () -> Unit,
    onPick: (CommMethod?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(contact.initials, contact.name, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (mode == PickerMode.SET_DEFAULT) "Способ связи по умолчанию" else "Как связаться?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (mode == PickerMode.SET_DEFAULT) "Будет срабатывать при нажатии на ${contact.name}" else contact.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.size(12.dp))

            val groups = contact.availableMethods.groupBy { it.channel }
            groups.entries.forEachIndexed { index, (channel, methods) ->
                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
                ChannelHeader(channel)
                methods.forEach { method ->
                    PickerRow(
                        selected = mode == PickerMode.SET_DEFAULT && contact.defaultMethod == method,
                        onClick = { onPick(method) },
                        leading = { MethodBadge(method, size = 36.dp) },
                        title = method.title,
                    )
                }
            }

            if (mode == PickerMode.SET_DEFAULT) {
                HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
                PickerRow(
                    selected = contact.defaultMethod == null,
                    onClick = { onPick(null) },
                    leading = {
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.HelpOutline, null, Modifier.size(18.dp))
                        }
                    },
                    title = "Спрашивать каждый раз",
                )
            }
        }
    }
}

@Composable
private fun ChannelHeader(channel: Channel) {
    Text(
        text = channel.title,
        style = MaterialTheme.typography.labelLarge,
        color = channel.color,
        modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun PickerRow(
    selected: Boolean,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    title: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        leading()
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = "Выбрано", tint = MaterialTheme.colorScheme.primary)
        }
    }
}
