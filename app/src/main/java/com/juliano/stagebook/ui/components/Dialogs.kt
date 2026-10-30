package com.juliano.stagebook.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import com.juliano.stagebook.R

/** Pergunta "tem certeza?"; o botão de confirmar fica vermelho quando apaga algo. */
@Composable
fun ConfirmDialog(
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(message, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirmLabel, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Pede um nome (setlist). Vazio vale: quem chamou decide o padrão. */
@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    placeholder: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(placeholder) },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()); onDismiss() }) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * Pergunta com duas respostas que fazem coisas diferentes ("Importar junto" / "Só a cifra"). Tocar fora
 * da janela vale [onOutside] (padrão: a resposta [noLabel]).
 */
@Composable
fun QuestionDialog(
    title: String,
    message: String,
    yesLabel: String,
    onYes: () -> Unit,
    noLabel: String,
    onNo: () -> Unit,
    onOutside: () -> Unit = onNo,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    AlertDialog(
        onDismissRequest = onOutside,
        icon = icon?.let { { androidx.compose.material3.Icon(it, null) } },
        title = { Text(title) },
        text = { Text(message, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = { TextButton(onClick = onYes) { Text(yesLabel) } },
        dismissButton = { TextButton(onClick = onNo) { Text(noLabel) } },
    )
}

/**
 * Aviso com título e um botão de ok; [extra] é um segundo botão opcional (texto e ação). Com [neverAgain], aparece a
 * caixa "Não mostrar de novo" e o ok devolve se ela ficou marcada.
 */
@Composable
fun InfoDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    extra: Pair<String, () -> Unit>? = null,
    neverAgain: ((Boolean) -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,   // abaixo do texto (lista, link)
) {
    var never by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            androidx.compose.foundation.layout.Column {
                Text(message, style = MaterialTheme.typography.bodyLarge)
                content?.invoke()
                if (neverAgain != null) {
                    androidx.compose.foundation.layout.Row(
                        Modifier.fillMaxWidth().padding(top = com.juliano.stagebook.ui.theme.Spacing.m)
                            .toggleable(never, role = androidx.compose.ui.semantics.Role.Checkbox) { never = it },
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Checkbox(checked = never, onCheckedChange = null)
                        Text(stringResource(R.string.action_never_again), Modifier.padding(start = com.juliano.stagebook.ui.theme.Spacing.s), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (neverAgain != null) neverAgain(never) else onDismiss() }) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = extra?.let { (label, action) -> { TextButton(onClick = action) { Text(label) } } },
    )
}

@LightPreview
@Composable
private fun ConfirmDialogLightPreview() = PreviewSurface(dark = false) {
    ConfirmDialog("Apagar \"Hazard\"? Ela também sai dos setlists.", "Apagar", onConfirm = {}, onDismiss = {})
}

@DarkPreview
@Composable
private fun ConfirmDialogDarkPreview() = PreviewSurface(dark = true) {
    ConfirmDialog("Apagar \"Hazard\"? Ela também sai dos setlists.", "Apagar", onConfirm = {}, onDismiss = {})
}

@LightPreview
@Composable
private fun TextInputDialogLightPreview() = PreviewSurface(dark = false) {
    TextInputDialog("Novo setlist", "", "Vazio = dia e hora de agora", onConfirm = {}, onDismiss = {})
}

@DarkPreview
@Composable
private fun TextInputDialogDarkPreview() = PreviewSurface(dark = true) {
    TextInputDialog("Novo setlist", "Show de sábado", "Vazio = dia e hora de agora", onConfirm = {}, onDismiss = {})
}

@LightPreview
@Composable
private fun InfoDialogLightPreview() = PreviewSurface(dark = false) {
    InfoDialog("Músicas atualizadas", "12 novas\n3 com a letra atualizada\nSuas edições, seções e zooms foram mantidos.", onDismiss = {})
}

@DarkPreview
@Composable
private fun InfoDialogDarkPreview() = PreviewSurface(dark = true) {
    InfoDialog("Músicas atualizadas", "12 novas\n3 com a letra atualizada\nSuas edições, seções e zooms foram mantidos.", onDismiss = {})
}
