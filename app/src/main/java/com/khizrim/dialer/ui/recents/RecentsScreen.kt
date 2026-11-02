package com.khizrim.dialer.ui.recents

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallMissed
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.khizrim.dialer.model.CallDirection
import com.khizrim.dialer.model.Contact
import com.khizrim.dialer.model.RecentCall
import com.khizrim.dialer.ui.components.Avatar
import com.khizrim.dialer.ui.components.color
import com.khizrim.dialer.ui.components.icon
import com.khizrim.dialer.ui.theme.CallGreen
import com.khizrim.dialer.ui.theme.DeclineRed
import com.khizrim.dialer.ui.theme.SpamOrange

@Composable
fun RecentsScreen(
    recents: List<RecentCall>,
    contactById: (Long) -> Contact?,
    onOpenContact: (Contact) -> Unit,
    onCallBack: (RecentCall, Contact?) -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            Text(
                "Недавние",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 12.dp),
            )
        }
        items(recents, key = { it.id }) { call ->
            val contact = call.contactId?.let(contactById)
            RecentRow(
                call = call,
                contact = contact,
                onClick = { contact?.let(onOpenContact) },
                onCallBack = { onCallBack(call, contact) },
            )
        }
    }
}

@Composable
private fun RecentRow(call: RecentCall, contact: Contact?, onClick: () -> Unit, onCallBack: () -> Unit) {
    val (dirIcon, dirColor) = when (call.direction) {
        CallDirection.INCOMING -> Icons.AutoMirrored.Rounded.CallReceived to CallGreen
        CallDirection.OUTGOING -> Icons.AutoMirrored.Rounded.CallMade to MaterialTheme.colorScheme.primary
        CallDirection.MISSED -> Icons.AutoMirrored.Rounded.CallMissed to DeclineRed
        CallDirection.BLOCKED -> Icons.Rounded.Block to SpamOrange
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (contact != null) {
            Avatar(contact.initials, contact.name)
        } else {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                contact?.name ?: call.number,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (call.direction == CallDirection.MISSED) DeclineRed else MaterialTheme.colorScheme.onSurface,
            )
            call.callerIdLabel?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = if (call.direction == CallDirection.BLOCKED) SpamOrange else MaterialTheme.colorScheme.primary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(dirIcon, null, tint = dirColor, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    listOfNotNull(call.method.shortTitle, call.whenText, call.durationText).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (call.direction != CallDirection.BLOCKED) {
            IconButton(onClick = onCallBack) {
                Icon(call.method.icon, "Перезвонить", tint = call.method.channel.color)
            }
        }
    }
}
