package com.khizrim.dialer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khizrim.dialer.model.Channel
import com.khizrim.dialer.model.CommMethod
import com.khizrim.dialer.model.MethodKind
import kotlin.math.abs

val Channel.color: Color
    get() = when (this) {
        Channel.PHONE -> Color(0xFF3D5AFE)
        Channel.WHATSAPP -> Color(0xFF25D366)
        Channel.TELEGRAM -> Color(0xFF2AABEE)
        Channel.SIGNAL -> Color(0xFF3A76F0)
        Channel.VIBER -> Color(0xFF7360F2)
    }

val CommMethod.icon: ImageVector
    get() = when (kind) {
        MethodKind.CALL -> Icons.Rounded.Call
        MethodKind.VIDEO -> Icons.Rounded.Videocam
        MethodKind.MESSAGE -> if (channel == Channel.PHONE) Icons.Rounded.Sms else Icons.AutoMirrored.Rounded.Chat
    }

private val avatarPalette = listOf(
    Color(0xFF6366F1) to Color(0xFF8B5CF6),
    Color(0xFF0EA5E9) to Color(0xFF22D3EE),
    Color(0xFFF43F5E) to Color(0xFFFB7185),
    Color(0xFF10B981) to Color(0xFF34D399),
    Color(0xFFF59E0B) to Color(0xFFFBBF24),
    Color(0xFFEC4899) to Color(0xFFA855F7),
)

@Composable
fun Avatar(initials: String, seed: String, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    val (a, b) = avatarPalette[abs(seed.hashCode()) % avatarPalette.size]
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(a, b))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value * 0.38f).sp,
        )
    }
}

/** Круглая иконка способа связи в цвете приложения. */
@Composable
fun MethodBadge(method: CommMethod, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(method.channel.color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = method.icon,
            contentDescription = method.title,
            tint = method.channel.color,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}
