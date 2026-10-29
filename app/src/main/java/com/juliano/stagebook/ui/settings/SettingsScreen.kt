package com.juliano.stagebook.ui.settings

import androidx.core.net.toUri
import com.juliano.stagebook.data.AppLinks
import androidx.compose.runtime.DisposableEffect
import com.juliano.stagebook.ui.band.BandViewModel
import com.juliano.stagebook.ui.band.BandUi
import com.juliano.stagebook.ui.band.BandDetail
import com.juliano.stagebook.ui.band.BandActions
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import com.juliano.stagebook.ui.components.SpectrumView
import com.juliano.stagebook.player.StagePlayer
import com.juliano.stagebook.domain.Spectrum
import com.juliano.stagebook.domain.MixerLevels
import com.juliano.stagebook.ui.components.VerticalFader
import androidx.compose.foundation.layout.size
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn
import com.juliano.stagebook.ui.components.MixerStrip
import com.juliano.stagebook.ui.components.HelpTip
import com.juliano.stagebook.ui.components.MixerChannelEditor
import com.juliano.stagebook.ui.components.label
import com.juliano.stagebook.domain.MixerConfig
import com.juliano.stagebook.ui.components.OptionDropdown
import com.juliano.stagebook.domain.Voicings
import com.juliano.stagebook.ui.components.ChordDiagram
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import com.juliano.stagebook.domain.ImportSiteRules
import com.juliano.stagebook.data.model.ImportSite
import com.juliano.stagebook.data.model.ChordListPlace
import com.juliano.stagebook.data.model.Instrument
import com.juliano.stagebook.ui.stage.family
import com.juliano.stagebook.data.model.StageFont
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.style.LineHeightStyle
import com.juliano.stagebook.ui.stage.ChordText
import com.juliano.stagebook.domain.Contrast
import com.juliano.stagebook.data.Languages
import com.juliano.stagebook.data.AppLanguage
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import android.app.Activity
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import com.juliano.stagebook.domain.MidiCode
import com.juliano.stagebook.data.MidiState
import com.juliano.stagebook.data.MidiFound
import android.os.Build
import android.content.Intent
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.juliano.stagebook.R
import com.juliano.stagebook.ui.installApk
import com.juliano.stagebook.data.model.Look
import com.juliano.stagebook.data.model.SectionLang
import com.juliano.stagebook.data.model.StageColumns
import com.juliano.stagebook.domain.PedalAction
import com.juliano.stagebook.ui.PedalHandler
import com.juliano.stagebook.ui.components.ActionButton
import com.juliano.stagebook.ui.components.AppIcons
import com.juliano.stagebook.ui.components.ButtonTone
import com.juliano.stagebook.ui.components.CenteredContent
import com.juliano.stagebook.ui.components.ColorSwatchRow
import com.juliano.stagebook.ui.components.ConfirmDialog
import com.juliano.stagebook.ui.components.DarkPreview
import com.juliano.stagebook.ui.components.LightPreview
import com.juliano.stagebook.ui.components.ScreenHeader
import com.juliano.stagebook.ui.components.SettingsCard
import com.juliano.stagebook.ui.components.SongListItem
import com.juliano.stagebook.ui.stage.ChordLook
import com.juliano.stagebook.ui.stage.lyricsAnnotated
import com.juliano.stagebook.ui.stage.rememberSectionNames
import com.juliano.stagebook.ui.theme.StagebookTheme
import com.juliano.stagebook.ui.theme.Sizes
import com.juliano.stagebook.ui.theme.Spacing
import com.juliano.stagebook.ui.theme.StagePalette
import kotlin.math.roundToInt

@Composable
fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::importFrom) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(viewModel::exportTo) }
    // backup: escolhe as partes (todas marcadas), depois onde salvar o arquivo .stagebook
    var backupParts by remember { mutableStateOf(com.juliano.stagebook.data.Backup.Part.entries.toSet()) }
    val backupSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(com.juliano.stagebook.data.Backup.MIME)) { uri ->
        uri?.let { viewModel.backupTo(it, backupParts) }
    }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::inspectBackup) }
    val pendingRestore by viewModel.pendingRestore.collectAsStateWithLifecycle()
    pendingRestore?.let { p ->
        BackupPartsDialog(
            title = stringResource(R.string.backup_restore),
            confirmLabel = stringResource(R.string.backup_restore_ok),
            available = p.contents.parts,
            onConfirm = viewModel::restore,
            onDismiss = viewModel::cancelRestore,
            warning = stringResource(R.string.backup_restore_warning),
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { m ->
            val r = resources
            snackbar.showSnackbar(
                when (m) {
                    is SettingsMessage.Normalized -> r.getQuantityString(R.plurals.settings_normalized, m.count, m.count) +
                        if (m.unidentified > 0) " " + r.getQuantityString(R.plurals.settings_unidentified, m.unidentified, m.unidentified) else ""
                    is SettingsMessage.Imported -> r.getString(R.string.settings_imported, m.added, m.replaced)
                    SettingsMessage.ImportFailed -> r.getString(R.string.settings_import_failed)
                    SettingsMessage.ImportNewer -> r.getString(R.string.scan_newer)
                    SettingsMessage.Exported -> r.getString(R.string.settings_exported)
                    SettingsMessage.ExportFailed -> r.getString(R.string.settings_export_failed)
                    SettingsMessage.BackupDone -> r.getString(R.string.backup_done)
                    SettingsMessage.BackupFailed -> r.getString(R.string.backup_failed)
                    SettingsMessage.NotBackup -> r.getString(R.string.backup_not_backup)
                    SettingsMessage.RestoreFailed -> r.getString(R.string.backup_restore_failed)
                    is SettingsMessage.Restored -> {
                        // língua e aparência valem ao abrir a tela de novo
                        if (m.restart) (context as? android.app.Activity)?.recreate()
                        r.getString(R.string.backup_restored)
                    }
                },
            )
        }
    }
    // teste do pedal: enquanto os ajustes estão abertos, qualquer botão aparece no cartão do pedal
    PedalHandler { p ->
        val ignored = p.code in setOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE)
        if (ignored) return@PedalHandler false
        if (p.down) viewModel.onKey(p)
        true
    }

    // instalar: na primeira vez o Android precisa liberar "instalar apps desta fonte" para o Stagebook
    val install: () -> Unit = { context.installApk(viewModel.canInstall(), viewModel.installIntent()) }
    LaunchedEffect(viewModel) { viewModel.install.collect { install() } }

    // pedal MIDI Bluetooth: a procura pede a permissão do Bluetooth (ou da localização, no Android 9 a 11)
    val midiPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.all { it }) viewModel.scanMidi()
    }
    var section by rememberSaveable { mutableStateOf<SettingsSection?>(null) }
    // medidores do mixer: só com o modo player ligado, a seção Player aberta e a tela visível
    // banda: a procura na rede fica ligada enquanto Ajustes → Banda está aberto (para alguém novo achar este aparelho)
    val bandVm: BandViewModel = hiltViewModel()
    val band by bandVm.ui.collectAsStateWithLifecycle()
    val bandActions = remember(bandVm) { bandVm.actions() }
    DisposableEffect(section == SettingsSection.BAND) {
        val open = section == SettingsSection.BAND
        bandVm.screenOpen(open)
        onDispose { if (open) bandVm.screenOpen(false) }
    }
    // chave "Monitorar": começa desligada e não fica salva
    var monitoring by remember { mutableStateOf(false) }
    val levels = remember { mutableStateOf(MixerLevels.SILENT) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val metering = monitoring && uiState.look.playerMode && section == SettingsSection.PLAYER
    LaunchedEffect(metering) {
        if (metering) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { StagePlayer.liveLevels().collect { levels.value = it } }
        levels.value = MixerLevels.SILENT
    }
    Box(Modifier.fillMaxSize()) {
        SettingsScreen(
            section = section,
            onSection = { section = it },
            uiState = uiState,
            actions = SettingsActions(
                onFont = viewModel::setFont,
                onTextColor = viewModel::setTextColor,
                onBackground = viewModel::setBackground,
                onBold = viewModel::setBold,
                onSectionColor = viewModel::setSectionColor,
                onShowSections = viewModel::setShowSections,
                onTranslateSections = viewModel::setTranslateSections,
                onChords = viewModel::setChords,
                onChordScale = viewModel::setChordScale,
                onSectionScale = viewModel::setSectionScale,
                onChordColor = viewModel::setChordColor,
                onFontFamily = viewModel::setFontFamily,
                onChordFont = viewModel::setChordFont,
                onInstrument = viewModel::setInstrument,
                onChordList = viewModel::setChordList,
                onChordTap = viewModel::setChordTap,
                onNormalize = viewModel::normalizeSections,
                onPairPedal = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                onPedalAction = viewModel::setPedalAction,
                onResetPedalKeys = viewModel::resetPedalKeys,
                onMidiScan = { if (viewModel.hasMidiPermissions()) viewModel.scanMidi() else midiPermissions.launch(viewModel.midiPermissions()) },
                onMidiChoose = viewModel::chooseMidi,
                onMidiForget = viewModel::forgetMidi,
                onMidiUsb = viewModel::setMidiUsb,
                onSites = viewModel::setSites,
                onPlayerMode = viewModel::setPlayerMode,
                onMixer = viewModel::setMixer,
                mixerLevels = { levels.value },
                monitoring = monitoring,
                onMonitoring = { monitoring = it },
                band = band,
                bandActions = bandActions,
                onLanguage = { lang ->
                    if (lang != uiState.language) {
                        Languages.set(context, lang)
                        (context as? Activity)?.recreate()   // as telas voltam já na língua nova
                    }
                },
                onKeepScreenOn = viewModel::setKeepScreenOn,
                onColumns = viewModel::setColumns,
                onStageMode = viewModel::setStageMode,
                onAutoScroll = viewModel::setAutoScroll,
                onImport = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                onExport = { exporter.launch("stagebook-biblioteca.json") },
                onBackup = { parts ->
                    backupParts = parts
                    backupSaver.launch("stagebook-backup-${java.time.LocalDate.now()}.${com.juliano.stagebook.data.Backup.EXTENSION}")
                },
                onRestore = { restorePicker.launch(arrayOf("*/*")) },
                onCheckUpdate = viewModel::checkUpdate,
                onOpenLink = { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } },
                onDownloadUpdate = viewModel::downloadUpdate,
                onInstallUpdate = install,
            ),
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    val pending by viewModel.pendingImport.collectAsStateWithLifecycle()
    pending?.let { p ->
        ConfirmDialog(
            pluralStringResource(R.plurals.settings_import_confirm, p.lyricsChanged, p.lyricsChanged, p.added),
            stringResource(R.string.settings_import_file),
            onConfirm = viewModel::confirmImport,
            onDismiss = viewModel::cancelImport,
        )
    }
}

/** Todas as interações da tela de ajustes (fica um parâmetro só em vez de onze). */
data class SettingsActions(
    val onFont: (Float) -> Unit = {},
    val onTextColor: (Int) -> Unit = {},
    val onBackground: (Int) -> Unit = {},
    val onBold: (Boolean) -> Unit = {},
    val onSectionColor: (Int) -> Unit = {},
    val onShowSections: (Boolean) -> Unit = {},
    val onTranslateSections: (Boolean) -> Unit = {},
    val onChords: (Boolean) -> Unit = {},
    val onChordScale: (Float) -> Unit = {},
    val onSectionScale: (Float) -> Unit = {},
    val onChordColor: (Int) -> Unit = {},
    val onFontFamily: (StageFont) -> Unit = {},
    val onChordFont: (StageFont) -> Unit = {},
    val onInstrument: (Instrument) -> Unit = {},
    val onChordList: (ChordListPlace) -> Unit = {},
    val onChordTap: (Boolean) -> Unit = {},
    val onNormalize: () -> Unit = {},
    val onPairPedal: () -> Unit = {},
    val onPedalAction: (Int, PedalAction) -> Unit = { _, _ -> },
    val onResetPedalKeys: () -> Unit = {},
    val onMidiScan: () -> Unit = {},
    val onMidiChoose: (MidiFound) -> Unit = {},
    val onMidiForget: () -> Unit = {},
    val onMidiUsb: (Boolean) -> Unit = {},
    val onLanguage: (AppLanguage) -> Unit = {},
    val onSites: (chords: Boolean, sites: List<ImportSite>) -> Unit = { _, _ -> },
    val onPlayerMode: (Boolean) -> Unit = {},
    val onMixer: (MixerConfig) -> Unit = {},
    val mixerLevels: () -> MixerLevels = { MixerLevels.SILENT },   // lido só no desenho dos medidores
    val monitoring: Boolean = false,
    // Ajustes → Banda: o estado da banda (BandViewModel) e o que a tela faz
    val band: BandUi = BandUi(),
    val bandActions: BandActions = BandActions(),
    val onMonitoring: (Boolean) -> Unit = {},
    val onKeepScreenOn: (Boolean) -> Unit = {},
    val onColumns: (StageColumns) -> Unit = {},
    val onStageMode: (com.juliano.stagebook.data.model.StageMode) -> Unit = {},
    val onAutoScroll: (Int) -> Unit = {},
    val onImport: () -> Unit = {},
    val onExport: () -> Unit = {},
    val onBackup: (Set<com.juliano.stagebook.data.Backup.Part>) -> Unit = {},
    val onRestore: () -> Unit = {},
    val onCheckUpdate: () -> Unit = {},
    val onOpenLink: (String) -> Unit = {},
    val onDownloadUpdate: () -> Unit = {},
    val onInstallUpdate: () -> Unit = {},
)

/** Seções dos ajustes: lista à esquerda no tablet, uma tela por seção no celular. */
enum class SettingsSection(@StringRes val title: Int, val icon: ImageVector) {
    LYRICS(R.string.settings_appearance, AppIcons.FormatSize),
    CHORDS(R.string.settings_chords, AppIcons.MusicNote),
    PLAYER(R.string.settings_player, AppIcons.Play),
    SECTIONS(R.string.settings_sections, AppIcons.ListBulleted),
    PEDAL(R.string.settings_pedal, AppIcons.Bluetooth),
    BAND(R.string.settings_band, AppIcons.Group),
    SCREEN(R.string.settings_screen, AppIcons.Brightness),
    LANGUAGE(R.string.settings_language, AppIcons.Translate),
    IMPORT(R.string.settings_import_sites, AppIcons.Public),
    LIBRARY(R.string.settings_library, AppIcons.Folder),
    ABOUT(R.string.settings_about, AppIcons.Info),   // sempre por último: o app, atualizar, privacidade, código, apoio
}

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    section: SettingsSection? = null,
    onSection: (SettingsSection?) -> Unit = {},
) {
    Crossfade(uiState.loading, modifier.fillMaxSize(), label = "settings") { loading ->
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Crossfade
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth >= Sizes.contentMax) {
                // tablet deitado: lista fixa à esquerda, seção aberta à direita
                val open = section ?: SettingsSection.LYRICS
                Row(Modifier.fillMaxSize()) {
                    SectionList(uiState, open, onSection, Modifier.width(Sizes.settingsList).fillMaxHeight(), band = actions.band)
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = Spacing.m),
                    ) {
                        // cabeçalho fixo; só o conteúdo rola
                        ScreenHeader(stringResource(open.title), Modifier.padding(top = Spacing.s, bottom = Spacing.m))
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                            SectionDetail(open, uiState, actions)
                            Spacer(Modifier.height(Spacing.l))
                        }
                    }
                }
            } else {
                BackHandler(enabled = section != null) { onSection(null) }
                Crossfade(section, label = "section") { open ->
                    if (open == null) {
                        SectionList(uiState, null, onSection, Modifier.fillMaxSize(), band = actions.band)
                    } else {
                        CenteredContent(Modifier.fillMaxSize()) {
                            Column(Modifier.fillMaxHeight().padding(horizontal = Spacing.m)) {
                                ScreenHeader(stringResource(open.title), Modifier.padding(top = Spacing.s, bottom = Spacing.m), onBack = { onSection(null) })
                                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                                    SectionDetail(open, uiState, actions)
                                    Spacer(Modifier.height(Spacing.l))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionList(uiState: SettingsUiState, selected: SettingsSection?, onSection: (SettingsSection) -> Unit, modifier: Modifier = Modifier, band: BandUi = BandUi()) {
    CenteredContent(modifier) {
        Column(Modifier.fillMaxHeight().padding(horizontal = Spacing.m)) {
            ScreenHeader(stringResource(R.string.settings_title), Modifier.padding(top = Spacing.s, bottom = Spacing.s))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                SettingsSection.entries.forEach { s ->
                    SongListItem(
                        title = stringResource(s.title),
                        subtitle = summary(s, uiState, band),
                        onClick = { onSection(s) },
                        containerColor = if (s == selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                        leading = {
                            Icon(s.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailing = if (selected == null) ({
                            Icon(AppIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }) else null,
                    )
                }
                Text(
                    stringResource(R.string.settings_version, uiState.version),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.m),
                )
            }
        }
    }
}

/** Resumo de cada seção na lista (o que está valendo agora). */
@Composable
private fun summary(s: SettingsSection, ui: SettingsUiState, band: BandUi): String = when (s) {
    SettingsSection.BAND -> if (band.members.isEmpty()) stringResource(R.string.settings_band_none)
    else pluralStringResource(R.plurals.settings_band_short, band.members.size, band.members.size)
    SettingsSection.LYRICS -> stringResource(R.string.settings_font_size, ui.look.fontSp.roundToInt()) +
        when (ui.look.columns) {
            StageColumns.AUTO -> ""
            StageColumns.ONE -> " · " + stringResource(R.string.settings_columns_one_short)
            StageColumns.TWO -> " · " + stringResource(R.string.settings_columns_two_short)
        } +
        if (ui.look.bold) " · " + stringResource(R.string.settings_bold_short) else ""
    SettingsSection.SECTIONS -> stringResource(if (ui.look.showSections) R.string.settings_sections_shown else R.string.settings_sections_hidden)
    SettingsSection.CHORDS -> if (ui.look.chords) stringResource(R.string.settings_chords_on_short, (ui.look.chordScale * 100).roundToInt()) else stringResource(R.string.settings_chords_off_short)
    SettingsSection.PEDAL -> if (ui.look.pedalName.isEmpty()) stringResource(R.string.settings_pedal_none_short)
    else ui.look.pedalName + " · " + stringResource(if (ui.pedalConnected) R.string.settings_pedal_connected_short else R.string.settings_pedal_disconnected_short)
    SettingsSection.SCREEN -> stringResource(if (ui.look.keepScreenOn) R.string.settings_screen_on_short else R.string.settings_screen_off_short)
    SettingsSection.LANGUAGE -> languageLabel(ui.language)
    SettingsSection.PLAYER -> stringResource(if (ui.look.playerMode) R.string.settings_player_on_short else R.string.settings_player_off_short)
    SettingsSection.IMPORT -> (if (ui.look.chords) ui.look.chordSites else ui.look.lyricSites).firstOrNull()?.name.orEmpty()
    SettingsSection.ABOUT -> stringResource(R.string.settings_update_installed, ui.version) +
        if (ui.update is UpdateState.Available) " · " + stringResource(R.string.settings_update_new_short) else ""
    SettingsSection.LIBRARY -> pluralStringResource(R.plurals.song_count, ui.songCount, ui.songCount) + " · " +
        pluralStringResource(R.plurals.set_count, ui.setCount, ui.setCount)
}

@Composable
private fun SectionDetail(section: SettingsSection, uiState: SettingsUiState, actions: SettingsActions) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        when (section) {
            SettingsSection.LYRICS -> LyricsDetail(uiState.look, actions)
            SettingsSection.SECTIONS -> SectionsDetail(uiState.look, actions)
            SettingsSection.CHORDS -> ChordsDetail(uiState.look, actions)
            SettingsSection.PEDAL -> PedalDetail(uiState, actions)
            SettingsSection.BAND -> BandDetail(actions.band, actions.bandActions)
            SettingsSection.SCREEN -> ScreenDetail(uiState.look, actions)
            SettingsSection.LANGUAGE -> LanguageDetail(uiState.language, actions)
            SettingsSection.PLAYER -> PlayerDetail(uiState.look, actions)
            SettingsSection.IMPORT -> ImportSitesDetail(uiState.look, actions)
            SettingsSection.LIBRARY -> LibraryDetail(uiState, actions)
            SettingsSection.ABOUT -> AboutDetail(uiState, actions)
        }
    }
}

@Composable
private fun LyricsDetail(look: Look, actions: SettingsActions) {
    // o tamanho muda ao vivo na prévia e só grava ao soltar o controle
    var font by remember(look.fontSp) { mutableFloatStateOf(look.fontSp) }
    SettingsCard(stringResource(R.string.settings_font_card)) {
        // controle em cima e prévia de altura fixa embaixo: mudar o tamanho não mexe na tela
        Text(stringResource(R.string.settings_font_value, font.roundToInt()), style = MaterialTheme.typography.headlineSmall)
        Slider(
            value = font,
            onValueChange = { font = it },
            onValueChangeFinished = { actions.onFont(font) },
            valueRange = Look.MIN_FONT..Look.MAX_FONT,
            steps = (Look.MAX_FONT - Look.MIN_FONT).toInt() - 1,
        )
        Surface(
            color = Color(look.bg),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().height(Sizes.fontPreview),
        ) {
            StagePreview(look, font, Modifier.padding(Spacing.m).clipToBounds())
        }
        HelpTip(stringResource(R.string.settings_font_help))
    }
    // virar página (padrão) ou rolar a letra; nos dois o pedal e o toque andam um passo
    SettingsCard(stringResource(R.string.settings_stage_mode)) {
        val modes = listOf(
            com.juliano.stagebook.data.model.StageMode.PAGES to R.string.settings_stage_mode_pages,
            com.juliano.stagebook.data.model.StageMode.SCROLL to R.string.settings_stage_mode_scroll,
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            modes.forEachIndexed { i, (m, label) ->
                SegmentedButton(
                    selected = look.stageMode == m,
                    onClick = { actions.onStageMode(m) },
                    shape = SegmentedButtonDefaults.itemShape(i, modes.size, baseShape = CircleShape),
                ) { Text(stringResource(label)) }
            }
        }
        HelpTip(stringResource(R.string.settings_stage_mode_help))
        if (look.stageMode == com.juliano.stagebook.data.model.StageMode.SCROLL) {
            var level by remember(look.autoScroll) { mutableFloatStateOf(look.autoScroll.toFloat()) }
            Text(stringResource(R.string.settings_auto_scroll, level.roundToInt()), style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = level,
                onValueChange = { level = it },
                onValueChangeFinished = { actions.onAutoScroll(level.roundToInt()) },
                valueRange = 1f..Look.MAX_AUTO_SCROLL.toFloat(),
                steps = Look.MAX_AUTO_SCROLL - 2,
            )
            HelpTip(stringResource(R.string.settings_auto_scroll_help))
        }
    }
    if (look.stageMode == com.juliano.stagebook.data.model.StageMode.PAGES) SettingsCard(stringResource(R.string.settings_columns)) {
        val options = listOf(
            StageColumns.AUTO to R.string.settings_columns_auto,
            StageColumns.ONE to R.string.settings_columns_one,
            StageColumns.TWO to R.string.settings_columns_two,
        )
        // pontas em pílula, como os botões do resto do app (Material 3 Expressive)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, (c, label) ->
                SegmentedButton(
                    selected = look.columns == c,
                    onClick = { actions.onColumns(c) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size, baseShape = CircleShape),
                ) { Text(stringResource(label)) }
            }
        }
        HelpTip(stringResource(R.string.settings_columns_help))
    }
    SettingsCard(stringResource(R.string.settings_colors)) {
        Text(stringResource(R.string.settings_text_color), style = MaterialTheme.typography.bodyLarge)
        ColorSwatchRow(StagePalette.text, look.fg, actions.onTextColor)
        Text(stringResource(R.string.settings_background), style = MaterialTheme.typography.bodyLarge)
        ColorSwatchRow(StagePalette.background, look.bg, actions.onBackground)
        SwitchRow(stringResource(R.string.settings_bold), look.bold, actions.onBold)
        // a fonte da letra no mesmo cartão (combo, escrita na própria fonte)
        FontPicker(look.font, actions.onFontFamily)
        LegibilityWarnings(look)
    }
}

/** Prévia do palco com a aparência escolhida (letra, seção e acordes), no tamanho [font]. */
@Composable
private fun StagePreview(look: Look, font: Float, modifier: Modifier = Modifier) {
    val style = TextStyle(
        color = Color(look.fg),
        fontSize = font.sp,
        lineHeight = (font * 1.25f).sp,
        fontWeight = if (look.bold) FontWeight.Bold else FontWeight.Normal,
        fontFamily = look.font.family,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )
    ChordText(
        lyricsAnnotated(
            stringResource(R.string.settings_preview_text),
            rememberSectionNames(look.translateSections),
            Color(look.sectionColor),
            look.showSections,
            ChordLook(look.chords, look.chordScale, Color(look.chordColor)),
            look.sectionScale,
        ),
        style = style,
        chordStyle = style.copy(color = Color(look.chordColor), fontSize = (font * look.chordScale).sp, fontWeight = FontWeight.Bold, fontFamily = look.chordFont.family, lineHeight = TextUnit.Unspecified),
        modifier = modifier,
    )
}

/**
 * Avisa quando uma cor do palco vai sumir no fundo (igual ou parecida demais, contraste abaixo de
 * [Contrast.LEGIBLE]): letra, títulos de seção e acordes (só se aparecem). Só avisa; a escolha é do usuário.
 */
@Composable
private fun LegibilityWarnings(look: Look) {
    val problems = buildList {
        if (!Contrast.legible(look.fg, look.bg)) add(R.string.settings_illegible_lyrics)
        if (look.showSections && !Contrast.legible(look.sectionColor, look.bg)) add(R.string.settings_illegible_sections)
        if (look.chords && !Contrast.legible(look.chordColor, look.bg)) add(R.string.settings_illegible_chords)
    }
    problems.forEach { msg ->
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Text(stringResource(msg), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ChordsDetail(look: Look, actions: SettingsActions) {
    // o tamanho muda ao vivo na prévia e só grava ao soltar o controle
    var scale by remember(look.chordScale) { mutableFloatStateOf(look.chordScale) }
    SettingsCard(stringResource(R.string.settings_chords)) {
        SwitchRow(stringResource(R.string.settings_chords_show), look.chords, actions.onChords)
        HelpTip(stringResource(R.string.settings_chords_show_help))
        if (look.chords) {
            Text(stringResource(R.string.settings_chords_size, (scale * 100).roundToInt()), style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = scale,
                onValueChange = { scale = it },
                onValueChangeFinished = { actions.onChordScale(scale) },
                valueRange = Look.MIN_CHORD_SCALE..Look.MAX_CHORD_SCALE,
                steps = ((Look.MAX_CHORD_SCALE - Look.MIN_CHORD_SCALE) * 10).roundToInt() - 1,
            )
            Text(stringResource(R.string.settings_chords_color), style = MaterialTheme.typography.bodyLarge)
            ColorSwatchRow(StagePalette.chord, look.chordColor, actions.onChordColor)
            FontPicker(look.chordFont, actions.onChordFont)
            // a prévia logo depois do que ela mostra (tamanho, cor, fonte)
            Surface(color = Color(look.bg), shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().height(Sizes.fontPreview)) {
                StagePreview(look.copy(chordScale = scale), minOf(look.fontSp, PREVIEW_FONT), Modifier.padding(Spacing.m).clipToBounds())
            }
            LegibilityWarnings(look)
        }
    }
    // os desenhos dos acordes num cartão próprio (só no perfil letra e cifra), com um exemplo do instrumento
    if (look.chords) {
        SettingsCard(stringResource(R.string.settings_chord_shapes)) {
            InstrumentPicker(look.instrument, actions.onInstrument)
            HelpTip(stringResource(R.string.settings_instrument_help))
            SwitchRow(stringResource(R.string.settings_chord_tap), look.chordTap, actions.onChordTap)
            Text(stringResource(R.string.settings_chord_list), style = MaterialTheme.typography.bodyLarge)
            val places = listOf(
                ChordListPlace.NONE to R.string.settings_chord_list_none,
                ChordListPlace.TOP to R.string.settings_chord_list_top,
                ChordListPlace.BOTTOM to R.string.settings_chord_list_bottom,
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                places.forEachIndexed { i, (p, label) ->
                    SegmentedButton(
                        selected = look.chordList == p,
                        onClick = { actions.onChordList(p) },
                        shape = SegmentedButtonDefaults.itemShape(i, places.size, baseShape = CircleShape),
                    ) { Text(stringResource(label)) }
                }
            }
            HelpTip(stringResource(R.string.settings_chord_list_help))
            // exemplo: o G no instrumento escolhido, como aparece no palco
            ChordDiagram(
                "G",
                look.instrument,
                remember(look.instrument) { Voicings.shapes("G", look.instrument, max = 1).firstOrNull() },
                remember { Voicings.keys("G").firstOrNull() },
                MaterialTheme.colorScheme.onSurface,
                Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

/** O instrumento dos desenhos de acorde, num combo (violão e guitarra mostram os mesmos). */
@Composable
private fun InstrumentPicker(selected: Instrument, onSelect: (Instrument) -> Unit) {
    val names = mapOf(
        Instrument.VIOLAO to R.string.instrument_violao,
        Instrument.GUITARRA to R.string.instrument_guitarra,
        Instrument.UKULELE to R.string.instrument_ukulele,
        Instrument.CAVAQUINHO to R.string.instrument_cavaquinho,
        Instrument.TECLADO to R.string.instrument_teclado,
    )
    OptionDropdown(stringResource(R.string.settings_instrument), Instrument.entries, selected, onSelect, nameOf = { stringResource(names.getValue(it)) })
}

/** A fonte do palco num combo: o campo e cada opção escritos na própria fonte. */
@Composable
private fun FontPicker(selected: StageFont, onSelect: (StageFont) -> Unit) {
    val names = mapOf(
        StageFont.DEFAULT to R.string.font_default,
        StageFont.CONDENSED to R.string.font_condensed,
        StageFont.SERIF to R.string.font_serif,
        StageFont.MONO to R.string.font_mono,
    )
    OptionDropdown(
        stringResource(R.string.settings_font_family),
        StageFont.entries,
        selected,
        onSelect,
        nameOf = { stringResource(names.getValue(it)) },
        styleOf = { MaterialTheme.typography.titleMedium.copy(fontFamily = it.family) },
    )
}

/** A prévia dos acordes não passa deste tamanho (a letra grande não caberia na caixa). */
private const val PREVIEW_FONT = 30f

@Composable
private fun SectionsDetail(look: Look, actions: SettingsActions) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    SettingsCard(stringResource(R.string.settings_sections_look)) {
        HelpTip(stringResource(R.string.settings_sections_help))
        Text(stringResource(R.string.settings_section_color), style = MaterialTheme.typography.bodyLarge)
        ColorSwatchRow(StagePalette.section, look.sectionColor, actions.onSectionColor)
        SwitchRow(stringResource(R.string.settings_show_sections), look.showSections, actions.onShowSections)
        if (look.showSections) {
            // o tamanho muda ao vivo na prévia e só grava ao soltar o controle (como o dos acordes)
            var scale by remember(look.sectionScale) { mutableFloatStateOf(look.sectionScale) }
            Text(stringResource(R.string.settings_sections_size, (scale * 100).roundToInt()), style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = scale,
                onValueChange = { scale = it },
                onValueChangeFinished = { actions.onSectionScale(scale) },
                valueRange = Look.MIN_SECTION_SCALE..Look.MAX_SECTION_SCALE,
                steps = ((Look.MAX_SECTION_SCALE - Look.MIN_SECTION_SCALE) * 10).roundToInt() - 1,
            )
            Surface(color = Color(look.bg), shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().height(Sizes.fontPreview)) {
                StagePreview(look.copy(sectionScale = scale), minOf(look.fontSp, PREVIEW_FONT), Modifier.padding(Spacing.m).clipToBounds())
            }
        }
        SwitchRow(stringResource(R.string.settings_translate_sections), look.translateSections, actions.onTranslateSections)
        HelpTip(stringResource(R.string.settings_translate_sections_help))
        LegibilityWarnings(look)
    }
    SettingsCard(stringResource(R.string.settings_normalize_card)) {
        HelpTip(stringResource(R.string.settings_normalize_help))
        ActionButton(stringResource(R.string.settings_normalize), { confirming = true }, tone = ButtonTone.Tonal)
    }
    if (confirming) {
        ConfirmDialog(
            message = stringResource(R.string.settings_normalize_confirm),
            confirmLabel = stringResource(R.string.settings_normalize_ok),
            onConfirm = actions.onNormalize,
            onDismiss = { confirming = false },
            destructive = false,
        )
    }
}

@Composable
private fun PedalDetail(uiState: SettingsUiState, actions: SettingsActions) {
    val name = uiState.look.pedalName
    SettingsCard(stringResource(R.string.settings_pedal_yours)) {
        if (name.isEmpty()) {
            Text(stringResource(R.string.settings_pedal_none), style = MaterialTheme.typography.bodyLarge)
        } else {
            Text(name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                stringResource(if (uiState.pedalConnected) R.string.settings_pedal_connected else R.string.settings_pedal_disconnected),
                style = MaterialTheme.typography.bodyLarge,
                color = if (uiState.pedalConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
        ActionButton(stringResource(R.string.settings_pair_pedal), actions.onPairPedal, icon = AppIcons.Bluetooth, tone = ButtonTone.Tonal)
    }
    SettingsCard(stringResource(R.string.settings_pedal_test_card)) {
        val key = uiState.lastKey
        // como no another app: o botão apertado por último pode ser ligado a outro comando do palco
        if (key != null) PedalActionPicker(key.action, { actions.onPedalAction(key.keyCode, it) })
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    if (key == null) stringResource(R.string.settings_pedal_test)
                    else stringResource(
                        R.string.settings_pedal_last,
                        pedalKeyName(key),
                        stringResource(
                            when (key.action) {
                                PedalAction.NEXT -> R.string.settings_pedal_next
                                PedalAction.PREVIOUS -> R.string.settings_pedal_previous
                                PedalAction.NEXT_SONG -> R.string.settings_pedal_next_song
                                PedalAction.PREVIOUS_SONG -> R.string.settings_pedal_previous_song
                                PedalAction.PLAY_PAUSE -> R.string.settings_pedal_play_pause
                                PedalAction.NONE -> R.string.settings_pedal_unused
                            },
                        ),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (key == null || key.action == PedalAction.NONE) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                )
                if (key != null) {
                    Text(
                        if (key.device.isEmpty()) stringResource(R.string.settings_pedal_from_device_builtin) else stringResource(R.string.settings_pedal_from_device, key.device),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    PedalKeysCard(uiState.look.pedalKeys, actions)
    MidiCard(uiState.midi, uiState.look, actions)
}

@Composable
// o mesmo nome no "Último botão" e na lista de botões configurados
private fun pedalKeyName(key: PedalKeyUi): String = if (key.keyCode != 0) pedalKeyName(key.keyCode) else key.name

/** Nome do botão: a tecla como o Android chama (PAGE_DOWN) ou a mensagem MIDI. */
@Composable
private fun pedalKeyName(code: Int): String {
    if (!MidiCode.isMidi(code)) {
        // tecla sem nome conhecido pelo Android: vem só o número
        val name = KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
        return name.toIntOrNull()?.let { stringResource(R.string.pedal_key_code, it) } ?: name
    }
    val n = MidiCode.number(code)
    val ch = MidiCode.channel(code)
    return when (MidiCode.kind(code)) {
        MidiCode.Kind.CC -> stringResource(R.string.midi_cc, n, ch)
        MidiCode.Kind.NOTE -> stringResource(R.string.midi_note, n, ch)
        MidiCode.Kind.PROGRAM -> stringResource(R.string.midi_program, n, ch)
    }
}

@Composable
private fun MidiCard(midi: MidiState, look: Look, actions: SettingsActions) {
    SettingsCard(stringResource(R.string.settings_midi)) {
        if (!midi.supported) {
            Text(stringResource(R.string.settings_midi_unsupported), style = MaterialTheme.typography.bodyLarge)
            return@SettingsCard
        }
        SwitchRow(stringResource(R.string.settings_midi_usb_switch), look.midiUsb, actions.onMidiUsb)
        HelpTip(stringResource(R.string.settings_midi_usb_help), label = stringResource(R.string.settings_midi_usb_about))
        if (midi.connected.isEmpty()) {
            Text(stringResource(R.string.settings_midi_none), style = MaterialTheme.typography.bodyLarge)
        } else {
            midi.connected.forEach { d ->
                Text(
                    stringResource(R.string.settings_midi_connected, d.name, stringResource(if (d.bluetooth) R.string.settings_midi_bt else R.string.settings_midi_usb)),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (look.midiBtAddress.isNotEmpty()) {
            val on = midi.connected.any { it.bluetooth }
            Text(stringResource(R.string.settings_midi_saved, look.midiBtName), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (!on) Text(stringResource(R.string.settings_midi_waiting), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = actions.onMidiForget) { Text(stringResource(R.string.settings_midi_forget)) }
        }
        ActionButton(
            stringResource(if (midi.scanning) R.string.settings_midi_scanning else R.string.settings_midi_scan),
            actions.onMidiScan,
            icon = AppIcons.Bluetooth,
            tone = ButtonTone.Tonal,
            enabled = !midi.scanning,
        )
        if (midi.scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
        midi.found.forEach { f ->
            SongListItem(title = f.name, subtitle = stringResource(R.string.settings_midi_choose), onClick = { actions.onMidiChoose(f) })
        }
        when {
            midi.bluetoothOff -> Text(stringResource(R.string.settings_midi_bt_off), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            midi.searched && !midi.scanning && midi.found.isEmpty() ->
                Text(stringResource(R.string.settings_midi_found_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        HelpTip(stringResource(R.string.settings_midi_help), label = stringResource(R.string.settings_midi_after))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Text(stringResource(R.string.settings_midi_location), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun pedalActionLabel(a: PedalAction): String = stringResource(
    when (a) {
        PedalAction.NEXT -> R.string.settings_pedal_cmd_next
        PedalAction.PREVIOUS -> R.string.settings_pedal_cmd_previous
        PedalAction.NEXT_SONG -> R.string.settings_pedal_cmd_next_song
        PedalAction.PREVIOUS_SONG -> R.string.settings_pedal_cmd_previous_song
        PedalAction.PLAY_PAUSE -> R.string.settings_pedal_cmd_play_pause
        PedalAction.NONE -> R.string.settings_pedal_cmd_none
    },
)

/** Os botões ligados a outro comando, sempre à vista: tocar troca o comando, ✕ volta aquele botão ao padrão. */
@Composable
private fun PedalKeysCard(keys: Map<Int, PedalAction>, actions: SettingsActions) {
    SettingsCard(stringResource(R.string.settings_pedal_keys)) {
        if (keys.isEmpty()) {
            Text(stringResource(R.string.settings_pedal_keys_none), style = MaterialTheme.typography.bodyLarge)
            return@SettingsCard
        }
        keys.forEach { (code, action) ->
            PedalKeyRow(pedalKeyName(code), action, { actions.onPedalAction(code, it) }, { actions.onPedalAction(code, PedalAction.default(code)) })
        }
        TextButton(onClick = actions.onResetPedalKeys) {
            Text(pluralStringResource(R.plurals.settings_pedal_reset, keys.size, keys.size))
        }
    }
}

@Composable
private fun PedalKeyRow(name: String, action: PedalAction, onSelect: (PedalAction) -> Unit, onRemove: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { open = true }.padding(start = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            // nome em cima, comando embaixo: o nome do MIDI é longo e quebrava a linha no celular
            Column(Modifier.weight(1f).padding(vertical = Spacing.xs)) {
                Text(name, style = MaterialTheme.typography.bodyLarge)
                Text(pedalActionLabel(action), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onRemove) { Icon(AppIcons.Close, contentDescription = stringResource(R.string.settings_pedal_key_reset, name)) }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            PedalAction.entries.forEach { a ->
                DropdownMenuItem(text = { Text(pedalActionLabel(a)) }, onClick = { onSelect(a); open = false })
            }
        }
    }
}

/** Escolha do comando do botão (combo, cabe também no celular). */
@Composable
private fun PedalActionPicker(selected: PedalAction, onSelect: (PedalAction) -> Unit) =
    OptionDropdown(stringResource(R.string.settings_pedal_command), PedalAction.entries, selected, onSelect, nameOf = { pedalActionLabel(it) })

/** Sites de importação: o primeiro de cada lista é o padrão (onde o importador abre); dá para tirar e acrescentar. */
@Composable
private fun ImportSitesDetail(look: Look, actions: SettingsActions) {
    SiteListCard(stringResource(R.string.settings_lyric_sites), look.lyricSites) { actions.onSites(false, it) }
    // cifra só no perfil letra e cifra
    if (look.chords) SiteListCard(stringResource(R.string.settings_chord_sites), look.chordSites) { actions.onSites(true, it) }
}

@Composable
private fun SiteListCard(title: String, sites: List<ImportSite>, onChange: (List<ImportSite>) -> Unit) {
    var adding by rememberSaveable { mutableStateOf(false) }
    SettingsCard(title) {
        sites.forEachIndexed { i, site ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                // o padrão é o primeiro: escolher outro o põe no topo
                RadioButton(selected = i == 0, onClick = { onChange(listOf(site) + (sites - site)) })
                Column(Modifier.weight(1f)) {
                    Text(site.name, style = MaterialTheme.typography.bodyLarge)
                    Text(site.url.removePrefix("https://").removePrefix("www.").trimEnd('/'), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (sites.size > 1) {
                    IconButton(onClick = { onChange(sites - site) }) { Icon(AppIcons.Close, stringResource(R.string.settings_site_remove, site.name)) }
                }
            }
        }
        HelpTip(stringResource(R.string.settings_sites_help))
        ActionButton(stringResource(R.string.settings_site_add), { adding = true }, icon = AppIcons.Add, tone = ButtonTone.Tonal)
    }
    if (adding) {
        SiteDialog(onDone = { s -> adding = false; if (sites.none { it.url == s.url }) onChange(sites + s) }, onDismiss = { adding = false })
    }
}

/** Acrescentar um site: endereço (o nome sai do domínio se ficar em branco). */
@Composable
private fun SiteDialog(onDone: (ImportSite) -> Unit, onDismiss: () -> Unit) {
    var address by rememberSaveable { mutableStateOf("") }
    val url = ImportSiteRules.normalize(address)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_site_add)) },
        text = {
            OutlinedTextField(
                value = address,
                onValueChange = { address = it.trim() },
                singleLine = true,
                placeholder = { Text("www.site.com") },
                isError = address.isNotEmpty() && url == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                shape = MaterialTheme.shapes.small,
            )
        },
        confirmButton = { TextButton(onClick = { url?.let { onDone(ImportSite(ImportSiteRules.nameOf(it), it)) } }, enabled = url != null) { Text(stringResource(R.string.settings_site_add_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Modo player: desligado, o app é só letra e cifra e nada do player gasta CPU ou memória. */
@Composable
private fun PlayerDetail(look: Look, actions: SettingsActions) {
    SettingsCard(stringResource(R.string.settings_player)) {
        SwitchRow(stringResource(R.string.settings_player_mode), look.playerMode, actions.onPlayerMode)
        HelpTip(stringResource(R.string.settings_player_mode_help))
        UsbRequirements()
    }
    if (look.playerMode) {
        MixerCard(look.mixer, actions)
        EqCard(look.mixer, actions)
    }
}

@Composable
private fun eqLabel(hz: Int) =
    if (hz < 1000) stringResource(R.string.eq_hz, hz) else stringResource(R.string.eq_khz, (hz / 1000f).toString().removeSuffix(".0"))

// requisitos da saída USB: escondidos, no mesmo balão das outras explicações
@Composable
private fun UsbRequirements() = HelpTip(
    stringResource(R.string.settings_player_requirements_text),
    label = stringResource(R.string.settings_player_requirements),
)

@Composable
private fun MixerCard(mixer: MixerConfig, actions: SettingsActions) {
    val onMixer = actions.onMixer
    val levels = actions.mixerLevels
    var editing by rememberSaveable { mutableIntStateOf(-1) }   // canal em edição (o primeiro do par); -1 = nenhum
    SettingsCard(stringResource(R.string.mixer_title)) {
        HelpTip(stringResource(R.string.mixer_help))
        OptionDropdown(
            stringResource(R.string.mixer_channels), (1..MixerConfig.MAX).toList(), mixer.channels,
            { onMixer(mixer.copy(channels = it)) }, { it.toString() },
        )
        SwitchRow(stringResource(R.string.mixer_monitor), actions.monitoring, actions.onMonitoring)
        // de qual saída é o roteamento mostrado (a em uso ou a última usada)
        Text(
            pluralStringResource(R.plurals.mixer_now_output, mixer.output.count, mixer.output.label(), mixer.output.count),
            style = MaterialTheme.typography.titleSmall,
        )
        HelpTip(stringResource(R.string.mixer_routes_help))
        val groups = mixer.groups()
        // celular: um canal por linha, fader deitado; tablet: faixas estreitas lado a lado, fader de pé (como numa mesa)
        BoxWithConstraints {
            val upright = maxWidth >= 600.dp
            val columns = if (upright) (maxWidth / 150.dp).toInt().coerceIn(4, MixerConfig.MAX) else 1
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                groups.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        row.forEach { g ->
                            MixerStrip(
                                first = g.first,
                                strip = mixer.strip(g.first),
                                pair = g.count() == 2,
                                onChange = { s -> onMixer(mixer.withStrip(g.first) { s }) },
                                onEdit = { editing = g.first },
                                output = mixer.outputOf(g.first),
                                outputs = mixer.output,
                                meters = { levels().meters },
                                showMeters = actions.monitoring,
                                modifier = Modifier.weight(1f),
                                upright = upright,
                            )
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        groups.firstOrNull { it.first == editing }?.let { g ->
            MixerChannelEditor(
                first = g.first,
                strip = mixer.strip(g.first),
                pair = g.count() == 2,
                canPair = g.first % 2 == 0 && g.first + 1 < mixer.channels,
                output = mixer.outputOf(g.first),
                outputs = mixer.output,
                onChange = { s -> onMixer(mixer.withStrip(g.first) { s }) },
                onOutput = { o -> onMixer(mixer.withOutput(g.first, o)) },
                onDismiss = { editing = -1 },
            )
        }
    }
}

@Composable
private fun EqCard(mixer: MixerConfig, actions: SettingsActions) {
    val onMixer = actions.onMixer
    val levels = actions.mixerLevels
    SettingsCard(stringResource(R.string.eq_title)) {
        SwitchRow(stringResource(R.string.eq_on), mixer.eqOn) { onMixer(mixer.copy(eqOn = it)) }
        HelpTip(stringResource(R.string.eq_help))
        val setBand = { i: Int, v: Float ->
            onMixer(mixer.copy(eqDb = List(MixerConfig.EQ_BANDS) { k -> if (k == i) (v * 2).roundToInt() / 2f else mixer.eqDb.getOrElse(k) { 0f } }))
        }
        val range = -MixerConfig.EQ_RANGE_DB..MixerConfig.EQ_RANGE_DB
        // faders de pé lado a lado (celular e tablet), como um equalizador gráfico, com o espectro ao vivo por trás (já
        // com a curva do EQ somada: o motor mede antes do EQ). Cada fader no centro da sua quinta parte.
        val response = remember(mixer.eqDb, mixer.eqOn) {
            if (mixer.eqOn) Spectrum.eqResponseDb(mixer.eqDb) else FloatArray(Spectrum.BINS)
        }
        Box(Modifier.fillMaxWidth()) {
            SpectrumView(
                { levels().spectrumDb.mapIndexed { b, v -> if (v <= Spectrum.FLOOR_DB) v else v + response[b] } },
                active = { actions.monitoring && levels().live },
                modifier = Modifier.matchParentSize().padding(vertical = Spacing.l),
            )
            Row(Modifier.fillMaxWidth()) {
                MixerConfig.EQ_FREQ.forEachIndexed { i, hz ->
                    val db = mixer.eqDb.getOrElse(i) { 0f }
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Text(stringResource(R.string.mixer_db, db), style = MaterialTheme.typography.labelLarge)
                        VerticalFader(db, { setBand(i, it) }, valueRange = range, enabled = mixer.eqOn)
                        Text(eqLabel(hz), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        ActionButton(stringResource(R.string.eq_reset), onClick = { onMixer(mixer.copy(eqDb = List(MixerConfig.EQ_BANDS) { 0f })) },
            icon = AppIcons.Undo, tone = ButtonTone.Tonal, enabled = mixer.eqOn)
    }
}

@Composable
private fun languageLabel(l: AppLanguage): String = stringResource(
    when (l) {
        AppLanguage.SYSTEM -> R.string.language_system
        AppLanguage.PT -> R.string.language_pt
        AppLanguage.ES -> R.string.language_es
        AppLanguage.IT -> R.string.language_it
        AppLanguage.EN -> R.string.language_en
    },
)

@Composable
private fun LanguageDetail(selected: AppLanguage, actions: SettingsActions) {
    SettingsCard(stringResource(R.string.settings_language)) {
        OptionDropdown(stringResource(R.string.settings_language), Languages.available, selected, actions.onLanguage, nameOf = { languageLabel(it) })
        HelpTip(stringResource(R.string.settings_language_help))
    }
}

@Composable
private fun ScreenDetail(look: Look, actions: SettingsActions) {
    SettingsCard(stringResource(R.string.settings_screen)) {
        SwitchRow(stringResource(R.string.settings_keep_screen_on), look.keepScreenOn, actions.onKeepScreenOn)
        HelpTip(stringResource(R.string.settings_keep_screen_on_help))
    }
}

@Composable
private fun LibraryDetail(uiState: SettingsUiState, actions: SettingsActions) {
    SettingsCard(stringResource(R.string.settings_library)) {
        Text(
            pluralStringResource(R.plurals.song_count, uiState.songCount, uiState.songCount) + " · " +
                pluralStringResource(R.plurals.set_count, uiState.setCount, uiState.setCount),
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            ActionButton(stringResource(R.string.settings_import_file), actions.onImport, Modifier.weight(1f), icon = AppIcons.Upload, tone = ButtonTone.Tonal)
            ActionButton(stringResource(R.string.settings_export_file), actions.onExport, Modifier.weight(1f), icon = AppIcons.Download, tone = ButtonTone.Tonal)
        }
        HelpTip(stringResource(R.string.settings_import_help))
    }
    // backup completo num arquivo .stagebook (músicas, faixas e ajustes); restaurar escolhe as partes
    // as caixas ficam no próprio cartão, todas marcadas (pedido do usuário, 29/10); sai um arquivo só com o que estiver marcado
    SettingsCard(stringResource(R.string.backup_card)) {
        var chosen by rememberSaveable { mutableStateOf(com.juliano.stagebook.data.Backup.Part.entries.map { it.name }.toSet()) }
        val parts = com.juliano.stagebook.data.Backup.Part.entries.filter { it.name in chosen }.toSet()
        Text(stringResource(R.string.backup_what), style = MaterialTheme.typography.bodyLarge)
        BackupPartsChecks(com.juliano.stagebook.data.Backup.Part.entries.toSet(), parts) { part, on ->
            chosen = if (on) chosen + part.name else chosen - part.name
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            ActionButton(stringResource(R.string.backup_make), { actions.onBackup(parts) }, Modifier.weight(1f), icon = AppIcons.Download, enabled = parts.isNotEmpty())
            ActionButton(stringResource(R.string.backup_restore), actions.onRestore, Modifier.weight(1f), icon = AppIcons.Upload, tone = ButtonTone.Tonal)
        }
        HelpTip(stringResource(R.string.backup_help))
    }
}

@Composable
private fun UpdateDetail(uiState: SettingsUiState, actions: SettingsActions) {
    val up = uiState.update
    SettingsCard(stringResource(R.string.settings_update)) {
        Text(stringResource(R.string.settings_update_installed, uiState.version), style = MaterialTheme.typography.bodyLarge)
        val status = when (up) {
            UpdateState.UpToDate -> stringResource(R.string.settings_update_latest)
            is UpdateState.Available -> stringResource(R.string.settings_update_available, up.versionName)
            is UpdateState.Downloading -> stringResource(R.string.settings_update_downloading, up.percent)
            UpdateState.Ready -> stringResource(R.string.settings_update_ready)
            UpdateState.Offline -> stringResource(R.string.settings_update_offline)
            UpdateState.DownloadFailed -> stringResource(R.string.settings_update_failed)
            UpdateState.Idle, UpdateState.Checking -> null
        }
        status?.let {
            val error = up == UpdateState.Offline || up == UpdateState.DownloadFailed
            Text(it, style = MaterialTheme.typography.bodyLarge, color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }
        if (up is UpdateState.Downloading) {
            LinearProgressIndicator(progress = { up.percent / 100f }, modifier = Modifier.fillMaxWidth())
        }
        when (up) {
            is UpdateState.Available, UpdateState.DownloadFailed ->
                ActionButton(stringResource(R.string.settings_update_download), actions.onDownloadUpdate, Modifier.fillMaxWidth(), icon = AppIcons.Download)
            UpdateState.Ready ->
                ActionButton(stringResource(R.string.settings_update_install), actions.onInstallUpdate, Modifier.fillMaxWidth(), icon = AppIcons.Download)
            is UpdateState.Downloading -> Unit
            else -> ActionButton(
                stringResource(if (up == UpdateState.Checking) R.string.settings_update_checking else R.string.settings_update_check),
                actions.onCheckUpdate,
                Modifier.fillMaxWidth(),
                enabled = up != UpdateState.Checking,
                tone = ButtonTone.Tonal,
            )
        }
        HelpTip(stringResource(R.string.settings_update_help))
    }
}

// Sobre: o app, a atualização (procurar agora), privacidade em poucas linhas, o código aberto e o apoio (doação)
@Composable
private fun AboutDetail(uiState: SettingsUiState, actions: SettingsActions) {
    SettingsCard(stringResource(R.string.app_name)) {
        Text(stringResource(R.string.about_tagline), style = MaterialTheme.typography.bodyLarge)
    }
    UpdateDetail(uiState, actions)
    SettingsCard(stringResource(R.string.about_privacy)) {
        Text(stringResource(R.string.about_privacy_text), style = MaterialTheme.typography.bodyMedium)
    }
    SettingsCard(stringResource(R.string.about_open_source)) {
        Text(stringResource(R.string.about_open_source_text), style = MaterialTheme.typography.bodyMedium)
        ActionButton(stringResource(R.string.about_see_code), { actions.onOpenLink(AppLinks.REPOSITORY) }, icon = AppIcons.Public, tone = ButtonTone.Tonal)
    }
    SettingsCard(stringResource(R.string.about_support)) {
        Text(stringResource(R.string.about_support_text), style = MaterialTheme.typography.bodyMedium)
        // o botão só aparece com o link de doação preenchido (AppLinks.DONATE)
        if (AppLinks.DONATE.startsWith("https://")) {
            ActionButton(stringResource(R.string.about_donate), { actions.onOpenLink(AppLinks.DONATE) }, icon = AppIcons.Favorite)
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private val previewState = SettingsUiState(
    loading = false,
    look = Look(pedalName = "PageFlip Firefly"),
    songCount = 42,
    setCount = 3,
    lastKey = PedalKeyUi("PAGE_DOWN", PedalAction.NEXT, "PageFlip Firefly"),
    version = "0.2",
    pedalConnected = true,
)

@LightPreview
@Composable
private fun SettingsLightPreview() = StagebookTheme(darkTheme = false) { SettingsScreen(previewState, SettingsActions()) }

@DarkPreview
@Composable
private fun SettingsDarkPreview() = StagebookTheme(darkTheme = true) { SettingsScreen(previewState, SettingsActions(), section = SettingsSection.PEDAL) }
