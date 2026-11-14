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
    onAutoBlocker: (() -> Unit)? = null,   // a Samsung with Auto Blocker, which may stop the install
) {
    val downloading = offer is UpdateOffer.Downloading
    br.app.stagebook.ui.components.FollowBusyWhile()
    AlertDialog(
        // downloading: tapping outside doesn't close it (the download goes on and the notice comes back ready).
        // Only the "Not now" button puts the version off: a stray tap outside would hide it until tomorrow
        onDismissRequest = { if (!downloading) onClose() },
        properties = DialogProperties(dismissOnClickOutside = !downloading),
        title = { Text(stringResource(R.string.update_offer_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                when (offer) {
                    is UpdateOffer.Available -> {
                        // several versions: each one's number heads its list, and the first is this one
                        if (offer.notes.size < 2) OfferStatus(stringResource(R.string.update_version, offer.versionName.substringBefore(' ')))
                        // the list gives way on a short screen: the warning and the buttons stay
                        ReleaseNotes(offer.notes, Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()))
                        Text(
                            stringResource(R.string.update_offer_band),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    is UpdateOffer.Downloading -> {
                        OfferStatus(stringResource(R.string.settings_update_downloading, offer.percent))
                        LinearProgressIndicator(progress = { offer.percent / 100f }, modifier = Modifier.fillMaxWidth())
                    }
                    is UpdateOffer.Ready -> {
                        OfferStatus(stringResource(R.string.update_offer_ready))
                        if (onAutoBlocker != null) {
                            Text(stringResource(R.string.auto_blocker_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = onAutoBlocker) { Text(stringResource(R.string.auto_blocker_open)) }
                        }
                    }
                    UpdateOffer.Failed -> OfferStatus(stringResource(R.string.settings_update_failed))
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

@Composable
private fun OfferStatus(text: String) = Text(text, style = MaterialTheme.typography.bodyLarge)

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
