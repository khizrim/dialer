package com.khizrim.dialer.ui.person

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.khizrim.dialer.DialerViewModel
import com.khizrim.dialer.R
import com.khizrim.dialer.data.ContactEditor
import com.khizrim.dialer.data.EditPhone
import com.khizrim.dialer.ui.components.GhostIconButton
import com.khizrim.dialer.ui.components.TextAction
import com.khizrim.dialer.ui.components.press
import com.khizrim.dialer.ui.theme.LocalOz
import com.khizrim.dialer.ui.theme.OzShape
import com.khizrim.dialer.ui.theme.OzType
import com.khizrim.dialer.ui.theme.raisedFill

/**
 * Свой редактор контакта: только имя и номера, всё прижато к низу под большой палец.
 * Без иконок у полей, без цветных минусов: тип номера — тихое слово, по тапу меняется.
 */
@Composable
fun EditContactScreen(vm: DialerViewModel) {
    val oz = LocalOz.current
    val context = LocalContext.current
    val d = vm.editDraft ?: return
    val isNew = d.contactId == null
    val cursor = SolidColor(oz.accent)

    Column(Modifier.fillMaxSize().imePadding()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                stringResource(if (isNew) R.string.edit_new_title else R.string.edit_title), style = OzType.small, color = oz.fg3,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp).semantics { heading() },
            )
            // имя крупно, как в карточке
            val nameHint = stringResource(R.string.edit_name)
            Field(
                value = d.name,
                onChange = { vm.updateDraft(d.copy(name = it)) },
                hint = nameHint,
                style = OzType.nameSearch.copy(color = oz.fg),
                keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                cursor = cursor,
            )
            HorizontalDivider(thickness = 1.dp, color = oz.line)

            d.phones.forEachIndexed { i, ph ->
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    // тип номера словом; тап — следующий тип
                    val typeText = ContactEditor.typeLabel(context, ph.type, ph.label).lowercase()
                    Box(
                        Modifier.width(104.dp).heightIn(min = 48.dp).offset(x = (-8).dp)
                            .press({
                                val next = ContactEditor.types.getOrElse((ContactEditor.types.indexOf(ph.type) + 1) % ContactEditor.types.size) { ContactEditor.types[0] }
                                vm.updateDraft(d.copy(phones = d.phones.toMutableList().also { it[i] = ph.copy(type = next, label = null) }))
                            }, shape = OzShape.text, label = stringResource(R.string.edit_type_change, typeText))
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) { Text(typeText, style = OzType.small, color = oz.fg3) }
                    Box(Modifier.weight(1f)) {
                        Field(
                            value = ph.number,
                            onChange = { v -> vm.updateDraft(d.copy(phones = d.phones.toMutableList().also { it[i] = ph.copy(number = v) })) },
                            hint = stringResource(R.string.edit_number),
                            style = OzType.list.copy(color = oz.fg),
                            keyboard = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                            cursor = cursor,
                        )
                    }
                    GhostIconButton(R.drawable.ic_oz_x, stringResource(R.string.edit_remove_number), {
                        vm.updateDraft(d.copy(phones = d.phones.toMutableList().also { it.removeAt(i) }))
                    })
                }
                HorizontalDivider(thickness = 1.dp, color = oz.line)
            }

            Column(Modifier.padding(top = 8.dp).offset(x = (-12).dp)) {
                TextAction(stringResource(R.string.edit_add_number), oz.accent, {
                    vm.updateDraft(d.copy(phones = d.phones + EditPhone(null, "")))
                })
                if (!isNew) TextAction(stringResource(R.string.edit_more), oz.fg3, vm::editInSystem, style = OzType.small)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 24.dp, top = 8.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextAction(stringResource(R.string.cancel), oz.fg2, vm::cancelEdit)
            Box(
                Modifier.height(48.dp).raisedFill(oz.accentFill, OzShape.text)
                    .press(vm::saveEdit, shape = OzShape.text, pressColor = Color.White.copy(alpha = .12f))
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.save), style = OzType.strong, color = Color.White) }
        }
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    style: TextStyle,
    keyboard: KeyboardOptions,
    cursor: SolidColor,
) {
    val oz = LocalOz.current
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = style,
        keyboardOptions = keyboard,
        cursorBrush = cursor,
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp).semantics { contentDescription = hint },
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(hint, style = style.copy(color = oz.fg3))
                inner()
            }
        },
    )
}
