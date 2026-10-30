package com.juliano.stagebook.ui.share

import com.juliano.stagebook.ui.components.LocalAppMessage
import com.juliano.stagebook.ui.components.SwitchRow
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
import com.juliano.stagebook.R
import com.juliano.stagebook.domain.ShareCode
import com.juliano.stagebook.ui.components.ActionButton
import com.juliano.stagebook.ui.components.AppIcons
import com.juliano.stagebook.ui.components.ButtonTone
import com.juliano.stagebook.ui.components.TextInputDialog
import com.juliano.stagebook.ui.theme.Spacing

// Compartilhar (setlist ou músicas avulsas): a folha com os jeitos de enviar e o diálogo do código da internet.

/**
 * Importar (nas abas Músicas e Setlists), um botão só com menu: dos sites ([onSites], só na aba Músicas e só com
 * internet), ler o QR (sempre) ou digitar o código (só com internet). O que chegar pelo QR ou código — setlist ou músicas
 * avulsas — abre a conferência certa, seja qual for a aba.
 */
@Composable
fun ImportButton(
    online: Boolean,
    onScan: () -> Unit,
    onCode: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSites: (() -> Unit)? = null,
    received: Int = 0,               // o que a banda mandou e não foi conferido
    onReceived: () -> Unit = {},
    onSync: (() -> Unit)? = null,    // "Sincronizar com a banda" (só com alguém da banda)
    internetOn: Boolean = true,      // modo essencial (T19): sem "Digitar as 3 palavras"
) {
    var open by rememberSaveable { mutableStateOf(false) }
    var typing by rememberSaveable { mutableStateOf(false) }
    Box(modifier) {
        ActionButton(stringResource(R.string.import_menu), onClick = { open = true }, modifier = Modifier.fillMaxWidth(), icon = AppIcons.Download, tone = ButtonTone.Tonal)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            onSync?.let { sync ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.import_sync_band)) },
                    onClick = { open = false; sync() },
                    leadingIcon = { Icon(AppIcons.Sync, null) },
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

/** Mostra para quantos aparelhos da banda chegou o envio (ou que ninguém recebeu). */
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

/** O que fazer com o código pronto: copiar ou mandar por um app de mensagem do aparelho (WhatsApp, e-mail, SMS…). */
class CodeActions(val copy: (code: String) -> Unit, val share: (name: String, code: String) -> Unit)

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
            share = { name, code ->
                val msg = if (name.isEmpty()) textSongs.format(code) else text.format(name, code)
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, msg)
                runCatching { context.startActivity(Intent.createChooser(send, null)) }
            },
        )
    }
}

/** Envio pela internet: enviando, enviado (com o código) ou falhou. */
@Immutable
sealed interface CloudUi {
    data object Sending : CloudUi
    /** [joined]: quem já aceitou e virou da banda (aparece no diálogo enquanto ele está aberto). */
    data class Sent(val code: String, val name: String, val songs: Int, val skipped: Int, val qr: ImageBitmap? = null, val joined: List<String> = emptyList()) : CloudUi
    data object Failed : CloudUi
}

/**
 * A folha "Compartilhar": cada jeito de enviar só aparece quando dá para usar (QR sempre; banda só com alguém da banda na
 * rede agora; internet só com internet). Aberta, liga a procura da banda ([onSearch]); fechada, desliga (bateria).
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
) {
    DisposableEffect(Unit) {
        onSearch(true)
        onDispose { onSearch(false) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ShareOptions(songs, online, onQr, onInternet, Modifier.padding(bottom = Spacing.l), band, onBand, internetOn)
    }
}

/** O conteúdo da folha (separado para o preview e os prints). [band]: quantos da banda estão na rede agora. */
@Composable
fun ShareOptions(
    songs: Int,
    online: Boolean,
    onQr: (() -> Unit)?,
    onInternet: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    band: Int = 0,
    onBand: (Boolean) -> Unit = {},
    internetOn: Boolean = true,   // modo essencial (T19): sem o envio pela internet nem o aviso de "sem internet"
) {
    var withSongs by rememberSaveable { mutableStateOf(true) }
    Column(modifier.fillMaxWidth().padding(horizontal = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(stringResource(R.string.share_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = Spacing.s))
        // banda na rede: chega sozinho para quem é da banda e está neste Wi-Fi (sem palavras, sem QR)
        if (band > 0) {
            ShareOption(AppIcons.Group, pluralStringResource(R.plurals.share_band, band, band), stringResource(R.string.share_band_hint)) { onBand(withSongs) }
        }
        // QR na tela: só no setlist (leva só os títulos; músicas avulsas não cabem num QR)
        onQr?.let { ShareOption(AppIcons.QrCode, stringResource(R.string.share_qr), stringResource(R.string.share_qr_hint), it) }
        if (!internetOn) Unit
        else if (online) {
            ShareOption(AppIcons.Public, stringResource(R.string.share_internet), stringResource(R.string.share_internet_hint)) { onInternet(withSongs) }
        } else {
            Text(stringResource(if (onQr != null) R.string.share_offline else R.string.share_offline_songs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // no setlist dá para mandar só os títulos (banda e internet); músicas avulsas sempre vão inteiras
        if (onQr != null && ((online && internetOn) || band > 0)) {
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
private fun ShareOption(icon: ImageVector, title: String, hint: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(vertical = Spacing.s, horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// enviando, o código pronto (com "Mandar por mensagem") ou o erro
@Composable
fun CloudDialog(cloud: CloudUi, onDismiss: () -> Unit, onShareCode: (String, String) -> Unit, onCopyCode: (String) -> Unit) {
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
            if (cloud is CloudUi.Sent) TextButton(onClick = { onShareCode(cloud.name, ShareCode.display(cloud.code)) }) { Text(stringResource(R.string.share_send_code)) }
            else if (cloud == CloudUi.Failed) TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
        dismissButton = { if (cloud is CloudUi.Sent) TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

/** O código grande (para ditar), o QR dele (para foto ou "Ler QR"), Copiar, e o que foi junto. */
@Composable
fun CodeBox(sent: CloudUi.Sent, onCopy: () -> Unit = {}) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m), horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        sent.qr?.let { img ->
            Image(
                bitmap = img,
                contentDescription = stringResource(R.string.share_code_qr),
                modifier = Modifier.widthIn(max = 260.dp).fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.small).background(Color.White),
            )
        }
        SelectionContainer {
            Text(ShareCode.display(sent.code), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        }
        ActionButton(stringResource(R.string.share_copy), onCopy, icon = AppIcons.Copy, tone = ButtonTone.Tonal)
        Text(pluralStringResource(R.plurals.share_sent_songs, sent.songs, sent.songs), style = MaterialTheme.typography.bodyMedium)
        sent.joined.forEach { name ->
            Text(stringResource(R.string.share_joined, name), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        if (sent.skipped > 0) {
            Text(pluralStringResource(R.plurals.share_skipped, sent.skipped, sent.skipped), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Text(stringResource(R.string.share_sent_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}


