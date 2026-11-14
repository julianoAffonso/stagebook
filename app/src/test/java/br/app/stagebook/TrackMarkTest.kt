package br.app.stagebook

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import br.app.stagebook.data.model.Look
import br.app.stagebook.ui.components.AppIcons
import br.app.stagebook.ui.components.SongFacts
import br.app.stagebook.ui.components.TrackMarkIcon
import br.app.stagebook.ui.stage.StageScreen
import br.app.stagebook.ui.stage.StageSong
import br.app.stagebook.ui.stage.StageUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A row's "this song has a backing track" mark is an audio wave: the play triangle there read as a button. The
 * triangle stays where it is one (the stage's play).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w1280dp-h800dp-land-hdpi", application = android.app.Application::class)
class TrackMarkTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `the mark is the audio wave, not the play triangle`() {
        assertSame(AppIcons.AudioWave, TrackMarkIcon)
        assertNotSame(AppIcons.Play, TrackMarkIcon)
        assertEquals("graphic_eq", TrackMarkIcon.name)
        assertEquals("play", AppIcons.Play.name)
    }

    @Test fun `a row with a backing track draws the mark with the same description, and one without draws none`() {
        compose.setContent { StagebookTheme { androidx.compose.foundation.layout.Column { SongFacts("Em", 120, 2, true); SongFacts("G", 96, 0, false) } } }
        compose.onAllNodesWithContentDescription("Backing track").assertCountEquals(1)
    }

    @Test fun `the stage keeps its play button`() {
        compose.setContent {
            StagebookTheme {
                StageScreen(
                    StageUiState(loading = false, songs = listOf(StageSong("Com faixa", "", "Uma linha", false, id = "s1", track = "a.ogg")), look = Look(playerMode = true)),
                    0, 0, { _, _ -> },
                )
            }
        }
        compose.onNodeWithContentDescription("Tocar o backing track").assertExists()
    }
}
