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
    val stale by produceState(false) {
        value = withContext(Dispatchers.IO) { (CallerId.dbAgeDays(context) ?: 0L) > SpamPolicy.FRESH_DAYS }
    }
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
            Text(value, style = OzType.small, color = oz.fg3)
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
        if (stale && action != SpamAction.LABEL) {
            Text(stringResource(R.string.spam_db_stale), style = OzType.small, color = oz.fg3, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** Короткая подпись АОН для строки списка; считается в фоне. */
// value присваивается; ложное срабатывание Lint на Kotlin 2 (ProduceStateDoesNotAssignValue)
@android.annotation.SuppressLint("ProduceStateDoesNotAssignValue")
@Composable
fun rememberCallerTag(number: String): String? {
    val context = LocalContext.current
    val tag by produceState<String?>(null, number) {
        value = withContext(Dispatchers.IO) { runCatching { CallerId.shortLabelFor(context, number) }.getOrNull() }
    }
    return tag
}

/** Строка «Внешний определитель: Яндекс». Ведёт туда, где его можно выбрать или включить. */
@Composable
fun ExternalCallerIdRow(modifier: Modifier = Modifier) {
    val oz = LocalOz.current
    val context = LocalContext.current
    // перечитываем при каждом возвращении на экран: человек мог включить определитель в Яндексе
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    var resumes by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) resumes++ }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    var apps by remember { androidx.compose.runtime.mutableStateOf<List<ExternalCallerId.App>?>(null) }
    androidx.compose.runtime.LaunchedEffect(resumes) { apps = withContext(Dispatchers.IO) { ExternalCallerId.apps(context) } }
    val list = apps
    val ready = list?.filter { it.ready }.orEmpty()
    val pending = list?.firstOrNull { !it.ready }
    val title = stringResource(R.string.ext_cid_title)
    val value = when {
        list == null -> ""
        ready.isNotEmpty() -> ready.take(2).joinToString(", ") { it.label }
        pending != null -> stringResource(R.string.ext_cid_off, pending.label)
        else -> stringResource(R.string.ext_cid_none)
    }
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .press({
                    runCatching { context.startActivity(ExternalCallerId.chooseIntent(context, list.orEmpty())) }
                        .onFailure { runCatching { context.startActivity(ExternalCallerId.storeFallback()) } }
                }, shape = RoundedCornerShape(14.dp), label = "$title: $value"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = OzType.list, color = oz.fg, modifier = Modifier.weight(1f))
            Text(value, style = OzType.small, color = oz.fg3, maxLines = 1)
        }
        Text(
            when {
                ready.isNotEmpty() -> stringResource(R.string.ext_cid_text)
                pending != null -> stringResource(R.string.ext_cid_text_off, pending.label)
                else -> stringResource(R.string.ext_cid_text_none)
            },
            style = OzType.small, color = oz.fg3,
        )
    }
}

/** Кто из внешних определителей что заблокировал: время звонка → название приложения. */
// value присваивается; ложное срабатывание Lint на Kotlin 2 (ProduceStateDoesNotAssignValue)
@android.annotation.SuppressLint("ProduceStateDoesNotAssignValue")
@Composable
fun rememberExternalBlocks(key: Any?): Map<Long, String> {
    val context = LocalContext.current
    val map by produceState<Map<Long, String>>(emptyMap(), key) {
        value = withContext(Dispatchers.IO) { ExternalCallerId.blockedBy(context) }
    }
    return map
}
