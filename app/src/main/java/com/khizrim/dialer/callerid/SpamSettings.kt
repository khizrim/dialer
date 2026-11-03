package com.khizrim.dialer.callerid

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.khizrim.dialer.R
import com.khizrim.dialer.ui.components.press
import com.khizrim.dialer.ui.theme.LocalOz
import com.khizrim.dialer.ui.theme.OzType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Строка настройки «Подозрительные звонки: Глушить / Сбрасывать / Только подпись». Нажатие листает режимы. */
@Composable
fun SuspiciousCallsSetting(modifier: Modifier = Modifier) {
    val oz = LocalOz.current
    val context = LocalContext.current
    val prefs = remember { CallerIdPrefs(context) }
    var action by remember { mutableStateOf(prefs.action) }
    val date by produceState<String?>(null) { value = withContext(Dispatchers.IO) { CallerId.registryDate(context) } }
    val title = stringResource(R.string.spam_action_title)
    val value = stringResource(
        when (action) {
            SpamAction.SILENCE -> R.string.spam_action_silence
            SpamAction.REJECT -> R.string.spam_action_reject
            SpamAction.LABEL -> R.string.spam_action_label
        }
    )
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .press({
                    action = SpamAction.entries[(action.ordinal + 1) % SpamAction.entries.size]
                    prefs.action = action
                }, shape = RoundedCornerShape(14.dp), label = "$title: $value"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = OzType.list, color = oz.fg, modifier = Modifier.weight(1f))
            Text(value, style = OzType.small, color = if (action == SpamAction.LABEL) oz.fg3 else oz.accent)
        }
        Text(
            stringResource(
                when (action) {
                    SpamAction.SILENCE -> R.string.spam_action_silence_text
                    SpamAction.REJECT -> R.string.spam_action_reject_text
                    SpamAction.LABEL -> R.string.spam_action_label_text
                }
            ),
            style = OzType.small, color = oz.fg3,
        )
        Text(
            date?.let { stringResource(R.string.spam_about, it) } ?: stringResource(R.string.spam_about_nodate),
            style = OzType.small, color = oz.fg3, modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Короткая подпись АОН для строки списка; считается в фоне. */
@Composable
fun rememberCallerTag(number: String): String? {
    val context = LocalContext.current
    val tag by produceState<String?>(null, number) {
        value = withContext(Dispatchers.IO) { runCatching { CallerId.shortLabelFor(context, number) }.getOrNull() }
    }
    return tag
}

/** Строка «Внешний определитель: Яндекс». Открывает системный выбор приложения-определителя. */
@Composable
fun ExternalCallerIdRow(modifier: Modifier = Modifier) {
    val oz = LocalOz.current
    val context = LocalContext.current
    val apps by produceState<List<String>?>(null) { value = withContext(Dispatchers.IO) { ExternalCallerId.installed(context) } }
    val title = stringResource(R.string.ext_cid_title)
    val value = when {
        apps == null -> ""
        apps!!.isEmpty() -> stringResource(R.string.ext_cid_none)
        else -> apps!!.take(2).joinToString(", ")
    }
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .press({
                    runCatching { context.startActivity(ExternalCallerId.chooseIntent(context)) }
                        .onFailure { runCatching { context.startActivity(ExternalCallerId.storeFallback()) } }
                }, shape = RoundedCornerShape(14.dp), label = "$title: $value"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = OzType.list, color = oz.fg, modifier = Modifier.weight(1f))
            Text(value, style = OzType.small, color = oz.fg3, maxLines = 1)
        }
        Text(
            stringResource(if (apps.isNullOrEmpty()) R.string.ext_cid_text_none else R.string.ext_cid_text),
            style = OzType.small, color = oz.fg3,
        )
    }
}

/** Кто из внешних определителей что заблокировал: время звонка → название приложения. */
@Composable
fun rememberExternalBlocks(key: Any?): Map<Long, String> {
    val context = LocalContext.current
    val map by produceState<Map<Long, String>>(emptyMap(), key) {
        value = withContext(Dispatchers.IO) { ExternalCallerId.blockedBy(context) }
    }
    return map
}
