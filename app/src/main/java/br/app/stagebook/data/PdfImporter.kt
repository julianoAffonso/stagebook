package br.app.stagebook.data

import android.net.Uri
import br.app.stagebook.R
import br.app.stagebook.data.model.Song
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PDFs that come in as songs: chosen in the library drawer's + menu ("Import PDFs") or opened with the app from a file
 * manager or a chat. Each is copied and checked ([PdfStore.attach], the same check as the editor's), and a song is made
 * named after the file, with no artist: the person fills the rest in the editor. One that doesn't pass is left out, with
 * the reason, and doesn't stop the others.
 */
@Singleton
class PdfImporter @Inject constructor(
    private val repository: LibraryRepository,
    private val store: PdfStore,
    private val inspector: PdfInspector,
) {
    data class Refusal(val name: String, val problem: PdfProblem)
    data class Result(val added: List<String>, val refused: List<Refusal>)

    suspend fun import(uris: List<Uri>): Result {
        val added = ArrayList<String>()
        val refused = ArrayList<Refusal>()
        for (uri in uris) {
            val shown = store.displayName(uri)
            when (val saved = store.attach(UUID.randomUUID().toString(), uri, inspector)) {
                is PdfAttach.Saved -> {
                    val (artist, named) = artistAndTitleOf(shown)
                    val title = freeTitle(named, artist)
                    repository.saveSong(Song(title = title, author = artist, pdf = saved.name, pdfName = shown))
                    added += title
                }
                is PdfAttach.Refused -> refused += Refusal(shown.ifEmpty { "PDF" }, saved.problem)
            }
        }
        return Result(added, refused)
    }

    // a title the artist doesn't already have ("Wonderwall", then "Wonderwall (2)")
    private fun freeTitle(title: String, artist: String): String {
        if (repository.findSong(title, artist) == null) return title
        var n = 2
        while (repository.findSong("$title ($n)", artist) != null) n++
        return "$title ($n)"
    }

    companion object {
        /**
         * The artist and the title in a file named "Artist - Title.pdf" (one " - ", both sides with something); otherwise no
         * artist and the whole name as the title ([titleOf]). The person fixes a wrong guess in the editor.
         */
        fun artistAndTitleOf(fileName: String): Pair<String, String> {
            val whole = titleOf(fileName)
            val parts = whole.split(" - ")
            return if (parts.size == 2 && parts.all { it.isNotBlank() }) parts[0].trim() to parts[1].trim() else "" to whole
        }

        /** The song's title from the file's name: no ".pdf", underscores as spaces, trimmed; "PDF" when nothing is left. */
        fun titleOf(fileName: String): String =
            fileName.substringAfterLast('/').removeSuffix(".pdf").removeSuffix(".PDF").replace('_', ' ').trim().ifEmpty { "PDF" }
    }
}

/** What to tell after an import: how many songs came, and for each PDF left out, its name and the reason. */
fun PdfImporter.Result.message(context: android.content.Context): String = buildList {
    if (added.isNotEmpty()) add(context.resources.getQuantityString(R.plurals.pdf_imported, added.size, added.size))
    refused.forEach { add(context.getString(R.string.pdf_import_refused, it.name, it.problem.text(context))) }
}.joinToString("\n")
