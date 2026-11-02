package br.app.stagebook.ui.components

import androidx.compose.runtime.setValue


import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import br.app.stagebook.R

/** Rounded search field, no underline, with a magnifier and a clear button. */
@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, label: String? = null) {
    if (label != null) return LabeledSearchField(value, onValueChange, placeholder, label, modifier)
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        // one line: beside the tag filter on phones it wrapped and the field grew taller
        placeholder = { FittingHint(placeholder) },
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

/**
 * The search with its label always small on top (phones, beside the tag filter, which shows "Tags" the same way). Only
 * the state-based field can keep the label up while empty; the text still comes from and goes to [value].
 */
@Composable
private fun LabeledSearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, label: String, modifier: Modifier) {
    val state = androidx.compose.foundation.text.input.rememberTextFieldState(value)
    val current by androidx.compose.runtime.rememberUpdatedState(value)
    val change by androidx.compose.runtime.rememberUpdatedState(onValueChange)
    androidx.compose.runtime.LaunchedEffect(value) { if (state.text.toString() != value) state.edit { replace(0, length, value) } }
    androidx.compose.runtime.LaunchedEffect(state) {
        androidx.compose.runtime.snapshotFlow { state.text.toString() }.collect { if (it != current) change(it) }
    }
    TextField(
        state = state,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        labelPosition = androidx.compose.material3.TextFieldLabelPosition.Attached(alwaysMinimize = true),
        placeholder = { FittingHint(placeholder) },
        leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(AppIcons.Close, contentDescription = stringResource(R.string.action_clear))
                }
            }
        },
        lineLimits = androidx.compose.foundation.text.input.TextFieldLineLimits.SingleLine,
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

/**
 * The search hint on one line, smaller step by step when it doesn't fit: with large system text it read "Música, …"
 * (device test B-20); the library's auto-size didn't act inside the text field.
 */
@androidx.compose.runtime.Composable
private fun FittingHint(text: String) {
    val base = androidx.compose.material3.MaterialTheme.typography.bodyLarge
    var scale by androidx.compose.runtime.remember(text) { androidx.compose.runtime.mutableFloatStateOf(1f) }
    Text(
        text,
        style = base.copy(fontSize = base.fontSize * scale),
        maxLines = 1,
        softWrap = false,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        onTextLayout = { if (it.hasVisualOverflow && scale > MIN_HINT_SCALE) scale -= HINT_STEP },
    )
}

private const val MIN_HINT_SCALE = 0.6f
private const val HINT_STEP = 0.05f
