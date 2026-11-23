package br.app.stagebook

import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.Backup
import br.app.stagebook.data.Backup.Part
import br.app.stagebook.data.LibraryCodec
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.PdfStore
import br.app.stagebook.data.TrackStore
import br.app.stagebook.data.mergeSongs
import br.app.stagebook.data.model.Library
import br.app.stagebook.data.model.PdfMark
import br.app.stagebook.data.model.PdfPoint
import br.app.stagebook.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/** A song that is a PDF: the model, the store, the backup (with and without the files) and deleting (T-PDF 1). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PdfSongStoreTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val pdfBytes = "%PDF-1.4\n%fake body\n".toByteArray()

    private fun setup(): Triple<LibraryRepository, PdfStore, Backup> = runBlocking {
        val repo = testRepo()
        val pdfs = PdfStore(context)
        Triple(repo, pdfs, Backup(context, repo, TrackStore(context), null, pdfs))
    }

    private fun marks() = listOf(PdfMark(page = 2, points = listOf(PdfPoint(0.1f, 0.2f), PdfPoint(0.5f, 0.25f)), width = 0.03f))

    @Test fun `a library file from before the PDF reads with the defaults`() {
        val lib = LibraryCodec.decode("""{"version":1,"songs":[{"id":"a","title":"Old","text":"x"}]}""") { "n" }
        val s = lib.songs.single()
        assertEquals("", s.pdf)
        assertEquals("", s.pdfName)
        assertTrue(s.pdfMarks.isEmpty())
        assertFalse(s.pdfLost)
    }

    @Test fun `the strokes and the names survive the library file`() {
        val song = Song(id = "a", title = "T", pdf = "a-1.pdf", pdfName = "Chart.pdf", pdfMarks = marks())
        val back = LibraryCodec.decode(LibraryCodec.encode(Library(songs = listOf(song)))) { "n" }.songs.single()
        assertEquals(song, back)
    }

    @Test fun `a PDF without its file reads as lost`() {
        assertTrue(Song(title = "T", pdfName = "Chart.pdf").pdfLost)
        assertFalse(Song(title = "T", pdf = "a.pdf", pdfName = "Chart.pdf").pdfLost)
    }

    @Test fun `the text export and a merged new song never carry the PDF`() {
        val song = Song(id = "a", title = "T", text = "x", pdf = "a.pdf", pdfName = "C.pdf", pdfMarks = marks())
        val exported = LibraryCodec.decodeSongs(LibraryCodec.encodeSongs(listOf(song))).single()
        assertEquals("", exported.pdf)
        assertEquals("", exported.pdfName)
        assertTrue(exported.pdfMarks.isEmpty())
        val (merged, _) = Library().mergeSongs(listOf(song)) { "new" }
        assertEquals("", merged.songs.single().pdf)
    }

    @Test fun `importing over a song that has a PDF keeps the PDF`() {
        val mine = Song(id = "a", title = "T", author = "A", text = "old", pdf = "a.pdf", pdfName = "C.pdf", pdfMarks = marks())
        val (merged, _) = Library(songs = listOf(mine)).mergeSongs(listOf(Song(title = "T", author = "A", text = "new"))) { "n" }
        val s = merged.songs.single()
        assertEquals("new", s.text)
        assertEquals("a.pdf", s.pdf)
        assertEquals(marks(), s.pdfMarks)
    }

    @Test fun `copyPdf takes a real header and refuses other files and big ones`() {
        val to = File(context.cacheDir, "x.part")
        assertTrue(PdfStore.copyPdf(ByteArrayInputStream(pdfBytes), to))
        assertFalse(PdfStore.copyPdf(ByteArrayInputStream("PK\u0003\u0004zipfile".toByteArray()), to))
        assertFalse(PdfStore.copyPdf(ByteArrayInputStream(ByteArray(0)), to))
        val big = "%PDF-".toByteArray() + ByteArray(PdfStore.MAX_BYTES.toInt())
        assertFalse(PdfStore.copyPdf(ByteArrayInputStream(big), to))
        val limit = "%PDF-".toByteArray() + ByteArray(PdfStore.MAX_BYTES.toInt() - 5)
        assertTrue(PdfStore.copyPdf(ByteArrayInputStream(limit), to))
    }

    @Test fun `sweep removes old files no song uses and keeps the used and the fresh`() {
        val pdfs = PdfStore(context)
        val day = PdfStore.SWEEP_AGE_MS
        fun f(n: String, age: Long) = pdfs.file(n).apply { writeBytes(pdfBytes); setLastModified(System.currentTimeMillis() - age) }
        val used = f("used.pdf", 3 * day)
        val orphan = f("orphan.pdf", 3 * day)
        val fresh = f("fresh.pdf", 1_000)
        pdfs.sweep(setOf("used.pdf"))
        assertTrue(used.exists())
        assertFalse(orphan.exists())
        assertTrue(fresh.exists())
    }

    @Test fun `deleting the song deletes its PDF file`() = runBlocking {
        val (repo, pdfs, _) = setup()
        pdfs.file("gone.pdf").writeBytes(pdfBytes)
        pdfs.file("stays.pdf").writeBytes(pdfBytes)
        val id = repo.saveSong(Song(title = "A", text = "x", pdf = "gone.pdf", pdfName = "A.pdf"))
        repo.saveSong(Song(title = "B", text = "x", pdf = "stays.pdf", pdfName = "B.pdf"))
        repo.deleteSong(id)
        assertFalse(pdfs.file("gone.pdf").exists())
        assertTrue(pdfs.file("stays.pdf").exists())
    }

    @Test fun `backup with the files brings the PDF and the strokes back`() = runBlocking {
        val (repo, pdfs, backup) = setup()
        pdfs.file("chart.pdf").writeBytes(pdfBytes)
        val id = repo.saveSong(Song(title = "Chart", text = "hidden", pdf = "chart.pdf", pdfName = "Chart.pdf", pdfMarks = marks()))
        val out = ByteArrayOutputStream()
        backup.write(out, Part.entries.toSet())
        repo.restore(emptyList(), emptyList())
        pdfs.file("chart.pdf").delete()
        assertTrue(backup.restore(ByteArrayInputStream(out.toByteArray()), Part.entries.toSet()))
        val s = repo.song(id)!!
        assertEquals("chart.pdf", s.pdf)
        assertEquals("hidden", s.text)
        assertEquals(marks(), s.pdfMarks)
        assertTrue(pdfs.file("chart.pdf").readBytes().contentEquals(pdfBytes))
    }

    @Test fun `backup without the tracks option leaves the PDF out and the song plain`() = runBlocking {
        val (repo, pdfs, backup) = setup()
        pdfs.file("chart.pdf").writeBytes(pdfBytes)
        val id = repo.saveSong(Song(title = "Chart", text = "x", pdf = "chart.pdf", pdfName = "Chart.pdf", pdfMarks = marks()))
        val out = ByteArrayOutputStream()
        backup.write(out, setOf(Part.LIBRARY))
        val names = mutableListOf<String>()
        java.util.zip.ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { z -> generateSequence { z.nextEntry }.forEach { names += it.name } }
        assertTrue(names.none { it.startsWith("pdfs/") })
        repo.restore(emptyList(), emptyList())
        assertTrue(backup.restore(ByteArrayInputStream(out.toByteArray()), setOf(Part.LIBRARY)))
        val s = repo.song(id)!!
        assertEquals("", s.pdf)
        assertEquals("", s.pdfName)
        assertFalse(s.pdfLost)
    }

    @Test fun `a backup whose PDF file is not a PDF restores the song as lost`() = runBlocking {
        val (repo, pdfs, backup) = setup()
        pdfs.file("chart.pdf").writeBytes(pdfBytes)
        val id = repo.saveSong(Song(title = "Chart", text = "x", pdf = "chart.pdf", pdfName = "Chart.pdf"))
        val out = ByteArrayOutputStream()
        backup.write(out, Part.entries.toSet())
        // swaps the PDF entry's content for something else
        val changed = ByteArrayOutputStream()
        java.util.zip.ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zin ->
            java.util.zip.ZipOutputStream(changed).use { zout ->
                generateSequence { zin.nextEntry }.forEach { e ->
                    val bytes = zin.readBytes()
                    zout.putNextEntry(java.util.zip.ZipEntry(e.name))
                    zout.write(if (e.name.startsWith("pdfs/")) "MZ not a pdf".toByteArray() else bytes)
                    zout.closeEntry()
                }
            }
        }
        repo.restore(emptyList(), emptyList())
        pdfs.file("chart.pdf").delete()
        assertTrue(backup.restore(ByteArrayInputStream(changed.toByteArray()), Part.entries.toSet()))
        val s = repo.song(id)!!
        assertEquals("", s.pdf)
        assertTrue(s.pdfLost)
        assertFalse(pdfs.file("chart.pdf").exists())
    }

    @Test fun `a song whose PDF file is gone loads as lost`() {
        val lib = Library(songs = listOf(Song(id = "a", title = "T", pdf = "missing.pdf", pdfName = "C.pdf"), Song(id = "b", title = "U", pdf = "here.pdf", pdfName = "D.pdf")))
        val clean = lib.withoutMissingPdfs { it == "here.pdf" }
        assertTrue(clean.song("a")!!.pdfLost)
        assertEquals("here.pdf", clean.song("b")!!.pdf)
        assertTrue(lib.withoutMissingPdfs { true } === lib)
    }
}
