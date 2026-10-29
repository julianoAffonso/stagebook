package com.juliano.stagebook

import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onAllNodesWithText
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

    // T18: com capo, o editor diz o desenho e o tom que soa
    @Test fun editorCapoTablet() = shoot("tablet_4_editor_capo") {
        SongEditorScreen(SongEditorUiState(false, EditorMode.EDIT, "Canção de teste", "Banda Fictícia", "", chordSong), {}, {}, {}, {}, {}, {}, {}, cursor = 0)
    }

    // T27: dados da música abertos (música nova)
    @Test fun editorDataTablet() = shoot("tablet_4_editor_dados") {
        SongEditorScreen(SongEditorUiState(false, EditorMode.NEW, "", "", "", chordSong), {}, {}, {}, {}, {}, {}, {})
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

    // T15: o ícone de rolagem na linha do relógio abre a janelinha com o play e a velocidade
    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    @Test fun autoScrollDialog() {
        compose.setContent {
            StagebookTheme { StageScreen(stage.copy(look = stage.look.copy(stageMode = com.juliano.stagebook.data.model.StageMode.SCROLL)), 0, 0, { _, _ -> }, onBack = {}) }
        }
        compose.onNodeWithContentDescription("Rolagem automática").performClick()
        compose.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/tablet_6_palco_rolagem_auto.png")
    }

    // rolando: o ícone ao lado do relógio vira pause
    @Test fun autoScrollRunning() {
        compose.setContent {
            StagebookTheme { StageScreen(stage.copy(look = stage.look.copy(stageMode = com.juliano.stagebook.data.model.StageMode.SCROLL)), 0, 0, { _, _ -> }, onBack = {}) }
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Rolagem automática").performClick()
        repeat(3) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        compose.onNodeWithContentDescription("Rolar sozinho").performClick()
        repeat(90) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/tablet_6_palco_rolando.png")
    }

    // T25: backup em Ajustes → Biblioteca e a escolha das partes (tudo marcado)
    @Test fun settingsLibraryBackup() = shoot("tablet_5_ajustes_backup") {
        HomeScaffold(HomeTab.SETTINGS, {}) { SettingsScreen(settings, SettingsActions(), it, section = SettingsSection.LIBRARY) }
    }

    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    @Test fun backupPartsDialog() {
        compose.setContent {
            StagebookTheme {
                com.juliano.stagebook.ui.settings.BackupPartsDialog("Abrir arquivo", "Abrir", com.juliano.stagebook.data.Backup.Part.entries.toSet(), { _, _ -> }, {}, warning = "Os ajustes do arquivo substituem os daqui.")
            }
        }
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/tablet_5_backup_restaurar.png")
    }

    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    @Test fun factoryResetDialog() {
        compose.setContent { StagebookTheme { com.juliano.stagebook.ui.settings.FactoryResetDialog({}, {}, {}, code = "4827") } }
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/tablet_5_apagar_tudo.png")
    }

    // T33c: o metrônomo ao lado do relógio e a janelinha
    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    @Test fun metronomeDialog() {
        compose.setContent { StagebookTheme { StageScreen(stage, 0, 0, { _, _ -> }, onBack = {}) } }
        compose.onNodeWithContentDescription("Metrônomo").performClick()
        compose.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/tablet_6_palco_metronomo.png")
    }

    @Test fun metronomeCount() {
        compose.setContent { StagebookTheme { StageScreen(stage, 0, 0, { _, _ -> }, onBack = {}) } }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Metrônomo").performClick()
        repeat(3) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        compose.onNodeWithContentDescription("Começar o metrônomo").performClick()
        repeat(3) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/tablet_6_palco_metronomo_contagem.png")
    }

    @Test fun settingsMetronomeTablet() = shoot("tablet_5_ajustes_metronomo") {
        HomeScaffold(HomeTab.SETTINGS, {}) { SettingsScreen(settings, SettingsActions(), it, section = SettingsSection.METRONOME) }
    }

    // T20: com capo, a janela explica o desenho e tem o Tocar
    @Test fun chordPopupCapo() {
        compose.setContent {
            StagebookTheme {
                StageScreen(stage.copy(songs = listOf(StageSong("Canção de teste", "Banda Fictícia", chordSong, false))), 0, 0, { _, _ -> }, onBack = {})
                com.juliano.stagebook.ui.stage.ChordShapePopup("G", com.juliano.stagebook.data.model.Instrument.VIOLAO, chordSong, {}, {})
            }
        }
        @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/tablet_6_acorde_tocar.png")
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

    // a faixa no contexto: embaixo do palco de quem acompanha, com a música da vez faltando para o baixo
    // T28: títulos de seção maiores, com fundo leve, e o tamanho ajustável em Ajustes → Seções da letra
    @Test fun settingsSectionsTablet() = shoot("tablet_5_ajustes_secoes") {
        HomeScaffold(HomeTab.SETTINGS, {}) { SettingsScreen(settings, SettingsActions(), it, section = SettingsSection.SECTIONS) }
    }

    @Test fun conductOnStage() = shoot("tablet_6_palco_acompanhando") {
        androidx.compose.foundation.layout.Column {
            androidx.compose.foundation.layout.Box(Modifier.weight(1f)) { StageScreen(stage, 0, 0, { _, _ -> }, onBack = {}) }
            com.juliano.stagebook.ui.band.ConductBar(
                com.juliano.stagebook.ui.band.ConductUi.Following("Voz", "Garota de Ipanema", false, false, listOf("Baixo"),
                    listOf(com.juliano.stagebook.domain.Conduct.Lack("Baixo", listOf("Garota de Ipanema")))),
                {}, {}, {},
            )
        }
    }

    @Test fun conductBar() = shoot("banda_conduzir_faixas") { com.juliano.stagebook.ui.band.ConductBarSamplesForTest() }

    @Test fun settingsUpdateTablet() = shoot("tablet_5_ajustes_atualizar") {
        HomeScaffold(HomeTab.SETTINGS, {}) {
            SettingsScreen(
                settings.copy(update = com.juliano.stagebook.ui.settings.UpdateState.Available("0.3.52 (9f1c2ab)")),
                SettingsActions(),
                it,
                section = com.juliano.stagebook.ui.settings.SettingsSection.ABOUT,
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

    // pedal com o botão que dá play/pause no backing track (modo player ligado)
    @Test @Config(qualifiers = "w412dp-h2200dp-port-xxhdpi")
    fun settingsPhonePedalPlay() = shoot("jornada_pedal_play_pause") {
        val st = settings.copy(
            look = settings.look.copy(playerMode = true, pedalKeys = settings.look.pedalKeys + (android.view.KeyEvent.KEYCODE_B to PedalAction.PLAY_PAUSE)),
            lastKey = PedalKeyUi("B", PedalAction.PLAY_PAUSE, "PageFlip Firefly", android.view.KeyEvent.KEYCODE_B),
        )
        HomeScaffold(HomeTab.SETTINGS, {}) { SettingsScreen(st, SettingsActions(), it, section = SettingsSection.PEDAL) }
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

    // T13: modo rolar, no 2º passo (a letra rolou até a 1ª linha que não cabia)
    @Test fun stageScrollTablet() = shoot("tablet_6_palco_rolar") {
        StageScreen(stage.copy(look = stage.look.copy(stageMode = com.juliano.stagebook.data.model.StageMode.SCROLL)), 0, 1, { _, _ -> }, onBack = {})
    }

    @Test fun settingsLyricsScrollTablet() = shoot("tablet_5_ajustes_rolar") {
        HomeScaffold(HomeTab.SETTINGS, {}) {
            SettingsScreen(settings.copy(look = settings.look.copy(stageMode = com.juliano.stagebook.data.model.StageMode.SCROLL)), SettingsActions(), it, section = SettingsSection.LYRICS)
        }
    }

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

    // T08/T09: backing track e capo acesos, primeiro depois do artista; o tom depois
    private val stageBadges = stage.copy(
        songs = listOf(StageSong("Canção de teste", "Banda Fictícia", chordSong, false, track = "faixa.ogg")),
        look = stage.look.copy(chords = true, playerMode = true),
    )

    @Test fun stageBadgesTablet() = shoot("tablet_6_palco_capo_backing") { StageScreen(stageBadges, 0, 0, { _, _ -> }, onBack = {}) }

    @Test @Config(qualifiers = PHONE)
    fun stageBadgesPhone() = shoot("celular_7_palco_capo_backing") { StageScreen(stageBadges, 0, 0, { _, _ -> }, onBack = {}) }

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

    // ---- mixer (Ajustes → Player): arquivo de 4 canais, backing em par estéreo e click mono na saída 7 ----
    private val mixerLook = Look(
        playerMode = true,
        mixer = com.juliano.stagebook.domain.MixerConfig(
            channels = 4, eqOn = true, eqDb = listOf(3f, 0f, -2f, 1.5f, 0f),
            output = com.juliano.stagebook.domain.OutputInfo("usb:1234:5678", "Interface USB", 10),
        )
            .withStrip(0) { it.copy(name = "Backing", stereo = true, eq = true, norm = com.juliano.stagebook.domain.NormMode.VOLUME, gainDb = -3f) }
            .withStrip(2) { it.copy(name = "Click", norm = com.juliano.stagebook.domain.NormMode.PEAK, center = true) }
            .withOutput(2, 6)
            .withStrip(3) { it.copy(name = "Guia", on = false) },
    )

    // tocando: medidores dos canais (o 2º lado do backing um pouco abaixo, o click batendo no pico) e o espectro
    private val mixerLevels = com.juliano.stagebook.domain.MixerLevels(
        com.juliano.stagebook.domain.Meters(
            listOf(-9f, -11f, -4f, -48f, -48f, -48f, -48f, -48f),
            listOf(-6f, -8f, -1.5f, -48f, -48f, -48f, -48f, -48f),
        ),
        List(com.juliano.stagebook.domain.Spectrum.BINS) { b -> -20f - b * 0.7f + 6f * kotlin.math.cos(b / 5f) },
        live = true,
    )

    @Test @Config(qualifiers = PHONE_LONGO)
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
        // a explicação agora é o ⓘ ao lado do título do cartão
        compose.onAllNodesWithContentDescription("Como funciona")[0].performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/jornada_mixer_4_explicacao.png")
    }

    @Test @Config(qualifiers = PHONE)
    fun normTipOpenPhone() {
        compose.setContent {
            StagebookTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.padding(com.juliano.stagebook.ui.theme.Spacing.l)) {
                        com.juliano.stagebook.ui.components.MixerChannelForm(2, mixerLook.mixer.strip(2), pair = false, canPair = true, output = mixerLook.mixer.outputOf(2), outputs = mixerLook.mixer.output, onChange = {}, onOutput = {})
                    }
                }
            }
        }
        compose.onAllNodesWithText("Sobre a normalização")[0].performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/jornada_mixer_5_sobre_normalizar.png")
    }

    // ---- ler o QR de um setlist: conferência (achadas, com dúvida, que faltam) ----
    private val scanReview = com.juliano.stagebook.ui.setlists.ScanUi.Review(
        "Show de sábado",
        listOf(
            com.juliano.stagebook.ui.setlists.ScanRow("Canção de teste", "", true, listOf(com.juliano.stagebook.ui.setlists.SongRef("1", "Canção de teste", "Banda Fictícia")), "1"),
            com.juliano.stagebook.ui.setlists.ScanRow("Noite clara", "", false, listOf(
                com.juliano.stagebook.ui.setlists.SongRef("2", "Noite Clara", "Banda A"), com.juliano.stagebook.ui.setlists.SongRef("3", "Noite Clara", "Banda B"),
            ), null),
            com.juliano.stagebook.ui.setlists.ScanRow("Estrada longa", "", true, listOf(com.juliano.stagebook.ui.setlists.SongRef("4", "Estrada Longa", "Banda C")), "4"),
            com.juliano.stagebook.ui.setlists.ScanRow("Música que não tenho", "", false, emptyList(), null),
        ),
    )

    @Test @Config(qualifiers = PHONE)
    fun scanReviewPhone() = shoot("jornada_qr_2_conferir") {
        com.juliano.stagebook.ui.setlists.ScanSetlistScreen(scanReview, true, {}, {}, {}, { _, _ -> }, {}, { null }, {}, {})
    }

    // fila: importando as que faltam para o setlist, buscando no 1º site da lista (no Google, restrito ao site)
    @Test @Config(qualifiers = PHONE)
    fun importQueuePhone() = shoot("jornada_qr_4_importar_fila") {
        ImportScreen(
            ImportUiState(), "", {}, {}, {},
            queue = com.juliano.stagebook.data.ImportQueue.State(
                "x", "Show de sábado", listOf("1", null, "4", null),
                listOf(com.juliano.stagebook.data.ImportQueue.Item("Música que não tenho", "", 1), com.juliano.stagebook.data.ImportQueue.Item("Outra que falta", "", 3)), 3,
            ),
            queueSite = { "Cifra Club" },
        )
    }

    // ---- compartilhar: a folha (com e sem internet) e o código pronto ----
    @Test @Config(qualifiers = PHONE)
    fun shareSheetPhone() = shoot("jornada_compartilhar_1_folha") {
        androidx.compose.foundation.layout.Column {
            com.juliano.stagebook.ui.share.ShareOptions(12, online = true, onQr = {}, onInternet = {})
            androidx.compose.material3.HorizontalDivider(androidx.compose.ui.Modifier.padding(vertical = com.juliano.stagebook.ui.theme.Spacing.l))
            com.juliano.stagebook.ui.share.ShareOptions(12, online = false, onQr = {}, onInternet = {})
        }
    }

    @Test @Config(qualifiers = PHONE)
    fun shareCodePhone() = shoot("jornada_compartilhar_2_codigo") {
        androidx.compose.material3.Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(com.juliano.stagebook.ui.theme.Spacing.l)) {
                androidx.compose.material3.Text("Para receber, digite", style = MaterialTheme.typography.headlineSmall)
                com.juliano.stagebook.ui.share.CodeBox(
                    com.juliano.stagebook.ui.share.CloudUi.Sent(
                        "47-violao-lua", "Show de sábado", 12, 0,
                        com.juliano.stagebook.domain.QrEncoder.encode(com.juliano.stagebook.domain.ShareCode.qrText("47-violao-lua").toByteArray(), 600).asImageBitmap(),
                        joined = listOf("Celular da Ana"),
                    ),
                )
            }
        }
    }

    @Test @Config(qualifiers = PHONE)
    fun receiveCodeReviewPhone() = shoot("jornada_compartilhar_3_receber") {
        com.juliano.stagebook.ui.setlists.ScanSetlistScreen(
            scanReview.copy(rows = scanReview.rows.mapIndexed { i, r -> if (i == 3) r.copy(carried = true) else r }),
            true, {}, {}, {}, { _, _ -> }, {}, { null }, {}, {}, byCode = true,
        )
    }

    // ---- músicas avulsas: seleção na aba Músicas e quem recebe ----
    @Test @Config(qualifiers = PHONE)
    fun songsSelectPhone() = shoot("jornada_musicas_1_selecao") {
        HomeScaffold(HomeTab.SONGS, {}) {
            com.juliano.stagebook.ui.songs.SongsScreen(songs, "", {}, {}, {}, {}, {}, {}, it, initialSelection = songs.songs.take(2).map { s -> s.id })
        }
    }

    // o menu Importar aberto na aba Músicas: sites, QR e código
    @Test @Config(qualifiers = PHONE)
    fun importMenuPhone() {
        compose.setContent {
            StagebookTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    HomeScaffold(HomeTab.SONGS, {}) { com.juliano.stagebook.ui.songs.SongsScreen(songs, "", {}, {}, {}, {}, {}, {}, it, received = 2, onSync = {}) }
                }
            }
        }
        compose.onAllNodesWithText("Importar")[0].performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/jornada_importar_menu.png")
    }

    @Test @Config(qualifiers = PHONE)
    fun songsReceivedPhone() = shoot("jornada_musicas_2_recebidas") {
        com.juliano.stagebook.ui.setlists.ScanSetlistScreen(
            scanReview.copy(
                name = "", songsOnly = true,
                rows = scanReview.rows.mapIndexed { i, r -> if (i == 1 || i == 3) r.copy(carried = true) else r },
            ),
            true, {}, {}, {}, { _, _ -> }, {}, { null }, {}, {}, byCode = true,
        )
    }

    // ---- banda na rede local: Ajustes → Banda (na rede: um da banda, um pedido esperando, um novo) ----
    private val bandSample = com.juliano.stagebook.ui.band.BandUi(
        name = "Meu tablet", wifi = true, searching = true,
        nearby = listOf(
            com.juliano.stagebook.ui.band.NearbyUi("1", "Celular da Ana", member = true),
            com.juliano.stagebook.ui.band.NearbyUi("2", "Teclado do Caio", member = false, join = com.juliano.stagebook.data.BandLink.Join.WAITING),
            com.juliano.stagebook.ui.band.NearbyUi("3", "Baixo", member = false),
            com.juliano.stagebook.ui.band.NearbyUi("4", "Bateria", member = true, otherVersion = "0.2.90"),
        ),
        members = listOf(com.juliano.stagebook.ui.band.MemberUi("1", "Celular da Ana", 0)),
    )

    @Test @Config(qualifiers = "w412dp-h1600dp-port-xxhdpi")
    fun bandPhone() = shoot("jornada_banda_1_ajustes") {
        HomeScaffold(HomeTab.SETTINGS, {}) { SettingsScreen(settings, SettingsActions(band = bandSample), it, section = SettingsSection.BAND) }
    }

    @Test @Config(qualifiers = PHONE)
    fun bandListPhone() = shoot("jornada_banda_2_lista_ajustes") {
        HomeScaffold(HomeTab.SETTINGS, {}) { SettingsScreen(settings, SettingsActions(band = bandSample), it) }
    }

    // ---- parte 2: mandar para a banda (folha) e o aviso nas abas com o número no ícone ----
    @Test @Config(qualifiers = PHONE)
    fun shareBandPhone() = shoot("jornada_banda_3_compartilhar") {
        com.juliano.stagebook.ui.share.ShareOptions(12, online = true, onQr = {}, onInternet = {}, band = 2)
    }

    @Test @Config(qualifiers = PHONE)
    fun inboxBannerPhone() = shoot("jornada_banda_4_aviso") {
        HomeScaffold(HomeTab.SETLISTS, {}, badges = mapOf(HomeTab.SETLISTS to 1, HomeTab.SONGS to 2)) {
            androidx.compose.foundation.layout.Column(it) {
                com.juliano.stagebook.ui.share.InboxBanner(
                    com.juliano.stagebook.data.BandInbox.Item("1", "a", "Celular da Ana", "LETRAS3\n12\nShow de sábado"), {}, {},
                    androidx.compose.ui.Modifier.padding(horizontal = com.juliano.stagebook.ui.theme.Spacing.m).padding(top = com.juliano.stagebook.ui.theme.Spacing.s),
                )
                SetlistsScreen(SetlistsUiState(false, listOf(SetlistRowUi("1", "Show de sábado", 15), SetlistRowUi("2", "18/10/2026 20:30", 6))), {}, {}, {}, {}, { _, _ -> }, androidx.compose.ui.Modifier.weight(1f))
            }
        }
    }

    @Test @Config(qualifiers = PHONE)
    fun scanAskBandPhone() = shoot("jornada_banda_5_pedir") {
        com.juliano.stagebook.ui.setlists.ScanSetlistScreen(
            scanReview.copy(rows = scanReview.rows + com.juliano.stagebook.ui.setlists.ScanRow("Outra que faltava", "", false, emptyList(), null, carried = true, fromBand = true)),
            true, {}, {}, {}, { _, _ -> }, {}, { null }, {}, {}, band = 2,
        )
    }

    @Test @Config(qualifiers = PHONE)
    fun scanVersionsPhone() = shoot("jornada_conferencia_versoes") {
        com.juliano.stagebook.ui.setlists.ScanSetlistScreen(
            scanReview.copy(
                name = "", songsOnly = true,
                rows = listOf(
                    com.juliano.stagebook.ui.setlists.ScanRow("Canção de teste", "", true, emptyList(), "1", differs = true),
                    com.juliano.stagebook.ui.setlists.ScanRow("Estrada longa", "", true, emptyList(), "4", differs = true, useIncoming = true),
                    com.juliano.stagebook.ui.setlists.ScanRow("Noite clara", "", true, emptyList(), "2"),
                    com.juliano.stagebook.ui.setlists.ScanRow("Música nova", "", false, emptyList(), null, carried = true, fromBand = true),
                ),
            ),
            true, {}, {}, {}, { _, _ -> }, {}, { null }, {}, {}, fromName = "Celular da Ana",
        )
    }

    @Test @Config(qualifiers = PHONE)
    fun scanLyricsOnlyPhone() = shoot("jornada_conferencia_so_letra") {
        com.juliano.stagebook.ui.setlists.ScanSetlistScreen(
            scanReview.copy(
                rows = listOf(
                    com.juliano.stagebook.ui.setlists.ScanRow("Canção de teste", "", true, emptyList(), "1"),
                    com.juliano.stagebook.ui.setlists.ScanRow("Canção de exemplo", "", false, emptyList(), null, carried = true, fromBand = true, lyricsOnly = true),
                    com.juliano.stagebook.ui.setlists.ScanRow("Outra canção", "", false, emptyList(), null, wantChords = true),
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
                    com.juliano.stagebook.ui.setlists.ScanSetlistScreen(scanReview, true, {}, {}, {}, { _, _ -> }, {}, { null }, {}, {})
                }
            }
        }
        compose.onAllNodesWithText("Criar setlist")[0].performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/jornada_conferencia_criar_aviso.png")
    }

    @Test @Config(qualifiers = PHONE)
    fun scanCameraPhone() = shoot("jornada_qr_1_camera") {
        com.juliano.stagebook.ui.setlists.ScanSetlistScreen(
            com.juliano.stagebook.ui.setlists.ScanUi.Scanning(com.juliano.stagebook.ui.setlists.ScanProblem.INCOMPLETE), true, {}, {}, {}, { _, _ -> }, {}, { null }, {}, {},
        )
    }

    @Test fun scanReviewTablet() = shoot("jornada_qr_3_conferir_tablet") {
        com.juliano.stagebook.ui.setlists.ScanSetlistScreen(scanReview, true, {}, {}, {}, { _, _ -> }, {}, { null }, {}, {})
    }

    @Test @Config(qualifiers = PHONE)
    fun mixerEditPhone() = shoot("jornada_mixer_3_editar_canal") {
        androidx.compose.material3.Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(com.juliano.stagebook.ui.theme.Spacing.l)) {
                androidx.compose.material3.Text("Canal 3", style = MaterialTheme.typography.headlineSmall)
                com.juliano.stagebook.ui.components.MixerChannelForm(2, mixerLook.mixer.strip(2), pair = false, canPair = true, output = mixerLook.mixer.outputOf(2), outputs = mixerLook.mixer.output, onChange = {}, onOutput = {})
            }
        }
    }

    @Test @Config(qualifiers = TABLET_LONGO)
    fun mixerTablet() = shoot("jornada_mixer_2_tablet") {
        HomeScaffold(HomeTab.SETTINGS, {}) { SettingsScreen(settings.copy(look = mixerLook), SettingsActions(mixerLevels = { mixerLevels }, monitoring = true), it, section = SettingsSection.PLAYER) }
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
private const val PHONE_LONGO = "w412dp-h3000dp-port-xxhdpi"     // a tela inteira, sem rolar
private const val TABLET_LONGO = "w1280dp-h2000dp-port-hdpi"
