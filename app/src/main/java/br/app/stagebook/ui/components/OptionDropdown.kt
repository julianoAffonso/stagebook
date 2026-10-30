package br.app.stagebook.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle

/**
 * Lista suspensa (combo) de uma escolha: uma linha só, no lugar de uma lista de botões de rádio. [styleOf] deixa
 * cada opção (e o campo, com a escolhida) num estilo próprio — a fonte aparece escrita nela mesma.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> OptionDropdown(
    label: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    nameOf: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    styleOf: (@Composable (T) -> TextStyle)? = null,
) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }, modifier = modifier) {
        TextField(
            value = nameOf(selected),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            textStyle = styleOf?.invoke(selected) ?: MaterialTheme.typography.bodyLarge,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            shape = MaterialTheme.shapes.small,
            colors = ExposedDropdownMenuDefaults.textFieldColors(),
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o ->
                DropdownMenuItem(
                    text = { Text(nameOf(o), style = styleOf?.invoke(o) ?: MaterialTheme.typography.bodyLarge) },
                    onClick = { onSelect(o); open = false },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@LightPreview
@Composable
private fun OptionDropdownLightPreview() = PreviewSurface(dark = false) {
    OptionDropdown("Instrumento", listOf("Violão", "Ukulele", "Teclado"), "Violão", {}, { it })
}

@DarkPreview
@Composable
private fun OptionDropdownDarkPreview() = PreviewSurface(dark = true) {
    OptionDropdown("Instrumento", listOf("Violão", "Ukulele", "Teclado"), "Ukulele", {}, { it })
}
