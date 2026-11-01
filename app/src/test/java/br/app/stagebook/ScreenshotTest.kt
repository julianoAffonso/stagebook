package br.app.stagebook

import br.app.stagebook.R
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.padding
import br.app.stagebook.ui.theme.Spacing
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onAllNodesWithText
import com.github.takahirom.roborazzi.captureRoboImage
import br.app.stagebook.data.model.Look
import br.app.stagebook.domain.PedalAction
import br.app.stagebook.ui.editor.EditorMode
import br.app.stagebook.ui.importer.ImportScreen
import br.app.stagebook.ui.importer.ImportUiState
import br.app.stagebook.ui.editor.SongEditorScreen
import br.app.stagebook.ui.editor.SongEditorUiState
import br.app.stagebook.ui.home.HomeScaffold
import br.app.stagebook.ui.home.HomeTab
import br.app.stagebook.ui.setlists.SetItemUi
import br.app.stagebook.ui.setlists.SetlistDetailScreen
import br.app.stagebook.ui.setlists.SetlistDetailUiState
import br.app.stagebook.ui.setlists.SetlistRowUi
import br.app.stagebook.ui.setlists.SetlistsScreen
import br.app.stagebook.ui.setlists.SetlistsUiState
import br.app.stagebook.ui.settings.PedalKeyUi
import br.app.stagebook.ui.settings.SettingsActions
import br.app.stagebook.ui.settings.SettingsScreen
import br.app.stagebook.ui.settings.SettingsSection
import br.app.stagebook.ui.settings.SettingsUiState
import br.app.stagebook.ui.songs.SongRowUi
import br.app.stagebook.ui.songs.SongsScreen
import br.app.stagebook.ui.songs.SongsUiState
import br.app.stagebook.ui.stage.StageScreen
import br.app.stagebook.ui.stage.StageSong
import br.app.stagebook.ui.stage.StageUiState
import br.app.stagebook.ui.theme.StagebookTheme
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots to check the look without a device: `./gradlew recordRoborazziDebug` saves to
 * app/build/outputs/roborazzi. Tablet = Fire HD 10 in landscape; phone = S26.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = TABLET, application = android.app.Application::class)
class ScreenshotTest {
    // the captures folder (T39) runs these again with the app in English: -PshotsLang=en switches the language before
    // anything is drawn
    @get:Rule(order = 0) val language = org.junit.rules.TestRule { base, _ ->
        object : org.junit.runners.model.Statement() {
            override fun evaluate() {
                SHOTS_LANG?.let { org.robolectric.RuntimeEnvironment.setQualifiers("+$it") }
                base.evaluate()
            }
        }
    }
    @get:Rule(order = 1) val compose = createComposeRule()

    private val songs = SongsUiState(
        loading = false,
        songs = listOf(
            SongRowUi("1", "Hazard", "Richard Marx · 1991"),
            SongRowUi("2", "Malandragem", "Cássia Eller · 1994"),
            SongRowUi("3", "Tempo Perdido", "Legião Urbana · 1986"),
            SongRowUi("4", "Wild Horses", "The Rolling Stones · 1971"),
            SongRowUi("5", "Por Enquanto", "Legião Urbana · 1985"),
        ),
        total = 5,
        tags = listOf("Internacional", "Nacional"),
    )
    private val set = SetlistDetailUiState(
        loading = false,
        name = "Show de sábado",
        items = songs.songs.map { SetItemUi(it.id, it.id, it.title, it.subtitle, false) },
    )
    private val lyrics = "[Verse 1]\nPrimeira linha da letra de exemplo\nSegunda linha um pouco mais comprida que a outra\nTerceira linha\n\n" +
        "[Chorus]\nO refrão aparece aqui\nE repete mais uma vez\n\n[Verse 2]\nMais uma estrofe\nCom outra linha\nE a última"

    private fun shoot(name: String, content: @Composable () -> Unit) {
        compose.setContent { StagebookTheme { Surface(color = MaterialTheme.colorScheme.background) { content() } } }
        compose.onRoot().captureRoboImage("$SHOTS_DIR/$name.png")
    }

    @Composable
    private fun Songs(modifier: Modifier = Modifier) = SongsScreen(songs, "", {}, {}, {}, {}, modifier)

    @Test fun homeSongsTablet() = shoot("tablet_1_musicas") { HomeScaffold(HomeTab.SONGS, {}) { Songs(it) } }

    // no internet: Import unavailable, with the crossed cloud and the notice
    @Test @Config(qualifiers = PHONE)
    fun homeSongsOfflinePhone() = shoot("celular_1_musicas_sem_internet") {
        HomeScaffold(HomeTab.SONGS, {}) { SongsScreen(songs, "", {}, {}, {}, {}, it, online = false) }
    }

    @Test @Config(qualifiers = PHONE)
    fun settingsPhoneImportSites() = shoot("celular_5_ajustes_importacao") {
        HomeScaffold(HomeTab.SETTINGS, {}) { SettingsScreen(settings, SettingsActions(), it, section = SettingsSection.IMPORT) }
    }

    @Test @Config(qualifiers = PHONE)
    fun homeSongsPhone() = shoot("celular_1_musicas") { HomeScaffold(HomeTab.SONGS, {}) { Songs(it) } }

    @Test fun setlistsTablet() = shoot("tablet_2_setlists") {
        HomeScaffold(HomeTab.SETLISTS, {}) {
            SetlistsScreen(SetlistsUiState(false, listOf(SetlistRowUi("1", "Show de sábado", 15), SetlistRowUi("2", "18/10/2026 20:30", 6))), {}, {}, {}, {}, it)
        }
    }

    @Test @Config(qualifiers = PHONE)
    fun setlistsPhone() = shoot("celular_2_setlists") {
        HomeScaffold(HomeTab.SETLISTS, {}) {
            SetlistsScreen(SetlistsUiState(false, listOf(SetlistRowUi("1", "Show de sábado", 15), SetlistRowUi("2", "18/10/2026 20:30", 6))), {}, {}, {}, {}, it)
        }
    }

    @Test fun setlistDetailTablet() = shoot("tablet_3_setlist") {
        SetlistDetailScreen(set, null, {}, {}, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {})
    }

    @Test @Config(qualifiers = PHONE)
    fun setlistDetailPhone() = shoot("celular_3_setlist") {
        SetlistDetailScreen(set, null, {}, {}, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {})
    }

    // setlist journey (user, 01/11): list, select, inside, select songs, add songs with the tag filter, add from Songs
    private val sets = SetlistsUiState(false, listOf(SetlistRowUi("1", "Show de sábado", 15), SetlistRowUi("2", "18/10/2026 20:30", 6), SetlistRowUi("3", "Casamento", 22)))

    private fun journey(name: String, content: @Composable () -> Unit, then: () -> Unit = {}) {
        compose.setContent { StagebookTheme { Surface(color = MaterialTheme.colorScheme.background) { content() } } }
        then()
        compose.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("$SHOTS_DIR/$name.png")
    }

    @Test fun setlistJourney1List() = journey("jornada_setlist_1_lista", { HomeScaffold(HomeTab.SETLISTS, {}) { SetlistsScreen(sets, {}, {}, {}, {}, it) } })

    @Test fun setlistJourney2Select() = journey("jornada_setlist_2_selecionar", { HomeScaffold(HomeTab.SETLISTS, {}) { SetlistsScreen(sets, {}, {}, {}, {}, it) } }) {
        compose.onNodeWithText("18/10/2026 20:30").performTouchInput { longClick() }
        compose.onNodeWithText("Casamento").performClick()
    }

    @Test fun setlistJourney2bShare() = journey("jornada_setlist_2b_compartilhar_varios", {
        HomeScaffold(HomeTab.SETLISTS, {}) { SetlistsScreen(sets, {}, {}, {}, {}, it, online = true, band = 2, songsIn = { ids -> ids.size * 20 }) }
    }) {
        compose.onNodeWithText("18/10/2026 20:30").performTouchInput { longClick() }
        compose.onNodeWithText("Casamento").performClick()
        compose.onNodeWithText("Show de sábado").performClick()
        compose.onNodeWithContentDescription(str(R.string.share_title)).performClick()
    }

    @Test fun setlistJourney3Inside() = journey("jornada_setlist_3_dentro", { SetlistDetailScreen(set, null, {}, {}, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}) })

    @Test fun setlistJourney4SelectSongs() = journey("jornada_setlist_4_selecionar_musicas", { SetlistDetailScreen(set, null, {}, {}, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}) }) {
        compose.onNodeWithText("Wild Horses").performTouchInput { longClick() }
    }

    @Test fun setlistJourney5Add() = journey("jornada_setlist_5_adicionar", {
        br.app.stagebook.ui.setlists.PickSongsScreen(
            br.app.stagebook.ui.setlists.PickSongsUiState(
                setName = "Show de sábado",
                rows = songs.songs.filter { it.id != "1" && it.id != "4" }.map { br.app.stagebook.ui.setlists.PickRowUi(it.id, it.title, it.subtitle, inSet = false, selected = it.id == "2") },
                selectedCount = 1,
                tags = listOf("Internacional", "Nacional"),
                chosenTags = setOf("Nacional"),
            ),
            "", {}, {}, {}, {},
        )
    })

    @Test fun setlistJourney6FromSongs() = journey("jornada_setlist_6_das_musicas", {
        HomeScaffold(HomeTab.SONGS, {}) {
            SongsScreen(songs.copy(sets = listOf(br.app.stagebook.ui.songs.SetPick("1", "Show de sábado"), br.app.stagebook.ui.songs.SetPick("3", "Casamento"))), "", {}, {}, {}, {}, it, initialSelection = listOf("2", "3"))
        }
    }) {
        compose.onNodeWithContentDescription(str(R.string.songs_add_to_set)).performClick()
    }

    // importer: the web page stays blank in the test (WebView without network); checks the top bar and the button
    @Test fun importTablet() = shoot("tablet_10_importar") { ImportScreen(ImportUiState(), "Hazard", {}, {}, {}) }

    @Test @Config(qualifiers = PHONE)
    fun importPhone() = shoot("celular_10_importar") { ImportScreen(ImportUiState(), "Hazard", {}, {}, {}) }

    @Test @Config(qualifiers = PHONE)
    fun editorPhone() = shoot("celular_4_editor") {
        SongEditorScreen(SongEditorUiState(false, EditorMode.IMPORTED_NEW, "Hazard", "Richard Marx", "1991", lyrics), {}, {}, {}, {}, {}, {}, {})
    }

    /** Editing the lyrics: the section shortcut strip shows at the top. */
    @Test @Config(qualifiers = PHONE)
    fun editorChordsPhone() = shoot("celular_4_editor_cifra") {
        SongEditorScreen(SongEditorUiState(false, EditorMode.EDIT, "Canção de teste", "Banda Fictícia", "", chordSong), {}, {}, {}, {}, {}, {}, {}, cursor = 0)
    }

    // lyrics only profile: the editor shows no chords
    @Test @Config(qualifiers = PHONE)
    fun editorLyricsOnlyPhone() = shoot("celular_4_editor_so_letra") {
        SongEditorScreen(SongEditorUiState(false, EditorMode.EDIT, "Canção de teste", "Banda Fictícia", "", chordSong), {}, {}, {}, {}, {}, {}, {}, cursor = 0, look = Look(chords = false))
    }

    // T18: with a capo, the editor states the shape and the sounding key
    @Test fun editorCapoTablet() = shoot("tablet_4_editor_capo") {
        SongEditorScreen(SongEditorUiState(false, EditorMode.EDIT, "Canção de teste", "Banda Fictícia", "", chordSong), {}, {}, {}, {}, {}, {}, {}, cursor = 0)
    }

    // T27: song details open (new song)
    // new song with the player on: the backing track is in the details too
    @Test fun editorDataTablet() = shoot("tablet_4_editor_dados") {
        SongEditorScreen(SongEditorUiState(false, EditorMode.NEW, "", "", "", chordSong), {}, {}, {}, {}, {}, {}, {}, look = Look(playerMode = true), track = "")
    }

    // the editor of a saved song: details open with the tags, and the ⋮ with Delete (user, 01/11)
    private fun editorOpen(name: String, open: () -> Unit) {
        compose.setContent {
            StagebookTheme {
                SongEditorScreen(
                    SongEditorUiState(false, EditorMode.EDIT, "Canção de teste", "Banda Fictícia", "1999", chordSong, tags = listOf("Nacional", "Rock")),
                    {}, {}, {}, {}, {}, {}, {}, look = Look(playerMode = true), track = "faixa.ogg", trackName = "Canção de teste (playback)",
                )
            }
        }
        open()
        compose.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("$SHOTS_DIR/$name.png")
    }

    @Test fun editorTagsTablet() = editorOpen("tablet_4_editor_tags") { compose.onNodeWithText("Canção de teste", substring = true).performClick() }

    @Test @Config(qualifiers = PHONE)
    fun editorTagsPhone() = editorOpen("celular_4_editor_tags") { compose.onNodeWithText("Canção de teste", substring = true).performClick() }

    @Test fun editorSectionsTablet() = shoot("tablet_4_editor_secoes") {
        SongEditorScreen(SongEditorUiState(false, EditorMode.EDIT, "Hazard", "Richard Marx", "1991", lyrics), {}, {}, {}, {}, {}, {}, {}, cursor = 0)
    }

    /** Diagram of the chord tapped on stage (another window), on each instrument. */
    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    private fun chordPopup(name: String, chord: String, instrument: br.app.stagebook.data.model.Instrument, extras: Boolean = true) {
        compose.setContent {
            StagebookTheme {
                StageScreen(stage.copy(songs = listOf(StageSong("Canção de teste", "Banda Fictícia", chordSong, false))), 0, 0, { _, _ -> }, onBack = {})
                br.app.stagebook.ui.stage.ChordShapePopup(chord, instrument, chordSong, {}, {}, extras = extras)
            }
        }
        com.github.takahirom.roborazzi.captureScreenRoboImage("$SHOTS_DIR/$name.png")
    }

    // T15: the scroll icon on the clock line opens the small window with play and speed
    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    @Test fun autoScrollDialog() {
        compose.setContent {
            StagebookTheme { StageScreen(stage.copy(look = stage.look.copy(stageMode = br.app.stagebook.data.model.StageMode.SCROLL)), 0, 0, { _, _ -> }, onBack = {}) }
        }
        compose.onNodeWithContentDescription(str(R.string.stage_auto_title)).performClick()
        compose.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("$SHOTS_DIR/tablet_6_palco_rolagem_auto.png")
    }

    // scrolling: the icon next to the clock becomes pause
    @Test fun autoScrollRunning() {
        compose.setContent {
            StagebookTheme { StageScreen(stage.copy(look = stage.look.copy(stageMode = br.app.stagebook.data.model.StageMode.SCROLL)), 0, 0, { _, _ -> }, onBack = {}) }
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription(str(R.string.stage_auto_title)).performClick()
        repeat(3) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        compose.onNodeWithContentDescription(str(R.string.stage_auto_play)).performClick()
        repeat(90) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        compose.onRoot().captureRoboImage("$SHOTS_DIR/tablet_6_palco_rolando.png")
    }

    // T25: backup in Settings → Library and the choice of parts (all checked)
    @Test fun settingsLibraryBackup() = shoot("tablet_5_ajustes_backup") {
        HomeScaffold(HomeTab.SETTINGS, {}) { SettingsScreen(settings, SettingsActions(), it, section = SettingsSection.LIBRARY) }
    }

    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    @Test fun backupPartsDialog() {
        compose.setContent {
            StagebookTheme {
                br.app.stagebook.ui.settings.BackupPartsDialog("Abrir arquivo", "Abrir", br.app.stagebook.data.Backup.Part.entries.toSet(), { _, _ -> }, {}, warning = "Os ajustes do arquivo substituem os daqui.")
            }
        }
        com.github.takahirom.roborazzi.captureScreenRoboImage("$SHOTS_DIR/tablet_5_backup_restaurar.png")
    }

    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    @Test fun factoryResetDialog() {
        compose.setContent { StagebookTheme { br.app.stagebook.ui.settings.FactoryResetDialog({}, {}, {}, code = "4827") } }
        com.github.takahirom.roborazzi.captureScreenRoboImage("$SHOTS_DIR/tablet_5_apagar_tudo.png")
    }

    // T33c: the metronome next to the clock and its small window
    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    @Test fun metronomeDialog() {
        compose.setContent { StagebookTheme { StageScreen(stage, 0, 0, { _, _ -> }, onBack = {}) } }
        compose.onNodeWithContentDescription(str(R.string.metro_title)).performClick()
        compose.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("$SHOTS_DIR/tablet_6_palco_metronomo.png")
    }

    @Test fun metronomeCount() {
        compose.setContent { StagebookTheme { StageScreen(stage, 0, 0, { _, _ -> }, onBack = {}) } }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription(str(R.string.metro_title)).performClick()
        repeat(3) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        compose.onNodeWithContentDescription(str(R.string.metro_play)).performClick()
        repeat(3) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        compose.onRoot().captureRoboImage("$SHOTS_DIR/tablet_6_palco_metronomo_contagem.png")
    }

    // the metronome is at the end of Stage look: a tall capture so it shows (it used to be its own section)
    @Test @Config(qualifiers = TABLET_TALL)
    fun settingsMetronomeTablet() = shoot("tablet_5_ajustes_metronomo") {
        HomeScaffold(HomeTab.SETTINGS, {}) { SettingsScreen(settings.copy(look = settings.look.copy(fontSp = 30f)), SettingsActions(), it, section = SettingsSection.METRONOME) }
    }

    // T20: with a capo, the window explains the shape and has Play
    @Test fun chordPopupCapo() {
        compose.setContent {
            StagebookTheme {
                StageScreen(stage.copy(songs = listOf(StageSong("Canção de teste", "Banda Fictícia", chordSong, false))), 0, 0, { _, _ -> }, onBack = {})
                br.app.stagebook.ui.stage.ChordShapePopup("G", br.app.stagebook.data.model.Instrument.ACOUSTIC_GUITAR, chordSong, {}, {})
            }
        }
        @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
        com.github.takahirom.roborazzi.captureScreenRoboImage("$SHOTS_DIR/tablet_6_acorde_tocar.png")
    }

    // a diagram starting above the 1st fret: the fret number on the left (A with a barre on the 5th)
    @Test fun chordDiagramHighFret() = shoot("acorde_casa_5") {
        androidx.compose.foundation.layout.Box(Modifier.padding(32.dp)) {
            br.app.stagebook.ui.components.ChordDiagram(
                "A", br.app.stagebook.data.model.Instrument.ACOUSTIC_GUITAR,
                br.app.stagebook.domain.Voicings.Shape(listOf(5, 7, 7, 6, 5, 5)), null, MaterialTheme.colorScheme.onSurface,
                width = 240.dp,
            )
        }
    }

    // F# with the barre on the 2nd fret (how the barre is drawn; user's request, 30/10)
    @Test fun chordDiagramBarre() = shoot("acorde_fsustenido_pestana") {
        androidx.compose.foundation.layout.Box(Modifier.padding(32.dp)) {
            br.app.stagebook.ui.components.ChordDiagram(
                "F#", br.app.stagebook.data.model.Instrument.ACOUSTIC_GUITAR,
                br.app.stagebook.domain.Voicings.Shape(listOf(2, 4, 4, 3, 2, 2)), null, MaterialTheme.colorScheme.onSurface,
                width = 240.dp,
            )
        }
    }

    // keyboard with a capo chart: chords in the sounding key, the notice at the top and the first-time message
    @Test fun stageKeyboardCapo() = shoot("tablet_6_palco_teclado_capo") {
        StageScreen(stage.copy(songs = listOf(StageSong("Canção de teste", "Banda Fictícia", chordSong, false)), look = stage.look.copy(instrument = br.app.stagebook.data.model.Instrument.KEYBOARD)), 0, 0, { _, _ -> }, onBack = {})
    }

    // T21: the chord's second screen (harmonic field)
    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    @Test fun chordDetails() {
        compose.setContent {
            StagebookTheme {
                StageScreen(stage.copy(songs = listOf(StageSong("Canção de teste", "Banda Fictícia", chordSong, false))), 0, 0, { _, _ -> }, onBack = {})
                br.app.stagebook.ui.stage.ChordShapePopup("C", br.app.stagebook.data.model.Instrument.ACOUSTIC_GUITAR, chordSong, {}, {})
            }
        }
        compose.onNodeWithText(str(R.string.chord_details)).performClick()
        compose.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("$SHOTS_DIR/tablet_6_acorde_detalhes.png")
    }

    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    @Test fun chordDetailsSeventh() {
        compose.setContent {
            StagebookTheme {
                StageScreen(stage.copy(songs = listOf(StageSong("Canção de teste", "Banda Fictícia", chordSong, false))), 0, 0, { _, _ -> }, onBack = {})
                br.app.stagebook.ui.stage.ChordShapePopup("D7", br.app.stagebook.data.model.Instrument.ACOUSTIC_GUITAR, chordSong, {}, {})
            }
        }
        compose.onNodeWithText(str(R.string.chord_details)).performClick()
        compose.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("$SHOTS_DIR/tablet_6_acorde_detalhes_7.png")
    }

    // Settings → Advanced (31/10): Android settings, all on except debugging; tall so the three cards show whole
    @Test @Config(qualifiers = TABLET_TALL)
    fun settingsAdvancedTablet() = shoot("tablet_5_ajustes_avancado") {
        HomeScaffold(HomeTab.SETTINGS, {}) {
            SettingsScreen(settings, SettingsActions(advanced = br.app.stagebook.ui.settings.sampleAdvanced), it, section = SettingsSection.ADVANCED)
        }
    }

    // without the permission: the card with the command to give it from the computer
    @Test @Config(qualifiers = PHONE)
    fun settingsAdvancedNoPermissionPhone() = shoot("celular_5_ajustes_avancado_permissao") {
        HomeScaffold(HomeTab.SETTINGS, {}) {
            SettingsScreen(settings, SettingsActions(advanced = br.app.stagebook.ui.settings.sampleAdvanced.copy(canWrite = false)), it, section = SettingsSection.ADVANCED)
        }
    }

    @Test fun settingsEssentialTablet() = shoot("tablet_5_ajustes_essencial") {
        val look = settings.look.copy(bandOn = false, internetOn = false, extrasOn = false, playerMode = false)
        HomeScaffold(HomeTab.SETTINGS, {}) { SettingsScreen(settings.copy(look = look), SettingsActions(), it, section = SettingsSection.ESSENTIAL) }
    }

    // T03: the app in English (the base), to check the translation
    @Test @Config(qualifiers = "en-w1280dp-h800dp-land-hdpi")
    fun englishSongsTablet() = shoot("ingles_1_musicas") { HomeScaffold(HomeTab.SONGS, {}) { Songs(it) } }

    @Test @Config(qualifiers = "en-w1280dp-h800dp-land-hdpi")
    fun englishSettingsTablet() = shoot("ingles_2_ajustes_essencial") {
        HomeScaffold(HomeTab.SETTINGS, {}) { SettingsScreen(settings, SettingsActions(), it, section = SettingsSection.ESSENTIAL) }
    }

    @Test fun essentialAlertTablet() = shoot("tablet_1_aviso_essencial") {
        HomeScaffold(HomeTab.SONGS, {}) { Songs(it) }
        br.app.stagebook.ui.home.EssentialAlert(Look(bandOn = false, internetOn = false, playerMode = false, extrasOn = false), {}, {})
    }

    @Test fun someOffAlertTablet() = shoot("tablet_1_aviso_desligadas") {
        HomeScaffold(HomeTab.SONGS, {}) { Songs(it) }
        br.app.stagebook.ui.home.EssentialAlert(Look(bandOn = false, extrasOn = false), {}, {})
    }

    @Test fun essentialBannerTablet() = shoot("tablet_1_musicas_essencial") {
        // the leaf next to the title instead of a strip (user, 01/11)
        androidx.compose.runtime.CompositionLocalProvider(br.app.stagebook.ui.components.LocalHomeStatus provides br.app.stagebook.ui.components.HomeStatus(true, 0, {}, {})) {
            HomeScaffold(HomeTab.SONGS, {}) { Songs(it) }
        }
    }

    @Test fun settingsScreenTablet() = shoot("tablet_5_ajustes_banda_transmissao") {
        HomeScaffold(HomeTab.SETTINGS, {}) { SettingsScreen(settings, SettingsActions(), it, section = SettingsSection.BAND) }
    }

    // T11: the same setlist sent by two members stays together in the inbox
    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    @Test fun inboxVersions() {
        val now = System.currentTimeMillis()
        val items = listOf(
            br.app.stagebook.data.BandInbox.Item("1", "a", "Celular da Ana", "STAGEBOOK1\n12\nShow de sábado", at = now - 600_000),
            br.app.stagebook.data.BandInbox.Item("2", "b", "Tablet do Beto", "STAGEBOOK1\n12\nShow de sábado", at = now - 60_000),
            br.app.stagebook.data.BandInbox.Item("3", "c", "Celular do Caio", "STAGEBOOK1\n8\nEnsaio", at = now - 300_000, updated = true),
        )
        compose.setContent { StagebookTheme { br.app.stagebook.ui.share.InboxDialog(items, {}, {}, {}) } }
        com.github.takahirom.roborazzi.captureScreenRoboImage("$SHOTS_DIR/tablet_2_recebidos_versoes.png")
    }

    @Test fun chordPopupGuitar() = chordPopup("tablet_6_acorde_violao", "F", br.app.stagebook.data.model.Instrument.ACOUSTIC_GUITAR)
    // essential mode: no sound and no Details, the diagram and its shapes stay (user, 01/11)
    @Test fun chordPopupEssential() = chordPopup("tablet_6_acorde_essencial", "F", br.app.stagebook.data.model.Instrument.ACOUSTIC_GUITAR, extras = false)
    @Test fun chordPopupUkulele() = chordPopup("tablet_6_acorde_ukulele", "G", br.app.stagebook.data.model.Instrument.UKULELE)
    @Test fun chordPopupKeyboard() = chordPopup("tablet_6_acorde_teclado", "Am7", br.app.stagebook.data.model.Instrument.KEYBOARD)
    @Test fun chordPopupCavaquinho() = chordPopup("tablet_6_acorde_cavaquinho", "D", br.app.stagebook.data.model.Instrument.CAVAQUINHO)
    @Test fun chordPopupElectricGuitar() = chordPopup("tablet_6_acorde_guitarra", "A", br.app.stagebook.data.model.Instrument.ELECTRIC_GUITAR)

    /** New app version offered on open (another window). */
    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    @Test fun updateOfferTablet() {
        compose.setContent {
            StagebookTheme {
                Surface(color = MaterialTheme.colorScheme.background) { HomeScaffold(HomeTab.SONGS, {}) { Songs(it) } }
                br.app.stagebook.ui.AppUpdateDialog(br.app.stagebook.data.UpdateOffer.Available("0.3.101 (abc1234)"), {}, {}, {}, {})
            }
        }
        com.github.takahirom.roborazzi.captureScreenRoboImage("$SHOTS_DIR/tablet_8_versao_nova.png")
    }

    @Test fun pickSongsTablet() = shoot("tablet_9_escolher_musicas") {
        br.app.stagebook.ui.setlists.PickSongsScreen(
            br.app.stagebook.ui.setlists.PickSongsUiState(
                setName = "Show de sábado",
                rows = songs.songs.map { br.app.stagebook.ui.setlists.PickRowUi(it.id, it.title, it.subtitle, inSet = it.id == "1", selected = it.id == "2" || it.id == "3") },
                selectedCount = 2,
                tags = listOf("Internacional", "Nacional"),
                chosenTags = setOf("Nacional"),
            ),
            "", {}, {}, {}, {},
        )
    }

    @Test @Config(qualifiers = PHONE)
    fun pickSongsPhone() = shoot("celular_9_escolher_musicas") {
        br.app.stagebook.ui.setlists.PickSongsScreen(
            br.app.stagebook.ui.setlists.PickSongsUiState(
                setName = "Show de sábado",
                rows = songs.songs.map { br.app.stagebook.ui.setlists.PickRowUi(it.id, it.title, it.subtitle, inSet = it.id == "1", selected = it.id == "2" || it.id == "3") },
                selectedCount = 2,
            ),
            "", {}, {}, {}, {},
        )
    }

    @Test fun settingsTablet() = shoot("tablet_5_ajustes") {
        HomeScaffold(HomeTab.SETTINGS, {}) {
            SettingsScreen(settings, SettingsActions(), it)
        }
    }

    // the strip in context: below a follower's stage, with the current song missing for the bass player. T28: bigger
    // section titles, with a light background, and the size adjustable in Settings → Lyrics sections
    @Test fun settingsSectionsTablet() = shoot("tablet_5_ajustes_secoes") {
        HomeScaffold(HomeTab.SETTINGS, {}) { SettingsScreen(settings, SettingsActions(), it, section = SettingsSection.LYRICS) }
    }

    @Test fun conductOnStage() = shoot("tablet_6_palco_acompanhando") {
        androidx.compose.foundation.layout.Column {
            androidx.compose.foundation.layout.Box(Modifier.weight(1f)) { StageScreen(stage, 0, 0, { _, _ -> }, onBack = {}) }
            br.app.stagebook.ui.band.ConductBar(
                br.app.stagebook.ui.band.ConductUi.Following("Voz", "Garota de Ipanema", false, false, listOf("Baixo"),
                    listOf(br.app.stagebook.domain.Conduct.Lack("Baixo", listOf("Garota de Ipanema")))),
                {}, {}, {},
            )
        }
    }

    @Test fun conductBar() = shoot("banda_conduzir_faixas") { br.app.stagebook.ui.band.ConductBarSamplesForTest() }

    @Test fun settingsUpdateTablet() = shoot("tablet_5_ajustes_atualizar") {
        HomeScaffold(HomeTab.SETTINGS, {}) {
            SettingsScreen(
                settings.copy(update = br.app.stagebook.ui.settings.UpdateState.Available("0.3.52 (9f1c2ab)")),
                SettingsActions(),
                it,
                section = br.app.stagebook.ui.settings.SettingsSection.ABOUT,
            )
        }
    }

    private val settings = SettingsUiState(
        false, Look(pedalName = "PageFlip Firefly", pedalKeys = mapOf(android.view.KeyEvent.KEYCODE_PAGE_UP to PedalAction.PREVIOUS_SONG, br.app.stagebook.domain.MidiCode.of(0xB0, 64) to PedalAction.NEXT)), 42, 3, PedalKeyUi("PAGE_DOWN", PedalAction.NEXT, "PageFlip Firefly", android.view.KeyEvent.KEYCODE_PAGE_DOWN), "0.2.8", pedalConnected = true,
    )

    @Test @Config(qualifiers = PHONE)
    fun settingsPhoneList() = shoot("celular_5_ajustes") { HomeScaffold(HomeTab.SETTINGS, {}) { SettingsScreen(settings, SettingsActions(), it) } }

    // T37: editor on the computer on, with the address and one authorized computer
    @Test @Config(qualifiers = PHONE)
    fun settingsPhoneWebEditor() = shoot("celular_5_ajustes_editor_web") {
        HomeScaffold(HomeTab.SETTINGS, {}) {
            SettingsScreen(
                settings.copy(look = settings.look.copy(webEditorOn = true)),
                SettingsActions(web = br.app.stagebook.ui.web.WebEditorUi("http://192.168.0.10:8090", listOf(br.app.stagebook.ui.web.WebDeviceUi("1", "Chrome · Windows")))),
                it, section = SettingsSection.WEB_EDITOR,
            )
        }
    }

    @Test @Config(qualifiers = PHONE)
    fun settingsPhonePedal() = shoot("celular_5_ajustes_pedal") {
        HomeScaffold(HomeTab.SETTINGS, {}) { SettingsScreen(settings, SettingsActions(), it, section = SettingsSection.PEDAL) }
    }

    // pedal with the button that plays/pauses the backing track (player mode on)
    @Test @Config(qualifiers = "pt-rBR-w412dp-h2200dp-port-xxhdpi")
    fun settingsPhonePedalPlay() = shoot("jornada_pedal_play_pause") {
        val st = settings.copy(
            look = settings.look.copy(playerMode = true, pedalKeys = settings.look.pedalKeys + (android.view.KeyEvent.KEYCODE_B to PedalAction.PLAY_PAUSE)),
            lastKey = PedalKeyUi("B", PedalAction.PLAY_PAUSE, "PageFlip Firefly", android.view.KeyEvent.KEYCODE_B),
        )
        HomeScaffold(HomeTab.SETTINGS, {}) { SettingsScreen(st, SettingsActions(), it, section = SettingsSection.PEDAL) }
    }

    @Test @Config(qualifiers = PHONE)
    fun settingsPhoneChords() = shoot("celular_5_ajustes_cifras") {
        HomeScaffold(HomeTab.SETTINGS, {}) { SettingsScreen(settings, SettingsActions(), it, section = SettingsSection.LYRICS) }
    }

    // the whole Settings → Chords screen (tall, no scrolling): instrument, diagram on tap and chord list
    @Test @Config(qualifiers = "pt-rBR-w412dp-h2200dp-port-xxhdpi")
    fun settingsPhoneChordsFull() = shoot("celular_5_ajustes_cifras_inteira") {
        HomeScaffold(HomeTab.SETTINGS, {}) {
            SettingsScreen(settings.copy(look = settings.look.copy(chordList = br.app.stagebook.data.model.ChordListPlace.TOP)), SettingsActions(), it, section = SettingsSection.LYRICS)
        }
    }

    // black chord on a black background: the hard-to-read warning shows
    @Test @Config(qualifiers = PHONE)
    fun settingsPhoneChordsIllegible() = shoot("celular_5_ajustes_cifras_ilegivel") {
        val dark = settings.copy(look = settings.look.copy(chordColor = 0xFF000000.toInt()))
        HomeScaffold(HomeTab.SETTINGS, {}) { SettingsScreen(dark, SettingsActions(), it, section = SettingsSection.LYRICS) }
    }

    @Test @Config(qualifiers = PHONE)
    fun settingsPhoneLanguage() = shoot("celular_5_ajustes_lingua") {
        HomeScaffold(HomeTab.SETTINGS, {}) { SettingsScreen(settings, SettingsActions(), it, section = SettingsSection.ABOUT) }
    }

    // MIDI pedal: a USB one plugged in and a saved Bluetooth one out of range; button test with a MIDI message
    @Test @Config(qualifiers = PHONE)
    fun settingsPhoneMidi() = shoot("celular_5_ajustes_pedal_midi") {
        val code = br.app.stagebook.domain.MidiCode.of(0xB0, 64)
        val midi = settings.copy(
            look = settings.look.copy(midiBtAddress = "AA:BB", midiBtName = "AirTurn BT200"),
            lastKey = PedalKeyUi("", PedalAction.NEXT, "FS-1 USB MIDI", code),
            midi = br.app.stagebook.data.MidiState(
                connected = listOf(br.app.stagebook.data.MidiDeviceUi("FS-1 USB MIDI", bluetooth = false)),
                searched = true,
                found = listOf(br.app.stagebook.data.MidiFound("AirTurn BT200", "AA:BB")),
            ),
        )
        HomeScaffold(HomeTab.SETTINGS, {}) { SettingsScreen(midi, SettingsActions(), it, section = SettingsSection.PEDAL) }
    }

    @Test @Config(qualifiers = PHONE)
    fun settingsPhoneLyrics() = shoot("celular_5_ajustes_letra") {
        HomeScaffold(HomeTab.SETTINGS, {}) { SettingsScreen(settings.copy(look = settings.look.copy(fontSp = 72f)), SettingsActions(), it, section = SettingsSection.LYRICS) }
    }

    // the stage preview stays pinned while scrolling to the section titles (user, 01/11)
    @Test @Config(qualifiers = PHONE)
    fun settingsPhoneLyricsScrolled() {
        compose.setContent {
            StagebookTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    HomeScaffold(HomeTab.SETTINGS, {}) { SettingsScreen(settings.copy(look = settings.look.copy(fontSp = 40f)), SettingsActions(), it, section = SettingsSection.LYRICS) }
                }
            }
        }
        compose.onNodeWithText(str(R.string.settings_sections_look)).performScrollTo()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("$SHOTS_DIR/celular_5_ajustes_letra_secoes.png")
    }

    private val stage = StageUiState(
        false,
        listOf(StageSong("Hazard", "Richard Marx · 1991", (1..4).joinToString("\n\n") { lyrics }, false), StageSong("Malandragem", "", "", false)),
        look = Look(fontSp = 34f),
    )

    // T13: scroll mode, at the 2nd step (the lyrics scrolled to the 1st line that didn't fit)
    @Test fun stageScrollTablet() = shoot("tablet_6_palco_rolar") {
        StageScreen(stage.copy(look = stage.look.copy(stageMode = br.app.stagebook.data.model.StageMode.SCROLL)), 0, 1, { _, _ -> }, onBack = {})
    }

    @Test fun settingsLyricsScrollTablet() = shoot("tablet_5_ajustes_rolar") {
        HomeScaffold(HomeTab.SETTINGS, {}) {
            SettingsScreen(settings.copy(look = settings.look.copy(stageMode = br.app.stagebook.data.model.StageMode.SCROLL)), SettingsActions(), it, section = SettingsSection.LYRICS)
        }
    }

    // T22: tablature on stage (monospace, fitting the column)
    private val tabSong = "{key: G}\n[Intro]\n{start_of_tab}\ne|-----------------0-----3-----5-----7-----8-----10----12-----|\nB|-------1-----3-----5-----6-----8-----10----12----13---------|\nG|---0-----2-----4-----5-----7-----9-----11----12-------------|\n{end_of_tab}\n\n[Verso 1]\n[G]Saio cedo pela [D]rua sem saber"

    @Test fun stageTabTablet() = shoot("tablet_6_palco_tab") {
        StageScreen(stage.copy(songs = listOf(StageSong("Com tablatura", "Banda Fictícia", tabSong, false))), 0, 0, { _, _ -> }, onBack = {})
    }

    @Test @Config(qualifiers = PHONE)
    fun stageTabPhone() = shoot("celular_7_palco_tab") {
        StageScreen(stage.copy(songs = listOf(StageSong("Com tablatura", "Banda Fictícia", tabSong, false))), 0, 0, { _, _ -> }, onBack = {})
    }

    @Test fun stageTablet() = shoot("tablet_6_palco") { StageScreen(stage, 0, 0, { _, _ -> }, onBack = {}) }

    // made-up chart (no real lyrics): chords above, a chords-only line, a ChordPro section and a comment
    private val chordSong = """
        {key: G}
        {capo: 2}
        [Intro]
        [G] [D] [Em] [C]

        [Verso 1]
        [G]Saio cedo pela [D]rua sem saber
        [Em]Onde a estrada vai [C]dar
        [G]Levo o violão e [D]um papel
        [Em]Pra cantar o que [C]sobrar

        {start_of_chorus}
        [C]Oh, [G]oh, ==[D]canta comigo==
        [C]Até o [D]sol voltar[G]
        {end_of_chorus}
        {c: Solo de sax}
        [Am7] [D7/F#] [G7M]
    """.trimIndent()

    @Test fun stageChordsTablet() = shoot("tablet_6_palco_cifra") {
        StageScreen(stage.copy(songs = listOf(StageSong("Canção de teste", "Banda Fictícia", chordSong, false))), 0, 0, { _, _ -> }, onBack = {})
    }

    // T08/T09: backing track and capo lit, first after the artist; the key after
    private val stageBadges = stage.copy(
        songs = listOf(StageSong("Canção de teste", "Banda Fictícia", chordSong, false, track = "faixa.ogg")),
        look = stage.look.copy(chords = true, playerMode = true),
    )

    @Test fun stageBadgesTablet() = shoot("tablet_6_palco_capo_backing") { StageScreen(stageBadges, 0, 0, { _, _ -> }, onBack = {}) }

    // T19: song with a backing track and the player off in essential mode: the play button and track badge go away
    @Test fun `player off - song with a backing track has no play`() {
        compose.setContent { StageScreen(stageBadges.copy(look = stageBadges.look.copy(playerMode = false)), 0, 0, { _, _ -> }, onBack = {}) }
        compose.onNodeWithContentDescription(str(R.string.stage_play)).assertDoesNotExist()
        compose.onNodeWithContentDescription(str(R.string.stage_track_pick)).assertDoesNotExist()
    }

    @Test fun `player on - song without a backing track shows no track button`() {
        compose.setContent { StageScreen(stage, 0, 0, { _, _ -> }, onBack = {}) }
        compose.onNodeWithContentDescription(str(R.string.stage_track_pick)).assertDoesNotExist()
        compose.onNodeWithContentDescription(str(R.string.stage_play)).assertDoesNotExist()
    }

    @Test fun `player on - song with a backing track shows play`() {
        compose.setContent { StageScreen(stageBadges, 0, 0, { _, _ -> }, onBack = {}) }
        compose.onNodeWithContentDescription(str(R.string.stage_play)).assertExists()
    }

    @Test @Config(qualifiers = PHONE)
    fun stageBadgesPhone() = shoot("celular_7_palco_capo_backing") { StageScreen(stageBadges, 0, 0, { _, _ -> }, onBack = {}) }

    // a tap on "Key G" opens − and + for key and capo, without leaving the stage
    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    @Test @Config(qualifiers = PHONE)
    fun stageKeyCapoPhone() {
        compose.setContent { StagebookTheme { StageScreen(stageBadges, 0, 0, { _, _ -> }, onBack = {}) } }
        compose.onNodeWithContentDescription(str(R.string.stage_key_desc, "G"), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("$SHOTS_DIR/celular_7_palco_tom_capo.png")
    }

    @Test @Config(qualifiers = PHONE)
    fun stageChordsPhone() = shoot("celular_7_palco_cifra") {
        StageScreen(stage.copy(songs = listOf(StageSong("Canção de teste", "Banda Fictícia", chordSong, false))), 0, 0, { _, _ -> }, onBack = {})
    }

    // chord list at the top, with C in a chosen shape (barre on the 3rd): the list shows the chosen one
    private val chordSongDefined = "{define-guitar: C base-fret 3 frets x 1 3 3 3 1}\n" + chordSong

    @Test fun stageChordListTopTablet() = shoot("tablet_6_palco_acordes_topo") {
        StageScreen(
            stage.copy(songs = listOf(StageSong("Canção de teste", "Banda Fictícia", chordSongDefined, false)), look = stage.look.copy(chordList = br.app.stagebook.data.model.ChordListPlace.TOP)),
            0, 0, { _, _ -> }, onBack = {},
        )
    }

    @Test @Config(qualifiers = PHONE)
    fun stageChordListTopPhone() = shoot("celular_7_palco_acordes_topo") {
        StageScreen(
            stage.copy(songs = listOf(StageSong("Canção de teste", "Banda Fictícia", chordSongDefined, false)), look = stage.look.copy(chordList = br.app.stagebook.data.model.ChordListPlace.TOP)),
            0, 0, { _, _ -> }, onBack = {},
        )
    }

    @Test fun stageChordListBottomTablet() = shoot("tablet_7_palco_acordes_fim") {
        StageScreen(
            stage.copy(songs = listOf(StageSong("Canção de teste", "Banda Fictícia", chordSongDefined, false)), look = stage.look.copy(chordList = br.app.stagebook.data.model.ChordListPlace.BOTTOM)),
            0, -1, { _, _ -> }, onBack = {},
        )
    }

    // player mode: no track (the button picks the file), with a track stopped and playing (Backing track icon at the
    // top)
    private val playerLook = Look(fontSp = 34f, playerMode = true)

    @Test fun stagePlayerNoTrackTablet() = shoot("tablet_11_player_sem_faixa") {
        StageScreen(stage.copy(songs = listOf(StageSong("Canção de teste", "Banda Fictícia", chordSong, false, id = "1")), look = playerLook), 0, 0, { _, _ -> }, onBack = {})
    }

    @Test fun stagePlayerTrackTablet() = shoot("tablet_11_player_com_faixa") {
        StageScreen(stage.copy(songs = listOf(StageSong("Canção de teste", "Banda Fictícia", chordSong, false, id = "1", track = "1.ogg")), look = playerLook), 0, 0, { _, _ -> }, onBack = {})
    }

    @Test @Config(qualifiers = PHONE)
    fun stagePlayerPlayingPhone() = shoot("celular_11_player_tocando") {
        StageScreen(stage.copy(songs = listOf(StageSong("Canção de teste", "Banda Fictícia", chordSong, false, id = "1", track = "1.ogg")), look = playerLook), 0, 0, { _, _ -> }, onBack = {}, playing = true)
    }

    // ---- journey: linking a track to a song (phone) ----
    @Test @Config(qualifiers = PHONE)
    fun journeyTrack1Settings() = shoot("jornada_faixa_1_ajustes_player") {
        HomeScaffold(HomeTab.SETTINGS, {}) { SettingsScreen(settings.copy(look = settings.look.copy(playerMode = true)), SettingsActions(), it, section = SettingsSection.PLAYER) }
    }

    // ---- mixer (Settings → Player): a 4-channel file, backing as a stereo pair and mono click on output 7 ----
    private val mixerLook = Look(
        playerMode = true,
        mixer = br.app.stagebook.domain.MixerConfig(
            channels = 4, eqOn = true, eqDb = listOf(3f, 0f, -2f, 1.5f, 0f),
            output = br.app.stagebook.domain.OutputInfo("usb:1234:5678", "Interface USB", 10),
        )
            .withStrip(0) { it.copy(name = "Backing", stereo = true, eq = true, norm = br.app.stagebook.domain.NormMode.VOLUME, gainDb = -3f) }
            .withStrip(2) { it.copy(name = "Click", norm = br.app.stagebook.domain.NormMode.PEAK, center = true) }
            .withOutput(2, 6)
            .withStrip(3) { it.copy(name = "Guia", on = false) },
    )

    // playing: channel meters (the backing's 2nd side a bit lower, the click hitting the peak) and the spectrum
    private val mixerLevels = br.app.stagebook.domain.MixerLevels(
        br.app.stagebook.domain.Meters(
            listOf(-9f, -11f, -4f, -48f, -48f, -48f, -48f, -48f),
            listOf(-6f, -8f, -1.5f, -48f, -48f, -48f, -48f, -48f),
        ),
        List(br.app.stagebook.domain.Spectrum.BINS) { b -> -20f - b * 0.7f + 6f * kotlin.math.cos(b / 5f) },
        live = true,
    )

    @Test @Config(qualifiers = PHONE_TALL)
    fun mixerPhone() = shoot("jornada_mixer_1_celular") {
        HomeScaffold(HomeTab.SETTINGS, {}) { SettingsScreen(settings.copy(look = mixerLook), SettingsActions(mixerLevels = { mixerLevels }, monitoring = true), it, section = SettingsSection.PLAYER) }
    }

    @Test @Config(qualifiers = PHONE)
    fun helpTipOpenPhone() {
        compose.setContent {
            StagebookTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    HomeScaffold(HomeTab.SETTINGS, {}) { SettingsScreen(settings.copy(look = mixerLook), SettingsActions(), it, section = SettingsSection.PLAYER) }
                }
            }
        }
        // the explanation is now the ⓘ next to the card title
        compose.onAllNodesWithContentDescription(str(R.string.help_how))[0].performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("$SHOTS_DIR/jornada_mixer_4_explicacao.png")
    }

    @Test @Config(qualifiers = PHONE)
    fun normTipOpenPhone() {
        compose.setContent {
            StagebookTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.padding(br.app.stagebook.ui.theme.Spacing.l)) {
                        br.app.stagebook.ui.components.MixerChannelForm(2, mixerLook.mixer.strip(2), pair = false, canPair = true, output = mixerLook.mixer.outputOf(2), outputs = mixerLook.mixer.output, onChange = {}, onOutput = {})
                    }
                }
            }
        }
        compose.onAllNodesWithText(str(R.string.mixer_norm_about))[0].performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("$SHOTS_DIR/jornada_mixer_5_sobre_normalizar.png")
    }

    // ---- scanning a setlist QR: review (found, uncertain, missing) ----
    private val scanReview = br.app.stagebook.ui.setlists.ScanUi.Review(
        "Show de sábado",
        listOf(
            br.app.stagebook.ui.setlists.ScanRow("Canção de teste", "", true, listOf(br.app.stagebook.ui.setlists.SongRef("1", "Canção de teste", "Banda Fictícia")), "1"),
            br.app.stagebook.ui.setlists.ScanRow("Noite clara", "", false, listOf(
                br.app.stagebook.ui.setlists.SongRef("2", "Noite Clara", "Banda A"), br.app.stagebook.ui.setlists.SongRef("3", "Noite Clara", "Banda B"),
            ), null),
            br.app.stagebook.ui.setlists.ScanRow("Estrada longa", "", true, listOf(br.app.stagebook.ui.setlists.SongRef("4", "Estrada Longa", "Banda C")), "4"),
            br.app.stagebook.ui.setlists.ScanRow("Música que não tenho", "", false, emptyList(), null),
        ),
    )

    @Test @Config(qualifiers = PHONE)
    fun scanReviewPhone() = shoot("jornada_qr_2_conferir") {
        br.app.stagebook.ui.setlists.ScanSetlistScreen(scanReview, true, {}, {}, {}, { _, _ -> }, {}, { null }, {}, {})
    }

    // queue: importing the missing ones for the setlist, searching the 1st site in the list (on Google, limited to the
    // site)
    @Test @Config(qualifiers = PHONE)
    fun importQueuePhone() = shoot("jornada_qr_4_importar_fila") {
        ImportScreen(
            ImportUiState(), "", {}, {}, {},
            queue = br.app.stagebook.data.ImportQueue.State(
                "x", "Show de sábado", listOf("1", null, "4", null),
                listOf(br.app.stagebook.data.ImportQueue.Item("Música que não tenho", "", 1), br.app.stagebook.data.ImportQueue.Item("Outra que falta", "", 3)), 3,
            ),
            queueSite = { "Cifra Club" },
        )
    }

    // ---- share: the sheet (with and without internet) and the ready code ----
    @Test @Config(qualifiers = PHONE)
    fun shareSheetPhone() = shoot("jornada_compartilhar_1_folha") {
        androidx.compose.foundation.layout.Column {
            br.app.stagebook.ui.share.ShareOptions(12, online = true, onQr = {}, onInternet = {})
            androidx.compose.material3.HorizontalDivider(androidx.compose.ui.Modifier.padding(vertical = br.app.stagebook.ui.theme.Spacing.l))
            br.app.stagebook.ui.share.ShareOptions(12, online = false, onQr = {}, onInternet = {})
        }
    }

    @Test @Config(qualifiers = PHONE)
    fun shareCodePhone() = shoot("jornada_compartilhar_2_codigo") {
        androidx.compose.material3.Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(br.app.stagebook.ui.theme.Spacing.l)) {
                androidx.compose.material3.Text(str(R.string.share_sent), style = MaterialTheme.typography.headlineSmall)
                br.app.stagebook.ui.share.CodeBox(
                    br.app.stagebook.ui.share.CloudUi.Sent(
                        "47-violao-lua", "Show de sábado", 12, 0,
                        br.app.stagebook.domain.QrEncoder.encode(br.app.stagebook.domain.ShareCode.qrText("47-violao-lua").toByteArray(), 600).asImageBitmap(),
                        joined = listOf("Celular da Ana"),
                    ),
                )
            }
        }
    }

    @Test @Config(qualifiers = PHONE)
    fun receiveCodeReviewPhone() = shoot("jornada_compartilhar_3_receber") {
        br.app.stagebook.ui.setlists.ScanSetlistScreen(
            scanReview.copy(rows = scanReview.rows.mapIndexed { i, r -> if (i == 3) r.copy(carried = true) else r }),
            true, {}, {}, {}, { _, _ -> }, {}, { null }, {}, {}, byCode = true,
        )
    }

    // ---- loose songs: selection on the Songs tab and the receiver ----
    @Test @Config(qualifiers = PHONE)
    fun songsSelectPhone() = shoot("jornada_musicas_1_selecao") {
        HomeScaffold(HomeTab.SONGS, {}) {
            br.app.stagebook.ui.songs.SongsScreen(songs, "", {}, {}, {}, {}, it, initialSelection = songs.songs.take(2).map { s -> s.id })
        }
    }

    // the Import menu open on the Songs tab: sites, QR and code
    @Test @Config(qualifiers = PHONE)
    fun importMenuPhone() {
        compose.setContent {
            StagebookTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    HomeScaffold(HomeTab.SONGS, {}) { br.app.stagebook.ui.songs.SongsScreen(songs, "", {}, {}, {}, {}, it, received = 2, onSync = {}) }
                }
            }
        }
        compose.onAllNodesWithText(str(R.string.import_menu))[0].performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("$SHOTS_DIR/jornada_importar_menu.png")
    }

    @Test @Config(qualifiers = PHONE)
    fun songsReceivedPhone() = shoot("jornada_musicas_2_recebidas") {
        br.app.stagebook.ui.setlists.ScanSetlistScreen(
            scanReview.copy(
                name = "", songsOnly = true,
                rows = scanReview.rows.mapIndexed { i, r -> if (i == 1 || i == 3) r.copy(carried = true) else r },
            ),
            true, {}, {}, {}, { _, _ -> }, {}, { null }, {}, {}, byCode = true,
        )
    }

    // ---- band on the local network: Settings → Band (on the network: one in the band, one request waiting, one new)
    // ----
    private val bandSample = br.app.stagebook.ui.band.BandUi(
        name = "Meu tablet", wifi = true, searching = true,
        nearby = listOf(
            br.app.stagebook.ui.band.NearbyUi("1", "Celular da Ana", member = true),
            br.app.stagebook.ui.band.NearbyUi("2", "Teclado do Caio", member = false, join = br.app.stagebook.data.BandLink.Join.WAITING),
            br.app.stagebook.ui.band.NearbyUi("3", "Baixo", member = false),
            br.app.stagebook.ui.band.NearbyUi("4", "Bateria", member = true, otherVersion = "0.2.90"),
        ),
        members = listOf(br.app.stagebook.ui.band.MemberUi("1", "Celular da Ana", 0), br.app.stagebook.ui.band.MemberUi("4", "Bateria", 0)),
    )

    @Test @Config(qualifiers = "pt-rBR-w412dp-h1600dp-port-xxhdpi")
    fun bandPhone() = shoot("jornada_banda_1_ajustes") {
        HomeScaffold(HomeTab.SETTINGS, {}) { SettingsScreen(settings, SettingsActions(band = bandSample), it, section = SettingsSection.BAND) }
    }

    @Test @Config(qualifiers = PHONE)
    fun bandListPhone() = shoot("jornada_banda_2_lista_ajustes") {
        HomeScaffold(HomeTab.SETTINGS, {}) { SettingsScreen(settings, SettingsActions(band = bandSample), it) }
    }

    // ---- part 2: sending to the band (sheet) and the notice on the tabs with the number on the icon ----
    @Test @Config(qualifiers = PHONE)
    fun shareBandPhone() = shoot("jornada_banda_3_compartilhar") {
        br.app.stagebook.ui.share.ShareOptions(12, online = true, onQr = {}, onInternet = {}, band = 2)
    }

    @Test @Config(qualifiers = PHONE)
    fun inboxBannerPhone() = shoot("jornada_banda_4_aviso") {
        // the band's count next to the title, with the leaf of essential mode (user, 01/11: icons, not strips)
        androidx.compose.runtime.CompositionLocalProvider(br.app.stagebook.ui.components.LocalHomeStatus provides br.app.stagebook.ui.components.HomeStatus(true, 3, {}, {})) {
            HomeScaffold(HomeTab.SETLISTS, {}, badges = mapOf(HomeTab.SETLISTS to 1, HomeTab.SONGS to 2)) {
                SetlistsScreen(SetlistsUiState(false, listOf(SetlistRowUi("1", "Show de sábado", 15), SetlistRowUi("2", "18/10/2026 20:30", 6))), {}, {}, {}, {}, it)
            }
        }
    }


    @Test @Config(qualifiers = PHONE)
    fun scanAskBandPhone() = shoot("jornada_banda_5_pedir") {
        br.app.stagebook.ui.setlists.ScanSetlistScreen(
            scanReview.copy(rows = scanReview.rows + br.app.stagebook.ui.setlists.ScanRow("Outra que faltava", "", false, emptyList(), null, carried = true, fromBand = true)),
            true, {}, {}, {}, { _, _ -> }, {}, { null }, {}, {}, band = 2,
        )
    }

    @Test @Config(qualifiers = PHONE)
    fun scanVersionsPhone() = shoot("jornada_conferencia_versoes") {
        br.app.stagebook.ui.setlists.ScanSetlistScreen(
            scanReview.copy(
                name = "", songsOnly = true,
                rows = listOf(
                    br.app.stagebook.ui.setlists.ScanRow("Canção de teste", "", true, emptyList(), "1", differs = true),
                    br.app.stagebook.ui.setlists.ScanRow("Estrada longa", "", true, emptyList(), "4", differs = true, useIncoming = true),
                    br.app.stagebook.ui.setlists.ScanRow("Noite clara", "", true, emptyList(), "2"),
                    br.app.stagebook.ui.setlists.ScanRow("Música nova", "", false, emptyList(), null, carried = true, fromBand = true),
                ),
            ),
            true, {}, {}, {}, { _, _ -> }, {}, { null }, {}, {}, fromName = "Celular da Ana",
        )
    }

    @Test @Config(qualifiers = PHONE)
    fun scanLyricsOnlyPhone() = shoot("jornada_conferencia_so_letra") {
        br.app.stagebook.ui.setlists.ScanSetlistScreen(
            scanReview.copy(
                rows = listOf(
                    br.app.stagebook.ui.setlists.ScanRow("Canção de teste", "", true, emptyList(), "1"),
                    br.app.stagebook.ui.setlists.ScanRow("Canção de exemplo", "", false, emptyList(), null, carried = true, fromBand = true, lyricsOnly = true),
                    br.app.stagebook.ui.setlists.ScanRow("Outra canção", "", false, emptyList(), null, wantChords = true),
                ),
            ),
            true, {}, {}, {}, { _, _ -> }, {}, { null }, {}, {}, fromName = "Celular da Ana",
        )
    }

    @Test @Config(qualifiers = PHONE)
    fun scanCreateWarningPhone() {
        compose.setContent {
            StagebookTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    br.app.stagebook.ui.setlists.ScanSetlistScreen(scanReview, true, {}, {}, {}, { _, _ -> }, {}, { null }, {}, {})
                }
            }
        }
        compose.onAllNodesWithText(str(R.string.scan_create))[0].performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("$SHOTS_DIR/jornada_conferencia_criar_aviso.png")
    }

    @Test @Config(qualifiers = PHONE)
    fun scanCameraPhone() = shoot("jornada_qr_1_camera") {
        br.app.stagebook.ui.setlists.ScanSetlistScreen(
            br.app.stagebook.ui.setlists.ScanUi.Scanning(br.app.stagebook.ui.setlists.ScanProblem.INCOMPLETE), true, {}, {}, {}, { _, _ -> }, {}, { null }, {}, {},
        )
    }

    @Test fun scanReviewTablet() = shoot("jornada_qr_3_conferir_tablet") {
        br.app.stagebook.ui.setlists.ScanSetlistScreen(scanReview, true, {}, {}, {}, { _, _ -> }, {}, { null }, {}, {})
    }

    @Test @Config(qualifiers = PHONE)
    fun mixerEditPhone() = shoot("jornada_mixer_3_editar_canal") {
        androidx.compose.material3.Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(br.app.stagebook.ui.theme.Spacing.l)) {
                androidx.compose.material3.Text(str(R.string.mixer_channel, 3), style = MaterialTheme.typography.headlineSmall)
                br.app.stagebook.ui.components.MixerChannelForm(2, mixerLook.mixer.strip(2), pair = false, canPair = true, output = mixerLook.mixer.outputOf(2), outputs = mixerLook.mixer.output, onChange = {}, onOutput = {})
            }
        }
    }

    @Test @Config(qualifiers = TABLET_TALL)
    fun mixerTablet() = shoot("jornada_mixer_2_tablet") {
        HomeScaffold(HomeTab.SETTINGS, {}) { SettingsScreen(settings.copy(look = mixerLook), SettingsActions(mixerLevels = { mixerLevels }, monitoring = true), it, section = SettingsSection.PLAYER) }
    }

    // the song's mixer and equalizer, opened over the stage (user, 01/11): the sheet as it opens, and its whole content
    @Test fun songMixerSheetTablet() {
        compose.setContent {
            StagebookTheme {
                StageScreen(stage.copy(songs = listOf(StageSong("Canção de teste", "Banda Fictícia", chordSong, false, id = "1", track = "1.ogg")), look = mixerLook), 0, 0, { _, _ -> }, onBack = {})
            }
        }
        compose.onNodeWithContentDescription(str(R.string.stage_play)).performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText(str(R.string.stage_mixer_open)).performClick()
        compose.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("$SHOTS_DIR/jornada_eq_musica_1_aberto.png")
    }

    @Test @Config(qualifiers = TABLET_TALL)
    fun songMixerContentTablet() = shoot("jornada_eq_musica_2_inteiro") {
        androidx.compose.foundation.layout.Column(
            androidx.compose.ui.Modifier.padding(br.app.stagebook.ui.theme.Spacing.m),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(br.app.stagebook.ui.theme.Spacing.m),
        ) {
            androidx.compose.material3.Text(str(R.string.stage_mix_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            br.app.stagebook.ui.settings.MixerCard(mixerLook.mixer, {}, { mixerLevels }, true, {})
            br.app.stagebook.ui.settings.EqCard(mixerLook.mixer, {}, { mixerLevels }, true)
        }
    }

    // on the global mixer: the controls dim, the live meters keep their colour (user, 01/11)
    @Test @Config(qualifiers = TABLET_TALL)
    fun songMixerLockedTablet() = shoot("jornada_eq_musica_3_global_niveis") {
        androidx.compose.runtime.CompositionLocalProvider(br.app.stagebook.ui.components.LocalMixerLocked provides true) {
            androidx.compose.foundation.layout.Column(
                androidx.compose.ui.Modifier.padding(br.app.stagebook.ui.theme.Spacing.m),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(br.app.stagebook.ui.theme.Spacing.m),
            ) {
                br.app.stagebook.ui.settings.MixerCard(mixerLook.mixer, {}, { mixerLevels }, true, {}, monitorSwitch = false)
                br.app.stagebook.ui.settings.EqCard(mixerLook.mixer, {}, { mixerLevels }, true)
            }
        }
    }

    @Test @Config(qualifiers = PHONE)
    fun journeyTrack2NoTrack() = shoot("jornada_faixa_2_palco_sem_faixa") {
        StageScreen(stage.copy(songs = listOf(StageSong("Canção de teste", "Banda Fictícia", chordSong, false, id = "1")), look = playerLook), 0, 0, { _, _ -> }, onBack = {})
    }

    @Test @Config(qualifiers = PHONE)
    fun journeyTrack3WithTrack() = shoot("jornada_faixa_3_palco_com_faixa") {
        StageScreen(stage.copy(songs = listOf(StageSong("Canção de teste", "Banda Fictícia", chordSong, false, id = "1", track = "1.ogg")), look = playerLook), 0, 0, { _, _ -> }, onBack = {})
    }

    /** Long press on play: the menu to change or remove the track (opens in another window). */
    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    @Test @Config(qualifiers = PHONE)
    fun journeyTrack4Menu() {
        compose.setContent {
            StagebookTheme {
                StageScreen(stage.copy(songs = listOf(StageSong("Canção de teste", "Banda Fictícia", chordSong, false, id = "1", track = "1.ogg")), look = playerLook), 0, 0, { _, _ -> }, onBack = {})
            }
        }
        compose.onNodeWithContentDescription(str(R.string.stage_play)).performTouchInput { longClick() }
        compose.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("$SHOTS_DIR/jornada_faixa_4_menu.png")
    }

    @Test fun stageLyricsOnlyTablet() = shoot("tablet_6_palco_so_letra") {
        StageScreen(stage.copy(songs = listOf(StageSong("Canção de teste", "Banda Fictícia", chordSong, false)), look = stage.look.copy(chords = false)), 0, 0, { _, _ -> }, onBack = {})
    }

    @Test fun stageTabletOneColumn() = shoot("tablet_6_palco_1coluna") {
        StageScreen(stage.copy(look = stage.look.copy(columns = br.app.stagebook.data.model.StageColumns.ONE)), 0, 0, { _, _ -> })
    }

    @Test fun stageTabletLastPage() = shoot("tablet_7_palco_ultima") { StageScreen(stage, 0, -1, { _, _ -> }, onBack = {}) }

    // Fire in portrait
    @Test @Config(qualifiers = TABLET_PORTRAIT)
    fun homeSongsTabletPortrait() = shoot("tablet_em_pe_1_musicas") { HomeScaffold(HomeTab.SONGS, {}) { Songs(it) } }

    @Test @Config(qualifiers = TABLET_PORTRAIT)
    fun stageTabletPortrait() = shoot("tablet_em_pe_6_palco") { StageScreen(stage, 0, 0, { _, _ -> }, onBack = {}) }

    @Test @Config(qualifiers = PHONE)
    fun stagePhone() = shoot("celular_7_palco") { StageScreen(stage, 0, 1, { _, _ -> }, onBack = {}) }

    // the tag filter open: several ticked at once, and with large system text (user, 01/11)
    private val tagged = songs.copy(tags = listOf("Abertura de show", "Internacional", "Lenta", "Nacional", "Rock anos 80"), chosenTags = setOf("Nacional", "Rock anos 80"))

    private fun tagFilterOpen(name: String, fontScale: Float) {
        compose.setContent {
            val d = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, fontScale)) {
                StagebookTheme { Surface(color = MaterialTheme.colorScheme.background) { SongsScreen(tagged, "", {}, {}, {}, {}) } }
            }
        }
        compose.onNodeWithText(if (fontScale > 0f && compose.onAllNodesWithText("2 tags").fetchSemanticsNodes().isNotEmpty()) "2 tags" else "Nacional +1").performClick()
        compose.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("$SHOTS_DIR/$name.png")
    }

    @Test fun tagFilterOpenTablet() = tagFilterOpen("tablet_1_musicas_tags_abertas", 1f)

    @Test fun tagFilterOpenTabletBigText() = tagFilterOpen("tablet_1_musicas_tags_abertas_texto_grande", 1.6f)

    @Test @Config(qualifiers = PHONE)
    fun tagFilterOpenPhoneBigText() = tagFilterOpen("celular_1_musicas_tags_abertas_texto_grande", 1.6f)

}

// Fire HD 10 (2019, 9th gen): 1920x1200 at 1.5x (hdpi) = 1280x800 dp; the app hides the bars
// texts by id: the same test clicks the right thing with the app in Portuguese or English (captures folder, T39)
private fun str(id: Int, vararg args: Any): String =
    androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().getString(id, *args)

// where the captures go and in which language (-PshotsDir, -PshotsLang; empty = the usual folder, Portuguese)
private val SHOTS_DIR = System.getProperty("shots.dir")?.takeIf { it.isNotEmpty() } ?: "build/outputs/roborazzi"
private val SHOTS_LANG = System.getProperty("shots.lang")?.takeIf { it.isNotEmpty() }

private const val TABLET = "pt-rBR-w1280dp-h800dp-land-hdpi"
private const val TABLET_PORTRAIT = "pt-rBR-w800dp-h1280dp-port-hdpi"
private const val PHONE = "pt-rBR-w412dp-h915dp-port-xxhdpi"
private const val PHONE_TALL = "pt-rBR-w412dp-h3000dp-port-xxhdpi"     // the whole screen, no scrolling
private const val TABLET_TALL = "pt-rBR-w1280dp-h2000dp-port-hdpi"
