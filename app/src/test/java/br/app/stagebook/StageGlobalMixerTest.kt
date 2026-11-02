package br.app.stagebook

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import br.app.stagebook.data.model.Look
import br.app.stagebook.domain.MixerConfig
import br.app.stagebook.ui.stage.StageScreen
import br.app.stagebook.ui.stage.StageSong
import br.app.stagebook.ui.stage.StageUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The mixer over the stage on the global mixer (user, 01/11): it scrolls down to the EQ, and its controls change the
 * global mixer (it used to be read-only, so the global mixer couldn't be adjusted while listening anywhere).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w411dp-h891dp-port-xxhdpi", application = android.app.Application::class)
class StageGlobalMixerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `on the global mixer the sheet scrolls and its controls change the global mixer`() {
        var global: MixerConfig? = null
        var songMix = false
        val song = StageSong("Canção de teste", "Banda Fictícia", "[G]Uma linha", false, id = "1", track = "1.ogg")
        compose.setContent {
            StagebookTheme {
                StageScreen(
                    StageUiState(loading = false, songs = listOf(song), look = Look(playerMode = true, mixer = MixerConfig(channels = 4))),
                    0, 0, { _, _ -> },
                    onSongMix = { _, _ -> songMix = true },
                    onGlobalMix = { global = it },
                )
            }
        }
        compose.onNodeWithContentDescription(str(R.string.stage_play)).performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText(str(R.string.stage_mixer_open)).performClick()
        compose.waitForIdle()
        compose.onAllNodesWithContentDescription(str(R.string.mixer_mute))[0].performClick()
        compose.mainClock.advanceTimeBy(500)   // the mixer saves a moment after the last change (MIXER_SAVE_MS)
        compose.waitForIdle()
        assertNotNull("mudou o mixer global", global)
        assertEquals(false, songMix)
        repeat(4) { compose.onAllNodes(isRoot())[1].performTouchInput { swipeUp(startY = bottom * 0.9f, endY = top + bottom * 0.2f) }; compose.waitForIdle() }
        compose.onNodeWithText(str(R.string.eq_title)).assertIsDisplayed()
    }

    private fun str(id: Int): String = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)
}
