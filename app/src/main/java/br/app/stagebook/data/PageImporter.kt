package br.app.stagebook.data

import br.app.stagebook.domain.ChordImport
import br.app.stagebook.domain.Chords
import br.app.stagebook.domain.LyricsCleaner
import br.app.stagebook.domain.PageData
import br.app.stagebook.domain.PageMeta
import br.app.stagebook.domain.Tablature
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

enum class ImportProblem { NOT_FOUND, UNREADABLE, NOT_SONG }

/** What came out of a page (or pasted text): the song ready to review, a problem, or the tablature question first. */
sealed interface PageImport {
    data object AskTab : PageImport
    data class Problem(val problem: ImportProblem) : PageImport
    data class Song(val title: String, val artist: String, val year: String, val text: String) : PageImport
}

/**
 * The import engine: a page's lyrics and chords become a song (ChordPro with key, capo and BPM in the chords profile,
 * clean lyrics otherwise), with artist, year and BPM looked up when missing. One engine for the app's import browser
 * and the editor on the computer (T37f): the same page gives the same song on both.
 */
@Singleton
class PageImporter @Inject constructor(
    private val repository: LibraryRepository,
    private val musicBrainz: MusicBrainz,
    private val deezer: DeezerBpm,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** `raw` is what evaluateJavascript returns for [ExtractScript]: the script's JSON string, in quotes. */
    suspend fun fromScript(raw: String?, withTab: Boolean? = null): PageImport {
        val page = runCatching { json.decodeFromString<PageData>(json.decodeFromString<String>(raw.orEmpty())) }.getOrNull()
            ?: return PageImport.Problem(ImportProblem.UNREADABLE)
        return fromPage(page, withTab)
    }

    /** Text pasted or dropped on the computer (chords above the lyrics, ChordPro or plain lyrics). */
    suspend fun fromText(text: String, title: String = "", withTab: Boolean? = null): PageImport =
        fromPage(PageData(title = title, text = text, chordText = if (ChordImport.hasChords(text)) text else ""), withTab)

    /** [withTab] null = asks first when the page has tablature (answer with true or false). */
    suspend fun fromPage(parsed: PageData, withTab: Boolean? = null): PageImport {
        if (withTab == null && (Tablature.hasTab(parsed.chordText) || Tablature.hasTab(parsed.text))) return PageImport.AskTab
        val tab: (String) -> String = { t -> if (withTab == true) Tablature.wrap(t) else Tablature.strip(t) }
        val page = parsed.copy(text = tab(ChordImport.plainSpaces(parsed.text)), chordText = tab(ChordImport.plainSpaces(parsed.chordText)))
        // lyrics and chords profile + page with chords: comes in as ChordPro (with key and capo); otherwise, lyrics only
        val chordSource = page.chordText.ifBlank { page.text }
        var text = if (repository.library.value.look.chords && ChordImport.hasChords(chordSource)) {
            ChordImport.toChordPro(chordSource, page.key, page.capo)
        } else {
            // lyrics only: no chords, but the page's key and capo are kept (hidden in this profile; not lost if the
            // profile changes or the song goes to someone who plays)
            var lyrics = LyricsCleaner.clean(page.text.ifBlank { Chords.stripChords(Chords.fromChordsOverLyrics(page.chordText)) })
            val capo = ChordImport.capoOf(page.capo, page.chordText)
            val key = ChordImport.keyOf(page.key, page.chordText)
            if (capo > 0) lyrics = Chords.setDirective(lyrics, "capo", capo.toString())
            if (key.isNotEmpty()) lyrics = Chords.setDirective(lyrics, "key", key)
            lyrics
        }
        if (text.lines().size < 3) return PageImport.Problem(ImportProblem.NOT_FOUND)
        // only lyrics and chords come in (the same filter as the band, plus the look of verses)
        if (!br.app.stagebook.domain.BandContent.isPlainText(text) || text.length > br.app.stagebook.domain.BandContent.MAX_TEXT ||
            !br.app.stagebook.domain.BandContent.looksLikeSong(text)
        ) return PageImport.Problem(ImportProblem.NOT_SONG)
        // BPM from the page (or written in the text): saved in the song, the metronome starts at it
        val bpm = ChordImport.bpmOf(page.bpm, page.chordText.ifBlank { page.text })
        if (bpm > 0 && Chords.tempo(text) == 0) text = Chords.setDirective(text, "tempo", bpm.toString())
        val meta = PageMeta.pick(page)
        var artist = meta.artist
        var year = PageMeta.yearOf(page)
        // year of the first release on MusicBrainz (the site usually lacks it); on a poor connection it doesn't keep
        // the singer waiting for more than 8 s
        val net = repository.library.value.look.internetOn   // essential mode (T19): no lookups
        if (net && meta.title.isNotEmpty()) musicBrainz.lookupWithin(meta.title, artist, LOOKUP_TIMEOUT_MS)?.let { m ->
            if (artist.isBlank()) artist = m.artist
            if (m.year.isNotEmpty()) year = m.year
        }
        // no BPM on the page or in the text: Deezer suggests one (the musician checks it in Song details)
        if (net && Chords.tempo(text) == 0 && meta.title.isNotEmpty()) deezer.lookupWithin(meta.title, artist, LOOKUP_TIMEOUT_MS)?.let { b ->
            text = Chords.setDirective(text, "tempo", b.toString())
        }
        return PageImport.Song(meta.title, artist, year, text)
    }

    private companion object {
        const val LOOKUP_TIMEOUT_MS = 8_000L
    }
}
