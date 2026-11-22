package br.app.stagebook.ui.stage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import br.app.stagebook.data.ExternalImageStore
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.PdfStore
import br.app.stagebook.data.model.ExternalShow
import br.app.stagebook.data.model.Look
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ExternalScreenViewModel @Inject constructor(
    repository: LibraryRepository,
    private val pdfs: PdfStore,
    private val images: ExternalImageStore,
) : ViewModel() {
    val look: StateFlow<Look> = repository.library.map { it.look }.stateIn(viewModelScope, SharingStarted.Eagerly, repository.library.value.look)
    fun pdfFile(name: String) = pdfs.file(name)
    val image get() = images.file
}

/**
 * The external screen for the whole app (user, 22/11): it stays on whatever screen is open. With the stage open it shows
 * the part of the song in [mirror]; with it closed, the idle picture chosen in Settings (the logo by default). Nothing
 * listens for screens with the option off.
 */
@Composable
fun ExternalScreenHost(mirror: StageMirror, viewModel: ExternalScreenViewModel = hiltViewModel()) {
    val look by viewModel.look.collectAsStateWithLifecycle()
    val display = rememberPresentationDisplay(look.extrasOn && look.externalShow != ExternalShow.OFF)
    SideEffect { mirror.active = display != null }
    // scrolling, the stage lets the screen go: Android mirrors the tablet, faithful to its zoom and its turn (user, 22/11)
    if (display != null && !mirror.mirrorTablet) ExternalPresentation(display) { ExternalStageContent(mirror.frame, look, viewModel::pdfFile, idleImage = viewModel.image) }
}
