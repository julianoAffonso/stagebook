package br.app.stagebook.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import br.app.stagebook.R

// "12 songs" / "12 setlists"; zero says "No songs" (Portuguese plurals put 0 in "one": "0 música", device test B-08)
@Composable
fun songCountText(count: Int): String =
    if (count == 0) stringResource(R.string.song_count_none) else pluralStringResource(R.plurals.song_count, count, count)

@Composable
fun setCountText(count: Int): String =
    if (count == 0) stringResource(R.string.set_count_none) else pluralStringResource(R.plurals.set_count, count, count)

/** A list's total and, while a filter is on, how many it left: "5 songs · 2 shown". */
@Composable
fun filteredCountText(total: String, shown: Int, filtering: Boolean): String =
    if (filtering) "$total · ${stringResource(R.string.list_filtered, shown)}" else total

/**
 * Takes the list back to the top when [filter] changes. The value is saved so coming back to a tab keeps its place, and
 * it's read with the new rows already in: a search waits for the typing to stop.
 */
@Composable
fun ScrollToTopOn(filter: String, list: androidx.compose.foundation.lazy.LazyListState) {
    var shown by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(filter) }
    androidx.compose.runtime.LaunchedEffect(filter) {
        if (filter != shown) { shown = filter; list.scrollToItem(0) }
    }
}
