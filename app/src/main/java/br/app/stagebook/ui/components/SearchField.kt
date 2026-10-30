package br.app.stagebook.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import br.app.stagebook.R

/** Campo de busca arredondado, sem sublinhado, com lupa e botão de limpar. */
@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(AppIcons.Close, contentDescription = stringResource(R.string.action_clear))
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = MaterialTheme.shapes.medium,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@LightPreview
@Composable
private fun SearchFieldLightPreview() = PreviewSurface(dark = false) {
    SearchField(value = "", onValueChange = {}, placeholder = "Buscar música ou artista")
}

@DarkPreview
@Composable
private fun SearchFieldDarkPreview() = PreviewSurface(dark = true) {
    SearchField(value = "legião", onValueChange = {}, placeholder = "Buscar música ou artista")
}
