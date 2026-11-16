package br.app.stagebook.domain

import br.app.stagebook.data.model.Song

/**
 * Which copy of a song a request to the band asks for (user, 15/11): lyrics only, the chart with chords, or the PDF. The one
 * who asks says it, and the one who answers sends that copy or nothing. [ANY] is a request from before this existed (an older
 * app, or the review of a shared setlist, which takes what there is): the answer is the song as it is, chords first.
 */
object AskKind {
    const val ANY = ""
    const val LYRICS = "lyrics"
    const val CHORDS = "chords"
    const val PDF = "pdf"

    /** The kinds a person can choose, in the order the screens list them. */
    val choices = listOf(LYRICS, CHORDS, PDF)

    /** The kind a profile that shows chords, or only lyrics, asks for by default. */
    fun default(chordsProfile: Boolean): String = if (chordsProfile) CHORDS else LYRICS

    /** The kind [song] is: a PDF, else a chart with chords, else lyrics. */
    fun of(song: Song): String = when {
        song.pdf.isNotEmpty() -> PDF
        Chords.hasChords(song.text) -> CHORDS
        else -> LYRICS
    }

    /** Whether [song] has what [kind] asks for ([pdfHere]: its PDF file is on this device). */
    fun has(song: Song, kind: String, pdfHere: Boolean): Boolean = when (kind) {
        PDF -> pdfHere
        CHORDS -> Chords.hasChords(song.text)
        LYRICS -> Chords.stripChords(song.text).isNotBlank()
        else -> true
    }

    /** The text that goes for [kind]: lyrics only has the chords taken out, the rest goes as it is. */
    fun textFor(text: String, kind: String): String = if (kind == LYRICS) Chords.stripChords(text) else text
}
