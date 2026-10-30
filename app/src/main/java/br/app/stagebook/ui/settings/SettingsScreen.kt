package br.app.stagebook.ui.settings

import br.app.stagebook.data.model.essential
import androidx.core.net.toUri
import br.app.stagebook.data.AppLinks
import androidx.compose.runtime.DisposableEffect
import br.app.stagebook.ui.band.BandViewModel
import br.app.stagebook.ui.band.BandUi
import br.app.stagebook.ui.band.BandDetail
import br.app.stagebook.ui.band.BandActions
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import br.app.stagebook.ui.components.SpectrumView
import br.app.stagebook.player.StagePlayer
import br.app.stagebook.domain.Spectrum
import br.app.stagebook.domain.MixerLevels
import br.app.stagebook.ui.components.VerticalFader
import androidx.compose.foundation.layout.size
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn
import br.app.stagebook.ui.components.MixerStrip
import br.app.stagebook.ui.components.HelpTip
import br.app.stagebook.ui.components.MixerChannelEditor
import br.app.stagebook.ui.components.label
import br.app.stagebook.domain.MixerConfig
import br.app.stagebook.ui.components.OptionDropdown
import br.app.stagebook.domain.Voicings
import br.app.stagebook.ui.components.ChordDiagram
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import br.app.stagebook.domain.ImportSiteRules
import br.app.stagebook.data.model.ImportSite
import br.app.stagebook.data.model.ChordListPlace
import br.app.stagebook.data.model.Instrument
import br.app.stagebook.ui.stage.family
import br.app.stagebook.data.model.StageFont
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.style.LineHeightStyle
import br.app.stagebook.ui.stage.ChordText
import br.app.stagebook.domain.Contrast
import br.app.stagebook.data.Languages
import br.app.stagebook.data.AppLanguage
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import android.app.Activity
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import br.app.stagebook.domain.MidiCode
import br.app.stagebook.data.MidiState
import br.app.stagebook.data.MidiFound
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
import br.app.stagebook.R
import br.app.stagebook.ui.installApk
import br.app.stagebook.data.model.Look
import br.app.stagebook.data.model.SectionLang
import br.app.stagebook.data.model.StageColumns
import br.app.stagebook.domain.PedalAction
import br.app.stagebook.ui.PedalHandler
import br.app.stagebook.ui.components.ActionButton
import br.app.stagebook.ui.components.SwitchRow
import br.app.stagebook.ui.components.AppIcons
import br.app.stagebook.ui.components.ButtonTone
import br.app.stagebook.ui.components.CenteredContent
import br.app.stagebook.ui.components.ColorSwatchRow
import br.app.stagebook.ui.components.ConfirmDialog
import br.app.stagebook.ui.components.DarkPreview
import br.app.stagebook.ui.components.LightPreview
import br.app.stagebook.ui.components.ScreenHeader
import br.app.stagebook.ui.components.SettingsCard
import br.app.stagebook.ui.components.SongListItem
import br.app.stagebook.ui.stage.ChordLook
import br.app.stagebook.ui.stage.lyricsAnnotated
import br.app.stagebook.ui.stage.rememberSectionNames
import br.app.stagebook.ui.theme.StagebookTheme
import br.app.stagebook.ui.theme.Sizes
import br.app.stagebook.ui.theme.Spacing
import br.app.stagebook.ui.theme.StagePalette
import kotlin.math.roundToInt

@Composable
fun SettingsRoute(start: SettingsSection? = null, viewModel: SettingsViewModel = hiltViewModel()) {   // start: seção aberta ao entrar (link do aviso)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    // backup: escolhe as partes (todas marcadas), depois onde salvar o arquivo .stagebook
    var backupParts by remember { mutableStateOf(br.app.stagebook.data.Backup.Part.entries.toSet()) }
    val backupSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(br.app.stagebook.data.Backup.MIME)) { uri ->
        uri?.let { viewModel.backupTo(it, backupParts) }
    }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::inspectBackup) }
    val pendingRestore by viewModel.pendingRestore.collectAsStateWithLifecycle()
    pendingRestore?.let { p ->
        BackupPartsDialog(
            title = stringResource(R.string.backup_open),
            confirmLabel = stringResource(R.string.backup_open_ok),
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
    var section by rememberSaveable { mutableStateOf(start) }
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
                onChordPlay = viewModel::setChordPlay,
                onAnnounce = viewModel::setAnnounce,
                onEssential = viewModel::setEssential,
                onBandOn = viewModel::setBandOn,
                onInternetOn = viewModel::setInternetOn,
                onExtrasOn = viewModel::setExtrasOn,
                onBackup = { parts ->
                    backupParts = parts
                    backupSaver.launch("stagebook-backup-${java.time.LocalDate.now()}.${br.app.stagebook.data.Backup.EXTENSION}")
                },
                onRestore = { restorePicker.launch(arrayOf("*/*")) },
                onFactoryReset = viewModel::factoryReset,
                onMetronome = viewModel::setMetronome,
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
            stringResource(R.string.backup_open_ok),
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
    val onStageMode: (br.app.stagebook.data.model.StageMode) -> Unit = {},
    val onAutoScroll: (Int) -> Unit = {},
    val onChordPlay: (br.app.stagebook.data.model.ChordPlayMode) -> Unit = {},
    val onAnnounce: (Boolean) -> Unit = {},
    val onEssential: (Boolean) -> Unit = {},
    val onBandOn: (Boolean) -> Unit = {},
    val onInternetOn: (Boolean) -> Unit = {},
    val onExtrasOn: (Boolean) -> Unit = {},
    val onBackup: (Set<br.app.stagebook.data.Backup.Part>) -> Unit = {},
    val onRestore: () -> Unit = {},
    val onFactoryReset: () -> Unit = {},
    val onMetronome: (count: Boolean, sound: Boolean, glow: Boolean, glowColor: Int) -> Unit = { _, _, _, _ -> },
    val onCheckUpdate: () -> Unit = {},
    val onOpenLink: (String) -> Unit = {},
    val onDownloadUpdate: () -> Unit = {},
    val onInstallUpdate: () -> Unit = {},
)

/** Seções dos ajustes: lista à esquerda no tablet, uma tela por seção no celular. */
enum class SettingsSection(@StringRes val title: Int, val icon: ImageVector) {
    ESSENTIAL(R.string.essential_title, AppIcons.Eco),   // primeiro: muda o app inteiro
    LYRICS(R.string.settings_appearance, AppIcons.FormatSize),
    CHORDS(R.string.settings_chords, AppIcons.MusicNote),
    PLAYER(R.string.settings_player, AppIcons.Play),
    SECTIONS(R.string.settings_sections, AppIcons.ListBulleted),
    METRONOME(R.string.metro_title, AppIcons.Metronome),
    PEDAL(R.string.settings_pedal, AppIcons.Bluetooth),
    BAND(R.string.settings_band, AppIcons.Group),
    SCREEN(R.string.settings_screen, AppIcons.Brightness),
    LANGUAGE(R.string.settings_language, AppIcons.Translate),
    IMPORT(R.string.settings_import_sites, AppIcons.Public),
    LIBRARY(R.string.settings_library, AppIcons.Folder),
    ABOUT(R.string.settings_about, AppIcons.Info),   // sempre por último: o app, atualizar, privacidade, código, apoio
}

/** Modo essencial (T19): a seção do que está desligado some da lista. */
fun SettingsSection.shown(look: Look): Boolean = when (this) {
    SettingsSection.METRONOME -> look.extrasOn
    SettingsSection.PLAYER -> look.playerMode
    SettingsSection.BAND -> look.bandOn || look.internetOn   // a banda vale também pela internet (os mesmos membros)
    else -> true
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
                SettingsSection.entries.filter { it.shown(uiState.look) }.forEach { s ->
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
    SettingsSection.ESSENTIAL -> stringResource(if (ui.look.essential) R.string.essential_on_short else R.string.essential_off_short)
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
    SettingsSection.METRONOME -> listOfNotNull(
        stringResource(R.string.metro_short_count).takeIf { ui.look.metroCount },
        stringResource(if (ui.look.metroSound) R.string.metro_short_sound else R.string.metro_short_silent),
        stringResource(R.string.metro_short_glow).takeIf { ui.look.metroGlow },
    ).joinToString(" · ")
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
            SettingsSection.ESSENTIAL -> EssentialDetail(uiState.look, actions)
            SettingsSection.LYRICS -> LyricsDetail(uiState.look, actions)
            SettingsSection.SECTIONS -> SectionsDetail(uiState.look, actions)
            SettingsSection.CHORDS -> ChordsDetail(uiState.look, actions)
            SettingsSection.PEDAL -> PedalDetail(uiState, actions)
            SettingsSection.BAND -> BandDetail(actions.band, actions.bandActions, lan = uiState.look.bandOn)
            SettingsSection.SCREEN -> ScreenDetail(uiState.look, actions)
            SettingsSection.METRONOME -> MetronomeDetail(uiState.look, actions)
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
    SettingsCard(stringResource(R.string.settings_font_card), help = stringResource(R.string.settings_font_help)) {
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
    }
    // virar página (padrão) ou rolar a letra; nos dois o pedal e o toque andam um passo
    SettingsCard(stringResource(R.string.settings_stage_mode), help = stringResource(R.string.settings_stage_mode_help) + "\n\n" + stringResource(R.string.settings_auto_scroll_help)) {
        val modes = listOf(
            br.app.stagebook.data.model.StageMode.PAGES to R.string.settings_stage_mode_pages,
            br.app.stagebook.data.model.StageMode.SCROLL to R.string.settings_stage_mode_scroll,
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
        if (look.stageMode == br.app.stagebook.data.model.StageMode.SCROLL) {
            var level by remember(look.autoScroll) { mutableFloatStateOf(look.autoScroll.toFloat()) }
            Text(stringResource(R.string.settings_auto_scroll, level.roundToInt()), style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = level,
                onValueChange = { level = it },
                onValueChangeFinished = { actions.onAutoScroll(level.roundToInt()) },
                valueRange = 1f..Look.MAX_AUTO_SCROLL.toFloat(),
                steps = Look.MAX_AUTO_SCROLL - 2,
            )
        }
    }
    if (look.stageMode == br.app.stagebook.data.model.StageMode.PAGES) SettingsCard(stringResource(R.string.settings_columns), help = stringResource(R.string.settings_columns_help)) {
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
    SettingsCard(stringResource(R.string.settings_chords), help = stringResource(R.string.settings_chords_show_help)) {
        SwitchRow(stringResource(R.string.settings_chords_show), look.chords, actions.onChords)
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
        SettingsCard(stringResource(R.string.settings_chord_shapes), help = stringResource(R.string.settings_instrument_help) + "\n\n" + stringResource(R.string.settings_chord_list_help)) {
            InstrumentPicker(look.instrument, actions.onInstrument)
            SwitchRow(stringResource(R.string.settings_chord_tap), look.chordTap, actions.onChordTap)
            // como o acorde soa ao tocar em "Tocar" no desenho (T20); no modo essencial o som não existe (T19)
            if (look.extrasOn) {
            Text(stringResource(R.string.settings_chord_play), style = MaterialTheme.typography.bodyLarge)
            val playModes = listOf(
                br.app.stagebook.data.model.ChordPlayMode.ARPEGGIO to R.string.settings_chord_play_arpeggio,
                br.app.stagebook.data.model.ChordPlayMode.CHORD to R.string.settings_chord_play_chord,
                br.app.stagebook.data.model.ChordPlayMode.BOTH to R.string.settings_chord_play_both,
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                playModes.forEachIndexed { i, (m, label) ->
                    SegmentedButton(
                        selected = look.chordPlay == m,
                        onClick = { actions.onChordPlay(m) },
                        shape = SegmentedButtonDefaults.itemShape(i, playModes.size, baseShape = CircleShape),
                    ) { Text(stringResource(label)) }
                }
            }
            }
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
    SettingsCard(stringResource(R.string.settings_sections_look), help = stringResource(R.string.settings_sections_help) + "\n\n" + stringResource(R.string.settings_translate_sections_help)) {
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
        LegibilityWarnings(look)
    }
    SettingsCard(stringResource(R.string.settings_normalize_card), help = stringResource(R.string.settings_normalize_help)) {
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
    SettingsCard(title, help = stringResource(R.string.settings_sites_help)) {
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

/** Modo essencial (T19): uma chave para tudo e uma para cada parte; mexer numa parte já tira do modo essencial. */
@Composable
private fun EssentialDetail(look: Look, actions: SettingsActions) {
    SettingsCard(stringResource(R.string.essential_title), help = stringResource(R.string.essential_help)) {
        SwitchRow(stringResource(R.string.essential_switch), look.essential, actions.onEssential, detail = stringResource(R.string.essential_keeps))
    }
    SettingsCard(stringResource(R.string.essential_parts)) {
        SwitchRow(stringResource(R.string.essential_band), look.bandOn, actions.onBandOn, detail = stringResource(R.string.essential_band_detail))
        SwitchRow(stringResource(R.string.essential_internet), look.internetOn, actions.onInternetOn, detail = stringResource(R.string.essential_internet_detail))
        SwitchRow(stringResource(R.string.essential_player), look.playerMode, actions.onPlayerMode, detail = stringResource(R.string.essential_player_detail))
        SwitchRow(stringResource(R.string.essential_extras), look.extrasOn, actions.onExtrasOn, detail = stringResource(R.string.essential_extras_detail))
    }
}

/** Player: a seção só aparece com ele ligado; liga e desliga só no Modo essencial (pedido do usuário, 29/10). */
@Composable
private fun PlayerDetail(look: Look, actions: SettingsActions) {
    SettingsCard(stringResource(R.string.settings_player), help = stringResource(R.string.settings_player_mode_help)) {
        UsbRequirements()
    }
    MixerCard(look.mixer, actions)
    EqCard(look.mixer, actions)
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
    SettingsCard(stringResource(R.string.mixer_title), help = stringResource(R.string.mixer_help) + "\n\n" + stringResource(R.string.mixer_routes_help)) {
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
    SettingsCard(stringResource(R.string.eq_title), help = stringResource(R.string.eq_help)) {
        SwitchRow(stringResource(R.string.eq_on), mixer.eqOn, { onMixer(mixer.copy(eqOn = it)) })
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
    SettingsCard(stringResource(R.string.settings_language), help = stringResource(R.string.settings_language_help)) {
        OptionDropdown(stringResource(R.string.settings_language), Languages.available, selected, actions.onLanguage, nameOf = { languageLabel(it) })
    }
}

/** Metrônomo (o palco tem só o BPM e o play): contagem, som, brilho na borda e a cor dele. */
@Composable
private fun MetronomeDetail(look: Look, actions: SettingsActions) {
    fun set(count: Boolean = look.metroCount, sound: Boolean = look.metroSound, glow: Boolean = look.metroGlow, color: Int = look.metroGlowColor) =
        actions.onMetronome(count, sound, glow, color)
    SettingsCard(stringResource(R.string.metro_title), help = stringResource(R.string.metro_help)) {
        SwitchRow(stringResource(R.string.metro_count), look.metroCount, { set(count = it) })
        SwitchRow(stringResource(R.string.metro_sound), look.metroSound, { set(sound = it) })
        SwitchRow(stringResource(R.string.metro_glow), look.metroGlow, { set(glow = it) })
        if (look.metroGlow) {
            Text(stringResource(R.string.metro_glow_color), style = MaterialTheme.typography.bodyLarge)
            ColorSwatchRow(br.app.stagebook.ui.theme.StagePalette.glow, look.metroGlowColor, { set(color = it) })
        }
    }
}

@Composable
private fun ScreenDetail(look: Look, actions: SettingsActions) {
    SettingsCard(stringResource(R.string.settings_screen), help = stringResource(R.string.settings_keep_screen_on_help)) {
        SwitchRow(stringResource(R.string.settings_keep_screen_on), look.keepScreenOn, actions.onKeepScreenOn)
    }
    // Now Playing: o "grito" do palco para OBS e outros (T23); extra do palco (T19)
    if (look.extrasOn) SettingsCard(stringResource(R.string.announce_title), help = stringResource(R.string.announce_help)) {
        SwitchRow(stringResource(R.string.announce_switch), look.announce, actions.onAnnounce)
    }
}

@Composable
private fun LibraryDetail(uiState: SettingsUiState, actions: SettingsActions) {
    SettingsCard(stringResource(R.string.library_card)) {
        Text(
            pluralStringResource(R.plurals.song_count, uiState.songCount, uiState.songCount) + " · " +
                pluralStringResource(R.plurals.set_count, uiState.setCount, uiState.setCount),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
    // um formato só, o .stagebook (pedido do usuário, 29/10), com a explicação à vista (sem esconder em "Como funciona")
    SettingsCard(stringResource(R.string.backup_save_card)) {
        var chosen by rememberSaveable { mutableStateOf(br.app.stagebook.data.Backup.Part.entries.map { it.name }.toSet()) }
        val parts = br.app.stagebook.data.Backup.Part.entries.filter { it.name in chosen }.toSet()
        InfoText(stringResource(R.string.backup_save_info))
        BackupPartsChecks(br.app.stagebook.data.Backup.Part.entries.toSet(), parts) { part, on ->
            chosen = if (on) chosen + part.name else chosen - part.name
        }
        ActionButton(stringResource(R.string.backup_make), { actions.onBackup(parts) }, Modifier.fillMaxWidth(), icon = AppIcons.Download, enabled = parts.isNotEmpty())
        InfoText(stringResource(R.string.backup_save_where))
    }
    SettingsCard(stringResource(R.string.backup_open_card)) {
        InfoText(stringResource(R.string.backup_open_info))
        InfoText(stringResource(R.string.backup_open_merge))
        InfoText(stringResource(R.string.backup_open_replace))
        ActionButton(stringResource(R.string.backup_open), actions.onRestore, Modifier.fillMaxWidth(), icon = AppIcons.Upload, tone = ButtonTone.Tonal)
        InfoText(stringResource(R.string.backup_open_other_device))
    }
    // as outras formas de mandar músicas e setlists ficam em outras telas: aqui só o mapa
    SettingsCard(stringResource(R.string.backup_share_card)) {
        InfoText(stringResource(R.string.backup_share_info))
    }
    // apagar tudo, por último e em vermelho; a janela oferece salvar o arquivo antes e pede o código
    var resetting by rememberSaveable { mutableStateOf(false) }
    SettingsCard(stringResource(R.string.reset_title)) {
        InfoText(stringResource(R.string.reset_info))
        ActionButton(stringResource(R.string.reset_title), { resetting = true }, Modifier.fillMaxWidth(), icon = AppIcons.Delete, tone = ButtonTone.Danger)
    }
    if (resetting) {
        FactoryResetDialog(
            onBackupFirst = { actions.onBackup(br.app.stagebook.data.Backup.Part.entries.toSet()) },
            onReset = { resetting = false; actions.onFactoryReset() },
            onDismiss = { resetting = false },
        )
    }
}

/** Parágrafo explicativo dos cartões (à vista, em tom mais baixo que o texto principal). */
@Composable
private fun InfoText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun UpdateDetail(uiState: SettingsUiState, actions: SettingsActions) {
    val up = uiState.update
    SettingsCard(stringResource(R.string.settings_update), help = stringResource(R.string.settings_update_help)) {
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
    }
}

// Sobre: o app, a atualização (procurar agora), privacidade em poucas linhas, o código aberto e o apoio (doação)
@Composable
private fun AboutDetail(uiState: SettingsUiState, actions: SettingsActions) {
    SettingsCard(stringResource(R.string.app_name)) {
        Text(stringResource(R.string.about_tagline), style = MaterialTheme.typography.bodyLarge)
    }
    if (uiState.look.internetOn) UpdateDetail(uiState, actions)   // procurar versão nova usa a internet (T19)
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
            ActionButton(stringResource(R.string.about_donate), { actions.onOpenLink(AppLinks.DONATE) }, icon = AppIcons.Favorite, tone = ButtonTone.Tonal)
        }
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
