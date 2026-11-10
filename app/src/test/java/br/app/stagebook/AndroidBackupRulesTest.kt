package br.app.stagebook

import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.model.Library
import br.app.stagebook.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Android's own copy of the app (Google account, device to device) carries the songs, the setlists and the language and
 * nothing else (audit 10/11, user's decision): with no rules it took everything, the band without the identity that
 * makes it worth something, and the songs pointing to tracks that never came.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class AndroidBackupRulesTest {
    private val travels = setOf("file:stagebook.json", "file:stagebook.json.bak", "sharedpref:lingua.xml")

    /** Every preferences file of the app and whether Android's copy carries it. A new one has to be decided here. */
    private val prefs = mapOf(
        "lingua" to true,
        "advanced" to false, "band" to false, "band_net" to false, "identity" to false, "norm" to false,
        "player" to false, "receive_links" to false, "resume_after_update" to false, "stage_quiet" to false,
        "usb_no_input" to false, "usb_queue" to false, "usb_route" to false, "versao_nova" to false, "web_editor" to false,
    )

    private fun includes(file: String, section: String?): Set<String> {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/xml/$file")).documentElement
        val at = if (section == null) root else root.getElementsByTagName(section).item(0) as org.w3c.dom.Element
        assertEquals("an exclude only makes sense under a wider include: the list is what goes", 0, at.getElementsByTagName("exclude").length)
        val found = at.getElementsByTagName("include")
        return (0 until found.length).map { found.item(it) as org.w3c.dom.Element }.map { "${it.getAttribute("domain")}:${it.getAttribute("path")}" }.toSet()
    }

    @Test fun `the copy carries the library and the language and nothing else, on every Android`() {
        assertEquals(travels, includes("backup_rules.xml", null))
        assertEquals(travels, includes("data_extraction_rules.xml", "cloud-backup"))
        assertEquals(travels, includes("data_extraction_rules.xml", "device-transfer"))
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:fullBackupContent=\"@xml/backup_rules\""))
        assertTrue(manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
    }

    @Test fun `every preferences file of the app is decided - it goes with the copy or it stays`() {
        val sources = File("src/main/java").walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }.toList()
        val opened = Regex("""getSharedPreferences\(\s*([^,)\s]+)""")
        val named = Regex("""const val PREFS\s*=\s*"([^"]+)"""")
        val names = mutableSetOf<String>()
        sources.forEach { f ->
            val text = f.readText()
            opened.findAll(text).forEach { m ->
                val arg = m.groupValues[1]
                when {
                    arg.startsWith("\"") -> names += arg.trim('"')
                    // PREFS is read below; `name` is the backup going through its own list of these same files
                    arg == "PREFS" || (arg == "name" && f.name == "Backup.kt") -> Unit
                    else -> throw AssertionError("${f.name} opens preferences by `$arg`: use a literal or a PREFS constant so this test can see the name")
                }
            }
            named.findAll(text).forEach { names += it.groupValues[1] }
        }
        assertEquals("a preferences file appeared or left: decide whether Android's copy carries it (res/xml) and list it here", prefs.keys.toSortedSet(), names.toSortedSet())
        assertEquals(prefs.filterValues { it }.keys.map { "sharedpref:$it.xml" }.toSet(), travels.filter { it.startsWith("sharedpref:") }.toSet())
    }

    @Test fun `a song whose track did not come loses the link and keeps the track's name`() {
        val lib = Library(songs = listOf(
            Song(id = "a", title = "Here", track = "here.ogg", trackName = "Here"),
            Song(id = "b", title = "Gone", track = "gone.ogg", trackName = "Gone live"),
            Song(id = "c", title = "None"),
        ))
        val clean = lib.withoutMissingTracks { it == "here.ogg" }
        assertEquals(listOf("here.ogg", "", ""), clean.songs.map { it.track })
        assertEquals("Gone live", clean.songs[1].trackName)
        // nothing missing: the very same library, so opening the app doesn't write the file again
        assertSame(lib, lib.withoutMissingTracks { true })
    }

    @Test fun `the library that came without its tracks opens with the links cleared, and saved that way`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        File(context.filesDir, "tracks").apply { mkdirs() }.resolve("here.ogg").writeBytes(byteArrayOf(1))
        val first = LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        withTimeout(5_000) { first.loaded.first { it } }
        first.saveSong(Song(title = "Here", author = "x", text = "l", track = "here.ogg", trackName = "Here"))
        first.saveSong(Song(title = "Gone", author = "x", text = "l", track = "gone.ogg", trackName = "Gone live"))
        val file = File(context.filesDir, "stagebook.json")
        withTimeout(5_000) { while (!file.exists() || !file.readText().contains("gone.ogg")) delay(20) }

        val second = LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        withTimeout(5_000) { second.loaded.first { it } }
        val songs = second.library.value.songs.associateBy { it.title }
        assertEquals("here.ogg", songs.getValue("Here").track)
        assertEquals("", songs.getValue("Gone").track)
        assertEquals("Gone live", songs.getValue("Gone").trackName)
        // said once, in a window that names the song and the audio
        assertEquals(listOf(br.app.stagebook.data.model.LostAudio("Gone", "Gone live")), second.lostAudio.value)
        second.lostAudioSeen()
        assertEquals(emptyList<br.app.stagebook.data.model.LostAudio>(), second.lostAudio.value)
        // the swap moves the old file to the backup first: for an instant there is no file to read
        withTimeout(5_000) { while (runCatching { file.readText() }.getOrDefault("gone.ogg").contains("gone.ogg")) delay(20) }
    }
}
