package com.juliano.stagebook

import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.juliano.stagebook.data.model.Look
import com.juliano.stagebook.domain.PedalAction
import com.juliano.stagebook.ui.editor.EditorMode
import com.juliano.stagebook.ui.importer.ImportScreen
import com.juliano.stagebook.ui.importer.ImportUiState
import com.juliano.stagebook.ui.editor.SongEditorScreen
import com.juliano.stagebook.ui.editor.SongEditorUiState
import com.juliano.stagebook.ui.home.HomeScaffold
import com.juliano.stagebook.ui.home.HomeTab
import com.juliano.stagebook.ui.setlists.SetItemUi
import com.juliano.stagebook.ui.setlists.SetlistDetailScreen
import com.juliano.stagebook.ui.setlists.SetlistDetailUiState
import com.juliano.stagebook.ui.setlists.SetlistRowUi
import com.juliano.stagebook.ui.setlists.SetlistsScreen
import com.juliano.stagebook.ui.setlists.SetlistsUiState
import com.juliano.stagebook.ui.settings.PedalKeyUi
import com.juliano.stagebook.ui.settings.SettingsActions
import com.juliano.stagebook.ui.settings.SettingsScreen
import com.juliano.stagebook.ui.settings.SettingsSection
import com.juliano.stagebook.ui.settings.SettingsUiState
import com.juliano.stagebook.ui.songs.SongRowUi
import com.juliano.stagebook.ui.songs.SongsScreen
import com.juliano.stagebook.ui.songs.SongsUiState
import com.juliano.stagebook.ui.stage.StageScreen
import com.juliano.stagebook.ui.stage.StageSong
import com.juliano.stagebook.ui.stage.StageUiState
import com.juliano.stagebook.ui.theme.StagebookTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Fotos das telas para conferir o visual sem aparelho: `./gradlew recordRoborazziDebug`
 * grava em app/build/outputs/roborazzi. Tablet = Fire HD 10 deitado; celular = S26.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = TABLET, application = android.app.Application::class)
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

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
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Composable
    private fun Songs(modifier: Modifier = Modifier) = SongsScreen(songs, "", {}, {}, {}, {}, {}, {}, modifier)

    @Test fun homeSongsTablet() = shoot("tablet_1_musicas") { HomeScaffold(HomeTab.SONGS, {}) { Songs(it) } }

    // sem internet: Importar indisponível, com a nuvem cortada e o aviso
    @Test @Config(qualifiers = PHONE)
    fun homeSongsOfflinePhone() = shoot("celular_1_musicas_sem_internet") {
        HomeScaffold(HomeTab.SONGS, {}) { SongsScreen(songs, "", {}, {}, {}, {}, {}, {}, it, online = false) }
    }

    @Test @Config(qualifiers = PHONE)
    fun settingsPhoneImportSites() = shoot("celular_5_ajustes_importacao") {
        HomeScaffold(HomeTab.SETTINGS, {}) { SettingsScreen(settings, SettingsActions(), it, section = SettingsSection.IMPORT) }
    }

    @Test @Config(qualifiers = PHONE)
    fun homeSongsPhone() = shoot("celular_1_musicas") { HomeScaffold(HomeTab.SONGS, {}) { Songs(it) } }

    @Test fun setlistsTablet() = shoot("tablet_2_setlists") {
        HomeScaffold(HomeTab.SETLISTS, {}) {
            SetlistsScreen(SetlistsUiState(false, listOf(SetlistRowUi("1", "Show de sábado", 15), SetlistRowUi("2", "18/10/2026 20:30", 6))), {}, {}, {}, {}, { _, _ -> }, it)
        }
    }

    @Test @Config(qualifiers = PHONE)
    fun setlistsPhone() = shoot("celular_2_setlists") {
        HomeScaffold(HomeTab.SETLISTS, {}) {
            SetlistsScreen(SetlistsUiState(false, listOf(SetlistRowUi("1", "Show de sábado", 15), SetlistRowUi("2", "18/10/2026 20:30", 6))), {}, {}, {}, {}, { _, _ -> }, it)
        }
    }

    @Test fun setlistDetailTablet() = shoot("tablet_3_setlist") {
        SetlistDetailScreen(set, null, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, { _, _ -> }, {})
    }

    @Test @Config(qualifiers = PHONE)
    fun setlistDetailPhone() = shoot("celular_3_setlist") {
        SetlistDetailScreen(set, null, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, { _, _ -> }, {})
    }

    // importador: a página da web fica em branco no teste (WebView sem rede); confere a barra de cima e o botão
    @Test fun importTablet() = shoot("tablet_10_importar") { ImportScreen(ImportUiState(), "Hazard", {}, {}, {}) }

    @Test @Config(qualifiers = PHONE)
    fun importPhone() = shoot("celular_10_importar") { ImportScreen(ImportUiState(), "Hazard", {}, {}, {}) }

    @Test @Config(qualifiers = PHONE)
    fun editorPhone() = shoot("celular_4_editor") {
        SongEditorScreen(SongEditorUiState(false, EditorMode.IMPORTED_NEW, "Hazard", "Richard Marx", "1991", lyrics), {}, {}, {}, {}, {}, {}, {})
    }

    /** Editando a letra: a faixa de atalhos das seções aparece no topo. */
    @Test @Config(qualifiers = PHONE)
    fun editorChordsPhone() = shoot("celular_4_editor_cifra") {
        SongEditorScreen(SongEditorUiState(false, EditorMode.EDIT, "Canção de teste", "Banda Fictícia", "", chordSong), {}, {}, {}, {}, {}, {}, {}, cursor = 0)
    }

    // perfil só letra: o editor não mostra nada de cifra
    @Test @Config(qualifiers = PHONE)
    fun editorLyricsOnlyPhone() = shoot("celular_4_editor_so_letra") {
        SongEditorScreen(SongEditorUiState(false, EditorMode.EDIT, "Canção de teste", "Banda Fictícia", "", chordSong), {}, {}, {}, {}, {}, {}, {}, cursor = 0, look = Look(chords = false))
    }

    @Test fun editorSectionsTablet() = shoot("tablet_4_editor_secoes") {
        SongEditorScreen(SongEditorUiState(false, EditorMode.EDIT, "Hazard", "Richard Marx", "1991", lyrics), {}, {}, {}, {}, {}, {}, {}, cursor = 0)
    }

    /** Desenho do acorde tocado no palco (outra janela), em cada instrumento. */
    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    private fun chordPopup(name: String, chord: String, instrument: com.juliano.stagebook.data.model.Instrument) {
        compose.setContent {
            StagebookTheme {
                StageScreen(stage.copy(songs = listOf(StageSong("Canção de teste", "Banda Fictícia", chordSong, false))), 0, 0, { _, _ -> }, onBack = {})
                com.juliano.stagebook.ui.stage.ChordShapePopup(chord, instrument, chordSong, {}, {})
            }
        }
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test fun chordPopupGuitar() = chordPopup("tablet_6_acorde_violao", "F", com.juliano.stagebook.data.model.Instrument.VIOLAO)
    @Test fun chordPopupUkulele() = chordPopup("tablet_6_acorde_ukulele", "G", com.juliano.stagebook.data.model.Instrument.UKULELE)
    @Test fun chordPopupKeyboard() = chordPopup("tablet_6_acorde_teclado", "Am7", com.juliano.stagebook.data.model.Instrument.TECLADO)

    /** Versão nova do app oferecida ao abrir (outra janela). */
    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    @Test fun updateOfferTablet() {
        compose.setContent {
            StagebookTheme {
                Surface(color = MaterialTheme.colorScheme.background) { HomeScaffold(HomeTab.SONGS, {}) { Songs(it) } }
                com.juliano.stagebook.ui.AppUpdateDialog(com.juliano.stagebook.data.UpdateOffer.Available("0.3.101 (abc1234)"), {}, {}, {}, {})
            }
        }
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/tablet_8_versao_nova.png")
    }

    @Test fun pickSongsTablet() = shoot("tablet_9_escolher_musicas") {
        com.juliano.stagebook.ui.setlists.PickSongsScreen(
            com.juliano.stagebook.ui.setlists.PickSongsUiState(
                setName = "Show de sábado",
                rows = songs.songs.map { com.juliano.stagebook.ui.setlists.PickRowUi(it.id, it.title, it.subtitle, inSet = it.id == "1", selected = it.id == "2" || it.id == "3") },
                selectedCount = 2,
            ),
            "", {}, {}, {}, {},
        )
    }

    @Test @Config(qualifiers = PHONE)
    fun pickSongsPhone() = shoot("celular_9_escolher_musicas") {
        com.juliano.stagebook.ui.setlists.PickSongsScreen(
            com.juliano.stagebook.ui.setlists.PickSongsUiState(
                setName = "Show de sábado",
                rows = songs.songs.map { com.juliano.stagebook.ui.setlists.PickRowUi(it.id, it.title, it.subtitle, inSet = it.id == "1", selected = it.id == "2" || it.id == "3") },
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

    @Test fun settingsUpdateTablet() = shoot("tablet_5_ajustes_atualizar") {
        HomeScaffold(HomeTab.SETTINGS, {}) {
            SettingsScreen(
                settings.copy(update = com.juliano.stagebook.ui.settings.UpdateState.Available("0.3.52 (9f1c2ab)")),
                SettingsActions(),
                it,
                section = com.juliano.stagebook.ui.settings.SettingsSection.UPDATE,
            )
        }
    }

    private val settings = SettingsUiState(
        false, Look(pedalName = "PageFlip Firefly", pedalKeys = mapOf(android.view.KeyEvent.KEYCODE_PAGE_UP to PedalAction.PREVIOUS_SONG, com.juliano.stagebook.domain.MidiCode.of(0xB0, 64) to PedalAction.NEXT)), 42, 3, PedalKeyUi("PAGE_DOWN", PedalAction.NEXT, "PageFlip Firefly", android.view.KeyEvent.KEYCODE_PAGE_DOWN), "0.2.8", pedalConnected = true,
    )

    @Test @Config(qualifiers = PHONE)
    fun settingsPhoneList() = shoot("celular_5_ajustes") { HomeScaffold(HomeTab.SETTINGS, {}) { SettingsScreen(settings, SettingsActions(), it) } }

    @Test @Config(qualifiers = PHONE)
    fun settingsPhonePedal() = shoot("celular_5_ajustes_pedal") {
        HomeScaffold(HomeTab.SETTINGS, {}) { SettingsScreen(settings, SettingsActions(), it, section = SettingsSection.PEDAL) }
    }

    @Test @Config(qualifiers = PHONE)
    fun settingsPhoneChords() = shoot("celular_5_ajustes_cifras") {
        HomeScaffold(HomeTab.SETTINGS, {}) { SettingsScreen(settings, SettingsActions(), it, section = SettingsSection.CHORDS) }
    }

    // tela inteira de Ajustes → Cifras (alta, sem rolar): instrumento, desenho ao tocar e lista de acordes
    @Test @Config(qualifiers = "w412dp-h2200dp-port-xxhdpi")
    fun settingsPhoneChordsFull() = shoot("celular_5_ajustes_cifras_inteira") {
        HomeScaffold(HomeTab.SETTINGS, {}) {
            SettingsScreen(settings.copy(look = settings.look.copy(chordList = com.juliano.stagebook.data.model.ChordListPlace.TOP)), SettingsActions(), it, section = SettingsSection.CHORDS)
        }
    }

    // acorde preto no fundo preto: o aviso de ilegível aparece
    @Test @Config(qualifiers = PHONE)
    fun settingsPhoneChordsIllegible() = shoot("celular_5_ajustes_cifras_ilegivel") {
        val dark = settings.copy(look = settings.look.copy(chordColor = 0xFF000000.toInt()))
        HomeScaffold(HomeTab.SETTINGS, {}) { SettingsScreen(dark, SettingsActions(), it, section = SettingsSection.CHORDS) }
    }

    @Test @Config(qualifiers = PHONE)
    fun settingsPhoneLanguage() = shoot("celular_5_ajustes_lingua") {
        HomeScaffold(HomeTab.SETTINGS, {}) { SettingsScreen(settings, SettingsActions(), it, section = SettingsSection.LANGUAGE) }
    }

    // pedal MIDI: um USB ligado e um Bluetooth guardado fora do alcance; teste de botão com uma mensagem MIDI
    @Test @Config(qualifiers = PHONE)
    fun settingsPhoneMidi() = shoot("celular_5_ajustes_pedal_midi") {
        val code = com.juliano.stagebook.domain.MidiCode.of(0xB0, 64)
        val midi = settings.copy(
            look = settings.look.copy(midiBtAddress = "AA:BB", midiBtName = "AirTurn BT200"),
            lastKey = PedalKeyUi("", PedalAction.NEXT, "FS-1 USB MIDI", code),
            midi = com.juliano.stagebook.data.MidiState(
                connected = listOf(com.juliano.stagebook.data.MidiDeviceUi("FS-1 USB MIDI", bluetooth = false)),
                searched = true,
                found = listOf(com.juliano.stagebook.data.MidiFound("AirTurn BT200", "AA:BB")),
            ),
        )
        HomeScaffold(HomeTab.SETTINGS, {}) { SettingsScreen(midi, SettingsActions(), it, section = SettingsSection.PEDAL) }
    }

    @Test @Config(qualifiers = PHONE)
    fun settingsPhoneLyrics() = shoot("celular_5_ajustes_letra") {
        HomeScaffold(HomeTab.SETTINGS, {}) { SettingsScreen(settings.copy(look = settings.look.copy(fontSp = 72f)), SettingsActions(), it, section = SettingsSection.LYRICS) }
    }

    private val stage = StageUiState(
        false,
        listOf(StageSong("Hazard", "Richard Marx · 1991", (1..4).joinToString("\n\n") { lyrics }, false), StageSong("Malandragem", "", "", false)),
        look = Look(fontSp = 34f),
    )

    @Test fun stageTablet() = shoot("tablet_6_palco") { StageScreen(stage, 0, 0, { _, _ -> }, onBack = {}) }

    // cifra inventada (nada de letra de verdade): acordes em cima, linha só de acordes, seção ChordPro e comentário
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
        [C]Oh, [G]oh, [D]canta comigo
        [C]Até o [D]sol voltar[G]
        {end_of_chorus}
        {c: Solo de sax}
        [Am7] [D7/F#] [G7M]
    """.trimIndent()

    @Test fun stageChordsTablet() = shoot("tablet_6_palco_cifra") {
        StageScreen(stage.copy(songs = listOf(StageSong("Canção de teste", "Banda Fictícia", chordSong, false))), 0, 0, { _, _ -> }, onBack = {})
    }

    @Test @Config(qualifiers = PHONE)
    fun stageChordsPhone() = shoot("celular_7_palco_cifra") {
        StageScreen(stage.copy(songs = listOf(StageSong("Canção de teste", "Banda Fictícia", chordSong, false))), 0, 0, { _, _ -> }, onBack = {})
    }

    // lista de acordes no topo, com o C numa variação escolhida (pestana na 3ª): a lista mostra a escolhida
    private val chordSongDefined = "{define-guitar: C base-fret 3 frets x 1 3 3 3 1}\n" + chordSong

    @Test fun stageChordListTopTablet() = shoot("tablet_6_palco_acordes_topo") {
        StageScreen(
            stage.copy(songs = listOf(StageSong("Canção de teste", "Banda Fictícia", chordSongDefined, false)), look = stage.look.copy(chordList = com.juliano.stagebook.data.model.ChordListPlace.TOP)),
            0, 0, { _, _ -> }, onBack = {},
        )
    }

    @Test @Config(qualifiers = PHONE)
    fun stageChordListTopPhone() = shoot("celular_7_palco_acordes_topo") {
        StageScreen(
            stage.copy(songs = listOf(StageSong("Canção de teste", "Banda Fictícia", chordSongDefined, false)), look = stage.look.copy(chordList = com.juliano.stagebook.data.model.ChordListPlace.TOP)),
            0, 0, { _, _ -> }, onBack = {},
        )
    }

    @Test fun stageChordListBottomTablet() = shoot("tablet_7_palco_acordes_fim") {
        StageScreen(
            stage.copy(songs = listOf(StageSong("Canção de teste", "Banda Fictícia", chordSongDefined, false)), look = stage.look.copy(chordList = com.juliano.stagebook.data.model.ChordListPlace.BOTTOM)),
            0, -1, { _, _ -> }, onBack = {},
        )
    }

    // modo player: sem faixa (o botão escolhe o arquivo), com faixa parada e tocando (ícone de Backing track no topo)
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

    // ---- jornada: ligar uma faixa a uma música (celular) ----
    @Test @Config(qualifiers = PHONE)
    fun journeyTrack1Settings() = shoot("jornada_faixa_1_ajustes_player") {
        HomeScaffold(HomeTab.SETTINGS, {}) { SettingsScreen(settings.copy(look = settings.look.copy(playerMode = true)), SettingsActions(), it, section = SettingsSection.PLAYER) }
    }

    @Test @Config(qualifiers = PHONE)
    fun journeyTrack2NoTrack() = shoot("jornada_faixa_2_palco_sem_faixa") {
        StageScreen(stage.copy(songs = listOf(StageSong("Canção de teste", "Banda Fictícia", chordSong, false, id = "1")), look = playerLook), 0, 0, { _, _ -> }, onBack = {})
    }

    @Test @Config(qualifiers = PHONE)
    fun journeyTrack3WithTrack() = shoot("jornada_faixa_3_palco_com_faixa") {
        StageScreen(stage.copy(songs = listOf(StageSong("Canção de teste", "Banda Fictícia", chordSong, false, id = "1", track = "1.ogg")), look = playerLook), 0, 0, { _, _ -> }, onBack = {})
    }

    /** Toque longo no play: o menu de trocar ou tirar a faixa (abre em outra janela). */
    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    @Test @Config(qualifiers = PHONE)
    fun journeyTrack4Menu() {
        compose.setContent {
            StagebookTheme {
                StageScreen(stage.copy(songs = listOf(StageSong("Canção de teste", "Banda Fictícia", chordSong, false, id = "1", track = "1.ogg")), look = playerLook), 0, 0, { _, _ -> }, onBack = {})
            }
        }
        compose.onNodeWithContentDescription("Tocar o backing track").performTouchInput { longClick() }
        compose.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/jornada_faixa_4_menu.png")
    }

    @Test fun stageLyricsOnlyTablet() = shoot("tablet_6_palco_so_letra") {
        StageScreen(stage.copy(songs = listOf(StageSong("Canção de teste", "Banda Fictícia", chordSong, false)), look = stage.look.copy(chords = false)), 0, 0, { _, _ -> }, onBack = {})
    }

    @Test fun stageTabletOneColumn() = shoot("tablet_6_palco_1coluna") {
        StageScreen(stage.copy(look = stage.look.copy(columns = com.juliano.stagebook.data.model.StageColumns.ONE)), 0, 0, { _, _ -> })
    }

    @Test fun stageTabletLastPage() = shoot("tablet_7_palco_ultima") { StageScreen(stage, 0, -1, { _, _ -> }, onBack = {}) }

    // Fire em pé
    @Test @Config(qualifiers = TABLET_EM_PE)
    fun homeSongsTabletPortrait() = shoot("tablet_em_pe_1_musicas") { HomeScaffold(HomeTab.SONGS, {}) { Songs(it) } }

    @Test @Config(qualifiers = TABLET_EM_PE)
    fun stageTabletPortrait() = shoot("tablet_em_pe_6_palco") { StageScreen(stage, 0, 0, { _, _ -> }, onBack = {}) }

    @Test @Config(qualifiers = PHONE)
    fun stagePhone() = shoot("celular_7_palco") { StageScreen(stage, 0, 1, { _, _ -> }, onBack = {}) }
}

// Fire HD 10 (2019, 9ª geração): 1920x1200 a 1,5x (hdpi) = 1280x800 dp; o app esconde as barras
private const val TABLET = "w1280dp-h800dp-land-hdpi"
private const val TABLET_EM_PE = "w800dp-h1280dp-port-hdpi"
private const val PHONE = "w412dp-h915dp-port-xxhdpi"
