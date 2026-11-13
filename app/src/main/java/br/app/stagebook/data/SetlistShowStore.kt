package br.app.stagebook.data

import android.content.Context
import androidx.core.content.edit
import br.app.stagebook.data.model.Setlist
import br.app.stagebook.di.ApplicationScope
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
    fun stageOpened(setId: String) = change(setId) { SetlistShow.opened(it, today()) }

    fun onStage(setId: String, songId: String) = change(setId) { SetlistShow.onStage(it, today(), songId) }

    fun played(setId: String, songId: String) = change(setId) { SetlistShow.played(it, today(), songId) }

    /** "Clear played": the setlist as if no song had been on stage. */
    fun clear(setId: String) = change(setId) { Marks() }

    private fun prune(sets: List<Setlist>) {
        val items = sets.associate { it.id to it.items.toSet() }
        replace { all -> all.mapNotNull { (id, marks) -> items[id]?.let { id to SetlistShow.pruned(marks, it) } }.filterNot { it.second.empty }.toMap() }
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

    private fun load(): Map<String, Marks> =
        runCatching { json.decodeFromString<Map<String, Marks>>(prefs.getString(KEY, null) ?: return emptyMap()) }.getOrDefault(emptyMap())

    private companion object {
        const val KEY = "marks"
    }
}
