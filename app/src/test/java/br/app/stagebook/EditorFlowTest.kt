package br.app.stagebook

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onFirst
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.MusicBrainz
import br.app.stagebook.data.SongMetadata
import br.app.stagebook.data.model.Song
import br.app.stagebook.ui.editor.SongEditorScreen
import br.app.stagebook.ui.editor.SongEditorUiState
import br.app.stagebook.ui.editor.SongEditorViewModel
import br.app.stagebook.data.ImportDraft
import br.app.stagebook.ui.theme.StagebookTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Editor: doesn't lose what was typed (accidental Cancel, Android closing the app). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class EditorFlowTest {
    @get:Rule val compose = createComposeRule()

    private fun screen(state: SongEditorUiState, onCancel: () -> Unit) = compose.setContent {
        StagebookTheme { SongEditorScreen(state, {}, {}, {}, {}, {}, onCancel, {}) }
    }

    @Test
    fun `with something typed, back asks before leaving`() {
        var left = 0
        screen(SongEditorUiState(loading = false, title = "Hazard", text = "letra nova", unsaved = true)) { left++ }
        compose.onNodeWithContentDescription("Voltar").performClick()   // the header's back is cancel
        compose.onNodeWithText("Sair sem salvar?", substring = true).assertExists()
        assertEquals(0, left)
        compose.onNodeWithText("Sair sem salvar").performClick()
        assertEquals(1, left)
    }

    @Test
    fun `with nothing changed, back leaves right away`() {
        var left = 0
        screen(SongEditorUiState(loading = false, title = "Hazard", text = "letra")) { left++ }
        compose.onNodeWithContentDescription("Voltar").performClick()   // the header's back is cancel
        assertEquals(1, left)
    }

    @Test
    fun `Android closed the app mid-edit - the typed text comes back`() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val context = ApplicationProvider.getApplicationContext<android.app.Application>()
            val scope = CoroutineScope(Dispatchers.IO)
            val repo = LibraryRepository(context, scope)
            withTimeout(5_000) { repo.loaded.first { it } }
            val id = repo.saveSong(Song(title = "Hazard", author = "Richard Marx", text = "letra antiga"))
            val meta = SongMetadata(repo, MusicBrainz(), scope)
            val handle = SavedStateHandle(mapOf("songId" to id, "fromImport" to false))
            val vm = SongEditorViewModel(repo, meta, ImportDraft(), handle, br.app.stagebook.data.ImportQueue(repo), br.app.stagebook.data.TrackStore(context))
            withTimeout(5_000) { while (vm.uiState.loading) kotlinx.coroutines.delay(10) }
            vm.onText("letra digitada e ainda não salva")
            assertTrue(vm.uiState.unsaved)
            // process killed: only the SavedStateHandle survives (Android hands it back to the new screen)
            val again = SongEditorViewModel(repo, meta, ImportDraft(), SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }), br.app.stagebook.data.ImportQueue(repo), br.app.stagebook.data.TrackStore(context))
            withTimeout(5_000) { while (again.uiState.loading) kotlinx.coroutines.delay(10) }
            assertEquals("letra digitada e ainda não salva", again.uiState.text)
            assertTrue(again.uiState.unsaved)
            assertEquals("letra antiga", repo.song(id)!!.text)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextFirst(text: String) =
        onAllNodes(androidx.compose.ui.test.hasText(text)).onFirst().performScrollTo()

    @Test
    fun `opened from the stage - cursor at the end of the held line and keyboard in the lyrics field`() {
        val text = "[Refrão]\nprimeira linha\nsegunda linha"
        compose.setContent {
            StagebookTheme { SongEditorScreen(SongEditorUiState(loading = false, title = "Hazard", text = text), {}, {}, {}, {}, {}, {}, {}, cursor = 9) }
        }
        compose.waitForIdle()
        val field = compose.onNode(androidx.compose.ui.test.hasSetTextAction() and androidx.compose.ui.test.hasText("segunda linha", substring = true))
        field.assertIsFocused()
        val range = field.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.TextSelectionRange]
        assertEquals(androidx.compose.ui.text.TextRange(9 + "primeira linha".length), range)
    }

    @Test
    fun `end of the line`() {
        assertEquals(4, br.app.stagebook.ui.editor.lineEnd("abcd\nef", 0))
        assertEquals(7, br.app.stagebook.ui.editor.lineEnd("abcd\nef", 5))
        assertEquals(0, br.app.stagebook.ui.editor.lineEnd("abcd", -1))
    }

    @Test
    fun `section shortcut puts the title in the lyrics at the cursor`() {
        val texts = mutableListOf<String>()
        compose.setContent {
            StagebookTheme { SongEditorScreen(SongEditorUiState(loading = false, title = "Hazard", text = "linha um"), {}, {}, {}, { texts += it }, {}, {}, {}, cursor = 0) }
        }
        compose.onNodeWithText("Refrão").performClick()
        assertEquals("linha um\n\n[Refrão]\n", texts.last())
    }

    @Test
    fun `a new song can get a backing track, and keeps it when saved`() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val context = ApplicationProvider.getApplicationContext<android.app.Application>()
            val scope = CoroutineScope(Dispatchers.IO)
            val repo = LibraryRepository(context, scope)
            withTimeout(5_000) { repo.loaded.first { it } }
            val tracks = br.app.stagebook.data.TrackStore(context)
            val vm = SongEditorViewModel(repo, SongMetadata(repo, MusicBrainz(), scope), ImportDraft(), SavedStateHandle(mapOf("fromImport" to false)), br.app.stagebook.data.ImportQueue(repo), tracks)
            withTimeout(5_000) { while (vm.uiState.loading) kotlinx.coroutines.delay(10) }
            val audio = java.io.File(context.cacheDir, "minha-faixa.ogg").apply { writeBytes("OggS".toByteArray() + ByteArray(12)) }
            assertTrue(vm.attachTrack(android.net.Uri.fromFile(audio)))
            vm.onTitle("Música nova com faixa")
            vm.save()
            val saved = repo.library.value.songs.single { it.title == "Música nova com faixa" }
            assertEquals(vm.track, saved.track)
            assertTrue(tracks.file(saved.track).exists())
        } finally {
            Dispatchers.resetMain()
        }
    }
}
