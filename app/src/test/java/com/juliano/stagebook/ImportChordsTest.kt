package com.juliano.stagebook

import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.juliano.stagebook.data.ImportDraft
import com.juliano.stagebook.data.LibraryRepository
import com.juliano.stagebook.data.MusicBrainz
import com.juliano.stagebook.domain.Chords
import com.juliano.stagebook.ui.importer.ImportViewModel
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

/** Importar de um site de cifra: perfil letra e cifra entra em ChordPro (com tom e capo); só letra, sem acordes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ImportChordsTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @After fun tearDown() = Dispatchers.resetMain()

    // o que o script devolve numa página do Cifra Club (cifra inventada; sem título, para não ir ao MusicBrainz)
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
        val vm = ImportViewModel(repo, MusicBrainz(), draft, SavedStateHandle(mapOf("query" to "")))
        vm.onExtracted(raw())
        withTimeout(5_000) { vm.openEditor.first() }
        draft.take()!!.text
    }

    @Test fun `letra e cifra entra em ChordPro com tom e capo`() {
        val text = import(chords = true)
        assertEquals("G", Chords.key(text))
        assertEquals(2, Chords.capo(text))
        assertTrue(text, text.contains("[G]Saio cedo pela [D]rua"))
        assertTrue(text, text.contains("[Intro]\n[G] [D]"))
    }

    @Test fun `só letra entra sem acordes`() {
        val text = import(chords = false)
        assertFalse(text, Chords.hasChords(text))
        assertTrue(text, text.contains("Saio cedo pela rua"))
    }
}
