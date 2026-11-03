package com.khizrim.dialer.ui.onboarding

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.khizrim.dialer.DialerViewModel
import com.khizrim.dialer.R
import com.khizrim.dialer.telecom.Launcher
import com.khizrim.dialer.ui.components.OzIcon
import com.khizrim.dialer.ui.components.TextAction
import com.khizrim.dialer.ui.components.press
import com.khizrim.dialer.ui.theme.LocalOz
import com.khizrim.dialer.ui.theme.OzShape
import com.khizrim.dialer.ui.theme.OzType
import com.khizrim.dialer.ui.theme.raisedFill

private val PERMISSIONS = buildList {
    add(Manifest.permission.READ_CONTACTS)
    add(Manifest.permission.WRITE_CONTACTS)
    add(Manifest.permission.READ_CALL_LOG)
    add(Manifest.permission.CALL_PHONE)
    add(Manifest.permission.READ_PHONE_STATE)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

/** Первый запуск: два тихих шага внизу экрана, под большим пальцем. */
@Composable
fun Onboarding(vm: DialerViewModel) {
    val oz = LocalOz.current
    val context = LocalContext.current
    val activity = context as? Activity
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.markPermsAsked(); vm.reload() }
    val role = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.reload()
        if (!Launcher.isDefaultDialer(context)) vm.skipRole()
    }
    val roleStep = !vm.isDefaultDialer && !vm.roleSkipped
    // спрашивали, отказали и система больше не покажет вопрос
    val denied = !roleStep && vm.permsAsked && !vm.hasContacts &&
        activity?.shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS) == false
    fun granted(p: String) = context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    fun openSettings() {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    Column(
        Modifier.fillMaxSize().background(oz.bg).safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        when {
            roleStep -> {
                Text(stringResource(R.string.ob_step, 1, 2), style = OzType.label, color = oz.fg3)
                Text("Ozero Phone", style = OzType.namePerson, color = oz.fg, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp).semantics { heading() })
                Promise(R.drawable.ic_oz_user, stringResource(R.string.ob_promise_people))
                Promise(R.drawable.ic_oz_bell_off, stringResource(R.string.ob_promise_quiet))
                Promise(R.drawable.ic_oz_shield, stringResource(R.string.ob_promise_local))
                Spacer(Modifier.height(32.dp))
                MainButton(stringResource(R.string.ob_role_button)) {
                    val intent = Launcher.defaultDialerRequest(context)
                    if (intent == null) vm.skipRole() else runCatching { role.launch(intent) }.onFailure { vm.skipRole() }
                }
                TextAction(stringResource(R.string.not_now), oz.fg3, vm::skipRole, modifier = Modifier.padding(top = 12.dp).offset(x = (-12).dp))
            }
            denied -> {
                Text(stringResource(R.string.ob_denied_title), style = OzType.namePerson, color = oz.fg, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.ob_denied_text), style = OzType.body, color = oz.fg2, modifier = Modifier.padding(top = 12.dp, bottom = 32.dp))
                MainButton(stringResource(R.string.open_settings), ::openSettings)
                TextAction(stringResource(R.string.ob_without_contacts), oz.fg3, vm::skipPerms, modifier = Modifier.padding(top = 12.dp).offset(x = (-12).dp))
            }
            else -> {
                Text(stringResource(R.string.ob_step, 2, 2), style = OzType.label, color = oz.fg3)
                Text(stringResource(R.string.ob_perm_title), style = OzType.namePerson, color = oz.fg, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
                Text(stringResource(R.string.ob_perm_sub), style = OzType.body, color = oz.fg2, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
                Access(R.drawable.ic_oz_users, stringResource(R.string.ob_access_contacts), stringResource(R.string.ob_access_contacts_why), granted(Manifest.permission.READ_CONTACTS))
                Access(R.drawable.ic_oz_clock, stringResource(R.string.ob_access_log), stringResource(R.string.ob_access_log_why), granted(Manifest.permission.READ_CALL_LOG))
                Access(
                    R.drawable.ic_oz_bell, stringResource(R.string.ob_access_notif), stringResource(R.string.ob_access_notif_why),
                    Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS),
                )
                Spacer(Modifier.height(32.dp))
                MainButton(stringResource(R.string.ob_perm_button)) { permissions.launch(PERMISSIONS) }
            }
        }
    }
}

@Composable
private fun Promise(icon: Int, text: String) {
    val oz = LocalOz.current
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        OzIcon(icon, oz.fg3, 22.dp)
        Spacer(Modifier.width(16.dp))
        Text(text, style = OzType.body, color = oz.fg2)
    }
}

@Composable
private fun Access(icon: Int, title: String, why: String, done: Boolean) {
    val oz = LocalOz.current
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        OzIcon(icon, oz.fg3, 22.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = OzType.body, color = oz.fg)
            Text(why, style = OzType.small, color = oz.fg3)
        }
        if (done) OzIcon(R.drawable.ic_oz_check, oz.accent, 20.dp)
    }
}

@Composable
private fun MainButton(text: String, onClick: () -> Unit) {
    val oz = LocalOz.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .raisedFill(oz.accentFill, OzShape.main)
            .press(onClick, shape = OzShape.main, pressColor = Color.White.copy(alpha = .12f)),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = OzType.strong, color = Color.White) }
}
