package br.app.stagebook.data

import android.content.Context
import androidx.core.content.edit
import br.app.stagebook.data.model.Song
import br.app.stagebook.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
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
 *
 * One press does the whole library (user, 22/11: it took three): the songs a service didn't answer for are tried again
 * in a second round. A song looked up and still lacking is remembered as looked up (until its title, artist or what it
 * lacks changes): it no longer counts as missing, and "Look again" asks for those. A song with a backing track gets its
 * length from the track itself, with no internet.
 */
@Singleton
class LibraryEnricher @Inject constructor(
    private val repository: LibraryRepository,
    private val musicBrainz: MusicBrainz,
    private val deezer: DeezerBpm,
    private val connectivity: Connectivity,
    @ApplicationScope private val scope: CoroutineScope,
    private val tracks: TrackStore,
    @ApplicationContext private val context: Context,
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
    internal var trackSeconds: suspend (String) -> Int = { tracks.seconds(it) }
    internal var roundPauseMs = ROUND_PAUSE_MS

    // songs looked up that still lack something, by id: the signature of what was looked up (title, artist, what it lacked)
    private val tried get() = context.getSharedPreferences("enrich", Context.MODE_PRIVATE)

    /** How many songs lack something that can be looked up, not counting the ones already looked up for it. */
    fun missingCount(songs: List<Song>): Int = songs.count { lacks(it) && !wasTried(it) }

    /** How many songs were looked up and still lack something the services don't have. */
    fun triedCount(songs: List<Song>): Int = songs.count { lacks(it) && wasTried(it) }

    private fun wasTried(s: Song) = tried.getString(s.id, null) == signature(s)

    /** Looks up what is missing; [again] also the songs looked up before with no luck. */
    fun start(again: Boolean = false) {
        if (job?.isActive == true) return
        if (!repository.library.value.look.internetOn) return   // essential mode (T19): no lookups
        if (!online()) { _progress.value = Progress(offline = true); return }
        val ids = repository.library.value.songs.filter { lacks(it) && (again || !wasTried(it)) }.map { it.id }
        _progress.value = Progress(running = ids.isNotEmpty(), total = ids.size)
        if (ids.isEmpty()) return
        job = scope.launch {
            var filled = 0
            var done = 0
            var total = ids.size
            try {
                // the first round, then the songs that still lack something once more: a busy service answers later
                var round = ids
                repeat(ROUNDS) { r ->
                    if (r > 0) {
                        round = round.filter { id -> repository.song(id)?.let(::lacks) == true }
                        if (round.isEmpty()) return@repeat
                        total += round.size
                        delay(roundPauseMs)
                    }
                    round.forEach { id ->
                        if (enrich(id)) filled++
                        done++
                        _progress.value = Progress(running = true, done = done, total = total, filled = filled)
                    }
                }
                // what still lacks something after both rounds was looked up: it leaves the count until it changes
                tried.edit {
                    ids.forEach { id -> repository.song(id)?.let { s -> if (lacks(s)) putString(id, signature(s)) else remove(id) } }
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
        // a backing track knows its own length better than any service (user, 22/11)
        val fromTrack = if (Chords.duration(song.text) == 0 && song.track.isNotEmpty()) trackSeconds(song.track) else 0
        val needFacts = Chords.tempo(song.text) == 0 || (Chords.duration(song.text) == 0 && fromTrack <= 0)
        // the artist found first helps Deezer pick the right track
        val info = if (needWho) artistYear(song.title, song.author) else null
        val looked = if (needFacts) {
            facts(song.title, song.author.ifBlank { info?.artist.orEmpty() }).also { delay(deezerSpacingMs) }
        } else null
        val f = if (fromTrack > 0) DeezerBpm.Facts(bpm = looked?.bpm ?: 0, seconds = fromTrack) else looked
        val before = repository.song(id) ?: return false
        val after = fill(before, info, f)
        if (after == before) return false
        repository.updateSong(id) { cur -> fill(cur, info, f) }
        return true
    }

    companion object {
        const val LOOKUP_MS = 10_000L
        const val DEEZER_SPACING_MS = 250L
        const val ROUNDS = 2
        const val ROUND_PAUSE_MS = 5_000L

        /** What was looked up for [s]: its title, artist and which fields it lacked. Another one means looking again. */
        fun signature(s: Song): String = listOf(
            s.title.trim(), s.author.trim(), s.author.isBlank(), s.year.isBlank(), Chords.tempo(s.text) == 0, Chords.duration(s.text) == 0,
        ).joinToString("|")

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
