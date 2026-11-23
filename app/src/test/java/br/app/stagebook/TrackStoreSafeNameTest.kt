package br.app.stagebook

import br.app.stagebook.data.TrackStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** A track's name from outside (a songs file, a backup) never reaches a file outside the tracks folder (audit 22/11). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class TrackStoreSafeNameTest {
    @Test fun `a name with a path points at nothing, and removing it deletes nothing`() {
        val context = appContext()
        val store = TrackStore(context)
        val prefs = File(context.filesDir.parentFile, "shared_prefs").apply { mkdirs() }
        val victim = File(prefs, "band.xml").apply { writeText("x") }
        val tracks = store.file("ok.ogg").parentFile!!
        assertEquals(tracks, store.file("../../shared_prefs/band.xml").parentFile)
        store.remove("../shared_prefs/band.xml")
        store.remove("../../shared_prefs/band.xml")
        assertTrue(victim.exists())
        File(tracks, "ok.ogg").writeText("a")
        store.remove("ok.ogg")
        assertTrue(!File(tracks, "ok.ogg").exists())
    }
}
