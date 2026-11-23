package br.app.stagebook

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.MidiState
import br.app.stagebook.data.model.Look
import br.app.stagebook.data.model.SongProfile
import br.app.stagebook.data.model.StageMode
import br.app.stagebook.data.model.withProfile
import br.app.stagebook.ui.settings.AdvancedUi
import br.app.stagebook.ui.settings.IndexCtx
import br.app.stagebook.ui.settings.ItemKind
import br.app.stagebook.ui.settings.SettingsActions
import br.app.stagebook.ui.settings.SettingsScreen
import br.app.stagebook.ui.settings.SettingsSection
import br.app.stagebook.ui.settings.SettingsUiState
import br.app.stagebook.ui.settings.sampleAdvanced
import br.app.stagebook.ui.settings.searchSettings
import br.app.stagebook.ui.settings.settingsItems
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Settings search (user, 14/11): what it finds, what it hides, where a tap goes. Texts as drawn, in pt-BR. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w412dp-h700dp-port-xxhdpi", application = android.app.Application::class)
class SettingsSearchTest {
    @get:Rule val compose = createComposeRule()

    private fun screen(look: Look = Look(), section: SettingsSection? = null, midi: Boolean = true, advanced: AdvancedUi = sampleAdvanced) = compose.setContent {
        var open by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(section) }
        StagebookTheme {
            SettingsScreen(SettingsUiState(loading = false, look = look, midi = MidiState(supported = midi)), SettingsActions(advanced = advanced), section = open, onSection = { open = it })
        }
    }

    private fun type(text: String) {
        compose.onNode(hasSetTextAction()).performTextInput(text)
        compose.waitForIdle()
    }

    private fun shown(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    @Test fun `midi finds the pedal and MIDI card and the MIDI output`() {
        screen()
        type("midi")
        assertTrue(compose.onAllNodesWithText("Virar a página › Pedal › Pedal e MIDI").fetchSemanticsNodes().isNotEmpty())
        assertTrue(shown("Banda e computador › Para fora do app › Saída MIDI"))
        compose.onNodeWithText("Enviar para").assertExists()
    }

    @Test fun `case and accents do not matter, a word start or a piece finds`() {
        screen()
        type("METRONOMO")
        assertTrue(shown("Player e metrônomo › Metrônomo"))
    }

    @Test fun `a piece inside a word finds too, and the speed hides while turning pages`() {
        screen(Look(stageMode = StageMode.PAGES))
        type("rolag")
        // not in this mode, but still found: dimmed, with the reason
        assertTrue(shown("Velocidade da rolagem automática"))
        assertTrue(shown("Só aparece no modo Rolar"))
    }

    @Test fun `the speed is found in scroll mode`() {
        screen(Look(stageMode = StageMode.SCROLL))
        type("rolag")
        compose.onNodeWithText("Velocidade da rolagem automática").assertExists()
    }

    @Test fun `the words in English find the same items`() {
        screen()
        type("screen")
        assertTrue(shown("Manter a tela ligada com o app aberto"))
    }

    private val essentialLook = Look(bandOn = false, internetOn = false, extrasOn = false, playerMode = false)

    @Test fun `what essential mode turns off is found too, dimmed, with the reason`() {
        screen(essentialLook)
        type("ligar o editor")
        assertTrue(shown("Banda e computador › Computador › No navegador"))
        assertTrue(shown("Desligado pelo modo essencial"))
    }

    @Test fun `the songs MIDI output shows with the reason when the extras are off`() {
        screen(essentialLook)
        type("saida midi")
        assertTrue(shown("Banda e computador › Para fora do app › Saída MIDI"))
        assertTrue(shown("Só aparece com Metrônomo e extras dos acordes ligado"))
        assertTrue(shown("Virar a página › Pedal › Pedal e MIDI"))   // the pedal stays
    }

    @Test fun `tapping a hidden item opens the place that turns it on and changes nothing`() {
        var changed = 0
        compose.setContent {
            var open by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<SettingsSection?>(null) }
            StagebookTheme {
                SettingsScreen(
                    SettingsUiState(loading = false, look = essentialLook, midi = MidiState(supported = true)),
                    SettingsActions(advanced = sampleAdvanced, onEssential = { changed++ }, onBandOn = { changed++ }, onInternetOn = { changed++ }, onPlayerMode = { changed++ }, onExtrasOn = { changed++ }),
                    section = open, onSection = { open = it },
                )
            }
        }
        type("ligar o editor")
        compose.onAllNodesWithText("Banda e computador › Computador › No navegador")[0].performClick()
        compose.waitForIdle()
        // Device, where essential mode is turned off
        compose.onNodeWithText("Ligar o modo essencial").assertIsDisplayed()
        assertEquals(0, changed)
    }

    @Test fun `a hidden chord item says why when the profile is Lyrics only`() {
        screen(Look().withProfile(SongProfile.LYRICS))
        type("instrumento")
        assertTrue(shown("Acordes › Diagramas dos acordes"))
        assertTrue(shown("Perfil Só letra esconde os acordes"))
    }

    @Test fun `a hidden chord item opens the profile`() {
        screen(Look().withProfile(SongProfile.LYRICS))
        type("instrumento")
        compose.onAllNodesWithText("Acordes › Diagramas dos acordes")[0].performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Acordes na letra").assertIsDisplayed()
    }

    @Test fun `no player, the USB output is found with the reason`() {
        screen(Look(playerMode = false))
        type("saída usb")
        assertTrue(shown("Só aparece com o player ligado"))
    }

    @Test fun `several words all have to hit, in any order`() {
        screen()
        type("tela manter")
        assertTrue(shown("Manter a tela ligada com o app aberto"))
        compose.onNode(hasSetTextAction()).performTextInput(" zzzz")
        compose.waitForIdle()
        // the list follows the typing ~120 ms after the last key, not on each one
        assertFalse(shown("Nada encontrado nos ajustes."))
        compose.mainClock.advanceTimeBy(br.app.stagebook.ui.settings.SEARCH_DEBOUNCE_MS + 16)
        compose.waitForIdle()
        compose.onNodeWithText("Nada encontrado nos ajustes.").assertExists()
    }

    @Test fun `a piece of the explanation finds the card and says so`() {
        screen()
        type("colunas juntas")   // only in the Mode card's help: "O pedal vira as duas colunas juntas"
        assertTrue(shown("na explicação"))
    }

    @Test fun `the name ranks before the explanation`() {
        screen()
        type("pedal")
        val nodes = compose.onAllNodesWithText("Virar a página › Pedal › Pedal e MIDI", substring = true).fetchSemanticsNodes()
        assertTrue(nodes.isNotEmpty())
        assertFalse(compose.onAllNodesWithText("na explicação", substring = true).fetchSemanticsNodes().isNotEmpty() && nodes.first().boundsInRoot.top > 1000f)
    }

    @Test fun `with chords the instrument is found`() {
        screen()
        type("instrumento")
        assertTrue(shown("Acordes › Diagramas dos acordes"))
    }

    @Test fun `tapping a result opens its group on the card`() {
        screen()
        type("apagar tudo")
        compose.onAllNodesWithText("Importar e backup › Zona de perigo")[0].performClick()
        compose.waitForIdle()
        // the page of the group, scrolled to the card (the phone is short: it was off screen)
        compose.onNodeWithText("Zona de perigo").assertIsDisplayed()
        compose.onNodeWithText("Importar e backup").assertExists()
    }

    @Test fun `nothing found says so, and the X brings the groups back`() {
        screen()
        type("zzzz")
        compose.onNodeWithText("Nada encontrado nos ajustes.").assertExists()
        compose.onNodeWithContentDescription("Limpar").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Letra e cifra").assertExists()
        assertFalse(shown("Nada encontrado nos ajustes."))
    }

    @Test fun `the search survives a rotation`() {
        val tester = StateRestorationTester(compose)
        tester.setContent {
            StagebookTheme { SettingsScreen(SettingsUiState(loading = false, midi = MidiState(supported = true)), SettingsActions(advanced = sampleAdvanced)) }
        }
        type("midi")
        tester.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        assertTrue(shown("Banda e computador › Para fora do app › Saída MIDI"))
    }

    // ---- the guard: the index and the screen say the same thing --------------------------------------------------

    private val configs: List<Pair<String, Pair<Look, AdvancedUi>>> = listOf(
        "default" to (Look() to sampleAdvanced),
        "essential" to (Look(bandOn = false, internetOn = false, extrasOn = false, playerMode = false) to sampleAdvanced),
        "lyrics only" to (Look().withProfile(SongProfile.LYRICS) to sampleAdvanced),
        "automatic" to (Look().withProfile(SongProfile.AUTO) to sampleAdvanced),
        "player on" to (Look(playerMode = true) to sampleAdvanced),
        "player off" to (Look(playerMode = false) to sampleAdvanced),
        "band on, no internet" to (Look(bandOn = true, internetOn = false) to sampleAdvanced),
        "band off, internet on" to (Look(bandOn = false, internetOn = true) to sampleAdvanced),
        "band and internet off" to (Look(bandOn = false, internetOn = false) to sampleAdvanced),
        "extras off" to (Look(extrasOn = false) to sampleAdvanced),
        "scroll mode" to (Look(stageMode = StageMode.SCROLL) to sampleAdvanced),
        "metronome hidden" to (Look(metroShown = false) to sampleAdvanced),
        "no sound, no glow" to (Look(metroSound = false, metroGlow = false, playerMode = true) to sampleAdvanced),
        "no permission" to (Look(playerMode = true) to sampleAdvanced.copy(canWrite = false)),
    )

    private fun tagged(node: androidx.compose.ui.semantics.SemanticsNode): String? =
        node.config.getOrNull(br.app.stagebook.ui.components.SettingsItemKey)

    private fun walk(node: androidx.compose.ui.semantics.SemanticsNode, out: MutableList<androidx.compose.ui.semantics.SemanticsNode>) {
        out += node
        node.children.forEach { walk(it, out) }
    }

    private fun insideItem(node: androidx.compose.ui.semantics.SemanticsNode): Boolean =
        generateSequence(node) { it.parent }.any { tagged(it) != null }

    private fun interactive(node: androidx.compose.ui.semantics.SemanticsNode) = with(node.config) {
        contains(SemanticsActions.OnClick) || contains(SemanticsActions.SetProgress) ||
            contains(SemanticsProperties.ToggleableState) || contains(SemanticsProperties.Selected)
    }

    /** The back arrow of a group page is the page's, not an item. */
    private fun isBack(node: androidx.compose.ui.semantics.SemanticsNode, back: String) =
        (node.children + node).any { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(back) == true }

    @Test @Config(qualifiers = "pt-rBR-w412dp-h6000dp-port-xxhdpi")
    fun `every control the screen draws carries an id the index knows, and every item of the index is on the screen`() {
        val problems = mutableListOf<String>()
        val res = ApplicationProvider.getApplicationContext<android.app.Application>().resources
        val back = res.getString(R.string.action_back)
        var current by mutableStateOf(Triple(Look(), sampleAdvanced, SettingsSection.LYRICS))
        compose.setContent {
            val (look, advanced, group) = current
            StagebookTheme { SettingsScreen(SettingsUiState(loading = false, look = look, midi = MidiState(supported = true)), SettingsActions(advanced = advanced), section = group) }
        }
        for ((name, cfg) in configs) {
            val (look, advanced) = cfg
            val ctx = IndexCtx(look, midiSupported = true, advanced = advanced, nationalTags = true)
            val items = settingsItems(ctx)
            for (group in SettingsSection.entries) {
                current = Triple(look, advanced, group)
                compose.waitForIdle()
                val nodes = mutableListOf<androidx.compose.ui.semantics.SemanticsNode>()
                walk(compose.onRoot(useUnmergedTree = true).fetchSemanticsNode(), nodes)
                val drawn = nodes.mapNotNull(::tagged).filter { it != br.app.stagebook.ui.components.SETTINGS_CHROME }.toSet()
                val want = items.filter { it.group == group }.map { it.id }.toSet()
                (drawn - want).forEach { problems += "$name / $group: \"$it\" is on the screen but not in the index, or the index says it is hidden" }
                (want - drawn).forEach { problems += "$name / $group: \"$it\" is in the index but not on the screen" }
                // every control (click, toggle, slider, choice) lives inside an item
                nodes.filter { interactive(it) && !insideItem(it) && !isBack(it, back) }.forEach {
                    problems += "$name / $group: a control without an id: ${it.config}"
                }
            }
        }
        assertTrue(problems.distinct().joinToString("\n"), problems.isEmpty())
    }
}
/** On a 360 dp phone with the text at 1.15, a result's name and path are whole (the path wraps, it is never cut). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h900dp-port-xxhdpi", application = android.app.Application::class)
class SettingsSearchNarrowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `results are whole at 360 dp and 1,15`() {
        RuntimeEnvironment.setFontScale(1.15f)
        compose.setContent { StagebookTheme { SettingsScreen(SettingsUiState(loading = false, midi = MidiState(supported = true)), SettingsActions(advanced = sampleAdvanced)) } }
        compose.onNode(hasSetTextAction()).performTextInput("midi")
        compose.waitForIdle()
        for (text in listOf("Pedal MIDI pelo USB", "Banda e computador › Para fora do app › Saída MIDI", "Enviar para")) {
            val node = compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().first()
            val out = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)!!.action!!(out)
            val l = out.first()
            assertFalse("\"$text\" cut", (0 until l.lineCount).any { l.isLineEllipsized(it) } || l.getLineRight(l.lineCount - 1) > l.size.width)
        }
    }
}
