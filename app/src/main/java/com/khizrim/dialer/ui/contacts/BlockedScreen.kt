package com.khizrim.dialer.ui.contacts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.khizrim.dialer.DialerViewModel
import com.khizrim.dialer.R
import com.khizrim.dialer.model.Person
import com.khizrim.dialer.ui.components.BottomBar
import com.khizrim.dialer.ui.components.GhostIconButton
import com.khizrim.dialer.ui.components.KeypadFab
import com.khizrim.dialer.ui.components.TextAction
import com.khizrim.dialer.ui.components.press
import com.khizrim.dialer.ui.theme.LocalOz
import com.khizrim.dialer.ui.theme.OzType
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

@Composable
fun BlockedScreen(vm: DialerViewModel) {
    val oz = LocalOz.current
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.Bottom) {
            item(key = "title") {
                Text(stringResource(R.string.blocked_title), style = OzType.namePerson, color = oz.fg, modifier = Modifier.padding(bottom = 16.dp).semantics { heading() })
            }
            if (!vm.isDefaultDialer) {
                item(key = "role") {
                    Text(
                        stringResource(R.string.blocked_need_role),
                        style = OzType.body, color = oz.fg2, modifier = Modifier.padding(bottom = 16.dp),
                    )
                }
            } else if (vm.blocked.isEmpty()) {
                item(key = "empty") {
                    Text(stringResource(R.string.blocked_empty), style = OzType.body, color = oz.fg2, modifier = Modifier.padding(bottom = 16.dp))
                }
            }
            items(vm.blocked, key = { it }) { n ->
                Row(Modifier.fillMaxWidth().height(56.dp).animateItem(), verticalAlignment = Alignment.CenterVertically) {
                    Text(com.khizrim.dialer.data.PhoneFormat.pretty(n), style = OzType.list, color = oz.fg, modifier = Modifier.weight(1f))
                    TextAction(stringResource(R.string.unblock), oz.accent, { vm.unblock(n) }, style = OzType.small)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Незнакомые номера: тихий режим и список звонков, которые не звенели. */
