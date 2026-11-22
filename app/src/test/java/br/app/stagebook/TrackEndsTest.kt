package br.app.stagebook

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import br.app.stagebook.data.model.Look
import br.app.stagebook.domain.TrackEnds
import br.app.stagebook.ui.settings.SettingsActions
import br.app.stagebook.ui.settings.SettingsScreen
import br.app.stagebook.ui.settings.SettingsSection
import br.app.stagebook.ui.settings.SettingsUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A fade when the track stops, and the track starting at its first sound (user, 22/11). The sound itself is in the engine simulator. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class TrackEndsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `a little silence stays before the first sound, and an unreadable file starts at the start`() {
        assertEquals(1950L, TrackEnds.startAt(2000))
        assertEquals(0L, TrackEnds.startAt(30))
        assertEquals(0L, TrackEnds.startAt(-1))
    }

    @Test fun `the jump to the start waits for the fade, and only then`() {
        assertEquals(0L, TrackEnds.seekDelay(0))
        assertEquals(2000L + TrackEnds.FADE_SLACK_MS, TrackEnds.seekDelay(2000))
    }

    @Test fun `another audio file under the same name, size and date is measured again`() {
        val a = ByteArray(100_000) { (it % 7).toByte() }
        val b = a.copyOf().also { it[10] = 99 }
        val keyA = TrackEnds.cacheKey("faixa.ogg", 100_000, 5L, TrackEnds.headOf(a.inputStream()))
        val keyB = TrackEnds.cacheKey("faixa.ogg", 100_000, 5L, TrackEnds.headOf(b.inputStream()))
        org.junit.Assert.assertNotEquals(keyA, keyB)
        assertEquals(keyA, TrackEnds.cacheKey("faixa.ogg", 100_000, 5L, TrackEnds.headOf(a.copyOf().inputStream())))
    }

    @Test fun `the old file's measures go, the current one's stay, and gone files are swept`() {
        val keys = listOf("faixa.ogg|1|1|1", "faixa.ogg|2|2|2", "faixa.ogg|2|2|2|3", "outra.ogg|1|1|1", "sumiu.ogg|1|1|1")
        assertEquals(
            listOf("faixa.ogg|1|1|1", "sumiu.ogg|1|1|1"),
            TrackEnds.stale(keys, setOf("faixa.ogg", "outra.ogg"), "faixa.ogg", "faixa.ogg|2|2|2"),
        )
    }

    @Test fun `old settings keep today's stop and start at the first sound`() {
        val old = lenientJson.decodeFromString(Look.serializer(), "{}")
        assertEquals(0, old.stopFadeMs)
        assertTrue(old.trimSilence)
    }

    @Test fun `Settings, Player chooses the fade and the silence`() {
        var fade = -1
        var trim: Boolean? = null
        var state by mutableStateOf(SettingsUiState(loading = false, look = Look(playerMode = true)))
        compose.setContent {
            StagebookTheme {
                SettingsScreen(
                    state, SettingsActions(onStopFade = { fade = it }, onTrimSilence = { trim = it }), Modifier.fillMaxSize(),
                    section = SettingsSection.PLAYER, onLeave = {}, onMenu = {},
                )
            }
        }
        compose.onNodeWithText("Começo e fim").performScrollTo().assertExists()
        compose.onNodeWithText("Sem fade").performScrollTo().performClick()
        compose.onNodeWithText("2 s").performClick()
        assertEquals(2000, fade)
        compose.runOnUiThread { state = state.copy(look = state.look.copy(stopFadeMs = 500)) }
        compose.onNodeWithText("0,5 s").performScrollTo().assertExists()
        compose.onNodeWithText("Pular o silêncio do começo").performScrollTo().performClick()
        assertEquals(false, trim)
        compose.runOnUiThread { state = state.copy(look = state.look.copy(playerMode = false)) }
        assertFalse(compose.onAllNodesWithTextCount("Começo e fim") > 0)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String) =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size
}
