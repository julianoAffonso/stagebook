package br.app.stagebook

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

/**
 * The player reaching the USB interface restarts the metronome only when it sounds there (audit 02/11): with the
 * device output, play on the track restarted the click from beat 1 mid-song.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w411dp-h891dp-port-xxhdpi", application = android.app.Application::class)
class StageMetronomeUsbTest {
    @get:Rule val compose = createComposeRule()

    private fun str(id: Int) = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    private fun starts(look: Look): Int {
        var ready by mutableStateOf(false)
        val sounds = mutableListOf<Int?>()
        compose.setContent {
            StagebookTheme {
                StageScreen(
                    StageUiState(loading = false, songs = listOf(StageSong("Teste", "", "{tempo: 100}\n[G]Uma linha", false, id = "s1")), look = look),
                    0, 0, { _, _ -> },
                    onMetronomeSound = { sounds += it },
                    metroUsbReady = ready,
                )
            }
        }
        compose.onNodeWithContentDescription(str(R.string.metro_title)).performClick()
        compose.onNodeWithContentDescription(str(R.string.metro_play)).performClick()
        compose.waitForIdle()
        val before = sounds.count { it != null }
        ready = true
        compose.waitForIdle()
        return sounds.count { it != null } - before
    }

    @Test fun `with the device output the player connecting doesn't restart the metronome`() {
        assertEquals(0, starts(Look(metroShown = true, playerMode = true, metroUsbOut = -1)))
    }

    @Test fun `with a USB output the player connecting restarts it there`() {
        assertEquals(1, starts(Look(metroShown = true, playerMode = true, metroUsbOut = 2)))
    }
}
