package br.app.stagebook.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import br.app.stagebook.R
import br.app.stagebook.domain.Tags
import br.app.stagebook.ui.theme.Spacing

/**
 * Tags of one song or a selection: a box per tag (half-ticked = only some of the selection have it) and a field for a
 * new one. Nothing changes until Save; [onSave] gets the tags to add and to take off.
 */
@Composable
fun TagsDialog(
    tags: List<String>,
    initial: Map<String, ToggleableState>,
    onSave: (add: List<String>, remove: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = remember { mutableStateMapOf<String, ToggleableState>().apply { putAll(initial) } }
    val shown = remember { mutableStateOf(tags) }
    var typed by rememberSaveable { mutableStateOf("") }
    fun addTyped() {
        val t = Tags.clean(typed)
        if (t.isEmpty()) return
        val same = shown.value.firstOrNull { it.equals(t, true) }
        if (same == null) shown.value = shown.value + t
        state[same ?: t] = ToggleableState.On
        typed = ""
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tags_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                shown.value.forEach { t ->
                    val s = state[t] ?: ToggleableState.Off
                    Row(
                        Modifier.fillMaxWidth().triStateToggleable(s, role = Role.Checkbox) {
                            state[t] = if (s == ToggleableState.On) ToggleableState.Off else ToggleableState.On
                        },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        TriStateCheckbox(s, onClick = null)
                        Text(t, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        placeholder = { Text(stringResource(R.string.tags_new)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = ::addTyped, enabled = typed.isNotBlank()) { Text(stringResource(R.string.tags_add)) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                addTyped()
                val add = state.filter { (t, s) -> s == ToggleableState.On && initial[t] != ToggleableState.On }.keys.toList()
                val remove = state.filter { (t, s) -> s == ToggleableState.Off && (initial[t] ?: ToggleableState.Off) != ToggleableState.Off }.keys.toList()
                onSave(add, remove)
                onDismiss()
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * National and International only exist in Portuguese (user, 01/11: in other languages it's unclear what people call
 * them): the split in Settings and these two suggestions show only with the app in Portuguese.
 */
@Composable
fun nationalTagsShown(): Boolean = androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language == "pt"

/** The tags offered even before anyone uses them: National and International, in Portuguese only. */
@Composable
fun suggestedTags(): List<String> =
    if (nationalTagsShown()) listOf(stringResource(R.string.tags_national), stringResource(R.string.tags_international)) else emptyList()

@LightPreview
@Composable
private fun TagsDialogLightPreview() = PreviewSurface(dark = false) {
    TagsDialog(listOf("Internacional", "Nacional"), mapOf("Nacional" to ToggleableState.On), { _, _ -> }, {})
}

@DarkPreview
@Composable
private fun TagsDialogDarkPreview() = PreviewSurface(dark = true) {
    TagsDialog(listOf("Internacional", "Nacional"), mapOf("Nacional" to ToggleableState.Indeterminate), { _, _ -> }, {})
}
