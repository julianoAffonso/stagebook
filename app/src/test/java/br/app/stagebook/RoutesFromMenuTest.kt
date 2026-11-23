package br.app.stagebook

import br.app.stagebook.navigation.Importer
import br.app.stagebook.navigation.Settings
import br.app.stagebook.navigation.SongEditor
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Settings, the song editor and the importer opened from the side menu bring the menu again on leaving (user, 15/11):
 * the route says it came from there. Anything else (a link, the stage's pencil, a setlist import) doesn't.
 */
class RoutesFromMenuTest {
    @Test fun `a route is not from the menu unless it says so`() {
        assertFalse(Settings().fromMenu)
        assertFalse(SongEditor(songId = "a").fromMenu)
        assertFalse(Importer().fromMenu)
    }

    @Test fun `the editor opened from the menu keeps the flag when it moves to another song or to the importer's song`() {
        val first = SongEditor(songId = "a", fromMenu = true)
        // what the previous/next buttons and the import do: a new route that carries the flag on
        val next = SongEditor(songId = "b", fromMenu = first.fromMenu)
        val imported = SongEditor(songId = "c", fromImport = true, fromMenu = Importer(fromMenu = true).fromMenu)
        assertTrue(next.fromMenu)
        assertTrue(imported.fromMenu)
    }

    @Test fun `an old saved route without the flag reads as not from the menu`() {
        val json = lenientJson
        assertEquals(false, json.decodeFromString<Settings>("""{"section":"DEVICE"}""").fromMenu)
        assertEquals(false, json.decodeFromString<SongEditor>("""{"songId":"a"}""").fromMenu)
    }
}
