package br.app.stagebook.data.web

import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.model.Song
import br.app.stagebook.domain.Chords
import br.app.stagebook.domain.SetlistBreaks
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
    // opens a page out of sight on the tablet and runs a script on it (null = the import extraction)
    private val readPage: suspend (url: String, script: String?) -> String? = { _, _ -> null },
    private val receiver: () -> WebReceive? = { null },
    private val band: () -> Band? = { null },
    private val backup: () -> br.app.stagebook.data.Backup? = { null },
    private val tracks: () -> br.app.stagebook.data.TrackStore? = { null },
    private val workDir: () -> java.io.File = { java.io.File(System.getProperty("java.io.tmpdir")) },
) {
    /** The band, for Import → Sync with the band / Received from the band. */
    class Band(val items: () -> List<br.app.stagebook.data.BandInbox.Item>, val sync: () -> Unit)

    @Serializable
    data class SongItem(val id: String, val title: String, val author: String, val year: String, val key: String, val tags: List<String> = emptyList())

    @Serializable
    data class SongData(
        val id: String = "",
        val title: String = "",
        val author: String = "",
        val year: String = "",
        val tempo: String = "",
        val text: String = "",
        val track: String = "",   // the backing track file ("" = none); read only, changed by its own calls
        val trackName: String = "",   // the track's own name, as the app shows it
        // key and capo live in the song's details, not in the lyrics, as in the app (user, 01/11): the page shows and
        // sends them apart, and the app puts them back in the text with its own rule
        val key: String = "",
        val capo: Int = 0,
        val tags: List<String> = emptyList(),
        // the song's MIDI on entering and leaving the stage, as the app's editor sets it (user, 07/11)
        val midi: br.app.stagebook.domain.SongMidi? = null,
    )

    @Serializable
    data class TextChange(val text: String = "", val semitones: Int = 0, val capo: Int = 0, val key: String = "", val meta: Meta? = null)

    /** The song's key and capo (the page keeps them apart from the lyrics). */
    @Serializable
    data class Meta(val key: String = "", val capo: Int = 0)

    @Serializable
    private data class Saved(val id: String)

    @Serializable
    data class ReceiveRequest(val code: String = "")

    @Serializable
    data class ReceiveCreate(val token: String = "", val name: String = "", val choices: Map<Int, String> = emptyMap())

    @Serializable
    private data class Created(val setlist: String)

    @Serializable
    data class SetItem(val id: String, val name: String, val count: Int)

    /** The Setlists tab's order, the same choice on the tablet, in the browser and for the assistants. */
    @Serializable
    data class SetsOrder(val abc: Boolean)

    /**
     * A setlist with its songs in order; a song deleted from the library comes with an empty title. [breaks] are the
     * positions of its breaks: 1..n-1, a break before the song at that place (0-based), as in the app.
     */
    @Serializable
    data class SetData(val id: String = "", val name: String = "", val songs: List<SongItem> = emptyList(), val breaks: List<Int> = emptyList())

    /** [breaks] null (a client that doesn't know them) keeps the breaks the setlist has, as the app does when songs are replaced. */
    @Serializable
    data class SetSave(val name: String = "", val items: List<String> = emptyList(), val breaks: List<Int>? = null)

    @Serializable
    data class BackupInfo(val token: String, val parts: List<String>, val songs: Int, val newer: Boolean)

    @Serializable
    data class BackupRestore(val token: String = "", val parts: List<String> = emptyList(), val replace: Boolean = false)

    @Serializable
    // the stored file and the name the page shows right away (without it the page showed a piece of the file name)
    private data class TrackSet(val track: String, val trackName: String = "")

    @Serializable
    private data class RestoreDone(val skipped: Int, val bandAgain: Boolean = false, val lost: List<br.app.stagebook.data.model.LostAudio> = emptyList())

    @Serializable
    data class Received(val id: String, val from: String, val name: String, val count: Int, val updated: Boolean)

    /** Import from the computer: pasted text (with a title from the file name) or a page address. */
    @Serializable
    data class ImportRequest(val text: String = "", val title: String = "", val url: String = "", val withTab: Boolean? = null)

    /** "song" (to review in the editor, not saved yet) or "askTab" (the tablature question first). */
    @Serializable
    data class ImportResult(
        val status: String, val title: String = "", val author: String = "", val year: String = "", val tempo: String = "", val text: String = "",
        val key: String = "", val capo: Int = 0,
    )

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
            parts == listOf("receive") && req.method == "POST" -> receive(req)
            parts == listOf("receive", "create") && req.method == "POST" -> receiveCreate(req)
            parts == listOf("band") && req.method == "GET" -> bandItems()
            parts == listOf("band", "sync") && req.method == "POST" -> bandSync()
            parts == listOf("band", "open") && req.method == "POST" -> bandOpen(req)
            parts == listOf("sets") && req.method == "GET" -> ok(json.encodeToString(setRows()))
            parts == listOf("sets-order") && req.method == "GET" -> ok(json.encodeToString(SetsOrder(repository.library.value.look.setsAbc)))
            parts == listOf("sets-order") && req.method == "PUT" -> setsOrder(req)
            parts == listOf("sets") && req.method == "POST" -> setCreate(req)
            parts.size == 2 && parts[0] == "sets" -> when (req.method) {
                "GET" -> repository.library.value.setlist(parts[1])?.let { ok(json.encodeToString(setData(it))) } ?: HttpResponse.status(404)
                "PUT" -> setSave(parts[1], req)
                "DELETE" -> if (repository.library.value.setlist(parts[1]) == null) HttpResponse.status(404) else { repository.deleteSetlist(parts[1]); HttpResponse.status(204) }
                else -> HttpResponse.status(405)
            }
            parts == listOf("backup", "inspect") && req.method == "POST" -> backupInspect(req)
            parts == listOf("backup", "restore") && req.method == "POST" -> backupRestore(req)
            parts.size == 3 && parts[0] == "songs" && parts[2] == "track" -> when (req.method) {
                "PUT" -> trackPut(parts[1], req)
                "DELETE" -> trackDelete(parts[1])
                else -> HttpResponse.status(405)
            }
            else -> null
        }
    }

    private fun list(): String = json.encodeToString(
        repository.library.value.songs
            // the app's order (accents and case ignored): "À Francesa" with the A's, not after Z (device test B-22)
            .sortedBy { br.app.stagebook.domain.TextKey.of(it.title) }
            .map { SongItem(it.id, it.title, it.author, it.year, Chords.key(it.text), it.tags) },
    )

    // the BPM has its own field: out of the text, so it isn't in two places
    private fun data(s: Song): SongData {
        val meta = Chords.meta(s.text)
        // the app's own lines (hidden chords, picked shapes) stay out of the page, as in the app's editor
        return SongData(s.id, s.title, s.author, s.year, meta.tempoText, br.app.stagebook.domain.InternalMarks.split(Chords.hideMeta(s.text)).first, s.track, s.trackName, meta.key, meta.capo, s.tags, s.midi ?: br.app.stagebook.domain.SongMidi())
    }

    // the page's lyrics with the key and capo it keeps apart, in the app's way (the BPM goes in on its own)
    private fun withMeta(text: String, key: String, capo: Int) =
        Chords.withMeta(text, br.app.stagebook.domain.SongMeta(key = key.trim(), capo = capo.coerceIn(0, 12)))

    private fun save(id: String?, req: HttpRequest): HttpResponse {
        val d = runCatching { json.decodeFromString<SongData>(req.text()) }.getOrNull() ?: return HttpResponse.status(400)
        val title = d.title.trim()
        if (title.isEmpty()) return problem("title_empty")
        // the same filter as the band: only song text (size, no file disguised as text)
        if (!br.app.stagebook.domain.BandContent.songOk(title, d.author.trim(), d.year.trim(), d.text)) return problem("not_song")
        val other = repository.findSong(title, d.author.trim())
        if (other != null && other.id != id) return problem("title_taken")
        // what the editor doesn't show stays (stage zoom, backing track)
        val base = id?.let(repository::song) ?: Song()
        // the key the chords imply is saved, as in the app's editor (31/10: one app, the same rules on the computer)
        val text = Chords.withDetectedKey(Chords.setDirective(Chords.fromChordsOverLyrics(withMeta(br.app.stagebook.domain.InternalMarks.restore(d.text, base.text), d.key, d.capo)), "tempo", d.tempo.filter(Char::isDigit).take(3)))
        val tags = br.app.stagebook.domain.Tags.apply(emptyList(), d.tags.filter { it.isNotBlank() }, emptyList())
        val saved = repository.saveSong(base.copy(id = id.orEmpty(), title = title, author = d.author.trim(), year = d.year.trim(), text = text, tags = tags,
            // a page from before MIDI sends none: the song keeps its own; the values are held to MIDI's ranges here too
            midi = d.midi?.let(br.app.stagebook.domain.MidiBytes::held)?.takeUnless { it.isEmpty() } ?: base.midi.takeIf { d.midi == null }))
        afterSave(saved)
        return ok(json.encodeToString(Saved(saved)))
    }

    // the change runs on the whole song (lyrics + key + capo) and comes back split again, as the page shows it
    private fun change(req: HttpRequest, how: (TextChange) -> String): HttpResponse {
        val c = runCatching { json.decodeFromString<TextChange>(req.text()) }.getOrNull() ?: return HttpResponse.status(400)
        val whole = c.meta?.let { c.copy(text = withMeta(c.text, it.key, it.capo)) } ?: c
        val out = how(whole)
        val meta = Chords.meta(out)
        return ok(json.encodeToString(TextChange(if (c.meta != null) Chords.hideMeta(out) else out, meta = Meta(meta.key, meta.capo))))
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
                engine.fromScript(readPage(url, null) ?: return@runBlocking null to "unreadable", r.withTab) to ""
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
            is br.app.stagebook.data.PageImport.Problem -> problem(
                when (found.problem) {
                    br.app.stagebook.data.ImportProblem.NOT_FOUND -> "not_found"
                    br.app.stagebook.data.ImportProblem.NOT_SONG -> "not_song"
                    br.app.stagebook.data.ImportProblem.UNREADABLE -> "unreadable"
                    br.app.stagebook.data.ImportProblem.SEARCH_PAGE -> "search_page"
                },
            )
            is br.app.stagebook.data.PageImport.Song -> ok(
                json.encodeToString(
                    ImportResult(
                        "song", found.title,
                        found.artist.ifBlank { Chords.directive(r.text, "artist") ?: Chords.directive(r.text, "subtitle").orEmpty() },
                        found.year, Chords.directive(found.text, "tempo").orEmpty(),
                        Chords.hideMeta(Chords.setDirective(Chords.setDirective(Chords.setDirective(found.text, "title", ""), "artist", ""), "subtitle", "")),
                        Chords.key(found.text), Chords.capo(found.text),
                    ),
                ),
            )
        }
    }

    // Import → Type the 3 words, from the computer: the review comes back here, creating is a second call
    private fun receive(req: HttpRequest): HttpResponse {
        val r = receiver() ?: return HttpResponse.status(404)
        val code = runCatching { json.decodeFromString<ReceiveRequest>(req.text()).code }.getOrNull().orEmpty()
        return when (val f = kotlinx.coroutines.runBlocking { r.fetch(code) }) {
            is WebReceive.Fetched.Ok -> ok(json.encodeToString(f.review))
            is WebReceive.Fetched.Problem -> problem(f.error)
        }
    }

    private fun receiveCreate(req: HttpRequest): HttpResponse {
        val r = receiver() ?: return HttpResponse.status(404)
        val c = runCatching { json.decodeFromString<ReceiveCreate>(req.text()) }.getOrNull() ?: return HttpResponse.status(400)
        val id = r.create(c.token, c.name, c.choices) ?: return problem("expired")
        return ok(json.encodeToString(Created(id)))
    }

    // the band: what arrived (also shown in the app's Import menu) and "Sync now"; works over the network or the internet
    private fun bandOff() = repository.library.value.look.let { !it.bandOn && !it.internetOn }

    private fun bandItems(): HttpResponse {
        val b = band() ?: return HttpResponse.status(404)
        if (bandOff()) return problem("offline")
        return ok(json.encodeToString(b.items().filterNot { it.newer }.map { Received(it.id, it.fromName, it.setName, it.songCount, it.updated) }))
    }

    private fun bandSync(): HttpResponse {
        val b = band() ?: return HttpResponse.status(404)
        if (bandOff()) return problem("offline")
        b.sync()
        return HttpResponse.status(204)
    }

    private fun bandOpen(req: HttpRequest): HttpResponse {
        val b = band() ?: return HttpResponse.status(404)
        val r = receiver() ?: return HttpResponse.status(404)
        val id = runCatching { json.decodeFromString<ReceiveRequest>(req.text()).code }.getOrNull().orEmpty()
        val item = b.items().firstOrNull { it.id == id } ?: return problem("not_found")
        val review = r.fromInbox(item.id, item.text, item.songs, item.breaks) ?: return problem("not_found")
        return ok(json.encodeToString(review))
    }

    // Import → Open a .stagebook file, from the computer: the file waits on the tablet while the page picks the parts
    // atomic, not under the lock: clear() runs on the main thread when the app leaves, and must not wait behind a
    // restore or a track upload holding the lock (that was an ANR; audit 01/11)
    private val waitingBackup = java.util.concurrent.atomic.AtomicReference<Pair<String, java.io.File>?>(null)

    @Synchronized
    private fun backupInspect(req: HttpRequest): HttpResponse {
        val b = backup() ?: return HttpResponse.status(404)
        val upload = req.file ?: return HttpResponse.status(400)
        val contents = kotlinx.coroutines.runBlocking { upload.inputStream().use { b.inspect(it) } } ?: return problem("unreadable")
        val token = java.util.UUID.randomUUID().toString()
        val kept = java.io.File(workDir(), "backup-$token.stagebook")
        if (!upload.renameTo(kept)) return problem("unreadable")
        waitingBackup.getAndSet(token to kept)?.second?.delete()
        return ok(json.encodeToString(BackupInfo(token, contents.parts.map { it.name.lowercase() }, contents.songs, contents.newer)))
    }

    @Synchronized
    private fun backupRestore(req: HttpRequest): HttpResponse {
        val b = backup() ?: return HttpResponse.status(404)
        val r = runCatching { json.decodeFromString<BackupRestore>(req.text()) }.getOrNull() ?: return HttpResponse.status(400)
        val waiting = waitingBackup.get() ?: return problem("expired")
        val (token, file) = waiting
        if (token != r.token) return problem("expired")
        val parts = r.parts.mapNotNull { p -> br.app.stagebook.data.Backup.Part.entries.firstOrNull { it.name.equals(p, ignoreCase = true) } }.toSet()
        val done = kotlinx.coroutines.runBlocking { file.inputStream().use { b.restore(it, parts, r.replace) } }
        file.delete()
        waitingBackup.compareAndSet(waiting, null)
        return if (done) ok(json.encodeToString(RestoreDone(b.skipped, b.bandAgain, b.lost))) else problem("unreadable")
    }

    /** The editor turned off: a backup waiting for its parts goes away (it could be 1 GB). */
    fun clear() {
        waitingBackup.getAndSet(null)?.second?.delete()
    }

    // the backing track from the computer (T37e): the file comes in the body, its name (for the format) in ?name=.
    // One at a time: two sends to the same song both read the old track and the first new file was never removed
    @Synchronized
    private fun trackPut(id: String, req: HttpRequest): HttpResponse {
        val store = tracks() ?: return HttpResponse.status(404)
        val song = repository.song(id) ?: return HttpResponse.status(404)
        val upload = req.file ?: return HttpResponse.status(400)
        val ext = req.query["name"].orEmpty().substringAfterLast('.', "").lowercase().filter(Char::isLetterOrDigit)
        val named = java.io.File(workDir(), "track-${java.util.UUID.randomUUID()}.$ext")
        if (!upload.renameTo(named)) return problem("unreadable")
        try {
            val name = kotlinx.coroutines.runBlocking { store.copy(id, android.net.Uri.fromFile(named)) } ?: return problem("format")
            val shown = br.app.stagebook.data.TrackStore.shownName(req.query["name"].orEmpty())
            repository.updateSong(id) { it.copy(track = name, trackName = shown) }
            if (song.track != name) store.remove(song.track)
            return ok(json.encodeToString(TrackSet(name, shown)))
        } finally {
            named.delete()
        }
    }

    private fun trackDelete(id: String): HttpResponse {
        val store = tracks() ?: return HttpResponse.status(404)
        val song = repository.song(id) ?: return HttpResponse.status(404)
        repository.updateSong(id) { it.copy(track = "", trackName = "") }
        store.remove(song.track)
        return HttpResponse.status(204)
    }

    // setlists (T37d): the same as the Setlists tab; an empty name becomes the date and time, as in the app
    private fun setData(set: br.app.stagebook.data.model.Setlist) = SetData(
        set.id, set.name,
        set.items.map { id -> repository.song(id)?.let { SongItem(it.id, it.title, it.author, it.year, Chords.key(it.text)) } ?: SongItem(id, "", "", "", "") },
        SetlistBreaks.normalise(set.breaks, set.items.size),
    )

    /** The setlists in the order the tab shows them, newest first or A to Z. */
    private fun setRows(): List<SetItem> {
        val lib = repository.library.value
        val rows = lib.sets.map { SetItem(it.id, it.name, it.items.size) }
        return if (lib.look.setsAbc) br.app.stagebook.domain.TextKey.sorted(rows) { it.name } else rows
    }

    private fun setsOrder(req: HttpRequest): HttpResponse {
        val d = runCatching { json.decodeFromString<SetsOrder>(req.text()) }.getOrNull() ?: return HttpResponse.status(400)
        repository.updateLook { it.copy(setsAbc = d.abc) }
        return ok(json.encodeToString(d))
    }

    private fun setCreate(req: HttpRequest): HttpResponse {
        val d = runCatching { json.decodeFromString<SetSave>(req.text()) }.getOrNull() ?: return HttpResponse.status(400)
        val name = d.name.trim().ifEmpty { java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.ROOT).format(java.util.Date()) }
        if (!breaksOk(d)) return problem("bad_breaks")
        val id = repository.createSetlist(name)
        if (d.items.isNotEmpty()) saveItems(id, d)
        return ok(json.encodeToString(Saved(id)))
    }

    private fun setSave(id: String, req: HttpRequest): HttpResponse {
        if (repository.library.value.setlist(id) == null) return HttpResponse.status(404)
        val d = runCatching { json.decodeFromString<SetSave>(req.text()) }.getOrNull() ?: return HttpResponse.status(400)
        if (!breaksOk(d)) return problem("bad_breaks")
        if (d.name.isNotBlank()) repository.renameSetlist(id, d.name.trim())
        saveItems(id, d)
        return ok(json.encodeToString(Saved(id)))
    }

    /** Breaks as sent are valid when they are what the app would keep: 1..n-1 of the sent songs, sorted, no repeats. */
    private fun breaksOk(d: SetSave) = d.breaks == null || d.breaks == SetlistBreaks.normalise(d.breaks, d.items.size)

    /**
     * Songs the library doesn't have, and repeats, drop out; sent breaks stay before the same song or the next one that
     * is left (the app's own rule, SetlistBreaks.place). Without breaks the setlist keeps its own.
     */
    private fun saveItems(id: String, d: SetSave) {
        if (d.breaks == null) { repository.setSetlistItems(id, d.items.filter { repository.song(it) != null }); return }
        val placed = SetlistBreaks.place(d.items.map { it.takeIf { sid -> repository.song(sid) != null } }, d.breaks)
        repository.setSetlistItems(id, placed.items, placed.breaks)
    }

    private fun ok(body: String) = HttpResponse.json(200, body)

    private fun problem(error: String) = HttpResponse.json(409, json.encodeToString(Problem(error)))
}
