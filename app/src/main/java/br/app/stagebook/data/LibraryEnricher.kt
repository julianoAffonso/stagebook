package br.app.stagebook.data

import br.app.stagebook.data.model.Song
import br.app.stagebook.di.ApplicationScope
import br.app.stagebook.domain.Chords
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fills in what the songs of the whole library lack (artist, year, BPM, length), one song after the other, at the pace each
 * service allows (user, 22/11): MusicBrainz takes one request a second (its own gate), Deezer about 50 every 5 seconds
 * (two requests a song, spaced here). Only empty fields are filled: what the musician wrote stays.
 */
@Singleton
class LibraryEnricher @Inject constructor(
    private val repository: LibraryRepository,
    private val musicBrainz: MusicBrainz,
    private val deezer: DeezerBpm,
    private val connectivity: Connectivity,
    @ApplicationScope private val scope: CoroutineScope,
) {
    /** [total] songs to look at, [done] already looked at, [filled] got something new; [running] false when idle or finished. */
    data class Progress(val running: Boolean = false, val done: Int = 0, val total: Int = 0, val filled: Int = 0, val offline: Boolean = false)

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress.asStateFlow()
    private var job: Job? = null

    // the tests swap the services for answers of their own
    internal var artistYear: suspend (String, String) -> MusicBrainz.Info? = { t, a -> musicBrainz.lookup(t, a) }
    internal var facts: suspend (String, String) -> DeezerBpm.Facts? = { t, a -> deezer.factsWithin(t, a, LOOKUP_MS) }
    internal var online: () -> Boolean = { connectivity.isOnline() }
    internal var deezerSpacingMs = DEEZER_SPACING_MS

    /** How many songs lack something that can be looked up. */
    fun missingCount(songs: List<Song>): Int = songs.count { lacks(it) }

    fun start() {
        if (job?.isActive == true) return
        if (!repository.library.value.look.internetOn) return   // essential mode (T19): no lookups
        if (!online()) { _progress.value = Progress(offline = true); return }
        val ids = repository.library.value.songs.filter { lacks(it) }.map { it.id }
        _progress.value = Progress(running = ids.isNotEmpty(), total = ids.size)
        if (ids.isEmpty()) return
        job = scope.launch {
            var filled = 0
            try {
                ids.forEachIndexed { i, id ->
                    if (enrich(id)) filled++
                    _progress.value = Progress(running = true, done = i + 1, total = ids.size, filled = filled)
                }
            } finally {
                _progress.value = _progress.value.copy(running = false, filled = filled)
            }
        }
    }

    fun stop() { job?.cancel() }

    private suspend fun enrich(id: String): Boolean {
        val song = repository.song(id) ?: return false
        val needWho = song.author.isBlank() || song.year.isBlank()
        val needFacts = Chords.tempo(song.text) == 0 || Chords.duration(song.text) == 0
        // the artist found first helps Deezer pick the right track
        val info = if (needWho) artistYear(song.title, song.author) else null
        val f = if (needFacts) {
            facts(song.title, song.author.ifBlank { info?.artist.orEmpty() }).also { delay(deezerSpacingMs) }
        } else null
        val before = repository.song(id) ?: return false
        val after = fill(before, info, f)
        if (after == before) return false
        repository.updateSong(id) { cur -> fill(cur, info, f) }
        return true
    }

    companion object {
        const val LOOKUP_MS = 10_000L
        const val DEEZER_SPACING_MS = 250L

        /** A song with a title that lacks artist, year, BPM or length. */
        fun lacks(s: Song): Boolean = s.title.isNotBlank() &&
            (s.author.isBlank() || s.year.isBlank() || Chords.tempo(s.text) == 0 || Chords.duration(s.text) == 0)

        /** [s] with only its empty fields filled from what was found. */
        fun fill(s: Song, info: MusicBrainz.Info?, f: DeezerBpm.Facts?): Song {
            var text = s.text
            if (f != null && f.bpm > 0 && Chords.tempo(text) == 0) text = Chords.setDirective(text, "tempo", f.bpm.toString())
            if (f != null && f.seconds > 0 && Chords.duration(text) == 0) text = Chords.setDirective(text, "duration", Chords.formatDuration(f.seconds))
            return s.copy(
                author = s.author.ifBlank { info?.artist.orEmpty() },
                year = s.year.ifBlank { info?.year.orEmpty() },
                text = text,
            )
        }
    }
}
