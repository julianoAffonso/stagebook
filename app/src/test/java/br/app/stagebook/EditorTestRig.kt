package br.app.stagebook

import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.EditorQueue
import br.app.stagebook.data.ImportDraft
import br.app.stagebook.data.ImportQueue
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.MusicBrainz
import br.app.stagebook.data.SongMetadata
import br.app.stagebook.data.TrackStore
import br.app.stagebook.data.model.Song
import br.app.stagebook.ui.editor.SongEditorViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** An editor view model over a real (Robolectric) library with one saved song, opened with [cursor] from the route. */
internal fun openEditorVm(title: String = "Hazard", author: String = "Richard Marx", text: String, cursor: Int = -1, queue: EditorQueue = EditorQueue()): SongEditorViewModel {
    val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    val scope = CoroutineScope(Dispatchers.IO)
    val repo = LibraryRepository(context, scope)
    runBlocking { withTimeout(5_000) { repo.loaded.first { it } } }
    val id = repo.saveSong(Song(title = title, author = author, text = text))
    val vm = SongEditorViewModel(repo, SongMetadata(repo, MusicBrainz(), scope), ImportDraft(), SavedStateHandle(mapOf("songId" to id, "fromImport" to false, "cursor" to cursor)), ImportQueue(repo), TrackStore(context), queue)
    runBlocking { withTimeout(5_000) { while (vm.uiState.loading) kotlinx.coroutines.delay(10) } }
    return vm
}
