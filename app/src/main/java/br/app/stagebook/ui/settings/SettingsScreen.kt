package br.app.stagebook.ui.settings

import br.app.stagebook.data.model.essential
import androidx.core.net.toUri
import br.app.stagebook.data.AppLinks
import androidx.compose.runtime.DisposableEffect
import br.app.stagebook.ui.band.BandViewModel
import br.app.stagebook.ui.band.BandUi
import br.app.stagebook.ui.band.BandDetail
import br.app.stagebook.ui.band.BandActions
import br.app.stagebook.ui.web.WebEditorDetail
import br.app.stagebook.ui.web.WebEditorUi
import br.app.stagebook.ui.web.WebEditorViewModel
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.layout
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
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
import br.app.stagebook.ui.components.InfoDialog
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
fun SettingsRoute(start: SettingsSection? = null, viewModel: SettingsViewModel = hiltViewModel()) {   // start: section open on entry (link from the alert)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    // backup: pick the parts (all checked), then where to save the .stagebook file
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
                        // language and look apply when the screen opens again
                        if (m.restart) (context as? android.app.Activity)?.recreate()
                        // what wasn't song text stayed out: said, not hidden
                        if (m.skipped > 0) r.getString(R.string.backup_restored) + " " + r.getQuantityString(R.plurals.backup_skipped, m.skipped, m.skipped)
                        else r.getString(R.string.backup_restored)
                    }
                },
            )
        }
    }
    // pedal test: while settings are open, any button shows on the pedal card
    PedalHandler { p ->
        val ignored = p.code in setOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE)
        if (ignored) return@PedalHandler false
        if (p.down) viewModel.onKey(p)
        true
    }

    // install: the first time, Android must allow "install apps from this source" for Stagebook
    val install: () -> Unit = { context.installApk(viewModel.canInstall(), viewModel.installIntent()) }
    LaunchedEffect(viewModel) { viewModel.install.collect { install() } }

    // Bluetooth MIDI pedal: searching asks for the Bluetooth permission (or location, on Android 9 to 11)
    val midiPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.all { it }) viewModel.scanMidi()
    }
    var section by rememberSaveable { mutableStateOf(start) }
    // mixer meters: only with player mode on, the Player section open and the screen visible. Band: the network search
    // stays on while Settings → Band is open (so someone new can find this device).
    val bandVm: BandViewModel = hiltViewModel()
    val band by bandVm.ui.collectAsStateWithLifecycle()
    val bandActions = remember(bandVm) { bandVm.actions() }
    val webVm: WebEditorViewModel = hiltViewModel()
    val web by webVm.ui.collectAsStateWithLifecycle()
    val advancedVm: AdvancedViewModel = hiltViewModel()
    val advanced by advancedVm.ui.collectAsStateWithLifecycle()
    // read again every time the section opens and when coming back from Android's settings (battery request)
    LifecycleResumeEffect(section == SettingsSection.ADVANCED) {
        if (section == SettingsSection.ADVANCED) advancedVm.refresh()
        onPauseOrDispose { }
    }
    // without the local network permission (where Android has it) the system hides this device from the band (the S26
    // wasn't found, 31/10); asked again when Settings → Band opens, and the search restarts once it's given
    val localNetwork = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) { bandVm.screenOpen(false); bandVm.screenOpen(true) }
    }
    LaunchedEffect(section == SettingsSection.BAND) {
        if (section == SettingsSection.BAND && br.app.stagebook.needsLocalNetwork(context)) localNetwork.launch(br.app.stagebook.LOCAL_NETWORK)
    }
    DisposableEffect(section == SettingsSection.BAND) {
        val open = section == SettingsSection.BAND
        bandVm.screenOpen(open)
        onDispose { if (open) bandVm.screenOpen(false) }
    }
    // "Monitor" switch: starts off and isn't saved
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
                web = web,
                onWebEditorOn = viewModel::setWebEditorOn,
                onWebRemove = webVm::remove,
                advanced = advanced,
                onTweak = advancedVm::set,
                onBatteryFree = { runCatching { context.startActivity(advancedVm.batteryRequest()) } },
                onLanguage = { lang ->
                    if (lang != uiState.language) {
                        viewModel.setLanguage(lang)
                        (context as? Activity)?.recreate()   // the screens come back in the new language
                    }
                },
                onKeepScreenOn = viewModel::setKeepScreenOn,
                onTrackStops = viewModel::setTrackStops,
                onColumns = viewModel::setColumns,
                onStageMode = viewModel::setStageMode,
                onAutoScroll = viewModel::setAutoScroll,
                onChordPlay = viewModel::setChordPlay,
                onAnnounce = viewModel::setAnnounce,
                onEssential = viewModel::setEssential,
                onBandOn = viewModel::setBandOn,
                onInternetOn = viewModel::setInternetOn,
                onExtrasOn = viewModel::setExtrasOn,
                onAutoTags = viewModel::autoTags,
                onBackup = { parts ->
                    backupParts = parts
                    backupSaver.launch("stagebook-backup-${java.time.LocalDate.now()}.${br.app.stagebook.data.Backup.EXTENSION}")
                },
                onRestore = { restorePicker.launch(arrayOf("*/*")) },
                onFactoryReset = viewModel::factoryReset,
                onMetronome = viewModel::setMetronome,
                onMetronomeShown = viewModel::setMetronomeShown,
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

/** Every interaction on the settings screen (one parameter instead of eleven). */
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
    val mixerLevels: () -> MixerLevels = { MixerLevels.SILENT },   // read only when drawing the meters
    val monitoring: Boolean = false,
    // Settings → Band: the band state (BandViewModel) and what the screen does
    val band: BandUi = BandUi(),
    val bandActions: BandActions = BandActions(),
    // Settings → Editor on the computer (T37)
    val web: WebEditorUi = WebEditorUi(),
    val onWebEditorOn: (Boolean) -> Unit = {},
    val onWebRemove: (String) -> Unit = {},
    // Settings → Advanced (31/10)
    val advanced: AdvancedUi = AdvancedUi(),
    val onTweak: (br.app.stagebook.data.device.DeviceTweak, Boolean) -> Unit = { _, _ -> },
    val onBatteryFree: () -> Unit = {},
    val onMonitoring: (Boolean) -> Unit = {},
    val onKeepScreenOn: (Boolean) -> Unit = {},
    val onTrackStops: (Boolean) -> Unit = {},
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
    val onAutoTags: (national: String, international: String, language: String, preview: Boolean) -> Pair<Int, Int> = { _, _, _, _ -> 0 to 0 },
    val onRestore: () -> Unit = {},
    val onFactoryReset: () -> Unit = {},
    val onMetronome: (count: Boolean, sound: Boolean, glow: Boolean, glowColor: Int) -> Unit = { _, _, _, _ -> },
    val onMetronomeShown: (Boolean) -> Unit = {},
    val onCheckUpdate: () -> Unit = {},
    val onOpenLink: (String) -> Unit = {},
    val onDownloadUpdate: () -> Unit = {},
    val onInstallUpdate: () -> Unit = {},
)

/** Settings sections: list on the left on tablets, one screen per section on phones. */
enum class SettingsSection(@StringRes val title: Int, val icon: ImageVector) {
    ESSENTIAL(R.string.essential_title, AppIcons.Eco),   // first: changes the whole app
    LYRICS(R.string.settings_appearance, AppIcons.FormatSize),
    CHORDS(R.string.settings_chords, AppIcons.MusicNote),
    PLAYER(R.string.settings_player, AppIcons.Play),
    SECTIONS(R.string.settings_sections, AppIcons.ListBulleted),
    PEDAL(R.string.settings_pedal, AppIcons.Pedal),
    BAND(R.string.settings_band, AppIcons.Group),
    WEB_EDITOR(R.string.settings_web_editor, AppIcons.Computer),
    SCREEN(R.string.settings_screen, AppIcons.Brightness),
    IMPORT(R.string.settings_import_sites, AppIcons.Public),
    LIBRARY(R.string.settings_library, AppIcons.Folder),
    ADVANCED(R.string.settings_advanced, AppIcons.Tune),   // Android settings for playing without failures (31/10)
    ABOUT(R.string.settings_about, AppIcons.Info),   // always last: the app, update, privacy, code, support
}

/** Essential mode (T19): the section of what is turned off leaves the list. */
fun SettingsSection.shown(look: Look): Boolean = when (this) {
    SettingsSection.PLAYER -> look.playerMode
    SettingsSection.BAND -> look.bandOn || look.internetOn   // the band also works over the internet (same members)
    SettingsSection.WEB_EDITOR -> !look.essential   // essential mode turns the editor on the computer off (T37)
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
                // tablet in landscape: fixed list on the left, open section on the right
                val open = section ?: SettingsSection.LYRICS
                Row(Modifier.fillMaxSize()) {
                    SectionList(uiState, open, onSection, Modifier.width(Sizes.settingsList).fillMaxHeight(), band = actions.band)
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = Spacing.m),
                    ) {
                        // fixed header; only the content scrolls
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
            }
        }
    }
}

/** Summary of each section in the list (what applies right now). */
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
    // the keyboard pedal, or else the saved MIDI pedal
    SettingsSection.PEDAL -> when {
        ui.look.pedalName.isNotEmpty() ->
            ui.look.pedalName + " · " + stringResource(if (ui.pedalConnected) R.string.settings_pedal_connected_short else R.string.settings_pedal_disconnected_short)
        ui.look.midiBtAddress.isNotEmpty() ->
            ui.look.midiBtName + " · " + stringResource(if (ui.midi.connected.any { it.bluetooth }) R.string.settings_pedal_connected_short else R.string.settings_pedal_disconnected_short)
        else -> stringResource(R.string.settings_pedal_none_short)
    }
    SettingsSection.SCREEN -> stringResource(if (ui.look.keepScreenOn) R.string.settings_screen_on_short else R.string.settings_screen_off_short)
    SettingsSection.PLAYER -> stringResource(if (ui.look.playerMode) R.string.settings_player_on_short else R.string.settings_player_off_short)
    SettingsSection.WEB_EDITOR -> stringResource(if (ui.look.webEditorOn) R.string.settings_web_editor_on_short else R.string.settings_web_editor_off_short)
    SettingsSection.IMPORT -> (if (ui.look.chords) ui.look.chordSites else ui.look.lyricSites).firstOrNull()?.name.orEmpty()
    SettingsSection.ABOUT -> stringResource(R.string.settings_update_installed, ui.version) +
        if (ui.update is UpdateState.Available) " · " + stringResource(R.string.settings_update_new_short) else ""
    SettingsSection.ADVANCED -> stringResource(R.string.settings_advanced_short)
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
            SettingsSection.WEB_EDITOR -> WebEditorDetail(uiState.look.webEditorOn, actions.web, actions.onWebEditorOn, actions.onWebRemove)
            SettingsSection.SCREEN -> ScreenDetail(uiState.look, actions)
            SettingsSection.PLAYER -> PlayerDetail(uiState.look, actions, uiState.ownMix)
            SettingsSection.IMPORT -> ImportSitesDetail(uiState.look, actions)
            SettingsSection.LIBRARY -> LibraryDetail(uiState, actions)
            SettingsSection.ADVANCED -> AdvancedDetail(actions.advanced, actions.onTweak, actions.onBatteryFree)
            SettingsSection.ABOUT -> AboutDetail(uiState, actions)
        }
    }
}

@Composable
private fun LyricsDetail(look: Look, actions: SettingsActions) {
    // the size changes live in the preview and only saves on release
    var font by remember(look.fontSp) { mutableFloatStateOf(look.fontSp) }
    SettingsCard(stringResource(R.string.settings_font_card), help = stringResource(R.string.settings_font_help)) {
        // control on top and a fixed-height preview below: changing the size doesn't move the screen
        Text(stringResource(R.string.settings_font_value, font.roundToInt()), style = MaterialTheme.typography.headlineSmall)
        Slider(
            value = font,
            onValueChange = { font = it },
            onValueChangeFinished = { actions.onFont(font) },
            valueRange = Look.MIN_FONT..Look.MAX_FONT,
            steps = (Look.MAX_FONT - Look.MIN_FONT).toInt() - 1,
        )
        // on a wide screen the box is taller: the miniature (sized so 72 fits) stays close to the real size (30/10)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            Surface(
                color = Color(look.bg),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().height(if (maxWidth >= Sizes.wideScreen) Sizes.fontPreviewWide else Sizes.fontPreview),
            ) {
                StagePreview(look, font, Modifier.padding(Spacing.m).clipToBounds().stageMiniature(font), R.string.settings_preview_size_text)
            }
        }
    }
    // turn pages (default) or scroll the lyrics; in both the pedal and a tap move one step
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
        // "Split into columns: Automatic | Never | Always", each with a picture of the page (lines whole or in two blocks)
        val options = listOf(
            Triple(StageColumns.AUTO, R.string.settings_columns_auto, AppIcons.AutoColumns),
            Triple(StageColumns.ONE, R.string.settings_columns_never, AppIcons.OneColumn),
            Triple(StageColumns.TWO, R.string.settings_columns_always, AppIcons.TwoColumns),
        )
        // pill ends, like the buttons in the rest of the app (Material 3 Expressive)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, (c, label, icon) ->
                SegmentedButton(
                    selected = look.columns == c,
                    onClick = { actions.onColumns(c) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size, baseShape = CircleShape),
                    icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(SegmentedButtonDefaults.IconSize)) },
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
        // the lyrics font on the same card (dropdown, written in the font itself)
        FontPicker(look.font, actions.onFontFamily)
        LegibilityWarnings(look)
    }
    // the stage metronome lives with the rest of the stage (user's decision of 30/10); off in essential mode
    if (look.extrasOn) MetronomeDetail(look, actions)
}

/** Stage preview with the chosen look (lyrics, section and chords), at size [font]. */
@Composable
private fun StagePreview(look: Look, font: Float, modifier: Modifier = Modifier, @StringRes sample: Int = R.string.settings_preview_text) {
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
            stringResource(sample),
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
 * The sample as a miniature of the stage: one fixed reduction, chosen so that even at [Look.MAX_FONT] the whole sample
 * fits in the box (height and width). The size still grows with the slider, in the same proportion, and nothing gets cut. Lines don't
 * wrap, so the height grows exactly with [font].
 */
private fun Modifier.stageMiniature(font: Float) = layout { measurable, constraints ->
    val sample = measurable.measure(Constraints())
    val growth = Look.MAX_FONT / font
    val scale = minOf(1f, constraints.maxHeight / (sample.height * growth), constraints.maxWidth / (sample.width * growth))
    layout(constraints.maxWidth, constraints.maxHeight) {
        sample.placeWithLayer(0, 0) {
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(0f, 0f)
        }
    }
}

/**
 * Warns when a stage color will vanish into the background (same or too similar, contrast below [Contrast.LEGIBLE]):
 * lyrics, section titles and chords (only if shown). Only a warning; the choice is the user's.
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
    // the size changes live in the preview and only saves on release
    var scale by remember(look.chordScale) { mutableFloatStateOf(look.chordScale) }
    // the card doesn't repeat the screen title ("Chords")
    SettingsCard(stringResource(R.string.settings_chords_card), help = stringResource(R.string.settings_chords_show_help)) {
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
            // the preview right after what it shows (size, color, font)
            Surface(color = Color(look.bg), shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().height(Sizes.fontPreview)) {
                StagePreview(look.copy(chordScale = scale), minOf(look.fontSp, PREVIEW_FONT), Modifier.padding(Spacing.m).clipToBounds(), R.string.settings_preview_size_text)
            }
            LegibilityWarnings(look)
        }
    }
    // chord diagrams on their own card (only in the lyrics and chords profile), with a sample for the instrument
    if (look.chords) {
        SettingsCard(stringResource(R.string.settings_chord_shapes), help = stringResource(R.string.settings_instrument_help) + "\n\n" + stringResource(R.string.settings_chord_list_help)) {
            InstrumentPicker(look.instrument, actions.onInstrument)
            SwitchRow(stringResource(R.string.settings_chord_tap), look.chordTap, actions.onChordTap)
            // how the chord sounds when tapping "Play" on the diagram (T20); in essential mode there is no sound (T19)
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
            // sample: G on the chosen instrument, as it shows on stage
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

/** The instrument for chord diagrams, in a dropdown (acoustic and electric guitar show the same). */
@Composable
private fun InstrumentPicker(selected: Instrument, onSelect: (Instrument) -> Unit) {
    val names = mapOf(
        Instrument.ACOUSTIC_GUITAR to R.string.instrument_acoustic_guitar,
        Instrument.ELECTRIC_GUITAR to R.string.instrument_electric_guitar,
        Instrument.UKULELE to R.string.instrument_ukulele,
        Instrument.CAVAQUINHO to R.string.instrument_cavaquinho,
        Instrument.KEYBOARD to R.string.instrument_keyboard,
    )
    OptionDropdown(stringResource(R.string.settings_instrument), Instrument.entries, selected, onSelect, nameOf = { stringResource(names.getValue(it)) })
}

/** The stage font in a dropdown: the field and each option written in the font itself. */
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

/** The chord preview doesn't go over this size (large lyrics wouldn't fit the box). */
private const val PREVIEW_FONT = 30f

@Composable
private fun SectionsDetail(look: Look, actions: SettingsActions) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    SettingsCard(stringResource(R.string.settings_sections_look), help = stringResource(R.string.settings_sections_help) + "\n\n" + stringResource(R.string.settings_translate_sections_help)) {
        Text(stringResource(R.string.settings_section_color), style = MaterialTheme.typography.bodyLarge)
        ColorSwatchRow(StagePalette.section, look.sectionColor, actions.onSectionColor)
        SwitchRow(stringResource(R.string.settings_show_sections), look.showSections, actions.onShowSections)
        if (look.showSections) {
            // the size changes live in the preview and only saves on release (like the chords one)
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
                StagePreview(look.copy(sectionScale = scale), minOf(look.fontSp, PREVIEW_FONT), Modifier.padding(Spacing.m).clipToBounds(), R.string.settings_preview_size_text)
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

/**
 * Pedal (user, 01/11: "the logic of adding the pedal is a bit odd", "hard to find where", "confusing texts"): two
 * cards in the order things are done. Connect: every pedal in one place, with its state, and how to add each kind.
 * Buttons: press one, see which it is and choose what it does; right below, the ones already set.
 */
@Composable
private fun PedalDetail(uiState: SettingsUiState, actions: SettingsActions) {
    PedalConnectCard(uiState, actions)
    PedalButtonsCard(uiState, actions)
}

@Composable
private fun PedalConnectCard(uiState: SettingsUiState, actions: SettingsActions) {
    val look = uiState.look
    val midi = uiState.midi
    SettingsCard(stringResource(R.string.settings_pedal_connect)) {
        val btMidiOn = midi.connected.any { it.bluetooth }
        val usb = midi.connected.filter { !it.bluetooth }
        val any = look.pedalName.isNotEmpty() || look.midiBtAddress.isNotEmpty() || usb.isNotEmpty()
        if (!any) Text(stringResource(R.string.settings_pedal_nothing), style = MaterialTheme.typography.bodyLarge)
        if (look.pedalName.isNotEmpty()) {
            PedalStatusRow(look.pedalName, stringResource(R.string.settings_pedal_kind_bt), uiState.pedalConnected)
        }
        if (look.midiBtAddress.isNotEmpty()) {
            PedalStatusRow(look.midiBtName, stringResource(R.string.settings_pedal_kind_midi_bt), btMidiOn, onForget = actions.onMidiForget)
        }
        usb.forEach { d -> PedalStatusRow(d.name, stringResource(R.string.settings_pedal_kind_midi_usb), true) }
        ActionButton(stringResource(R.string.settings_pedal_add_common), actions.onPairPedal, Modifier.fillMaxWidth(), icon = AppIcons.Bluetooth, tone = ButtonTone.Tonal)
        if (midi.supported) {
            ActionButton(
                stringResource(if (midi.scanning) R.string.settings_midi_scanning else R.string.settings_pedal_add_midi),
                actions.onMidiScan,
                Modifier.fillMaxWidth(),
                icon = AppIcons.Pedal,
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
        }
        HelpTip(stringResource(R.string.settings_pedal_kinds_help), label = stringResource(R.string.settings_pedal_kinds_label))
        if (midi.supported) {
            SwitchRow(stringResource(R.string.settings_midi_usb_switch), look.midiUsb, actions.onMidiUsb)
            HelpTip(stringResource(R.string.settings_midi_usb_help), label = stringResource(R.string.settings_midi_usb_about))
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                Text(stringResource(R.string.settings_midi_location), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Text(stringResource(R.string.settings_midi_unsupported), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A pedal and its state: name and kind, connected (accent) or not (with what happens next), and Forget when it applies. */
@Composable
private fun PedalStatusRow(name: String, kind: String, on: Boolean, onForget: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Column(Modifier.weight(1f)) {
            Text(if (name.isBlank()) kind else "$name · $kind", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                stringResource(if (on) R.string.settings_pedal_state_on else R.string.settings_pedal_state_off),
                style = MaterialTheme.typography.bodyMedium,
                color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onForget != null) TextButton(onClick = onForget) { Text(stringResource(R.string.settings_pedal_forget)) }
    }
}

@Composable
private fun PedalButtonsCard(uiState: SettingsUiState, actions: SettingsActions) {
    val keys = uiState.look.pedalKeys
    SettingsCard(stringResource(R.string.settings_pedal_buttons)) {
        val key = uiState.lastKey
        // first the button you pressed, then what it does: before pressing, the choice had no button to belong to
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                if (key == null) {
                    Text(stringResource(R.string.settings_pedal_press), style = MaterialTheme.typography.bodyLarge)
                } else {
                    val from = key.device.ifEmpty { stringResource(R.string.settings_pedal_from_tablet) }
                    Text(
                        stringResource(R.string.settings_pedal_pressed, pedalKeyName(key)) + " · " + from,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    // like another app: the button pressed can be set to another stage command
                    PedalActionPicker(key.action, { actions.onPedalAction(key.keyCode, it) })
                }
            }
        }
        Text(stringResource(R.string.settings_pedal_done), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (keys.isEmpty()) {
            Text(stringResource(R.string.settings_pedal_done_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            keys.forEach { (code, action) ->
                PedalKeyRow(pedalKeyName(code), action, { actions.onPedalAction(code, it) }, { actions.onPedalAction(code, PedalAction.default(code)) })
            }
            ActionButton(pluralStringResource(R.plurals.settings_pedal_reset, keys.size, keys.size), actions.onResetPedalKeys, icon = AppIcons.Undo, tone = ButtonTone.Tonal)
        }
    }
}

@Composable
// the same name in "Last button" and in the list of custom buttons
private fun pedalKeyName(key: PedalKeyUi): String = if (key.keyCode != 0) pedalKeyName(key.keyCode) else key.name

private val PEDAL_KEY_NAMES = mapOf(
    KeyEvent.KEYCODE_PAGE_DOWN to "Page Down", KeyEvent.KEYCODE_PAGE_UP to "Page Up",
    KeyEvent.KEYCODE_DPAD_DOWN to "↓", KeyEvent.KEYCODE_DPAD_UP to "↑", KeyEvent.KEYCODE_DPAD_LEFT to "←", KeyEvent.KEYCODE_DPAD_RIGHT to "→",
    KeyEvent.KEYCODE_SPACE to "Space", KeyEvent.KEYCODE_ENTER to "Enter",
    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE to "Play/Pause", KeyEvent.KEYCODE_MEDIA_NEXT to "Next", KeyEvent.KEYCODE_MEDIA_PREVIOUS to "Previous",
    KeyEvent.KEYCODE_VOLUME_UP to "Volume +", KeyEvent.KEYCODE_VOLUME_DOWN to "Volume −",
)

/** Button name: the key as printed on keyboards (Page Down), Android's name for the rest, or the MIDI message. */
@Composable
private fun pedalKeyName(code: Int): String {
    if (!MidiCode.isMidi(code)) {
        // key with no name known to Android: only the number
        // the keys page-turner pedals send, named as printed on keyboards (and the same on every Android)
        PEDAL_KEY_NAMES[code]?.let { return it }
        val name = KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
        // PAGE_DOWN → "Page Down": the name printed on keyboards, not Android's constant
        return name.toIntOrNull()?.let { stringResource(R.string.pedal_key_code, it) }
            ?: name.split('_').joinToString(" ") { w -> w.lowercase().replaceFirstChar { it.titlecase() } }
    }
    val n = MidiCode.number(code)
    val ch = MidiCode.channel(code)
    return when (MidiCode.kind(code)) {
        MidiCode.Kind.CC -> MidiCode.value(code)?.let { stringResource(R.string.midi_cc_value, n, it, ch) } ?: stringResource(R.string.midi_cc, n, ch)
        MidiCode.Kind.NOTE -> stringResource(R.string.midi_note, n, ch)
        MidiCode.Kind.PROGRAM -> stringResource(R.string.midi_program, n, ch)
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

@Composable
private fun PedalKeyRow(name: String, action: PedalAction, onSelect: (PedalAction) -> Unit, onRemove: () -> Unit) {
    // a choice field per button (like the button test above): the key or MIDI message is the label, the command is
    // the value, so it's visible that it can be changed; × resets that button to default
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        OptionDropdown(name, PedalAction.entries, action, onSelect, nameOf = { pedalActionLabel(it) }, modifier = Modifier.weight(1f))
        IconButton(onClick = onRemove) { Icon(AppIcons.Close, contentDescription = stringResource(R.string.settings_pedal_key_reset, name)) }
    }
}

/** Picking the button's command (dropdown, fits on phones too). */
@Composable
private fun PedalActionPicker(selected: PedalAction, onSelect: (PedalAction) -> Unit) =
    OptionDropdown(stringResource(R.string.settings_pedal_command), PedalAction.entries, selected, onSelect, nameOf = { pedalActionLabel(it) })

/** Import sites: the first in each list is the default (where the importer opens); you can remove and add. */
@Composable
private fun ImportSitesDetail(look: Look, actions: SettingsActions) {
    // only the list for the user's profile: chord sites with lyrics and chords, lyrics sites with lyrics only (the
    // importer opens the profile's default; the other list would just be noise)
    if (look.chords) SiteListCard(stringResource(R.string.settings_chord_sites), look.chordSites) { actions.onSites(true, it) }
    else SiteListCard(stringResource(R.string.settings_lyric_sites), look.lyricSites) { actions.onSites(false, it) }
}

@Composable
private fun SiteListCard(title: String, sites: List<ImportSite>, onChange: (List<ImportSite>) -> Unit) {
    var adding by rememberSaveable { mutableStateOf(false) }
    SettingsCard(title, help = stringResource(R.string.settings_sites_help)) {
        sites.forEachIndexed { i, site ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                // the default is the first one: picking another moves it to the top
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

/** Adding a site: address (the name comes from the domain if left blank). */
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

/** Essential mode (T19): one switch for everything and one for each part; touching a part leaves essential mode. */
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

/**
 * Player: the section only shows when it's on; it's turned on and off only in Essential mode (user's request, 29/10).
 */
@Composable
private fun PlayerDetail(look: Look, actions: SettingsActions, ownMix: Int = 0) {
    // "USB output", not "Player" again under the screen's own title
    SettingsCard(stringResource(R.string.settings_usb_card), help = stringResource(R.string.settings_player_mode_help)) {
        UsbRequirements()
    }
    // the stage's track button: stop (back to the start) by default; pausing is a choice (user, 31/10)
    SettingsCard(stringResource(R.string.settings_track_button)) {
        SwitchRow(stringResource(R.string.settings_track_stops), look.trackStops, actions.onTrackStops, detail = stringResource(R.string.settings_track_stops_detail))
    }
    // songs changed in the mixer over the stage keep their own settings: what changes here doesn't reach them (user, 31/10)
    if (ownMix > 0) {
        Text(
            pluralStringResource(R.plurals.mixer_own_songs, ownMix, ownMix),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
    MixerCard(look.mixer, actions.onMixer, actions.mixerLevels, actions.monitoring, actions.onMonitoring)
    EqCard(look.mixer, actions.onMixer, actions.mixerLevels, actions.monitoring)
}

@Composable
private fun eqLabel(hz: Int) =
    if (hz < 1000) stringResource(R.string.eq_hz, hz) else stringResource(R.string.eq_khz, (hz / 1000f).toString().removeSuffix(".0"))

// USB output requirements: hidden, in the same bubble as the other explanations
@Composable
private fun UsbRequirements() = HelpTip(
    stringResource(R.string.settings_player_requirements_text),
    label = stringResource(R.string.settings_player_requirements),
)

@Composable
internal fun MixerCard(
    mixer: MixerConfig,
    onMixer: (MixerConfig) -> Unit,
    levels: () -> MixerLevels,   // read only when drawing the meters
    monitoring: Boolean,
    onMonitoring: (Boolean) -> Unit,
) {
    // also opened over the stage, with the song playing (user, 31/10: from Settings the track stopped)
    var editing by rememberSaveable { mutableIntStateOf(-1) }   // channel being edited (the first of the pair); -1 = none
    SettingsCard(stringResource(R.string.mixer_title), help = stringResource(R.string.mixer_help) + "\n\n" + stringResource(R.string.mixer_routes_help)) {
        OptionDropdown(
            stringResource(R.string.mixer_channels), (1..MixerConfig.MAX).toList(), mixer.channels,
            { onMixer(mixer.copy(channels = it)) }, { it.toString() },
        )
        SwitchRow(stringResource(R.string.mixer_monitor), monitoring, onMonitoring)
        // which output the shown routing belongs to (the one in use or the last used)
        Text(
            pluralStringResource(R.plurals.mixer_now_output, mixer.output.count, mixer.output.label(), mixer.output.count),
            style = MaterialTheme.typography.titleSmall,
        )
        val groups = mixer.groups()
        // phone: one channel per row, horizontal fader; tablet: narrow strips side by side, vertical fader (like a
        // mixing desk)
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
                                showMeters = monitoring,
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
internal fun EqCard(mixer: MixerConfig, onMixer: (MixerConfig) -> Unit, levels: () -> MixerLevels, monitoring: Boolean) {
    SettingsCard(stringResource(R.string.eq_title), help = stringResource(R.string.eq_help)) {
        // turning it on with no channel going through it would change nothing: a notice, and the switch stays off until
        // a channel is sent to the EQ (user, 31/10)
        var noChannel by remember { mutableStateOf(false) }
        SwitchRow(stringResource(R.string.eq_on), mixer.eqOn, { on ->
            if (on && mixer.strips.take(mixer.channels).none { it.eq }) noChannel = true else onMixer(mixer.copy(eqOn = on))
        })
        if (noChannel) InfoDialog(stringResource(R.string.eq_no_channel_title), stringResource(R.string.eq_no_channel), { noChannel = false })
        val setBand = { i: Int, v: Float ->
            onMixer(mixer.copy(eqDb = List(MixerConfig.EQ_BANDS) { k -> if (k == i) (v * 2).roundToInt() / 2f else mixer.eqDb.getOrElse(k) { 0f } }))
        }
        val range = -MixerConfig.EQ_RANGE_DB..MixerConfig.EQ_RANGE_DB
        // vertical faders side by side (phone and tablet), like a graphic EQ, with the live spectrum behind (with the
        // EQ curve added: the engine measures before the EQ). Each fader centered in its fifth.
        val response = remember(mixer.eqDb, mixer.eqOn) {
            if (mixer.eqOn) Spectrum.eqResponseDb(mixer.eqDb) else FloatArray(Spectrum.BINS)
        }
        Box(Modifier.fillMaxWidth()) {
            SpectrumView(
                { levels().spectrumDb.mapIndexed { b, v -> if (v <= Spectrum.FLOOR_DB) v else v + response[b] } },
                active = { monitoring && levels().live },
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
        AppLanguage.EN -> R.string.language_en
    },
)

@Composable
private fun LanguageDetail(selected: AppLanguage, actions: SettingsActions) {
    SettingsCard(stringResource(R.string.settings_language), help = stringResource(R.string.settings_language_help)) {
        OptionDropdown(stringResource(R.string.settings_language), Languages.available, selected, actions.onLanguage, nameOf = { languageLabel(it) })
    }
}

/** Metronome (the stage only has BPM and play): count-in, sound, edge glow and its color. */
@Composable
private fun MetronomeDetail(look: Look, actions: SettingsActions) {
    fun set(count: Boolean = look.metroCount, sound: Boolean = look.metroSound, glow: Boolean = look.metroGlow, color: Int = look.metroGlowColor) =
        actions.onMetronome(count, sound, glow, color)
    SettingsCard(stringResource(R.string.metro_title), help = stringResource(R.string.metro_help)) {
        SwitchRow(stringResource(R.string.metro_shown), look.metroShown, actions.onMetronomeShown)
        if (!look.metroShown) return@SettingsCard
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
    // Now Playing: the stage's "shout" to OBS and others (T23); a stage extra (T19)
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
    // song tags: splitting national and international songs, once (moved here from the song list's ⋮, user 01/11);
    // Portuguese only
    if (br.app.stagebook.ui.components.nationalTagsShown()) SettingsCard(stringResource(R.string.tags_title)) {
        var ask by remember { mutableStateOf<Pair<Int, Int>?>(null) }
        val national = stringResource(R.string.tags_national)
        val international = stringResource(R.string.tags_international)
        // national = the language the app is showing (the tag names come in it too)
        val language = androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language
        InfoText(stringResource(R.string.tags_auto_info))
        ActionButton(stringResource(R.string.tags_auto), { ask = actions.onAutoTags(national, international, language, true) }, Modifier.fillMaxWidth(), icon = AppIcons.Label, tone = ButtonTone.Tonal)
        ask?.let { (br, other) ->
            if (br + other == 0) InfoDialog(stringResource(R.string.tags_auto), stringResource(R.string.tags_auto_none), { ask = null })
            else ConfirmDialog(
                stringResource(R.string.tags_auto_confirm, br, other),
                stringResource(R.string.tags_auto_ok),
                { actions.onAutoTags(national, international, language, false) },
                { ask = null },
                destructive = false,
            )
        }
    }
    // one format only, .stagebook (user's request, 29/10), with the explanation visible (not hidden in "How it works")
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
        ActionButton(stringResource(R.string.backup_open), actions.onRestore, Modifier.fillMaxWidth(), icon = AppIcons.Folder, tone = ButtonTone.Tonal)
        InfoText(stringResource(R.string.backup_open_other_device))
    }
    // the other ways to send songs and setlists live on other screens: here only the map
    SettingsCard(stringResource(R.string.backup_share_card)) {
        InfoText(stringResource(R.string.backup_share_info))
    }
    // erase everything, last and in red; the window offers to save the file first and asks for the code
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

/** Explanation paragraph for the cards (visible, in a softer tone than the main text). */
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

// About: the app, updating (check now), privacy in a few lines, the available code and support (donation)
@Composable
private fun AboutDetail(uiState: SettingsUiState, actions: SettingsActions) {
    SettingsCard(stringResource(R.string.app_name)) {
        Text(stringResource(R.string.about_tagline), style = MaterialTheme.typography.bodyLarge)
    }
    // the language lives here (user's decision of 30/10: a whole screen for one field was too much)
    LanguageDetail(uiState.language, actions)
    if (uiState.look.internetOn) UpdateDetail(uiState, actions)   // checking for a new version uses the internet (T19)
    SettingsCard(stringResource(R.string.about_privacy)) {
        Text(stringResource(R.string.about_privacy_text), style = MaterialTheme.typography.bodyMedium)
    }
    SettingsCard(stringResource(R.string.about_open_source)) {
        Text(stringResource(R.string.about_open_source_text), style = MaterialTheme.typography.bodyMedium)
        ActionButton(stringResource(R.string.about_see_code), { actions.onOpenLink(AppLinks.REPOSITORY) }, icon = AppIcons.Public, tone = ButtonTone.Tonal)
    }
    SettingsCard(stringResource(R.string.about_support)) {
        Text(stringResource(R.string.about_support_text), style = MaterialTheme.typography.bodyMedium)
        // the button only shows when the donation link is filled in (AppLinks.DONATE)
        if (AppLinks.DONATE.startsWith("https://")) {
            ActionButton(stringResource(R.string.about_donate), { actions.onOpenLink(AppLinks.DONATE) }, icon = AppIcons.Coffee, tone = ButtonTone.Tonal)
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
