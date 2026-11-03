package com.khizrim.dialer.ui.recents

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import com.khizrim.dialer.ui.components.TextAction
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.khizrim.dialer.DialerViewModel
import com.khizrim.dialer.R
import com.khizrim.dialer.data.CallRecord
import com.khizrim.dialer.data.Dates
import com.khizrim.dialer.data.PhoneFormat
import com.khizrim.dialer.model.Person
import com.khizrim.dialer.ui.components.BottomBar
import com.khizrim.dialer.ui.components.GhostIconButton
import com.khizrim.dialer.ui.components.KeypadFab
import com.khizrim.dialer.ui.components.press
import com.khizrim.dialer.ui.theme.LocalOz
import com.khizrim.dialer.ui.theme.OzType
import java.text.DateFormat
import java.util.Date

private sealed interface Line
private data class Day(val text: String) : Line
private data class Item(val record: CallRecord, val person: Person?) : Line

/** Полный журнал звонков: по дням, новые сверху. Тап перезванивает, долгое нажатие — меню. */
@Composable
fun RecentsScreen(vm: DialerViewModel) {
    val oz = LocalOz.current
    val res = LocalContext.current.resources
    val missedOnly = vm.missedOnly
    val calls = vm.recentCalls
    // список прижат к низу: новое внизу у большого пальца, подпись дня над своими звонками
    val lines = remember(calls, missedOnly) {
        buildList<Line> {
            var day: String? = null
            calls.filter { !missedOnly || it.first.missed }.forEach { (r, p) ->
                val d = Dates.day(res, r.date)
                if (day != null && d != day) add(Day(day!!))
                day = d
                add(Item(r, p))
            }
            day?.let { add(Day(it)) }
        }
    }
    val time = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    val askWrite = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.askClearLog()
    }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), state = rememberLazyListState(), reverseLayout = true) {
            if (lines.isEmpty()) {
                item(key = "empty") {
                    Text(stringResource(if (missedOnly) R.string.recents_no_missed else R.string.recents_empty), style = OzType.body, color = oz.fg2, modifier = Modifier.padding(12.dp))
                }
            }
            items(lines, key = { l -> when (l) { is Day -> "d" + l.text; is Item -> "c" + l.record.date + l.record.number } }) { l ->
                when (l) {
                    is Day -> Text(
                        l.text, style = OzType.small.copy(fontWeight = FontWeight.Medium), color = oz.fg3,
                        modifier = Modifier.padding(start = 12.dp, top = 24.dp, bottom = 4.dp).semantics { heading() },
                    )
                    is Item -> {
                        val r = l.record
                        val title = l.person?.name ?: PhoneFormat.pretty(r.number)
                        val method = l.person?.let { vm.defaultOf(it) } ?: if (l.person == null) com.khizrim.dialer.model.Method.PHONE else null
                        val label = listOf(title, r.text(res), time.format(Date(r.date)), stringResource(method?.title ?: R.string.choose_method)).joinToString(", ")
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .press({ vm.callBack(r, l.person) }, onLongClick = { vm.openRecentMenu(r, l.person) }, shape = RoundedCornerShape(16.dp), label = label)
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
                                Text(title, style = if (l.person != null && !l.person.nameIsNumber) OzType.nameList else OzType.number, color = oz.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                // SIM в конце второй строки, только когда их две
                                val simIdx = if (vm.sims.size > 1) vm.sims.indexOfFirst { it.first == r.account } else -1
                                val second = if (simIdx >= 0) r.text(res) + "  " + stringResource(R.string.sim_n, simIdx + 1) else r.text(res)
                                Text(second, style = OzType.small, color = if (r.missed) oz.danger else oz.fg3)
                            }
                            Text(time.format(Date(r.date)), style = OzType.time, color = oz.fg3, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            }
            item(key = "title") {
                // «Очистить» — тихой ссылкой у заголовка, подальше от большого пальца и кнопки набора
                Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.journal), style = OzType.namePerson, color = oz.fg,
                        modifier = Modifier.weight(1f).padding(start = 12.dp).semantics { heading() },
                    )
                    if (lines.isNotEmpty()) TextAction(stringResource(R.string.clear_log), oz.fg3, {
                        if (vm.canClearLog) vm.askClearLog() else askWrite.launch(android.Manifest.permission.WRITE_CALL_LOG)
                    })
                }
            }
        }
        BottomBar {
            // фильтр двумя словами: активное fg и 500, неактивное fg3, без заливки
            Row(Modifier.weight(1f)) {
                // активное слово подчёркнуто тонкой линией — видно и без цвета
                val underline = androidx.compose.ui.text.style.TextDecoration.Underline
                TextAction(stringResource(R.string.filter_all), if (missedOnly) oz.fg3 else oz.fg, { vm.showMissedOnly(false) },
                    style = OzType.body.copy(fontWeight = if (missedOnly) FontWeight.Normal else FontWeight.Medium, textDecoration = if (missedOnly) null else underline))
                TextAction(stringResource(R.string.filter_missed), if (missedOnly) oz.fg else oz.fg3, { vm.showMissedOnly(true) },
                    style = OzType.body.copy(fontWeight = if (missedOnly) FontWeight.Medium else FontWeight.Normal, textDecoration = if (missedOnly) underline else null))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                KeypadFab { vm.openKeypad() }
            }
        }
    }
}
