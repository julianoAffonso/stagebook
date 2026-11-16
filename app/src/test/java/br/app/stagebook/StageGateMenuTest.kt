package br.app.stagebook

import br.app.stagebook.data.StageGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The stage is the app's base, so "on stage" is nearly always true and a request to join (band, computer) never showed (user
 * test, 15/11: "I asked to come in and nothing arrived"). With the side menu open the person is not playing: that is told here
 * and the notices show then.
 */
class StageGateMenuTest {
    @Test fun `the menu is closed at first and follows what is told, apart from being on stage`() {
        val gate = StageGate()
        assertFalse(gate.menuOpen.value)
        gate.enter()
        assertTrue(gate.onStage.value)
        gate.setMenuOpen(true)
        assertTrue("on stage and the menu open at once", gate.onStage.value && gate.menuOpen.value)
        gate.setMenuOpen(false)
        assertFalse(gate.menuOpen.value)
        assertTrue("the menu doesn't change the stage's rule for sync", gate.onStage.value)
    }

    @Test fun `a request waits only while the stage is open, in view and the menu closed`() {
        assertTrue(StageGate.quiet(onStage = true, stageInView = true, menuOpen = false))
        assertFalse("menu open over the stage", StageGate.quiet(true, true, true))
        assertFalse("Settings over an open stage (the person turned the editor on there)", StageGate.quiet(true, false, false))
        assertFalse("no stage at all", StageGate.quiet(false, false, false))
    }

    @Test fun `the show is on screen only with a stage open, in view and the menu closed`() {
        val gate = StageGate()
        assertFalse("no stage yet", gate.playing.value)
        gate.enter()
        assertTrue("a stage that opens is in view", gate.playing.value)
        gate.setMenuOpen(true)
        assertFalse("the menu is open: the person is not playing", gate.playing.value)
        gate.setMenuOpen(false)
        assertTrue(gate.playing.value)
        gate.setStageInView(false)   // Settings, an editor or the importer over the stage
        assertFalse(gate.playing.value)
        assertTrue("the stage is still open under it", gate.onStage.value)
        gate.setStageInView(true)
        assertTrue(gate.playing.value)
        gate.leave()
        assertFalse(gate.playing.value)
    }
}
