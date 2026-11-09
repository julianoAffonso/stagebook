package br.app.stagebook

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.model.Look
import br.app.stagebook.ui.stage.StageScreen
import br.app.stagebook.ui.stage.StageSong
import br.app.stagebook.ui.stage.StageUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The track button while the USB interface is away mid-song (user, 09/11): Stop said "playing" when nothing played.
 * One sign for both ways back, and the usual button again once the interface returns.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w1280dp-h800dp-land-hdpi", application = android.app.Application::class)
class StageTrackUsbAwayTest {
    @get:Rule val compose = createComposeRule()
    private val res = ApplicationProvider.getApplicationContext<android.app.Application>().resources
    private val song = listOf(StageSong("Teste", "", "[G]Primeira linha", false, id = "s1", track = "faixa.ogg"))

    private fun show(playing: Boolean, away: Boolean) = compose.setContent {
        StagebookTheme {
            StageScreen(StageUiState(loading = false, songs = song, look = Look(playerMode = true)), 0, 0, { _, _ -> }, playing = playing, usbAway = away)
        }
    }

    @Test fun `waiting for the interface shows the USB sign, not Stop`() {
        show(playing = true, away = true)
        compose.onNodeWithContentDescription(res.getString(R.string.stage_track_usb_waiting)).assertExists()
        compose.onNodeWithContentDescription(res.getString(R.string.stage_stop)).assertDoesNotExist()
    }

    @Test fun `after the wait the same sign says a tap carries on`() {
        show(playing = false, away = true)
        compose.onNodeWithContentDescription(res.getString(R.string.stage_track_usb_away)).assertExists()
    }

    @Test fun `with the interface back the button is the usual one`() {
        show(playing = true, away = false)
        compose.onNodeWithContentDescription(res.getString(R.string.stage_stop)).assertExists()
    }
}
