package br.app.stagebook.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/** The library and settings are swapped on disk without ever leaving a half-written file. */
class AtomicFileTest {
    @get:Rule val folder = TemporaryFolder()

    private fun file(name: String = "lib.json") = File(folder.root, name)
    private fun tmpOf(f: File) = File(f.parentFile, "${f.name}.tmp")

    @Test fun `a new file is written and no temp file stays`() {
        val f = file()
        assertTrue(f.replaceAtomically("one".toByteArray()))
        assertEquals("one", f.readText())
        assertFalse(tmpOf(f).exists())
    }

    @Test fun `the previous content becomes the backup`() {
        val f = file()
        val backup = file("lib.bak")
        f.writeText("old")
        assertTrue(f.replaceAtomically("new".toByteArray(), backup))
        assertEquals("new", f.readText())
        assertEquals("old", backup.readText())
    }

    @Test fun `without a previous file no backup is made`() {
        val f = file()
        val backup = file("lib.bak")
        f.replaceAtomically("new".toByteArray(), backup)
        assertFalse(backup.exists())
    }

    @Test fun `ready sees the whole temp file and can refuse it, leaving everything as it was`() {
        val f = file()
        val backup = file("lib.bak")
        f.writeText("old")
        var seen: ByteArray? = null
        val ok = f.replaceAtomically("broken".toByteArray(), backup) { tmp -> seen = tmp.readBytes(); false }
        assertFalse(ok)
        assertArrayEquals("broken".toByteArray(), seen)
        assertEquals("old", f.readText())
        assertFalse(backup.exists())
        assertFalse(tmpOf(f).exists())
    }

    @Test fun `a write that fails midway keeps the old file and deletes the temp`() {
        val f = file()
        f.writeText("old")
        try {
            f.replaceAtomically { out -> out.write("half".toByteArray()); throw IOException("disk full") }
            fail("the disk error must reach the caller")
        } catch (e: IOException) {
            assertEquals("disk full", e.message)
        }
        assertEquals("old", f.readText())
        assertFalse(tmpOf(f).exists())
    }

    @Test fun `a temp file that can't be created fails with IOException and the file stays`() {
        val f = file()
        f.writeText("old")
        tmpOf(f).mkdirs()   // a folder where the temp file should go
        try {
            f.replaceAtomically("new".toByteArray())
            fail("expected IOException")
        } catch (_: IOException) {
        }
        assertEquals("old", f.readText())
    }

    @Test fun `when the new file can't go in, the previous one comes back from the backup`() {
        val f = file()
        val backup = file("lib.bak")
        f.writeText("old")
        // between the checks and the swap the temp file disappears: its rename over the file fails
        val r = runCatching { f.replaceAtomically("new".toByteArray(), backup) { tmp -> tmp.delete(); true } }
        assertTrue(r.exceptionOrNull() is IOException)
        assertEquals("old", f.readText())
    }

    @Test fun `big content goes through the buffer whole`() {
        val f = file()
        val bytes = ByteArray(300_000) { (it % 251).toByte() }
        f.replaceAtomically(bytes)
        assertArrayEquals(bytes, f.readBytes())
    }
}
