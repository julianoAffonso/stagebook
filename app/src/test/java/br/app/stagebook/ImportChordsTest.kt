package br.app.stagebook

import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.ImportDraft
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.MusicBrainz
import br.app.stagebook.domain.Chords
import br.app.stagebook.ui.importer.ImportViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Import from a chord site: the lyrics and chords profile gets ChordPro (with key and capo); lyrics only, no chords.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ImportChordsTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @After fun tearDown() = Dispatchers.resetMain()

    // what the script returns on a Cifra Club page (made-up chart; no title, so it doesn't go to MusicBrainz)
    private fun raw(): String {
        val page = buildJsonObject {
            put("title", ""); put("artist", "")
            put("text", "Saio cedo pela rua\nPra você ouvir\nE mais uma linha")
            put("chordText", "[Intro] G  D\n\nG              D\nSaio cedo pela rua\nEm       C\nPra você ouvir\nC\nE mais uma linha")
            put("pageText", "Tom: G\nCapotraste na 2ª casa"); put("site", "www.cifraclub.com.br")
        }.toString()
        return Json.encodeToString(kotlinx.serialization.json.JsonPrimitive(page))
    }

    private fun import(chords: Boolean): String = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val repo = LibraryRepository(context, CoroutineScope(Dispatchers.IO))
        withTimeout(5_000) { repo.loaded.first { it } }
        repo.updateLook { it.copy(chords = chords) }
        val draft = ImportDraft()
        val vm = ImportViewModel(repo, br.app.stagebook.data.PageImporter(repo, MusicBrainz(), br.app.stagebook.data.DeezerBpm(), br.app.stagebook.data.Connectivity(androidx.test.core.app.ApplicationProvider.getApplicationContext())), draft, SavedStateHandle(mapOf("query" to "")), br.app.stagebook.data.ImportQueue(repo))
        vm.onExtracted(raw())
        withTimeout(5_000) { vm.openEditor.first() }
        draft.take()!!.text
    }

    @Test fun `without internet the import still opens and says artist, year and BPM were not filled in (device test B-27)`() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val repo = LibraryRepository(context, CoroutineScope(Dispatchers.IO))
        withTimeout(5_000) { repo.loaded.first { it } }
        // the test device has no validated internet: the lookups can't run
        val vm = ImportViewModel(repo, br.app.stagebook.data.PageImporter(repo, MusicBrainz(), br.app.stagebook.data.DeezerBpm(), br.app.stagebook.data.Connectivity(context)), ImportDraft(), SavedStateHandle(mapOf("query" to "")), br.app.stagebook.data.ImportQueue(repo))
        vm.onExtracted(raw())
        withTimeout(5_000) { vm.offlineImport.first() }
        withTimeout(5_000) { vm.openEditor.first() }
        Unit
    }

    @Test fun `a search results page is refused - open the chords page first (B-05)`() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val repo = LibraryRepository(context, CoroutineScope(Dispatchers.IO))
        withTimeout(5_000) { repo.loaded.first { it } }
        val importer = br.app.stagebook.data.PageImporter(repo, MusicBrainz(), br.app.stagebook.data.DeezerBpm(), br.app.stagebook.data.Connectivity(context))
        val page = br.app.stagebook.domain.PageData(title = "cifra wave tom jobim", text = "Uma linha inventada para o teste aqui\nOutra linha de exemplo bem comum\nMais uma frase qualquer sem sentido\nE a última linha do exemplo", site = "www.google.com")
        assertEquals(br.app.stagebook.data.PageImport.Problem(br.app.stagebook.data.ImportProblem.SEARCH_PAGE), importer.fromPage(page, false))
        assertTrue(importer.fromPage(page.copy(site = "www.cifraclub.com.br"), false) is br.app.stagebook.data.PageImport.Song)
    }

    private fun importer(chords: Boolean = true) = runBlocking {
        val repo = LibraryRepository(context, CoroutineScope(Dispatchers.IO))
        withTimeout(5_000) { repo.loaded.first { it } }
        repo.updateLook { it.copy(chords = chords) }
        br.app.stagebook.data.PageImporter(repo, MusicBrainz(), br.app.stagebook.data.DeezerBpm(), br.app.stagebook.data.Connectivity(context))
    }

    private val lyrics = "Saio cedo pela rua\nPra você ouvir\nE mais uma linha\nE outra linha aqui\nE a última linha"
    private val code = "function a() {\n  return 1\n}\n".repeat(20)

    @Test fun `a pre without chords doesn't replace the page's lyrics`() = runBlocking {
        val r = importer().fromPage(br.app.stagebook.domain.PageData(text = lyrics, chordText = code, preLyrics = code, site = "www.exemplo.com"), false)
        val text = (r as br.app.stagebook.data.PageImport.Song).text
        assertTrue(text, text.contains("Saio cedo pela rua"))
        assertTrue(text, !text.contains("function"))
    }

    @Test fun `a pre without chords doesn't hide the chords in the text`() = runBlocking {
        val chart = "G              D\nSaio cedo pela rua\nEm       C\nPra você ouvir\nG    D\nE mais uma linha"
        val r = importer().fromPage(br.app.stagebook.domain.PageData(text = chart, chordText = code, preLyrics = code, site = "www.exemplo.com"), false)
        val text = (r as br.app.stagebook.data.PageImport.Song).text
        assertTrue(text, text.contains("[G]Saio cedo pela [D]rua"))
    }

    @Test fun `invisible marks in the page's text don't hide the capo`() = runBlocking {
        val r = importer().fromPage(br.app.stagebook.domain.PageData(text = lyrics, pageText = "Capo: \n\u200B2nd fret", site = "www.exemplo.com"), false)
        assertEquals(2, Chords.capo((r as br.app.stagebook.data.PageImport.Song).text))
    }

    @Test fun `tablature with invisible marks still asks before coming in`() = runBlocking {
        val chart = "\u200BG    D\n\u200BSaio cedo pela rua\n\u200BEm   C\n\u200BPra você ouvir\n\u200Be|-----3-----|\n\u200BB|---0---0---|"
        val r = importer().fromPage(br.app.stagebook.domain.PageData(chordText = chart, site = "www.exemplo.com"), null)
        assertEquals(br.app.stagebook.data.PageImport.AskTab, r)
    }

    @Test fun `a page far too big isn't read`() = runBlocking {
        val r = importer().fromPage(br.app.stagebook.domain.PageData(text = "linha\n".repeat(30_000), site = "www.exemplo.com"), false)
        assertEquals(br.app.stagebook.data.PageImport.Problem(br.app.stagebook.data.ImportProblem.NOT_SONG), r)
    }

    @Test fun `lyrics and chords come in as ChordPro with key and capo`() {
        val text = import(chords = true)
        assertEquals("G", Chords.key(text))
        assertEquals(2, Chords.capo(text))
        assertTrue(text, text.contains("[G]Saio cedo pela [D]rua"))
        assertTrue(text, text.contains("[Intro]\n[G] [D]"))
    }

    @Test fun `lyrics only come in without chords, but keep the page's key and capo`() {
        val text = import(chords = false)
        assertFalse(text, Chords.hasChords(text))
        assertTrue(text, text.contains("Saio cedo pela rua"))
        assertEquals("G", Chords.key(text))
        assertEquals(2, Chords.capo(text))
    }

    // a riff song: tablature and lyrics, not one chord. The page's own blocks made the script's fallback pick a piece from
    // the middle (user, 11/11): the <pre> counts, and there is nothing to ask
    private val tabOnly = "\u200B[Intro]\n\n\u200B[Tab - Intro]\n\nE|--------------|\nB|--------------|\nG|--------------|\nD|-0h1---1---0h1|\n\n" +
        "\u200B[Refrão]\n\n\u200BSaio cedo pela rua\n\u200BPra você ouvir\n\n\u200B[Primeira Parte]\n\n\u200BE mais uma linha\n\u200BE outra linha aqui\n\u200BE a última linha"
    private fun tabOnlyPage() = br.app.stagebook.domain.PageData(text = "D|-0h1---1---0h1|\n\n\u200B[Refrão]", chordText = tabOnly, preLyrics = tabOnly, site = "www.exemplo.com")

    @Test fun `tablature and lyrics without chords come in whole, tab included, without asking`() = runBlocking {
        val text = (importer(chords = true).fromPage(tabOnlyPage(), null) as br.app.stagebook.data.PageImport.Song).text
        assertTrue(text, text.contains("{start_of_tab}\nE|--------------|"))
        assertTrue(text, text.contains("D|-0h1---1---0h1|\n{end_of_tab}"))
        assertTrue(text, text.contains("Saio cedo pela rua\nPra você ouvir"))
        assertTrue(text, text.contains("E a última linha"))
    }

    @Test fun `pasted tablature and lyrics without chords come in with the tab, without asking`() = runBlocking {
        val text = (importer(chords = true).fromText(tabOnly) as br.app.stagebook.data.PageImport.Song).text
        assertTrue(text, text.contains("D|-0h1---1---0h1|\n{end_of_tab}"))
        assertTrue(text, text.contains("E a última linha"))
    }

    @Test fun `the tuning line names strings, not chords`() = runBlocking {
        val text = (importer(chords = true).fromPage(tabOnlyPage().let { it.copy(chordText = "\u200BAfinação: B F# B E G# C#\n\n" + it.chordText) }, null) as br.app.stagebook.data.PageImport.Song).text
        assertTrue(text, text.contains("Afinação: B F# B E G# C#"))
        assertFalse(Chords.isChordLine("Tuning: D A D G B E"))
        assertTrue(Chords.isChordLine("Intro: D A D G"))
    }

    @Test fun `lyrics only - tablature and lyrics without chords come in as the lyrics, without asking`() = runBlocking {
        val text = (importer(chords = false).fromPage(tabOnlyPage(), null) as br.app.stagebook.data.PageImport.Song).text
        assertFalse(text, text.contains("|--"))
        assertTrue(text, text.contains("Saio cedo pela rua\nPra você ouvir"))
        assertTrue(text, text.contains("E a última linha"))
    }

    // page with tablature: asks; with the tab it comes in as a block, without it it's gone
    private fun rawWithTab(): String {
        val page = buildJsonObject {
            put("title", ""); put("artist", "")
            put("text", "Saio cedo pela rua\nPra você ouvir\nE mais uma linha")
            put("chordText", "[Intro]\ne|---0---3---|\nB|---1---0---|\n\nG              D\nSaio cedo pela rua\nEm       C\nPra você ouvir\nC\nE mais uma linha")
            put("pageText", "Tom: G"); put("site", "www.cifraclub.com.br")
        }.toString()
        return Json.encodeToString(kotlinx.serialization.json.JsonPrimitive(page))
    }

    private fun importTab(include: Boolean): String = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val repo = LibraryRepository(context, CoroutineScope(Dispatchers.IO))
        withTimeout(5_000) { repo.loaded.first { it } }
        repo.updateLook { it.copy(chords = true) }
        val draft = ImportDraft()
        val vm = ImportViewModel(repo, br.app.stagebook.data.PageImporter(repo, MusicBrainz(), br.app.stagebook.data.DeezerBpm(), br.app.stagebook.data.Connectivity(androidx.test.core.app.ApplicationProvider.getApplicationContext())), draft, SavedStateHandle(mapOf("query" to "")), br.app.stagebook.data.ImportQueue(repo))
        vm.onExtracted(rawWithTab())
        withTimeout(5_000) { vm.uiState.first { it.askTab } }
        vm.answerTab(include)
        withTimeout(5_000) { vm.openEditor.first() }
        draft.take()!!.text
    }

    @Test fun `tablature - asks and comes in as a block`() {
        val text = importTab(include = true)
        assertTrue(text, text.contains("{start_of_tab}\ne|---0---3---|\nB|---1---0---|\n{end_of_tab}"))
        assertTrue(text, text.contains("[G]Saio cedo pela [D]rua"))
    }

    @Test fun `tablature - without it, no tab line comes in`() {
        val text = importTab(include = false)
        assertFalse(text, text.contains("|---"))
        assertTrue(text, text.contains("[G]Saio cedo pela [D]rua"))
    }
}
