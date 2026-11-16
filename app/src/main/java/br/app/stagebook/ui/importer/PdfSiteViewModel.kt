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

/**
 * What the importer's browser says about a download: a PDF waits for the person's yes ([asking]), is then fetched, checked and
 * becomes a song; anything else is told to be no PDF. A page can start a download with no tap, so nothing comes in unasked.
 */
@HiltViewModel
class PdfSiteViewModel @Inject constructor(private val importer: PdfImporter) : ViewModel() {
    sealed interface Outcome {
        data class Done(val result: PdfImporter.Result) : Outcome
        data object TooBig : Outcome
        data object Failed : Outcome
        data object NotPdf : Outcome
    }

    /** A PDF the browser offered, waiting for the person to say it comes in. */
    data class Offer(val name: String, val url: String, val userAgent: String?, val disposition: String?)

    private val _asking = MutableStateFlow<Offer?>(null)
    val asking: StateFlow<Offer?> = _asking
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy
    private val outcomes = Channel<Outcome>(Channel.BUFFERED)
    val outcome = outcomes.receiveAsFlow()

    /** The browser started a download. One at a time: with a question open or a file coming, another is left alone. */
    fun offered(url: String, userAgent: String?, disposition: String?, mime: String?) {
        if (_busy.value || _asking.value != null) return
        if (!PdfDownload.isPdf(url, mime, disposition)) { outcomes.trySend(Outcome.NotPdf); return }
        _asking.value = Offer(PdfDownload.nameOf(url, disposition), url, userAgent, disposition)
    }

    fun decline() { _asking.value = null }

    /** The person said yes: [cookiesFor] reads the browser's cookies of each address the download goes through. */
    fun accept(cookiesFor: (String) -> String?) {
        val offer = _asking.value ?: return
        _asking.value = null
        if (!_busy.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                outcomes.send(
                    when (val f = PdfDownload.fetch(offer.url, offer.userAgent, offer.disposition, cookiesFor)) {
                        is PdfDownload.Fetched.Bytes -> Outcome.Done(importer.importBytes(f.name, f.bytes))
                        PdfDownload.Fetched.TooBig -> Outcome.TooBig
                        PdfDownload.Fetched.Failed -> Outcome.Failed
                    },
                )
            } finally { _busy.value = false }
        }
    }
}
