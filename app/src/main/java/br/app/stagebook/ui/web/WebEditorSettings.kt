package br.app.stagebook.ui.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.app.stagebook.R
import br.app.stagebook.data.web.WebEditorServer
import br.app.stagebook.ui.components.AppIcons
import br.app.stagebook.ui.components.DarkPreview
import br.app.stagebook.ui.components.LightPreview
import br.app.stagebook.ui.components.PreviewSurface
import br.app.stagebook.ui.components.QuestionDialog
import br.app.stagebook.ui.components.SettingsCard
import br.app.stagebook.ui.components.SwitchRow
import br.app.stagebook.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@Immutable
data class WebDeviceUi(val id: String, val name: String)

/** Settings → Editor on the computer: the address while on and the authorized computers. */
@Immutable
data class WebEditorUi(val address: String = "", val devices: List<WebDeviceUi> = emptyList())

@HiltViewModel
class WebEditorViewModel @Inject constructor(private val server: WebEditorServer) : ViewModel() {
    val ui: StateFlow<WebEditorUi> = combine(server.address, server.auth.devices) { address, devices ->
        WebEditorUi(address, devices.map { WebDeviceUi(it.id, it.name) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WebEditorUi())

    fun remove(id: String) = server.auth.remove(id)
}

@Composable
fun WebEditorDetail(on: Boolean, ui: WebEditorUi, onSwitch: (Boolean) -> Unit, onRemove: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        // card name isn't the screen's again ("Editor on the computer" twice)
        SettingsCard(stringResource(R.string.settings_web_editor_card), help = stringResource(R.string.settings_web_editor_help)) {
            SwitchRow(stringResource(R.string.settings_web_editor_switch), on, onSwitch)
            if (on) {
                if (ui.address.isEmpty()) {
                    Text(stringResource(R.string.settings_web_editor_no_wifi), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                } else {
                    Text(stringResource(R.string.settings_web_editor_open), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // big, to read from the computer's chair
                    Text(ui.address, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        SettingsCard(stringResource(R.string.settings_web_editor_devices)) {
            if (ui.devices.isEmpty()) {
                Text(stringResource(R.string.settings_web_editor_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ui.devices.forEach { d ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.Computer, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(d.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = Spacing.s), maxLines = 1)
                    IconButton(onClick = { onRemove(d.id) }) { Icon(AppIcons.Delete, stringResource(R.string.settings_web_editor_remove, d.name)) }
                }
            }
        }
    }
}

/** "Chrome · Windows wants to edit": Allow / Deny (never on stage: the show isn't interrupted). */
@Composable
fun WebRequestDialog(name: String, onAnswer: (Boolean) -> Unit) {
    QuestionDialog(
        stringResource(R.string.web_request_title, name), stringResource(R.string.web_request_text),
        stringResource(R.string.web_allow), { onAnswer(true) },
        stringResource(R.string.web_deny), { onAnswer(false) },
        icon = AppIcons.Computer,
    )
}

private val sample = WebEditorUi("http://192.168.0.10:8090", listOf(WebDeviceUi("1", "Chrome · Windows")))

@LightPreview
@Composable
private fun WebEditorLightPreview() = PreviewSurface(dark = false) { WebEditorDetail(true, sample, {}, {}) }

@DarkPreview
@Composable
private fun WebEditorDarkPreview() = PreviewSurface(dark = true) { WebEditorDetail(true, sample, {}, {}) }
