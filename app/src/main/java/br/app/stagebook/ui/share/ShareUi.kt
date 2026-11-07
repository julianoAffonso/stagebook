package br.app.stagebook.ui.share

import br.app.stagebook.ui.components.AppMenu
import br.app.stagebook.ui.components.LocalAppMessage
import br.app.stagebook.ui.components.SwitchRow
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.app.stagebook.R
import br.app.stagebook.domain.ShareCode
import br.app.stagebook.ui.components.ActionButton
import br.app.stagebook.ui.components.AppIcons
import br.app.stagebook.ui.components.ButtonTone
import br.app.stagebook.ui.components.TextInputDialog
import br.app.stagebook.ui.theme.Spacing

// Share (setlist or loose songs): the sheet with the ways to send and the internet code dialog.

/**
 * Import (on the Songs and Setlists tabs), a single button with a menu: from the sites ([onSites], only on the Songs
 * tab and only with internet), scan the QR (always) or type the code (only with internet). Whatever arrives by QR or
 * code (setlist or loose songs) opens the right review, whatever the tab.
 */
@Composable
fun ImportButton(
    online: Boolean,
    onScan: () -> Unit,
    onCode: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSites: (() -> Unit)? = null,
    received: Int = 0,               // what the band sent and hasn't been reviewed
    onReceived: () -> Unit = {},
    onSync: (() -> Unit)? = null,    // "Sync with the band" (only with a band member)
    internetOn: Boolean = true,      // essential mode (T19): no "Type the 3 words"
    localNet: Boolean = true,        // no Wi-Fi: the band can't be reached, "Sync with the band" says so (B-26)
) {
    var open by rememberSaveable { mutableStateOf(false) }
    var typing by rememberSaveable { mutableStateOf(false) }
    Box(modifier) {
        ActionButton(stringResource(R.string.import_menu), onClick = { open = true }, modifier = Modifier.fillMaxWidth(), icon = AppIcons.Download, tone = ButtonTone.Tonal)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = AppMenu.color, shadowElevation = AppMenu.shadow, border = AppMenu.border) {
            onSync?.let { sync ->
                DropdownMenuItem(
                    text = { Text(stringResource(if (localNet) R.string.import_sync_band else R.string.import_sync_band_offline)) },
                    onClick = { open = false; sync() },
                    leadingIcon = { Icon(if (localNet) AppIcons.Sync else AppIcons.CloudOff, null) },
                    enabled = localNet,
                )
            }
            if (received > 0) {
                DropdownMenuItem(
                    text = { Text(pluralStringResource(R.plurals.import_received, received, received)) },
                    onClick = { open = false; onReceived() },
                    leadingIcon = { Icon(AppIcons.Group, null) },
                )
            }
            onSites?.let { sites ->
                DropdownMenuItem(
                    text = { Text(stringResource(if (online) R.string.import_from_sites else R.string.import_from_sites_offline)) },
                    onClick = { open = false; sites() },
                    leadingIcon = { Icon(if (online) AppIcons.Public else AppIcons.CloudOff, null) },
                    enabled = online,
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sets_scan)) },
                onClick = { open = false; onScan() },
                leadingIcon = { Icon(AppIcons.QrCode, null) },
            )
            if (internetOn) DropdownMenuItem(
                text = { Text(stringResource(if (online) R.string.sets_type_code else R.string.sets_type_code_offline)) },
                onClick = { open = false; typing = true },
                leadingIcon = { Icon(AppIcons.Edit, null) },
                enabled = online,
            )
        }
    }
    if (typing) {
        TextInputDialog(
            stringResource(R.string.sets_type_code), "", stringResource(R.string.sets_code_hint),
            onConfirm = { typing = false; if (it.isNotBlank()) onCode(it.trim()) },
            onDismiss = { typing = false },
        )
    }
}

/** Shows how many band devices got the send (or that nobody got it). */
@Composable
fun BandResultMessage(results: kotlinx.coroutines.flow.Flow<Int>) {
    val message = LocalAppMessage.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    androidx.compose.runtime.LaunchedEffect(results) {
        results.collect { n ->
            val msg = if (n > 0) resources.getQuantityString(R.plurals.share_band_sent, n, n) else resources.getString(R.string.share_band_failed)
            message.show(msg, long = true)
        }
    }
}

/** What to do with the ready code: copy it or send it through a messaging app on the device (WhatsApp, email, SMS…). */
class CodeActions(val copy: (code: String) -> Unit, val share: (sent: CloudUi.Sent) -> Unit)

@Composable
fun rememberCodeActions(): CodeActions {
    val context = LocalContext.current
    val text = stringResource(R.string.share_message)
    val textSongs = stringResource(R.string.share_message_songs)
    val copied = stringResource(R.string.share_copied)
    val message = LocalAppMessage.current
    return remember(context, text, textSongs, copied, message) {
        CodeActions(
            copy = { code ->
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Stagebook", code))
                message.show(copied)
            },
            share = { sent -> shareText(context, codeMessage(text, textSongs, sent, br.app.stagebook.data.Languages.shownTag(context))) },
        )
    }
}

/**
 * The message that goes with a code: the link to tap (user, 07/11) and the 3 words to type. [Sent.name] empty = loose
 * songs ([songs] template), else the setlists' names ([setlist] template). The link also says, in [lang], what was sent
 * and by whom, for the site.
 */
internal fun codeMessage(setlist: String, songs: String, sent: CloudUi.Sent, lang: String): String {
    val kind = if (sent.setlist.isEmpty()) ShareCode.LinkKind.SONGS else ShareCode.LinkKind.SETLIST
    val link = ShareCode.link(sent.code, kind, sent.from, sent.setlist, lang)
    return if (sent.name.isEmpty()) songs.format(link, sent.code) else setlist.format(sent.name, link, sent.code)
}

/** Opens Android's share sheet with [text] (WhatsApp, email, SMS…). */
fun shareText(context: android.content.Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    runCatching { context.startActivity(Intent.createChooser(send, null)) }
}

/** Sending over the internet: sending, sent (with the code) or failed. */
@Immutable
sealed interface CloudUi {
    data object Sending : CloudUi
    /**
     * [joined]: who already accepted and joined the band (shows in the dialog while it's open). [from] and [setlist]
     * (the first setlist's name; empty = loose songs) go in the link.
     */
    data class Sent(
        val code: String, val name: String, val songs: Int, val skipped: Int, val qr: ImageBitmap? = null, val joined: List<String> = emptyList(),
        val from: String = "", val setlist: String = name,
    ) : CloudUi
    data object Failed : CloudUi
}

/**
 * The "Share" sheet: each way to send only shows when it can be used (QR always; band only with a band member on the
 * network now; internet only with internet). Open, it turns on the band search ([onSearch]); closed, it turns it off
 * (battery).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(
    songs: Int,
    online: Boolean,
    onQr: (() -> Unit)?,
    onInternet: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    band: Int = 0,
    onBand: (Boolean) -> Unit = {},
    onSearch: (Boolean) -> Unit = {},
    internetOn: Boolean = true,
    limits: ShareLimits? = null,   // null: one list with [songs] songs (a setlist when there's a QR, else loose songs)
) {
    DisposableEffect(Unit) {
        onSearch(true)
        onDispose { onSearch(false) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ShareOptions(songs, online, onQr, onInternet, Modifier.padding(bottom = Spacing.l), band, onBand, internetOn, limits ?: ShareLimits(lists = 1, songs = songs, setlists = onQr != null))
    }
}

/**
 * What's being shared, for each way's limit ([br.app.stagebook.domain.BandContent]): [lists] setlists (loose songs are
 * one list) and [songs] songs that can go along; [setlists] true offers sending only the titles.
 */
@Immutable
data class ShareLimits(val lists: Int = 1, val songs: Int = 0, val setlists: Boolean = false)

/**
 * The sheet's content (separate for the preview and screenshots). [band]: how many band members are on the network now.
 * An option over its limit shows off, with the reason, and the others still work.
 */
@Composable
fun ShareOptions(
    songs: Int,
    online: Boolean,
    onQr: (() -> Unit)?,
    onInternet: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    band: Int = 0,
    onBand: (Boolean) -> Unit = {},
    internetOn: Boolean = true,   // essential mode (T19): no sending over the internet and no "no internet" notice
    limits: ShareLimits = ShareLimits(lists = 1, songs = songs, setlists = onQr != null),
) {
    var withSongs by rememberSaveable { mutableStateOf(true) }
    val bandOk = br.app.stagebook.domain.BandContent.bandFits(limits.lists)
    val internetOk = br.app.stagebook.domain.BandContent.internetFits(limits.lists, if (withSongs) limits.songs else 0)
    Column(modifier.fillMaxWidth().padding(horizontal = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(stringResource(R.string.share_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = Spacing.s))
        // band on the network: arrives by itself for band members on this Wi-Fi (no words, no QR)
        if (band > 0) {
            ShareOption(
                AppIcons.Group, pluralStringResource(R.plurals.share_band, band, band),
                if (bandOk) stringResource(R.string.share_band_hint) else stringResource(R.string.share_band_limit),
                enabled = bandOk,
            ) { onBand(withSongs) }
        }
        // QR on screen: only in a setlist (it carries only the titles; loose songs don't fit in a QR)
        onQr?.let { ShareOption(AppIcons.QrCode, stringResource(R.string.share_qr), stringResource(R.string.share_qr_hint), onClick = it) }
        if (!internetOn) Unit
        else if (online) {
            ShareOption(
                AppIcons.Public, stringResource(R.string.share_internet),
                if (internetOk) stringResource(R.string.share_internet_hint)
                else stringResource(if (limits.setlists && withSongs) R.string.share_internet_limit_titles else R.string.share_internet_limit),
                enabled = internetOk,
            ) { onInternet(withSongs) }
        } else {
            Text(stringResource(if (onQr != null) R.string.share_offline else R.string.share_offline_songs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // in a setlist you can send only the titles (band and internet); loose songs always go whole
        if (limits.setlists && ((online && internetOn) || band > 0)) {
            SwitchRow(
                pluralStringResource(R.plurals.share_with_songs, songs, songs),
                withSongs,
                { withSongs = it },
                Modifier.padding(top = Spacing.s),
                detail = stringResource(R.string.share_with_songs_hint),
            )
        }
    }
}

@Composable
private fun ShareOption(icon: ImageVector, title: String, hint: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(enabled = enabled, onClick = onClick).padding(vertical = Spacing.s, horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Icon(icon, null, tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            // off: the hint says why (over this way's limit)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
        }
    }
}

// sending, the ready code (with "Send in a message") or the error
@Composable
fun CloudDialog(cloud: CloudUi, onDismiss: () -> Unit, onShareCode: (CloudUi.Sent) -> Unit, onCopyCode: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = { if (cloud != CloudUi.Sending) onDismiss() },
        title = {
            Text(
                stringResource(
                    when (cloud) {
                        CloudUi.Sending -> R.string.share_sending
                        is CloudUi.Sent -> R.string.share_sent
                        CloudUi.Failed -> R.string.share_failed
                    },
                ),
            )
        },
        text = {
            when (cloud) {
                CloudUi.Sending -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                CloudUi.Failed -> Text(stringResource(R.string.share_failed_text), style = MaterialTheme.typography.bodyLarge)
                is CloudUi.Sent -> CodeBox(cloud, onCopy = { onCopyCode(ShareCode.display(cloud.code)) })
            }
        },
        confirmButton = {
            if (cloud is CloudUi.Sent) TextButton(onClick = { onShareCode(cloud) }) { Text(stringResource(R.string.share_send_code)) }
            else if (cloud == CloudUi.Failed) TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
        dismissButton = { if (cloud is CloudUi.Sent) TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

/**
 * The big code (to read out) with Copy right beside it (icon only), then its QR, smaller, for whoever prefers a photo
 * or "Scan QR" (user's layout of 30/10: the words come first, the title says "type"), and what went along.
 */
@Composable
fun CodeBox(sent: CloudUi.Sent, onCopy: () -> Unit = {}) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m), horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = Spacing.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SelectionContainer {
                Text(ShareCode.display(sent.code), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onCopy) { Icon(AppIcons.Copy, stringResource(R.string.share_copy)) }
        }
        sent.qr?.let { img ->
            Image(
                bitmap = img,
                contentDescription = stringResource(R.string.share_code_qr),
                modifier = Modifier.widthIn(max = 200.dp).fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.small).background(Color.White),
            )
            Text(stringResource(R.string.share_qr_or), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // "Sending 0 songs" read wrong when the songs aren't carried (user, 02/11): titles only
        Text(
            if (sent.songs > 0) pluralStringResource(R.plurals.share_sent_songs, sent.songs, sent.songs) else stringResource(R.string.share_sent_titles),
            style = MaterialTheme.typography.bodyMedium,
        )
        sent.joined.forEach { name ->
            Text(stringResource(R.string.share_joined, name), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        if (sent.skipped > 0) {
            Text(pluralStringResource(R.plurals.share_skipped, sent.skipped, sent.skipped), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Text(stringResource(R.string.share_sent_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}


