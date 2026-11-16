package br.app.stagebook.ui.setlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import br.app.stagebook.R
import br.app.stagebook.ui.components.AppIcons
import br.app.stagebook.ui.components.FollowBusyWhile
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** The day as the person reads it ("15/11/2026"); [date] is the stored "yyyy-MM-dd". A date that doesn't parse reads as none. */
internal fun dayText(date: String, locale: Locale): String? =
    runCatching { LocalDate.parse(date).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)) }.getOrNull()

/** The month heading of a group ("novembro de 2026"); [month] is "yyyy-MM". */
internal fun monthTitle(month: String, locale: Locale): String =
    runCatching {
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "yMMMM")
        java.time.YearMonth.parse(month).format(DateTimeFormatter.ofPattern(pattern, locale))
    }.getOrDefault(month)

/**
 * New setlist and edit setlist: its name and the show's day. An empty name is the date and time as before; the day can be
 * cleared (a setlist with no day goes to the end of the list).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetlistEditDialog(
    title: String,
    name: String,
    date: String,
    namePlaceholder: String,
    onConfirm: (name: String, date: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var typed by rememberSaveable { mutableStateOf(name) }
    var day by rememberSaveable { mutableStateOf(date) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val locale: Locale = LocalConfiguration.current.locales.get(0)
    FollowBusyWhile()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = typed, onValueChange = { typed = it }, singleLine = true, shape = MaterialTheme.shapes.small,
                    placeholder = { Text(namePlaceholder) }, modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) { picking = true },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        dayText(day, locale) ?: stringResource(R.string.sets_no_date),
                        style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                    )
                    if (day.isNotEmpty()) IconButton(onClick = { day = "" }) { Icon(AppIcons.Close, stringResource(R.string.sets_date_clear)) }
                    IconButton(onClick = { picking = true }) { Icon(AppIcons.Edit, stringResource(R.string.sets_date_pick)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(typed.trim(), day); onDismiss() }) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
    if (picking) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = runCatching { LocalDate.parse(day) }.getOrDefault(LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { day = java.time.Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                    picking = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state) }
    }
}
