package br.app.stagebook.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import br.app.stagebook.R
import br.app.stagebook.domain.ResetCode
import br.app.stagebook.ui.components.DarkPreview
import br.app.stagebook.ui.components.LightPreview
import br.app.stagebook.ui.components.PreviewSurface
import br.app.stagebook.ui.theme.Spacing

/**
 * Erase everything (like fresh from the store): says what is lost, offers to save the file first and only enables after
 * typing the code shown (an accidental tap never erases anything).
 */
@Composable
fun FactoryResetDialog(onBackupFirst: () -> Unit, onReset: () -> Unit, onDismiss: () -> Unit, code: String = rememberSaveable { ResetCode.generate() }) {
    var typed by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reset_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Text(stringResource(R.string.reset_warning), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onBackupFirst) { Text(stringResource(R.string.reset_backup_first)) }
                Text(stringResource(R.string.reset_type_code), style = MaterialTheme.typography.bodyMedium)
                Text(code, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.error)
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it.filter(Char::isDigit).take(code.length) },
                    label = { Text(stringResource(R.string.reset_code_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onReset,
                enabled = ResetCode.matches(code, typed),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.reset_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@LightPreview
@Composable
private fun FactoryResetLightPreview() = PreviewSurface(dark = false) { FactoryResetDialog({}, {}, {}, code = "4827") }

@DarkPreview
@Composable
private fun FactoryResetDarkPreview() = PreviewSurface(dark = true) { FactoryResetDialog({}, {}, {}, code = "4827") }
