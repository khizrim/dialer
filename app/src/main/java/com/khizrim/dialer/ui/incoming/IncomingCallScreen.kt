package com.khizrim.dialer.ui.incoming

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.khizrim.dialer.model.Contact
import com.khizrim.dialer.model.IncomingScenario
import com.khizrim.dialer.ui.components.Avatar
import com.khizrim.dialer.ui.components.color
import com.khizrim.dialer.ui.theme.CallGreen
import com.khizrim.dialer.ui.theme.DeclineRed
import com.khizrim.dialer.ui.theme.SpamOrange
import kotlinx.coroutines.delay

private enum class CallState { RINGING, ACTIVE, ENDED }

private data class CallerInfo(
    val title: String,
    val number: String,
    val contact: Contact?,
    val badge: String?,
    val badgeDetails: String?,
    val gradient: List<Color>,
)

@Composable
fun IncomingCallScreen(
    scenario: IncomingScenario,
    contact: Contact?,
    onClose: () -> Unit,
) {
    val info = remember(scenario, contact) { callerInfo(scenario, contact) }
    var state by remember { mutableStateOf(CallState.RINGING) }
    var endReason by remember { mutableStateOf("Звонок завершён") }

    BackHandler { onClose() }

    LaunchedEffect(state) {
        if (state == CallState.ENDED) {
            delay(1400)
            onClose()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(info.gradient)),
    ) {
        AnimatedContent(
            targetState = state,
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) },
            label = "call-state",
        ) { current ->
            when (current) {
                CallState.RINGING -> RingingContent(
                    info = info,
                    scenario = scenario,
                    onAnswer = { state = CallState.ACTIVE },
                    onDecline = { endReason = "Звонок отклонён"; state = CallState.ENDED },
                    onBlock = { endReason = "Номер заблокирован"; state = CallState.ENDED },
                    onQuick = { endReason = it; state = CallState.ENDED },
                )
                CallState.ACTIVE -> ActiveContent(info, onHangUp = { state = CallState.ENDED })
                CallState.ENDED -> EndedContent(info, endReason)
            }
        }
    }
}

private fun callerInfo(scenario: IncomingScenario, contact: Contact?): CallerInfo = when (scenario) {
    IncomingScenario.CONTACT -> CallerInfo(
        title = contact?.name ?: "Контакт",
        number = contact?.let { "${it.label} · ${it.phone}" } ?: "",
        contact = contact,
        badge = contact?.defaultMethod?.let { "Обычно вы связываетесь: ${it.shortTitle}" },
        badgeDetails = null,
        gradient = listOf(Color(0xFF1E1B4B), Color(0xFF312E81), Color(0xFF0F172A)),
    )
    IncomingScenario.UNKNOWN_IDENTIFIED -> CallerInfo(
        title = "Служба доставки",
        number = "+7 800 555-35-35",
        contact = null,
        badge = "Определено АОН",
        badgeDetails = "Курьерская компания · номер проверен",
        gradient = listOf(Color(0xFF0C4A6E), Color(0xFF164E63), Color(0xFF0F172A)),
    )
    IncomingScenario.SPAM -> CallerInfo(
        title = "+7 495 980-00-00",
        number = "Москва",
        contact = null,
        badge = "Подозрение на спам",
        badgeDetails = "128 жалоб · «Финансовые услуги»",
        gradient = listOf(Color(0xFF450A0A), Color(0xFF7C2D12), Color(0xFF1C1917)),
    )
}

@Composable
private fun CallerHeader(info: CallerInfo, subtitle: String, pulsing: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(subtitle, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(32.dp))
        PulsingAvatar(info, pulsing)
        Spacer(Modifier.height(24.dp))
        Text(
            info.title,
            color = Color.White,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(info.number, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PulsingAvatar(info: CallerInfo, pulsing: Boolean) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse-progress",
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
        if (pulsing) {
            listOf(0f, 0.5f).forEach { shift ->
                val p = (progress + shift) % 1f
                Box(
                    Modifier
                        .size(120.dp)
                        .scale(1f + p * 0.65f)
                        .alpha(1f - p)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f)),
                )
            }
        }
        val contact = info.contact
        if (contact != null) {
            Avatar(contact.initials, contact.name, size = 120.dp)
        } else {
            Box(
                Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Person, null, tint = Color.White, modifier = Modifier.size(64.dp))
            }
        }
    }
}

@Composable
private fun CallerIdBadge(info: CallerInfo, scenario: IncomingScenario) {
    val badge = info.badge ?: return
    val isSpam = scenario == IncomingScenario.SPAM
    val accent = when (scenario) {
        IncomingScenario.SPAM -> SpamOrange
        IncomingScenario.UNKNOWN_IDENTIFIED -> Color(0xFF38BDF8)
        IncomingScenario.CONTACT -> info.contact?.defaultMethod?.channel?.color ?: Color.White
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(accent.copy(alpha = if (isSpam) 0.22f else 0.16f))
            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            when (scenario) {
                IncomingScenario.SPAM -> Icons.Rounded.Warning
                IncomingScenario.UNKNOWN_IDENTIFIED -> Icons.Rounded.VerifiedUser
                IncomingScenario.CONTACT -> Icons.Rounded.Call
            },
            null,
            tint = accent,
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(badge, color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            info.badgeDetails?.let {
                Text(it, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun RingingContent(
    info: CallerInfo,
    scenario: IncomingScenario,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onBlock: () -> Unit,
    onQuick: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        CallerHeader(info, subtitle = "Входящий звонок · Телефон", pulsing = true)
        Spacer(Modifier.height(24.dp))
        CallerIdBadge(info, scenario)
        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            QuickAction(Icons.Rounded.Sms, "Сообщение") { onQuick("Отправлено: «Перезвоню позже»") }
            QuickAction(Icons.Rounded.Alarm, "Напомнить") { onQuick("Напомним через 30 минут") }
            if (scenario != IncomingScenario.CONTACT) {
                QuickAction(Icons.Rounded.Block, "Блок", tint = if (scenario == IncomingScenario.SPAM) SpamOrange else Color.White, onClick = onBlock)
            }
        }
        Spacer(Modifier.height(40.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundCallButton(Icons.Rounded.CallEnd, "Отклонить", DeclineRed, onClick = onDecline)
            RoundCallButton(Icons.Rounded.Call, "Ответить", CallGreen, pulsing = true, onClick = onAnswer)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ActiveContent(info: CallerInfo, onHangUp: () -> Unit) {
    var seconds by remember { mutableIntStateOf(0) }
    var muted by remember { mutableStateOf(false) }
    var speaker by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            seconds++
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        CallerHeader(info, subtitle = "%d:%02d".format(seconds / 60, seconds % 60), pulsing = false)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ToggleAction(Icons.Rounded.MicOff, "Без звука", muted) { muted = !muted }
            ToggleAction(Icons.Rounded.Dialpad, "Клавиатура", false) {}
            ToggleAction(Icons.AutoMirrored.Rounded.VolumeUp, "Динамик", speaker) { speaker = !speaker }
        }
        Spacer(Modifier.height(48.dp))
        RoundCallButton(Icons.Rounded.CallEnd, "Завершить", DeclineRed, onClick = onHangUp)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun EndedContent(info: CallerInfo, reason: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        CallerHeader(info, subtitle = reason, pulsing = false)
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, tint: Color = Color.White, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = tint)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ToggleAction(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(if (active) Color.White else Color.White.copy(alpha = 0.12f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, tint = if (active) Color.Black else Color.White)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun RoundCallButton(
    icon: ImageVector,
    label: String,
    color: Color,
    size: Dp = 76.dp,
    pulsing: Boolean = false,
    onClick: () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "btn")
    val bounce by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (pulsing) 1.08f else 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "btn-scale",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .scale(bounce)
                .size(size)
                .clip(CircleShape)
                .background(color)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}
