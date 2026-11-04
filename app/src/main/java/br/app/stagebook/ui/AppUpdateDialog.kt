package br.app.stagebook.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import br.app.stagebook.R
import br.app.stagebook.data.UpdateOffer
import br.app.stagebook.ui.components.DarkPreview
import br.app.stagebook.ui.components.LightPreview
import br.app.stagebook.ui.components.PreviewSurface
import br.app.stagebook.ui.components.ReleaseNotes
import br.app.stagebook.ui.theme.Spacing

/** New app version: asks, shows the download and opens the Android installer. */
@Composable
fun AppUpdateDialog(
    offer: UpdateOffer,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
    onInstall: () -> Unit,
    onClose: () -> Unit,
) {
    val downloading = offer is UpdateOffer.Downloading
    AlertDialog(
        // downloading: tapping outside doesn't close it (the download goes on and the notice comes back ready)
        onDismissRequest = { if (!downloading) (if (offer is UpdateOffer.Available) onLater() else onClose()) },
        properties = DialogProperties(dismissOnClickOutside = !downloading),
        title = { Text(stringResource(R.string.update_offer_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                if (offer is UpdateOffer.Available) {
                    // several versions: each one's number heads its list, and the first is this one
                    if (offer.notes.size < 2) Text(stringResource(R.string.update_version, offer.versionName.substringBefore(' ')), style = MaterialTheme.typography.bodyLarge)
                    // the list gives way on a short screen: the warning and the buttons stay
                    ReleaseNotes(offer.notes, Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()))
                    Text(
                        stringResource(R.string.update_offer_band),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else Text(
                    when (offer) {
                        is UpdateOffer.Available -> ""
                        is UpdateOffer.Downloading -> stringResource(R.string.settings_update_downloading, offer.percent)
                        is UpdateOffer.Ready -> stringResource(R.string.update_offer_ready)
                        UpdateOffer.Failed -> stringResource(R.string.settings_update_failed)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (offer is UpdateOffer.Downloading) {
                    LinearProgressIndicator(progress = { offer.percent / 100f }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            when (offer) {
                is UpdateOffer.Available -> TextButton(onClick = onUpdate) { Text(stringResource(R.string.update_offer_update)) }
                is UpdateOffer.Ready -> TextButton(onClick = onInstall) { Text(stringResource(R.string.settings_update_install)) }
                UpdateOffer.Failed -> TextButton(onClick = onUpdate) { Text(stringResource(R.string.update_offer_retry)) }
                is UpdateOffer.Downloading -> Unit
            }
        },
        dismissButton = {
            when (offer) {
                is UpdateOffer.Available -> TextButton(onClick = onLater) { Text(stringResource(R.string.update_offer_later)) }
                UpdateOffer.Failed -> TextButton(onClick = onClose) { Text(stringResource(R.string.action_cancel)) }
                else -> Unit
            }
        },
    )
}

@LightPreview
@Composable
private fun AppUpdateDialogLightPreview() = PreviewSurface(dark = false) {
    AppUpdateDialog(UpdateOffer.Available("0.3.101 (abc1234)"), {}, {}, {}, {})
}

@DarkPreview
@Composable
private fun AppUpdateDialogDarkPreview() = PreviewSurface(dark = true) {
    AppUpdateDialog(UpdateOffer.Downloading(42), {}, {}, {}, {})
}
