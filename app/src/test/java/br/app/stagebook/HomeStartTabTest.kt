package br.app.stagebook

import br.app.stagebook.ui.home.HomeTab
import br.app.stagebook.ui.home.startOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The app opens on Setlists, or on Songs while there's no setlist yet (user, 31/10). */
class HomeStartTabTest {
    @Test fun `no tab until the library has loaded`() = assertNull(startOf(loaded = false, noSetlists = false))

    @Test fun `opens on Setlists when there is a setlist`() = assertEquals(HomeTab.SETLISTS, startOf(loaded = true, noSetlists = false))

    @Test fun `opens on Songs while there is no setlist`() = assertEquals(HomeTab.SONGS, startOf(loaded = true, noSetlists = true))

    // the tab picked last wins, even Settings (user, 24/11)
    @Test fun `opens on the tab picked last`() {
        assertEquals(HomeTab.SETTINGS, startOf(loaded = true, noSetlists = false, remembered = HomeTab.SETTINGS))
        assertEquals(HomeTab.SONGS, startOf(loaded = true, noSetlists = true, remembered = HomeTab.SONGS))
        assertEquals(HomeTab.SETLISTS, startOf(loaded = true, noSetlists = true, remembered = HomeTab.SETLISTS))
    }

    @Test fun `still no tab before the library loads, whatever was picked`() =
        assertNull(startOf(loaded = false, noSetlists = false, remembered = HomeTab.SETTINGS))
}
