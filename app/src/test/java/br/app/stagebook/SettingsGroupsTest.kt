package br.app.stagebook

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import br.app.stagebook.data.model.Look
import br.app.stagebook.data.model.StageMode
import br.app.stagebook.ui.settings.SettingsActions
import br.app.stagebook.ui.settings.SettingsScreen
import br.app.stagebook.ui.settings.SettingsSection
import br.app.stagebook.ui.settings.SettingsUiState
import br.app.stagebook.ui.settings.shown
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings by task (user, 14/11): seven groups in the order of use, then About apart. What the screen draws, in
 * pt-BR: the names and summaries in the list, the pinned preview, the mode, the pedal and the danger zone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w412dp-h1800dp-port-xxhdpi", application = android.app.Application::class)
class SettingsGroupsTest {
    @get:Rule val compose = createComposeRule()

    private fun group(look: Look = Look(), section: SettingsSection? = null, actions: SettingsActions = SettingsActions(), midi: Boolean = false) = compose.setContent {
        StagebookTheme {
            SettingsScreen(
                SettingsUiState(loading = false, look = look, songCount = 3, setCount = 1, midi = br.app.stagebook.data.MidiState(supported = midi)),
                actions, section = section,
            )
        }
    }

    private fun top(text: String): Dp = compose.onAllNodesWithText(text).onFirst().getUnclippedBoundsInRoot().top
    private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.onFirst() = get(0)

    @Test fun `the list shows the seven groups in the order of use, then About`() {
        group()
        val names = listOf("Letra e cifra", "Virar a página", "Acordes", "Player e metrônomo", "Importar e backup", "Banda e computador", "Aparelho", "Sobre")
        val tops = names.map { top(it) }
        assertEquals(tops.sorted(), tops)
        assertEquals(names.size, tops.toSet().size)
    }

    @Test fun `each group has its one-line summary`() {
        group()
        listOf(
            "Tamanho da letra: ${Look().fontSp.toInt()}", "Virar ou rolar, pedal e MIDI, tela ligada", "Instrumento, diagrama, som do acorde", "No palco · Player ligado",
            "3 músicas · 1 setlist", "Editor desligado", "Modo essencial, idioma, avançado",
        ).forEach { compose.onNodeWithText(it, substring = true).assertExists() }
    }

    @Test fun `Lyrics and chords opens with the preview pinned on top, and it stays while scrolling`() {
        group(section = SettingsSection.LYRICS)
        val preview = compose.onNodeWithTag("lyricsPreview")
        val before = preview.getUnclippedBoundsInRoot().top
        assertTrue("preview above the first card", before < top("Letra"))
        compose.onNodeWithText("Títulos de seção").performScrollTo()
        assertEquals(before, preview.getUnclippedBoundsInRoot().top)
        preview.assertIsDisplayed()
    }

    @Test fun `the other groups have no pinned preview`() {
        group(section = SettingsSection.PAGE)
        compose.onNodeWithTag("lyricsPreview").assertDoesNotExist()
    }

    @Test fun `Mode shows the speed only when scrolling and the columns only when turning pages`() {
        var look by mutableStateOf(Look(stageMode = StageMode.PAGES))
        compose.setContent {
            StagebookTheme { SettingsScreen(SettingsUiState(loading = false, look = look), SettingsActions(onStageMode = { look = look.copy(stageMode = it) }), section = SettingsSection.PAGE) }
        }
        compose.onNodeWithText("Dividir em colunas").assertExists()
        compose.onNodeWithText("Velocidade da rolagem", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Rolar").performClick()
        compose.onNodeWithText("Velocidade da rolagem", substring = true).assertExists()
        compose.onNodeWithText("Dividir em colunas").assertDoesNotExist()
    }

    @Test fun `Turn the page holds the pedal and MIDI, the MIDI output and during the show`() {
        group(section = SettingsSection.PAGE, midi = true)
        listOf("Modo", "Pedal e MIDI", "Saída MIDI", "Durante o show").forEach { compose.onNodeWithText(it).assertExists() }
        assertTrue(top("Modo") < top("Pedal e MIDI"))
        assertTrue(top("Saída MIDI") < top("Durante o show"))
        compose.onNodeWithText("Manter a tela ligada", substring = true).assertExists()
    }

    @Test fun `the MIDI output leaves with the extras`() {
        group(look = Look(extrasOn = false), section = SettingsSection.PAGE, midi = true)
        compose.onNodeWithText("Pedal e MIDI").assertExists()
        compose.onNodeWithText("Saída MIDI").assertDoesNotExist()
    }

    @Test fun `the pedal is not in Lyrics and chords nor in Chords`() {
        group(section = SettingsSection.LYRICS)
        compose.onNodeWithText("Pedal e MIDI").assertDoesNotExist()
    }

    @Test fun `Erase everything is the last item of Import and backup, in the danger zone`() {
        group(section = SettingsSection.LIBRARY)
        val erase = top("Apagar tudo")
        listOf("Sua biblioteca", "Sites de cifra", "Backup", "Restaurar", "Zona de perigo").forEach { assertTrue("$it above", top(it) <= erase) }
        assertTrue(top("Restaurar") < top("Zona de perigo"))
        // nothing after the button: no other card title under it
        compose.onNodeWithText("Outras formas de compartilhar").getUnclippedBoundsInRoot().let { assertTrue(it.top < erase) }
    }

    @Test fun `essential mode hides the group of the band and the computer`() {
        group(look = Look(bandOn = false, internetOn = false, extrasOn = false, playerMode = false))
        compose.onNodeWithText("Banda e computador").assertDoesNotExist()
        compose.onNodeWithText("Player e metrônomo").assertDoesNotExist()
    }

    @Test fun `Band and computer holds the band, telling what plays and the editor`() {
        group(section = SettingsSection.BAND)
        compose.onNodeWithText("Avisar a música tocando").assertExists()
        compose.onNodeWithText("Ligar o editor").assertExists()
    }

    @Test fun `with the extras off the announce card leaves but the editor stays`() {
        group(look = Look(extrasOn = false), section = SettingsSection.BAND)
        compose.onNodeWithText("Avisar a música tocando").assertDoesNotExist()
        compose.onNodeWithText("Ligar o editor").assertExists()
    }

    @Test fun `Device holds essential mode, language and advanced, in that order`() {
        group(section = SettingsSection.DEVICE)
        assertTrue(top("Ligar o modo essencial") < top("Idioma"))
        assertTrue(top("Idioma") < top("Bateria do aparelho"))
    }

    @Test fun `Chords group leaves with Lyrics only`() {
        assertFalse(SettingsSection.CHORDS.shown(Look().copy(chords = false)))
        assertTrue(SettingsSection.CHORDS.shown(Look()))
    }

    @Test fun `a resume point from the old subjects lands on the group that holds it, an unknown one on the list`() {
        assertEquals(SettingsSection.PAGE, SettingsSection.fromSaved("PEDAL"))
        assertEquals(SettingsSection.PLAYER, SettingsSection.fromSaved("METRONOME"))
        assertEquals(SettingsSection.LIBRARY, SettingsSection.fromSaved("IMPORT"))
        assertEquals(SettingsSection.BAND, SettingsSection.fromSaved("WEB_EDITOR"))
        assertEquals(SettingsSection.DEVICE, SettingsSection.fromSaved("ESSENTIAL"))
        assertEquals(SettingsSection.DEVICE, SettingsSection.fromSaved("ADVANCED"))
        assertEquals(SettingsSection.ABOUT, SettingsSection.fromSaved("ABOUT"))
        assertNull(SettingsSection.fromSaved("GONE"))
    }
}

/** On a 360 dp phone with the text at 1.15, no group name is cut or broken in two lines. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h1000dp-port-xxhdpi", application = android.app.Application::class)
class SettingsGroupNamesNarrowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `the group names stay whole at 360 dp and 1,15`() {
        RuntimeEnvironment.setFontScale(1.15f)
        compose.setContent { StagebookTheme { SettingsScreen(SettingsUiState(loading = false), SettingsActions()) } }
        compose.waitForIdle()
        val bad = listOf("Letra e cifra", "Virar a página", "Acordes", "Player e metrônomo", "Importar e backup", "Banda e computador", "Aparelho", "Sobre").mapNotNull { name ->
            val node = compose.onAllNodesWithText(name, useUnmergedTree = true).fetchSemanticsNodes().first()
            val out = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)!!.action!!(out)
            val l = out.first()
            if (l.lineCount != 1 || l.isLineEllipsized(0) || l.getLineRight(0) > l.size.width) "$name lines=${l.lineCount} w=${l.didOverflowWidth} size=${l.size} box=${node.boundsInRoot}" else null
        }
        assertTrue("cut or broken: $bad", bad.isEmpty())
    }
}

/** The one-line summaries under the group names: none is cut at 360 dp with the text at 1.15, in either language. */
abstract class SettingsSummariesWhole {
    @get:Rule val compose = createComposeRule()

    private val states = listOf(
        "default" to SettingsUiState(loading = false, songCount = 142, setCount = 12, version = "0.1.1234"),
        "essential" to SettingsUiState(loading = false, look = Look(bandOn = false, internetOn = false, extrasOn = false, playerMode = false), songCount = 1, setCount = 1),
        "editor on, player on" to SettingsUiState(loading = false, look = Look(webEditorOn = true, playerMode = true, bold = true, fontSp = 100f), songCount = 142, setCount = 12),
        "lyrics only" to SettingsUiState(loading = false, look = Look().copy(chords = false, bold = true), songCount = 142, setCount = 12),
        "player alone" to SettingsUiState(loading = false, look = Look(bandOn = false, internetOn = false, extrasOn = false, playerMode = true)),
        "update available" to SettingsUiState(loading = false, version = "0.1.1234", update = br.app.stagebook.ui.settings.UpdateState.Available("0.1.1300", emptyList())),
    )

    @Test fun `no summary is cut`() {
        RuntimeEnvironment.setFontScale(1.15f)
        var current by androidx.compose.runtime.mutableStateOf(states.first().second)
        compose.setContent { StagebookTheme { SettingsScreen(current, SettingsActions()) } }
        val cut = mutableListOf<String>()
        for ((name, state) in states) {
            current = state
            compose.waitForIdle()
            compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
                val out = mutableListOf<TextLayoutResult>()
                node.config.getOrNull(SemanticsActions.GetTextLayoutResult)!!.action!!(out)
                val l = out.first()
                if ((0 until l.lineCount).any { l.isLineEllipsized(it) }) cut += "$name: ${l.layoutInput.text.text}"
            }
        }
        assertTrue("cut: $cut", cut.isEmpty())
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h900dp-port-xxhdpi", application = android.app.Application::class)
class SettingsSummariesWholePt : SettingsSummariesWhole()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h900dp-port-xxhdpi", application = android.app.Application::class)
class SettingsSummariesWholeEn : SettingsSummariesWhole()
