package com.khizrim.dialer.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.khizrim.dialer.DialerViewModel
import com.khizrim.dialer.R
import com.khizrim.dialer.telecom.Launcher
import com.khizrim.dialer.ui.components.BottomBar
import com.khizrim.dialer.ui.components.GhostIconButton
import com.khizrim.dialer.ui.components.OzIcon
import com.khizrim.dialer.ui.components.press
import com.khizrim.dialer.ui.theme.LocalOz
import com.khizrim.dialer.ui.theme.OzShape
import com.khizrim.dialer.ui.theme.OzType
import com.khizrim.dialer.ui.theme.well

/** Один экран настроек: группы с серыми подзаголовками, значения справа. */
@Composable
fun SettingsScreen(vm: DialerViewModel) {
    val oz = LocalOz.current
    val context = LocalContext.current
    val role = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.reload() }
    var simOpen by remember { mutableStateOf(false) }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(vm::exportTo) }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importFrom) }
    fun open(intent: Intent) { runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            Text(
                stringResource(R.string.settings), style = OzType.namePerson, color = oz.fg,
                modifier = Modifier.padding(start = 12.dp, top = 24.dp, bottom = 8.dp).semantics { heading() },
            )

            Group(stringResource(R.string.set_calls))
            if (vm.sims.size > 1) {
                val current = vm.sims.firstOrNull { it.first == vm.simForCalls }?.second ?: stringResource(R.string.set_sim_ask)
                ValueRow(stringResource(R.string.set_sim), current) { simOpen = !simOpen }
                if (simOpen) {
                    (listOf<Pair<String?, String>>(null to stringResource(R.string.set_sim_ask)) + vm.sims).forEach { (id, label) ->
                        Choice(label, vm.simForCalls == id) { vm.chooseSimForCalls(id); simOpen = false }
                    }
                }
            }
            SwitchRow(stringResource(R.string.silence_unknown), vm.silenceUnknown, vm::toggleSilenceUnknown)
            ValueRow(stringResource(R.string.unknown_numbers), null, onClick = vm::openUnknown)

            Group(stringResource(R.string.set_blocking))
            ValueRow(stringResource(R.string.blocked_numbers), null, onClick = vm::openBlocked)
            com.khizrim.dialer.callerid.SuspiciousCallsSetting(Modifier.padding(horizontal = 12.dp))
            com.khizrim.dialer.callerid.ExternalCallerIdRow(Modifier.padding(start = 12.dp, end = 12.dp, top = 16.dp))

            Group(stringResource(R.string.set_appearance))
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
            ValueRow(stringResource(R.string.set_default_dialer), stringResource(if (vm.isDefaultDialer) R.string.yes else R.string.no)) {
                val intent = Launcher.defaultDialerRequest(context)
                if (intent != null && !vm.isDefaultDialer) runCatching { role.launch(intent) } else open(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            }
            ValueRow(stringResource(R.string.set_permissions), null) { open(appDetails) }
            ValueRow(stringResource(R.string.set_version), versionName(context), onClick = null)
        }
        BottomBar {
            GhostIconButton(R.drawable.ic_oz_back, stringResource(R.string.back), vm::leaveSettings)
            Box(Modifier)
        }
    }
}

private fun versionName(context: android.content.Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

@Composable
private fun Group(title: String) {
    val oz = LocalOz.current
    Text(title, style = OzType.label, color = oz.fg3, modifier = Modifier.padding(start = 12.dp, top = 24.dp, bottom = 4.dp).semantics { heading() })
}

@Composable
private fun ValueRow(title: String, value: String?, sub: String? = null, onClick: (() -> Unit)?) {
    val oz = LocalOz.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .let { if (onClick != null) it.press(onClick, shape = OzShape.row) else it }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = OzType.list, color = oz.fg)
            if (sub != null) Text(sub, style = OzType.small, color = oz.fg3)
        }
        if (value != null) Text(value, style = OzType.small, color = oz.fg3, modifier = Modifier.padding(start = 12.dp))
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

/** Переключатель 44×26: выключенный — в «лунке», включённый — заливка accentFill. */
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
                .let { if (on) it.background(oz.accentFill, CircleShape) else it.well(CircleShape) }
                .padding(3.dp),
            contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(Modifier.size(20.dp).background(if (on) androidx.compose.ui.graphics.Color.White else oz.fg3, CircleShape))
        }
    }
}
