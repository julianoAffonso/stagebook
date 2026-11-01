package br.app.stagebook

import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.TrackStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Backing track files (audit 01/11): the ones no song points to leave the disk, but not a fresh one. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class TrackStoreTest {
    private val store = TrackStore(ApplicationProvider.getApplicationContext<android.app.Application>())
    private val day = TrackStore.SWEEP_AGE_MS

    private fun file(name: String, age: Long) = store.file(name).apply { writeText("x"); setLastModified(System.currentTimeMillis() - age) }

    @Test fun `old files without a song and old half copies go - the used ones and fresh ones stay`() {
        val used = file("usada.ogg", 3 * day)
        val orphan = file("apagada.ogg", 3 * day)
        val half = file("meia.ogg.part", 3 * day)
        val fresh = file("nova-ainda-nao-salva.ogg", 60_000)
        store.sweep(setOf("usada.ogg"))
        assertTrue(used.exists())
        assertFalse(orphan.exists())
        assertFalse(half.exists())
        assertTrue(fresh.exists())
    }

    class Revoked : android.content.ContentProvider() {
        override fun onCreate() = true
        override fun query(uri: android.net.Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): android.database.Cursor? =
            throw SecurityException("permission revoked")
        override fun getType(uri: android.net.Uri): String? = throw SecurityException("permission revoked")
        override fun insert(uri: android.net.Uri, values: android.content.ContentValues?): android.net.Uri? = null
        override fun delete(uri: android.net.Uri, s: String?, a: Array<out String>?) = 0
        override fun update(uri: android.net.Uri, v: android.content.ContentValues?, s: String?, a: Array<out String>?) = 0
    }

    @Test fun `a picked file whose permission was revoked is just not used - no crash`() {
        org.robolectric.Robolectric.setupContentProvider(Revoked::class.java, "revogado")
        val name = kotlinx.coroutines.runBlocking { store.copy("s1", android.net.Uri.parse("content://revogado/faixa.ogg")) }
        org.junit.Assert.assertNull(name)
    }
}
