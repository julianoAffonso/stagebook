package br.app.stagebook

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import br.app.stagebook.data.model.Look
import br.app.stagebook.domain.toSongMix
import br.app.stagebook.ui.stage.StageScreen
import br.app.stagebook.ui.stage.StageSong
import br.app.stagebook.ui.stage.StageUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Stage: the mixer opens over the stage, so the backing track keeps playing while adjusting (user, 31/10). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w1100dp-h720dp-land-xhdpi", application = android.app.Application::class)
class StageMixerTest {
    @get:Rule val compose = createComposeRule()

    // the mixer is in play's long press (user, 01/11)
    private fun openMixer() {
        compose.onNodeWithContentDescription("Tocar o backing track").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("Abrir o mixer").performClick()
    }

    private fun stage(track: String, playerMode: Boolean = true, playing: Boolean = false, stops: Boolean = true) {
        val song = StageSong("Música", "", "[G]linha", false, id = "s1", track = track)
        compose.setContent {
            StagebookTheme {
                StageScreen(StageUiState(false, listOf(song), look = Look(playerMode = playerMode, trackStops = stops)), 0, 0, { _, _ -> }, playing = playing)
            }
        }
    }

    // live, the track button stops and goes back to the start; pausing is a choice in Settings → Player (user, 31/10)
    @Test fun `playing, the track button stops by default`() {
        stage(track = "s1.ogg", playing = true)
        compose.onNodeWithContentDescription("Parar o backing track e voltar ao início").assertExists()
    }

    @Test fun `playing, the track button pauses when stop is turned off`() {
        stage(track = "s1.ogg", playing = true, stops = false)
        compose.onNodeWithContentDescription("Pausar o backing track").assertExists()
    }

    @Test fun `the mixer button opens the mixer over the stage`() {
        stage(track = "s1.ogg")
        openMixer()
        compose.waitForIdle()
        compose.onNodeWithText("Ver os níveis ao vivo").assertExists()
    }

    @Test fun `the sheet says changes stay with this song, and offers going back when the song has its own`() {
        stage(track = "s1.ogg")
        openMixer()
        compose.waitForIdle()
        compose.onNodeWithText("Desligue para mexer só nesta música", substring = true).assertExists()
        compose.onNodeWithText("Usar o mixer global").assertExists()   // on: no settings of its own
    }

    @Test fun `a song with its own mix shows it and the way back to the general mixer`() {
        val song = StageSong("Música", "", "[G]linha", false, id = "s1", track = "s1.ogg", mix = br.app.stagebook.domain.MixerConfig().toSongMix())
        compose.setContent {
            StagebookTheme { StageScreen(StageUiState(false, listOf(song), look = Look(playerMode = true)), 0, 0, { _, _ -> }) }
        }
        openMixer()
        compose.waitForIdle()
        compose.onNodeWithText("Esta música tem ajustes próprios", substring = true).assertExists()
        compose.onNodeWithText("Usar o mixer global").assertExists()
    }

    private fun lostStage(playerMode: Boolean = true, playing: Boolean = false, onPick: (String) -> Unit = {}, onForget: (String) -> Unit = {}) {
        val song = StageSong("Música", "", "[G]linha", false, id = "s1", lostTrack = "Hazard (backing)")
        compose.setContent {
            StagebookTheme {
                StageScreen(StageUiState(false, listOf(song), look = Look(playerMode = playerMode)), 0, 0, { _, _ -> }, playing = playing, onPickTrack = onPick, onForgetTrack = onForget)
            }
        }
    }

    // a song whose audio didn't come with it (user, 10/11): an alert where play would be, naming the audio
    @Test fun `a song whose audio did not come shows an alert that names it and offers to attach it again`() {
        var picked = ""
        lostStage(onPick = { picked = it })
        compose.onNodeWithContentDescription("Tocar o backing track").assertDoesNotExist()
        compose.onNodeWithContentDescription("O áudio desta música está faltando").performClick()
        compose.onNodeWithText("Hazard (backing)").assertExists()
        compose.onNodeWithText("O áudio não está neste aparelho. Anexe de novo.").assertExists()
        compose.onNodeWithText("Escolher").performClick()
        org.junit.Assert.assertEquals("s1", picked)
    }

    @Test fun `leaving the song without audio forgets the name`() {
        var forgotten = ""
        lostStage(onForget = { forgotten = it })
        compose.onNodeWithContentDescription("O áudio desta música está faltando").performClick()
        compose.onNodeWithText("Não usar áudio").performClick()
        org.junit.Assert.assertEquals("s1", forgotten)
    }

    @Test fun `the alert stays out with the player off, and never covers a track that is playing`() {
        lostStage(playerMode = false)
        compose.onNodeWithContentDescription("O áudio desta música está faltando").assertDoesNotExist()
    }

    @Test fun `with another song's track playing, the stop button stays instead of the alert`() {
        lostStage(playing = true)
        compose.onNodeWithContentDescription("O áudio desta música está faltando").assertDoesNotExist()
        compose.onNodeWithContentDescription("Parar o backing track e voltar ao início").assertExists()
    }

    @Test fun `no mixer button on a song without a backing track`() {
        stage(track = "")
        compose.onNodeWithContentDescription("Abrir o mixer").assertDoesNotExist()
    }

    @Test fun `no mixer button with the player off`() {
        stage(track = "s1.ogg", playerMode = false)
        compose.onNodeWithContentDescription("Abrir o mixer").assertDoesNotExist()
    }

    // a link inside a text runs its action (the global mixer one opens Settings → Player, user 01/11)
    @Test fun `a link in a text runs its action`() {
        var opened = false
        compose.setContent { StagebookTheme { br.app.stagebook.ui.components.LinkText("%1${'$'}s", "mixer global", { opened = true }) } }
        compose.onNodeWithText("mixer global").performClick()
        org.junit.Assert.assertTrue(opened)
    }

    // "Use the global mixer" on top of the song's mixer (user, 01/11)
    @Test fun `use the global mixer - on without own settings, off with them`() {
        stage(track = "s1.ogg")
        openMixer()
        compose.waitForIdle()
        compose.onNodeWithText("Usar o mixer global").assertExists()
    }


    // removing the track from play's menu asks first (user, 01/11)
    @Test fun `removing the track asks first`() {
        var removed = 0
        val song = StageSong("Música", "", "[G]linha", false, id = "s1", track = "s1.ogg")
        compose.setContent {
            StagebookTheme { StageScreen(StageUiState(false, listOf(song), look = Look(playerMode = true)), 0, 0, { _, _ -> }, onRemoveTrack = { removed++ }) }
        }
        compose.onNodeWithContentDescription("Tocar o backing track").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("Remover o backing track").performClick()
        org.junit.Assert.assertEquals(0, removed)
        compose.onNodeWithText("Remover").performClick()
        org.junit.Assert.assertEquals(1, removed)
    }
}
