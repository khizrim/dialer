package com.khizrim.dialer.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khizrim.dialer.DialerViewModel
import com.khizrim.dialer.R
import com.khizrim.dialer.data.PhoneFormat
import com.khizrim.dialer.telecom.Launcher
import com.khizrim.dialer.ui.kit.OzIcon
import com.khizrim.dialer.ui.kit.TextAction
import com.khizrim.dialer.ui.kit.press
import com.khizrim.dialer.ui.nav.Layer
import com.khizrim.dialer.ui.theme.LocalOz
import com.khizrim.dialer.ui.theme.OzShape
import com.khizrim.dialer.ui.theme.OzType

/** Страница справа: заголовок крупно, группы тихими подписями. «Назад» от края ведёт её за пальцем. */
@Composable
private fun Page(title: String, content: @Composable () -> Unit) {
    val oz = LocalOz.current
    Column(Modifier.fillMaxSize().background(oz.bg).statusBarsPadding()) {
        Text(
            title, style = OzType.namePerson.copy(fontSize = 32.sp), color = oz.fg,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 8.dp).semantics { heading() },
        )
        content()
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun SettingsPage(vm: DialerViewModel, l: Layer) {
    val context = LocalContext.current
    val role = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.reload() }
    var simOpen by remember { mutableStateOf(false) }
    val askWrite = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) vm.askClearLog(missed = false) }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(vm::exportTo) }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importFrom) }
    fun open(intent: Intent) { runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

    Page(stringResource(R.string.settings)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp).navigationBarsPadding().padding(bottom = 24.dp)) {
            // «звонилка по умолчанию: да» ничего не сообщает — строка есть, только когда это нужно исправить
            if (!vm.isDefaultDialer) ValueRow(stringResource(R.string.not_default_dialer), stringResource(R.string.fix_it), warn = true) {
                val intent = Launcher.defaultDialerRequest(context)
                if (intent != null) runCatching { role.launch(intent) } else open(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            }

            if (vm.sims.size > 1) {
                Group(stringResource(R.string.set_calls))
                val current = vm.sims.firstOrNull { it.first == vm.simForCalls }?.second ?: stringResource(R.string.set_sim_ask)
                ValueRow(stringResource(R.string.set_sim), current) { simOpen = !simOpen }
                AnimatedVisibility(simOpen, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Column {
                        (listOf<Pair<String?, String>>(null to stringResource(R.string.set_sim_ask)) + vm.sims).forEach { (id, label) ->
                            Choice(label, vm.simForCalls == id) { vm.chooseSimForCalls(id); simOpen = false }
                        }
                    }
                }
            }

            // всё про незнакомые номера — одной группой: без звука, спам, определитель, заблокированные
            Group(stringResource(R.string.unknown_numbers))
            SwitchRow(stringResource(R.string.silence_unknown), vm.silenceUnknown, vm::toggleSilenceUnknown)
            com.khizrim.dialer.callerid.SuspiciousCallsSetting(Modifier.padding(horizontal = 12.dp))
            com.khizrim.dialer.callerid.ExternalCallerIdRow(Modifier.padding(start = 12.dp, end = 12.dp, top = 16.dp))
            ValueRow(stringResource(R.string.blocked_numbers), vm.blocked.size.takeIf { it > 0 }?.toString(), onClick = vm::openBlocked)

            Group(stringResource(R.string.set_appearance))
            SwitchRow(stringResource(R.string.set_fav_nickname), vm.favNickname, vm::toggleFavNickname)
            ValueRow(stringResource(R.string.set_language), stringResource(R.string.set_language_system)) {
                // язык приложения задаётся системой (Android 13+), иначе — языком телефона
                if (Build.VERSION.SDK_INT >= 33) open(Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                else open(Intent(Settings.ACTION_LOCALE_SETTINGS))
            }

            Group(stringResource(R.string.set_transfer))
            ValueRow(stringResource(R.string.transfer_save), null, sub = stringResource(R.string.transfer_save_sub)) { runCatching { saveFile.launch("ozero-phone.json") } }
            ValueRow(stringResource(R.string.transfer_restore), null, sub = stringResource(R.string.transfer_restore_sub)) {
                runCatching { openFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
            }

            Group(stringResource(R.string.set_app))
            ValueRow(stringResource(R.string.clear_log), null) {
                if (vm.canClearLog) vm.askClearLog(missed = false) else askWrite.launch(android.Manifest.permission.WRITE_CALL_LOG)
            }
            ValueRow(stringResource(R.string.set_permissions), null) { open(appDetails) }
            ValueRow(stringResource(R.string.set_version), versionName(context), onClick = null)
        }
    }
}

/** Заблокированные номера: список и «Разблокировать» у каждого. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun BlockedPage(vm: DialerViewModel, l: Layer) {
    val oz = LocalOz.current
    Page(stringResource(R.string.blocked_title)) {
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 20.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
            if (!vm.isDefaultDialer) {
                item(key = "role") { Text(stringResource(R.string.blocked_need_role), style = OzType.body, color = oz.fg3, modifier = Modifier.padding(vertical = 12.dp)) }
            } else if (vm.blocked.isEmpty()) {
                item(key = "empty") { Text(stringResource(R.string.blocked_empty), style = OzType.body, color = oz.fg3, modifier = Modifier.padding(vertical = 12.dp)) }
            }
            items(vm.blocked, key = { it }) { n ->
                Row(Modifier.fillMaxWidth().height(56.dp).animateItem(), verticalAlignment = Alignment.CenterVertically) {
                    Text(PhoneFormat.pretty(n), style = OzType.number, color = oz.fg, modifier = Modifier.weight(1f))
                    TextAction(stringResource(R.string.unblock), oz.accent, { vm.unblock(n) }, style = OzType.small)
                }
            }
            item(key = "pad") { Spacer(Modifier.navigationBarsPadding()) }
        }
    }
}

private fun versionName(context: android.content.Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

@Composable
private fun Group(title: String) {
    Text(title, style = OzType.label, color = LocalOz.current.fg3, modifier = Modifier.padding(start = 12.dp, top = 24.dp, bottom = 4.dp).semantics { heading() })
}

@Composable
private fun ValueRow(title: String, value: String?, sub: String? = null, warn: Boolean = false, onClick: (() -> Unit)?) {
    val oz = LocalOz.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .let { if (onClick != null) it.press(onClick, shape = OzShape.row) else it }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = OzType.list, color = if (warn) oz.warn else oz.fg)
            if (sub != null) Text(sub, style = OzType.small, color = oz.fg3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (value != null) Text(value, style = OzType.small, color = if (warn) oz.accent else oz.fg3, modifier = Modifier.padding(start = 12.dp))
        else if (onClick != null) OzIcon(R.drawable.ic_oz_chevron, oz.fg3, 18.dp)
    }
}

@Composable
private fun Choice(title: String, on: Boolean, onClick: () -> Unit) {
    val oz = LocalOz.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).press(onClick, shape = OzShape.row).padding(start = 28.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = OzType.body, color = if (on) oz.accent else oz.fg2, modifier = Modifier.weight(1f))
        if (on) OzIcon(R.drawable.ic_oz_check, oz.accent, 18.dp)
    }
}

/** Переключатель 44×26: выключенный — тонкий контур, включённый — заливка акцентом. */
@Composable
private fun SwitchRow(title: String, on: Boolean, onToggle: () -> Unit) {
    val oz = LocalOz.current
    val state = stringResource(if (on) R.string.state_on else R.string.state_off)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .press(onToggle, shape = OzShape.row, role = Role.Switch, label = title)
            .semantics { stateDescription = state }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = OzType.list, color = oz.fg, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .size(44.dp, 26.dp)
                .let { if (on) it.background(oz.accentFill, CircleShape) else it.border(1.5.dp, oz.fg3, CircleShape) }
                .padding(3.dp),
            contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(Modifier.size(20.dp).background(if (on) Color.White else oz.fg3, CircleShape))
        }
    }
}
