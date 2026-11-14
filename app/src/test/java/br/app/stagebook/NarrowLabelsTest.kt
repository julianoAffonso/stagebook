package br.app.stagebook

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import br.app.stagebook.data.model.SongProfile
import br.app.stagebook.ui.components.Choice
import br.app.stagebook.ui.components.ChoiceFilter
import br.app.stagebook.ui.components.SearchWithFilter
import br.app.stagebook.ui.components.SegmentedChoice
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Labels on a narrow phone (360 dp) with the system's text made bigger (1.15): "Recentes" (the order of the setlists
 * list) and "Letra e cifra" (the chords profile in Settings) were cut. Real text sizes (NATIVE), or everything fits.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h800dp-port-xxhdpi", application = android.app.Application::class)
class NarrowLabelsTest {
    @get:Rule val compose = createComposeRule()

    @Before fun bigText() = RuntimeEnvironment.setFontScale(1.15f)

    private fun layoutOf(text: String): TextLayoutResult {
        val node = compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().first()
        val out = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)!!.action!!(out)
        return out.first()
    }

    private fun whole(text: String) {
        val l = layoutOf(text)
        val node = compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().first()
        assertEquals("\"$text\" on one line", 1, l.lineCount)
        assertFalse("\"$text\" isn't cut", l.didOverflowWidth || l.didOverflowHeight)
    }

    /**
     * A text field's text isn't measured against its visible part (it scrolls): the room it has is the field's width
     * less its two side paddings (16 dp) and the 48 dp the open/close arrow takes at the end.
     */
    private fun wholeInField(text: String) {
        val l = layoutOf(text)
        val node = compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().first()
        val room = node.boundsInRoot.width - with(compose.density) { (16.dp + 16.dp + 48.dp).toPx() }
        val need = l.getLineRight(0) - l.getLineLeft(0)
        assertTrue("\"$text\" needs $need px and the field has $room px for it", need <= room)
    }

    @Test fun `Recentes in the setlists order field is whole`() {
        compose.setContent {
            StagebookTheme {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SearchWithFilter(
                        "", {}, "Buscar setlist", "Buscar", Modifier,
                        filter = { m -> ChoiceFilter("Ordem", listOf(false, true), false, {}, { if (it) "A a Z" else "Recentes" }, m) },
                    )
                }
            }
        }
        compose.waitForIdle()
        wholeInField("Recentes")
    }

    @Test fun `Letra e cifra in the profile chooser is whole`() {
        compose.setContent {
            StagebookTheme {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SegmentedChoice(
                        listOf(Choice(SongProfile.CHORDS, br.app.stagebook.R.string.editor_lyrics_chords), Choice(SongProfile.LYRICS, br.app.stagebook.R.string.settings_lyrics_only), Choice(SongProfile.AUTO, br.app.stagebook.R.string.settings_profile_auto)),
                        SongProfile.CHORDS, {},
                    )
                }
            }
        }
        compose.waitForIdle()
        whole("Letra e cifra")
        whole("Só letra")
    }
}
