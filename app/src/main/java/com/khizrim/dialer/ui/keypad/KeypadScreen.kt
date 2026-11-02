package com.khizrim.dialer.ui.keypad

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khizrim.dialer.model.Contact
import com.khizrim.dialer.ui.components.Avatar
import com.khizrim.dialer.ui.theme.CallGreen

private val keys = listOf(
    "1" to "", "2" to "АБВГ", "3" to "ДЕЁЖЗ",
    "4" to "ИЙКЛ", "5" to "МНОП", "6" to "РСТУ",
    "7" to "ФХЦЧ", "8" to "ШЩЪЫ", "9" to "ЬЭЮЯ",
    "*" to "", "0" to "+", "#" to "",
)

@Composable
fun KeypadScreen(
    contacts: List<Contact>,
    onCall: (String, Contact?) -> Unit,
    onOpenContact: (Contact) -> Unit,
    contentPadding: PaddingValues,
) {
    var number by rememberSaveable { mutableStateOf("") }
    val match = if (number.length >= 3) {
        contacts.firstOrNull { it.phone.filter(Char::isDigit).contains(number.filter(Char::isDigit)) }
    } else null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Box(Modifier.height(56.dp), contentAlignment = Alignment.Center) {
            if (match != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onOpenContact(match) }
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Avatar(match.initials, match.name, size = 32.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(match.name, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Text(
            text = number.ifEmpty { " " },
            fontSize = 36.sp,
            fontWeight = FontWeight.Light,
            maxLines = 1,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        keys.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                row.forEach { (digit, letters) ->
                    Key(digit, letters) { if (number.length < 18) number += digit }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.size(72.dp))
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(CallGreen)
                    .clickable(enabled = number.isNotEmpty()) { onCall(number, match) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Call, "Позвонить", tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                if (number.isNotEmpty()) {
                    IconButton(onClick = { number = number.dropLast(1) }) {
                        Icon(Icons.AutoMirrored.Rounded.Backspace, "Стереть")
                    }
                }
            }
        }
    }
}

@Composable
private fun Key(digit: String, letters: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .size(76.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(digit, fontSize = 28.sp, fontWeight = FontWeight.Normal)
        if (letters.isNotEmpty()) {
            Text(letters, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
