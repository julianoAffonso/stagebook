package br.app.stagebook.ui.importer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.app.stagebook.data.PdfDownload
import br.app.stagebook.data.PdfImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the importer's browser says about a PDF the person tapped: it is fetched, checked and becomes a song (the screen shows the outcome). */
@HiltViewModel
class PdfSiteViewModel @Inject constructor(private val importer: PdfImporter) : ViewModel() {
    sealed interface Outcome {
        data class Done(val result: PdfImporter.Result) : Outcome
        data object TooBig : Outcome
        data object Failed : Outcome
    }

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy
    private val outcomes = Channel<Outcome>(Channel.BUFFERED)
    val outcome = outcomes.receiveAsFlow()

    fun take(url: String, userAgent: String?, cookies: String?, disposition: String?, mime: String?) {
        if (!PdfDownload.isPdf(url, mime, disposition) || !_busy.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                outcomes.send(
                    when (val f = PdfDownload.fetch(url, userAgent, cookies, disposition, mime)) {
                        is PdfDownload.Fetched.Bytes -> Outcome.Done(importer.importBytes(f.name, f.bytes))
                        PdfDownload.Fetched.TooBig -> Outcome.TooBig
                        PdfDownload.Fetched.Failed -> Outcome.Failed
                    },
                )
            } finally { _busy.value = false }
        }
    }
}
