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

enum class ImportProblem { NOT_FOUND, UNREADABLE, NOT_SONG, SEARCH_PAGE }

/** What came out of a page (or pasted text): the song ready to review, a problem, or the tablature question first. */
sealed interface PageImport {
    data object AskTab : PageImport
    data class Problem(val problem: ImportProblem) : PageImport
    /** [offline]: no internet for the lookups (artist, year, BPM): the editor says so (device test B-27). */
    data class Song(val title: String, val artist: String, val year: String, val text: String, val offline: Boolean = false) : PageImport
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
    private val connectivity: Connectivity,
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
    suspend fun fromPage(source: PageData, withTab: Boolean? = null): PageImport {
        // a page that big is no song (a log, a book): not worth reading (limits in ExtractScript)
        if (maxOf(source.text.length, source.chordText.length) > 2 * br.app.stagebook.domain.BandContent.MAX_TEXT) return PageImport.Problem(ImportProblem.NOT_SONG)
        // a <pre> only counts if it has chords or tablature: others (code, notes) leave the page's text as it is. A riff
        // song has tablature and lyrics and not one chord: without its <pre> a piece from the middle came in (user, 11/11)
        val charted = ChordImport.hasChords(source.chordText)
        val preTab = !charted && Tablature.hasTab(ChordImport.plainSpaces(source.chordText))
        val parsed = if (charted || preTab) source.copy(text = source.preLyrics.ifBlank { source.text }) else source.copy(chordText = "")
        // pasted text too: tablature and no chords
        val tabOnly = ChordImport.plainSpaces(parsed.chordText.ifBlank { parsed.text }).let { !ChordImport.hasChords(it) && Tablature.hasTab(it) }
        // the search results aren't a song: Google's summary has bits of lyrics and came in as "cifra wave tom jobim"
        // with a made-up key (device test B-05): open the chords page first
        if (SEARCH_HOST.containsMatchIn(parsed.site)) return PageImport.Problem(ImportProblem.SEARCH_PAGE)
        // invisible marks start every line on some sites: the tablature only shows after they're gone
        val chordsProfile = repository.library.value.look.chords
        // nothing to choose when the tablature is all the chart has: it comes in with the chords profile, stays out of
        // the lyrics only one
        @Suppress("NAME_SHADOWING") val withTab = withTab ?: chordsProfile.takeIf { tabOnly }
        if (withTab == null && (Tablature.hasTab(ChordImport.plainSpaces(parsed.chordText)) || Tablature.hasTab(ChordImport.plainSpaces(parsed.text)))) return PageImport.AskTab
        val tab: (String) -> String = { t -> if (withTab == true) Tablature.wrap(t) else Tablature.strip(t) }
        val page = parsed.copy(text = tab(ChordImport.plainSpaces(parsed.text)), chordText = tab(ChordImport.plainSpaces(parsed.chordText)), pageText = ChordImport.plainSpaces(parsed.pageText))
        // lyrics and chords profile + page with chords: comes in as ChordPro (with key and capo); otherwise, lyrics only
        val chordSource = page.chordText.ifBlank { page.text }
        var text = if (chordsProfile && (ChordImport.hasChords(chordSource) || Tablature.hasTab(chordSource))) {
            ChordImport.toChordPro(chordSource, page.pageText)
        } else {
            // lyrics only: no chords, but the page's key and capo are kept (hidden in this profile; not lost if the
            // profile changes or the song goes to someone who plays)
            // the tuning is for who plays
            val sung = page.text.ifBlank { Chords.stripChords(Chords.fromChordsOverLyrics(page.chordText)) }.lineSequence().filterNot(Chords::isTuningLine).joinToString("\n")
            val lyrics = LyricsCleaner.clean(sung)
            ChordImport.withPageInfo(lyrics, page.chordText, page.pageText, page.chordText)
        }
        if (text.lines().size < 3) return PageImport.Problem(ImportProblem.NOT_FOUND)
        // only lyrics and chords come in (the same filter as the band, plus the look of verses)
        if (!br.app.stagebook.domain.BandContent.isPlainText(text) || text.length > br.app.stagebook.domain.BandContent.MAX_TEXT ||
            !br.app.stagebook.domain.BandContent.looksLikeSong(text)
        ) return PageImport.Problem(ImportProblem.NOT_SONG)
        // BPM from the page (or written in the text): saved in the song, the metronome starts at it
        val bpm = ChordImport.bpmOf(page.pageText, page.chordText.ifBlank { page.text })
        if (bpm > 0 && Chords.tempo(text) == 0) text = Chords.setDirective(text, "tempo", bpm.toString())
        val meta = PageMeta.pick(page)
        var artist = meta.artist
        var year = PageMeta.yearOf(page)
        // year of the first release on MusicBrainz (the site usually lacks it); on a poor connection it doesn't keep
        // the singer waiting for more than 8 s
        val internetOn = repository.library.value.look.internetOn   // essential mode (T19): no lookups
        val online = internetOn && connectivity.isOnline()
        val net = online
        if (net && meta.title.isNotEmpty()) musicBrainz.lookupWithin(meta.title, artist, LOOKUP_TIMEOUT_MS)?.let { m ->
            if (artist.isBlank()) artist = m.artist
            if (m.year.isNotEmpty()) year = m.year
        }
        // no BPM on the page or in the text: Deezer suggests one (the musician checks it in Song details)
        if (net && Chords.tempo(text) == 0 && meta.title.isNotEmpty()) deezer.lookupWithin(meta.title, artist, LOOKUP_TIMEOUT_MS)?.let { b ->
            text = Chords.setDirective(text, "tempo", b.toString())
        }
        // essential mode turns the lookups off on purpose: only a missing connection is worth a notice
        return PageImport.Song(meta.title, artist, year, text, offline = internetOn && !online)
    }

    private companion object {
        const val LOOKUP_TIMEOUT_MS = 8_000L
        val SEARCH_HOST = Regex("""(^|\.)(google|bing|duckduckgo)\.""")
    }
}
