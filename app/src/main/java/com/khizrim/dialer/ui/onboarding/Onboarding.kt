package com.khizrim.dialer.ui.onboarding

import com.khizrim.dialer.R
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.khizrim.dialer.DialerViewModel
import com.khizrim.dialer.telecom.Launcher
import com.khizrim.dialer.ui.components.TextAction
import com.khizrim.dialer.ui.components.press
import com.khizrim.dialer.ui.theme.LocalOz
import com.khizrim.dialer.ui.theme.OzType

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
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.reload() }
    val role = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.reload()
        if (!Launcher.isDefaultDialer(context)) vm.roleSkipped = true
    }
    val roleStep = !vm.isDefaultDialer && !vm.roleSkipped
    fun next() {
        if (!roleStep) {
            permissions.launch(PERMISSIONS)
            return
        }
        val intent = Launcher.defaultDialerRequest(context)
        if (intent == null) {
            vm.roleSkipped = true
            return
        }
        role.launch(intent)
    }

    Column(
        Modifier.fillMaxSize().background(oz.bg).safeDrawingPadding().padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text("Ozero Phone", style = OzType.namePerson, color = oz.fg, modifier = Modifier.semantics { heading() })
        Text(
            if (roleStep) stringResource(R.string.ob_role_text)
            else stringResource(R.string.ob_perm_text),
            style = OzType.body, color = oz.fg2, modifier = Modifier.padding(top = 12.dp, bottom = 32.dp),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .press({ next() }, shape = RoundedCornerShape(18.dp), pressColor = oz.accentFill)
                .background(oz.accentFill, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (roleStep) stringResource(R.string.ob_role_button) else stringResource(R.string.ob_perm_button), style = OzType.strong, color = Color.White)
        }
        if (roleStep) {
            TextAction(stringResource(R.string.not_now), oz.fg3, { vm.roleSkipped = true }, modifier = Modifier.padding(top = 12.dp).offset(x = (-12).dp))
        } else {
            // если доступ уже запрещён навсегда, система не покажет вопрос снова
            TextAction(stringResource(R.string.open_settings), oz.fg3, {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }, modifier = Modifier.padding(top = 12.dp).offset(x = (-12).dp))
        }
    }
}
