package br.app.stagebook.data.web

import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.model.Song
import br.app.stagebook.domain.Chords
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The editor on the computer's library calls (T37c): songs. Saving does what the app's editor does (chords above the
 * lyrics become ChordPro, same title + artist refused, BPM in `{tempo}`), and the stage follows on its own because it
 * reads the same library.
 */
class WebEditorApi(
    private val repository: LibraryRepository,
    private val afterSave: (String) -> Unit = {},
    private val importer: () -> br.app.stagebook.data.PageImporter? = { null },
    private val readPage: suspend (String) -> String? = { null },
) {
    @Serializable
    data class SongItem(val id: String, val title: String, val author: String, val year: String, val key: String)

    @Serializable
    data class SongData(
        val id: String = "",
        val title: String = "",
        val author: String = "",
        val year: String = "",
        val tempo: String = "",
        val text: String = "",
    )

    @Serializable
    data class TextChange(val text: String = "", val semitones: Int = 0, val capo: Int = 0)

    @Serializable
    private data class Saved(val id: String)

    /** Import from the computer: pasted text (with a title from the file name) or a page address. */
    @Serializable
    data class ImportRequest(val text: String = "", val title: String = "", val url: String = "", val withTab: Boolean? = null)

    /** "song" (to review in the editor, not saved yet) or "askTab" (the tablature question first). */
    @Serializable
    data class ImportResult(val status: String, val title: String = "", val author: String = "", val year: String = "", val tempo: String = "", val text: String = "")

    @Serializable
    private data class Problem(val error: String)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun handle(req: HttpRequest, @Suppress("UNUSED_PARAMETER") device: WebEditorAuth.Device): HttpResponse? {
        val parts = req.path.removePrefix("/api/").split('/')
        return when {
            parts == listOf("songs") && req.method == "GET" -> ok(list())
            parts == listOf("songs") && req.method == "POST" -> save(null, req)
            parts.size == 2 && parts[0] == "songs" -> when (req.method) {
                "GET" -> repository.song(parts[1])?.let { ok(json.encodeToString(data(it))) } ?: HttpResponse.status(404)
                "PUT" -> if (repository.song(parts[1]) == null) HttpResponse.status(404) else save(parts[1], req)
                "DELETE" -> if (repository.song(parts[1]) == null) HttpResponse.status(404) else { repository.deleteSong(parts[1]); HttpResponse.status(204) }
                else -> HttpResponse.status(405)
            }
            // − and + of the key and the capo, like the app's: the text stays in the browser until saving
            parts == listOf("transpose") && req.method == "POST" -> change(req) { Chords.transposeSong(it.text, it.semitones) }
            parts == listOf("capo") && req.method == "POST" -> change(req) { Chords.withCapo(it.text, it.capo) }
            parts == listOf("import") && req.method == "POST" -> import(req)
            else -> null
        }
    }

    private fun list(): String = json.encodeToString(
        repository.library.value.songs
            .sortedBy { it.title.lowercase() }
            .map { SongItem(it.id, it.title, it.author, it.year, Chords.key(it.text)) },
    )

    // the BPM has its own field: out of the text, so it isn't in two places
    private fun data(s: Song) = SongData(s.id, s.title, s.author, s.year, Chords.directive(s.text, "tempo").orEmpty(), Chords.setDirective(s.text, "tempo", ""))

    private fun save(id: String?, req: HttpRequest): HttpResponse {
        val d = runCatching { json.decodeFromString<SongData>(req.text()) }.getOrNull() ?: return HttpResponse.status(400)
        val title = d.title.trim()
        if (title.isEmpty()) return problem("title_empty")
        val other = repository.findSong(title, d.author.trim())
        if (other != null && other.id != id) return problem("title_taken")
        // what the editor doesn't show stays (stage zoom, backing track)
        val base = id?.let(repository::song) ?: Song()
        val text = Chords.setDirective(Chords.fromChordsOverLyrics(d.text), "tempo", d.tempo.filter(Char::isDigit).take(3))
        val saved = repository.saveSong(base.copy(id = id.orEmpty(), title = title, author = d.author.trim(), year = d.year.trim(), text = text))
        afterSave(saved)
        return ok(json.encodeToString(Saved(saved)))
    }

    private fun change(req: HttpRequest, how: (TextChange) -> String): HttpResponse {
        val c = runCatching { json.decodeFromString<TextChange>(req.text()) }.getOrNull() ?: return HttpResponse.status(400)
        return ok(json.encodeToString(TextChange(how(c))))
    }

    // the app's import engine: the same page or text gives the same song as in the import browser
    private fun import(req: HttpRequest): HttpResponse {
        val engine = importer() ?: return HttpResponse.status(404)
        val r = runCatching { json.decodeFromString<ImportRequest>(req.text()) }.getOrNull() ?: return HttpResponse.status(400)
        val result = kotlinx.coroutines.runBlocking {
            if (r.url.isNotBlank()) {
                val url = r.url.trim()
                // the page is opened by the tablet over the internet: not in essential mode, only web addresses
                if (!repository.library.value.look.internetOn) return@runBlocking null to "offline"
                if (!url.startsWith("https://") && !url.startsWith("http://")) return@runBlocking null to "bad_address"
                engine.fromScript(readPage(url) ?: return@runBlocking null to "unreadable", r.withTab) to ""
            } else {
                // a ChordPro file brings its own title and artist
                val title = Chords.directive(r.text, "title") ?: r.title
                engine.fromText(r.text, title, r.withTab) to ""
            }
        }
        val (found, error) = result
        return when (found) {
            null -> problem(error)
            br.app.stagebook.data.PageImport.AskTab -> ok(json.encodeToString(ImportResult("askTab")))
            is br.app.stagebook.data.PageImport.Problem -> problem(if (found.problem == br.app.stagebook.data.ImportProblem.NOT_FOUND) "not_found" else "unreadable")
            is br.app.stagebook.data.PageImport.Song -> ok(
                json.encodeToString(
                    ImportResult(
                        "song", found.title,
                        found.artist.ifBlank { Chords.directive(r.text, "artist") ?: Chords.directive(r.text, "subtitle").orEmpty() },
                        found.year, Chords.directive(found.text, "tempo").orEmpty(),
                        Chords.setDirective(Chords.setDirective(Chords.setDirective(Chords.setDirective(found.text, "tempo", ""), "title", ""), "artist", ""), "subtitle", ""),
                    ),
                ),
            )
        }
    }

    private fun ok(body: String) = HttpResponse.json(200, body)

    private fun problem(error: String) = HttpResponse.json(409, json.encodeToString(Problem(error)))
}
