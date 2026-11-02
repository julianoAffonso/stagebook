package br.app.stagebook

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import br.app.stagebook.domain.MixerConfig
import br.app.stagebook.ui.settings.MIXER_SAVE_MS
import br.app.stagebook.ui.settings.rememberMixerDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Dragging a fader (audit 01/11): shown at once, saved a few times a second with the latest value, not per event. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class MixerDraftTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `a fast drag saves a few times with the last value and shows each step at once`() {
        var saved by mutableStateOf(MixerConfig())
        val saves = mutableListOf<MixerConfig>()
        lateinit var shown: MixerConfig
        lateinit var set: (MixerConfig) -> Unit
        compose.setContent {
            val (m, s) = rememberMixerDraft(saved) { saves += it; saved = it }
            shown = m; set = s
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        repeat(30) { i ->
            compose.runOnIdle { set(MixerConfig(eqDb = List(5) { i.toFloat() })) }
            repeat(2) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
            assertEquals(i.toFloat(), shown.eqDb[0])
        }
        compose.mainClock.advanceTimeBy(MIXER_SAVE_MS * 2)
        compose.waitForIdle()
        assertTrue("salvou ${saves.size} vezes", saves.size in 1..8)
        assertEquals(29f, saves.last().eqDb[0])
        assertEquals(29f, shown.eqDb[0])
    }

    @Test fun `a change from elsewhere shows when nothing is waiting`() {
        var saved by mutableStateOf(MixerConfig())
        lateinit var shown: MixerConfig
        compose.setContent { shown = rememberMixerDraft(saved) { saved = it }.first }
        compose.runOnIdle { saved = MixerConfig(channels = 4) }
        compose.waitForIdle()
        assertEquals(4, shown.channels)
    }
}
