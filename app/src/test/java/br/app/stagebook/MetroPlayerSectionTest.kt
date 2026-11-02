package br.app.stagebook

import br.app.stagebook.data.model.Look
import br.app.stagebook.ui.components.AppIcons
import br.app.stagebook.ui.settings.SettingsSection
import br.app.stagebook.ui.settings.iconOf
import br.app.stagebook.ui.settings.shown
import br.app.stagebook.ui.settings.titleOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Metronome and player" (user, 02/11): named after what essential mode left on, gone only with both off. */
class MetroPlayerSectionTest {
    private val s = SettingsSection.METRONOME

    @Test fun `the item is named after what is on`() {
        assertEquals(R.string.metro_player_title, s.titleOf(Look(extrasOn = true, playerMode = true)))
        assertEquals(R.string.metro_title, s.titleOf(Look(extrasOn = true, playerMode = false)))
        assertEquals(R.string.player_title, s.titleOf(Look(extrasOn = false, playerMode = true)))
        assertEquals(AppIcons.Play, s.iconOf(Look(extrasOn = false, playerMode = true)))
    }

    @Test fun `the item leaves only when both are off`() {
        assertTrue(s.shown(Look(extrasOn = false, playerMode = true)))
        assertTrue(s.shown(Look(extrasOn = true, playerMode = false)))
        assertFalse(s.shown(Look(extrasOn = false, playerMode = false)))
    }
}
