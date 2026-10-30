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
            put("key", "G"); put("capo", "Capotraste na 2ª casa"); put("site", "www.cifraclub.com.br")
        }.toString()
        return Json.encodeToString(kotlinx.serialization.json.JsonPrimitive(page))
    }

    private fun import(chords: Boolean): String = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val repo = LibraryRepository(context, CoroutineScope(Dispatchers.IO))
        withTimeout(5_000) { repo.loaded.first { it } }
        repo.updateLook { it.copy(chords = chords) }
        val draft = ImportDraft()
        val vm = ImportViewModel(repo, br.app.stagebook.data.PageImporter(repo, MusicBrainz(), br.app.stagebook.data.DeezerBpm()), draft, SavedStateHandle(mapOf("query" to "")), br.app.stagebook.data.ImportQueue(repo))
        vm.onExtracted(raw())
        withTimeout(5_000) { vm.openEditor.first() }
        draft.take()!!.text
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

    // page with tablature: asks; with the tab it comes in as a block, without it it's gone
    private fun rawWithTab(): String {
        val page = buildJsonObject {
            put("title", ""); put("artist", "")
            put("text", "Saio cedo pela rua\nPra você ouvir\nE mais uma linha")
            put("chordText", "[Intro]\ne|---0---3---|\nB|---1---0---|\n\nG              D\nSaio cedo pela rua\nEm       C\nPra você ouvir\nC\nE mais uma linha")
            put("key", "G"); put("site", "www.cifraclub.com.br")
        }.toString()
        return Json.encodeToString(kotlinx.serialization.json.JsonPrimitive(page))
    }

    private fun importTab(include: Boolean): String = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val repo = LibraryRepository(context, CoroutineScope(Dispatchers.IO))
        withTimeout(5_000) { repo.loaded.first { it } }
        repo.updateLook { it.copy(chords = true) }
        val draft = ImportDraft()
        val vm = ImportViewModel(repo, br.app.stagebook.data.PageImporter(repo, MusicBrainz(), br.app.stagebook.data.DeezerBpm()), draft, SavedStateHandle(mapOf("query" to "")), br.app.stagebook.data.ImportQueue(repo))
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
