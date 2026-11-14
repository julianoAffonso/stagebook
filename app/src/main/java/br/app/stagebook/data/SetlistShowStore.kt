package br.app.stagebook.data

import android.content.Context
import androidx.core.content.edit
import br.app.stagebook.data.model.Setlist
import br.app.stagebook.di.ApplicationScope
import br.app.stagebook.domain.BreakTimer
import br.app.stagebook.domain.BreakTimer.Timer
import br.app.stagebook.domain.SetlistShow
import br.app.stagebook.domain.SetlistShow.Marks
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Each setlist's show marks (played, on stage now; see [SetlistShow]), by setlist id. Kept apart from the library on
 * purpose: they are this device's and never go in a QR, a code, a backup or to the band. They follow the library by
 * themselves: a deleted setlist takes its marks along, and a song that left the setlist or the library leaves them.
 */
@Singleton
class SetlistShowStore @Inject constructor(
    @ApplicationContext context: Context,
    repository: LibraryRepository,
    @ApplicationScope scope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences("setlist_show", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _marks = MutableStateFlow(load())
    val marks: StateFlow<Map<String, Marks>> = _marks.asStateFlow()

    /** Each break's counter ([BreakTimer.key]); it lives with the show, so it goes on when the stage is left and opened again. */
    private val _breaks = MutableStateFlow(loadBreaks())
    val breaks: StateFlow<Map<String, Timer>> = _breaks.asStateFlow()

    /** The clock the counters use; a test names another one. */
    internal var now: () -> Long = { System.currentTimeMillis() }

    /** The calendar day the marks are kept by; a test names another one. */
    internal var today: () -> String = { java.time.LocalDate.now().toString() }

    init {
        scope.launch {
            // only a library that was really read: before that it is empty and every mark would look orphaned
            combine(repository.loaded, repository.library.map { it.sets }.distinctUntilChanged()) { loaded, sets -> sets.takeIf { loaded } }
                .collect { sets -> if (sets != null) prune(sets) }
        }
    }

    /** The stage opened from this setlist: yesterday's show isn't today's. */
    fun stageOpened(setId: String) {
        change(setId) { SetlistShow.opened(it, today()) }
        // the counters of another day's break are over too
        replaceBreaks { all -> all.filterNot { (k, t) -> k.startsWith("$setId|") && t.day != today() } }
    }

    fun onStage(setId: String, songId: String) = change(setId) { SetlistShow.onStage(it, today(), songId) }

    /** The song after a break stayed on stage long enough: that break is over, the next one starts from zero. */
    fun played(setId: String, songId: String) {
        change(setId) { SetlistShow.played(it, today(), songId) }
        replaceBreaks { it - BreakTimer.key(setId, songId) }
    }

    /** The break before [songId] is on screen: its counter starts running the first time, and goes on after that. */
    fun breakEntered(setId: String, songId: String): Timer {
        val key = BreakTimer.key(setId, songId)
        replaceBreaks { all -> all[key]?.let { all + (key to it.copy(open = true)) } ?: (all + (key to BreakTimer.started(today(), now()))) }
        return _breaks.value.getValue(key)
    }

    /** The break before [songId] was left (forward or back to the song): a stage opened later doesn't show it. */
    fun breakLeft(setId: String, songId: String) = changeBreak(setId, songId) { t, _ -> t.copy(open = false) }

    /** The break on screen when the setlist's stage was left, if it is still there (the song after it is [songId]). */
    fun breakOpen(setId: String, songId: String): Boolean = _breaks.value[BreakTimer.key(setId, songId)]?.open == true

    fun breakPause(setId: String, songId: String) = changeBreak(setId, songId, BreakTimer::pause)

    fun breakResume(setId: String, songId: String) = changeBreak(setId, songId, BreakTimer::resume)

    fun breakReset(setId: String, songId: String) = changeBreak(setId, songId, BreakTimer::reset)

    private fun changeBreak(setId: String, songId: String, change: (Timer, Long) -> Timer) {
        val key = BreakTimer.key(setId, songId)
        replaceBreaks { all -> all[key]?.let { all + (key to change(it, now())) } ?: all }
    }

    /** The setlist's breaks aren't open any more: this device follows the band, whose break is the one that counts. */
    fun closeBreaks(setId: String) =
        replaceBreaks { all -> all.mapValues { (k, t) -> if (k.startsWith("$setId|") && t.open) t.copy(open = false) else t } }

    /** "Clear played": the setlist as if no song had been on stage. */
    fun clear(setId: String) {
        change(setId) { Marks() }
        replaceBreaks { all -> all.filterKeys { !it.startsWith("$setId|") } }
    }

    private fun prune(sets: List<Setlist>) {
        val items = sets.associate { it.id to it.items.toSet() }
        replace { all -> all.mapNotNull { (id, marks) -> items[id]?.let { id to SetlistShow.pruned(marks, it) } }.filterNot { it.second.empty }.toMap() }
        replaceBreaks { all -> all.filterKeys { k -> items[k.substringBefore('|')]?.contains(k.substringAfter('|')) == true } }
    }

    private fun change(setId: String, change: (Marks) -> Marks) = replace { all ->
        val marks = change(all[setId] ?: Marks())
        if (marks.empty) all - setId else all + (setId to marks)
    }

    private fun replace(change: (Map<String, Marks>) -> Map<String, Marks>) {
        val before = _marks.value
        _marks.update(change)
        if (_marks.value != before) prefs.edit { putString(KEY, json.encodeToString(_marks.value)) }
    }

    private fun replaceBreaks(change: (Map<String, Timer>) -> Map<String, Timer>) {
        val before = _breaks.value
        _breaks.update(change)
        if (_breaks.value != before) prefs.edit { putString(BREAKS_KEY, json.encodeToString(_breaks.value)) }
    }

    private fun loadBreaks(): Map<String, Timer> =
        runCatching { json.decodeFromString<Map<String, Timer>>(prefs.getString(BREAKS_KEY, null) ?: return emptyMap()) }.getOrDefault(emptyMap())

    private fun load(): Map<String, Marks> =
        runCatching { json.decodeFromString<Map<String, Marks>>(prefs.getString(KEY, null) ?: return emptyMap()) }.getOrDefault(emptyMap())

    private companion object {
        const val KEY = "marks"
        const val BREAKS_KEY = "breaks"
    }
}
