package com.khizrim.dialer.ui.person

import com.khizrim.dialer.ui.theme.raisedFill
import com.khizrim.dialer.ui.theme.well
import com.khizrim.dialer.ui.theme.lift
import com.khizrim.dialer.ui.theme.OzShape

import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.khizrim.dialer.DialerViewModel
import com.khizrim.dialer.R
import com.khizrim.dialer.SheetMode
import com.khizrim.dialer.ui.components.GhostIconButton
import com.khizrim.dialer.ui.components.OzIcon
import com.khizrim.dialer.ui.components.TextAction
import com.khizrim.dialer.ui.components.press
import com.khizrim.dialer.ui.theme.LocalOz
import com.khizrim.dialer.ui.theme.Motion
import com.khizrim.dialer.ui.theme.OzType

/** Человек: редкая хронология сверху, имя, главная кнопка со способом по умолчанию. */
@Composable
fun PersonScreen(vm: DialerViewModel, id: Long) {
    val oz = LocalOz.current
    val p = vm.person(id) ?: return
    val method = vm.defaultOf(p)
    val fav = p.starred
    val history = remember(p, vm.ready) { vm.history(p).take(5) }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            if (history.isNotEmpty()) {
                Column(Modifier.padding(bottom = 40.dp)) {
                    history.forEach { e ->
                        Row(Modifier.padding(vertical = 6.dp)) {
                            Text(e.day, style = OzType.time, color = oz.fg3, modifier = Modifier.width(56.dp))
                            Spacer(Modifier.width(16.dp))
                            Text(e.text, style = OzType.small, color = if (e.missed) oz.danger else oz.fg2)
                        }
                    }
                }
            }
            // фото только если оно есть, без кружков с буквами
            p.photo?.let { ContactPhoto(it, Modifier.padding(bottom = 16.dp)) }
            Text(p.shownName, style = if (p.nameIsNumber) OzType.numberHome else OzType.namePerson, color = oz.fg, modifier = Modifier.padding(bottom = 12.dp).semantics { heading() })
            // номера под именем: тип и номер серым, чтобы было видно, куда звоним
            // тип номера нужен, только чтобы различать несколько номеров
            if (!p.nameIsNumber || p.phones.size > 1) {
                val many = p.phones.size > 1
                // тап по номеру — звонок именно на него, долгое нажатие копирует
                p.phones.take(3).forEach { ph ->
                    Text(
                        (if (many) ph.label.replaceFirstChar { it.uppercase() } + "  " else "") + com.khizrim.dialer.data.PhoneFormat.pretty(ph.number),
                        style = OzType.body.copy(fontFeatureSettings = "cv11, tnum"), color = oz.fg3, maxLines = 1,
                        modifier = Modifier
                            .offset(x = (-8).dp)
                            .press({ vm.callNumberOf(p, ph.number) }, onLongClick = { vm.copyNumber(ph.number) }, shape = OzShape.text,
                                label = stringResource(R.string.call_to, com.khizrim.dialer.data.PhoneFormat.pretty(ph.number)))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .raisedFill(oz.accentFill, OzShape.main)
                    .press({ vm.perform(p, method) }, onLongClick = { vm.openSheet(p, SheetMode.OTHER) }, shape = OzShape.main, pressColor = Color.White.copy(alpha = .12f)),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OzIcon(method?.icon ?: R.drawable.ic_oz_help, Color.White)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(method?.title ?: R.string.choose_method), style = OzType.strong, color = Color.White)
            }
            Column(Modifier.padding(top = 12.dp).offset(x = (-12).dp)) {
                TextAction(stringResource(R.string.other_way), oz.accent, { vm.openSheet(p, SheetMode.OTHER) })
                TextAction(stringResource(R.string.change_default), oz.fg3, { vm.openSheet(p, SheetMode.DEFAULT) })
                TextAction(stringResource(R.string.edit_contact), oz.fg3, { vm.editContact(p) })
                TextAction(
                    stringResource(if (fav) R.string.remove_favorite else R.string.add_favorite),
                    if (fav) oz.accent else oz.fg3, { vm.toggleFavorite(p) },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}


// value присваивается; ложное срабатывание Lint на Kotlin 2 (ProduceStateDoesNotAssignValue)
@android.annotation.SuppressLint("ProduceStateDoesNotAssignValue")
@Composable
private fun ContactPhoto(uri: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            }.getOrNull()
        }
    }
    bitmap?.let {
        Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier.size(72.dp).clip(CircleShape))
    }
}
