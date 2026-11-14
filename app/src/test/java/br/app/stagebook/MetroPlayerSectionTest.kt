package br.app.stagebook

import br.app.stagebook.data.model.Look
import br.app.stagebook.ui.settings.SettingsSection
import br.app.stagebook.ui.settings.iconOf
import br.app.stagebook.ui.settings.shown
import br.app.stagebook.ui.settings.titleOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Player and metronome" (user, 14/11): a fixed name whatever is on, gone only with both off. */
class MetroPlayerSectionTest {
    private val s = SettingsSection.PLAYER

    @Test fun `the name is fixed whatever is on`() {
        listOf(Look(extrasOn = true, playerMode = true), Look(extrasOn = true, playerMode = false), Look(extrasOn = false, playerMode = true)).forEach {
            assertEquals(R.string.metro_player_title, s.titleOf(it))
            assertEquals(s.icon, s.iconOf(it))
        }
    }

    @Test fun `the group leaves only when both are off`() {
        assertTrue(s.shown(Look(extrasOn = false, playerMode = true)))
        assertTrue(s.shown(Look(extrasOn = true, playerMode = false)))
        assertFalse(s.shown(Look(extrasOn = false, playerMode = false)))
    }
}
