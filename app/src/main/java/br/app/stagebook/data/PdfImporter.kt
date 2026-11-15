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
                    val title = freeTitle(titleOf(shown))
                    repository.saveSong(Song(title = title, pdf = saved.name, pdfName = shown))
                    added += title
                }
                is PdfAttach.Refused -> refused += Refusal(shown.ifEmpty { "PDF" }, saved.problem)
            }
        }
        return Result(added, refused)
    }

    // a title no other song without an artist already has ("Wonderwall", then "Wonderwall (2)")
    private fun freeTitle(title: String): String {
        val taken = repository.library.value.songs.filter { it.author.isBlank() }.map { it.title.lowercase() }.toSet()
        if (title.lowercase() !in taken) return title
        var n = 2
        while ("$title ($n)".lowercase() in taken) n++
        return "$title ($n)"
    }

    companion object {
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
