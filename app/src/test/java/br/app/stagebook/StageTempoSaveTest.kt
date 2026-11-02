package br.app.stagebook

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import br.app.stagebook.data.model.Look
import br.app.stagebook.ui.stage.StageScreen
import br.app.stagebook.ui.stage.StageSong
import br.app.stagebook.ui.stage.StageUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The metronome's BPM is the song's (user, 02/11): + and − on stage save it in the song, only after a press. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w411dp-h891dp-port-xxhdpi", application = android.app.Application::class)
class StageTempoSaveTest {
    @get:Rule val compose = createComposeRule()

    private fun show(text: String, saved: MutableList<Pair<String, Int>>) = compose.setContent {
        StagebookTheme {
            StageScreen(
                StageUiState(loading = false, songs = listOf(StageSong("Teste", "", text, false, id = "s1")), look = Look(metroShown = true)),
                0, 0, { _, _ -> },
                onTempo = { id, bpm -> saved += id to bpm },
            )
        }
    }

    private fun str(id: Int) = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    @Test fun `faster twice saves the new BPM in the song`() {
        val saved = mutableListOf<Pair<String, Int>>()
        show("{tempo: 100}\n[G]Uma linha", saved)
        compose.onNodeWithContentDescription(str(R.string.metro_title)).performClick()
        compose.onNodeWithContentDescription(str(R.string.metro_faster)).performClick()
        compose.onNodeWithContentDescription(str(R.string.metro_faster)).performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertEquals("s1" to 102, saved.last())
    }

    @Test fun `opening a song without a BPM writes nothing`() {
        val saved = mutableListOf<Pair<String, Int>>()
        show("[G]Uma linha", saved)
        compose.onNodeWithContentDescription(str(R.string.metro_title)).performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertEquals(emptyList<Pair<String, Int>>(), saved)
    }
}
