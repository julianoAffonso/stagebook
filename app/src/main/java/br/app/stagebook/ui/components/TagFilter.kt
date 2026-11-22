package br.app.stagebook.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuBoxScope
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import br.app.stagebook.ui.theme.Spacing
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import br.app.stagebook.R

/**
 * Tag filter: one field that opens every tag, with a box each, several at a time (a simple
 * combo, but multiple choice). The menu stays open while ticking; "All" clears the filter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagFilter(tags: List<String>, chosen: Set<String>, onChange: (Set<String>) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val all = stringResource(R.string.tags_all)
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }, modifier = modifier) {
        FilterField(
            // one tag by name; more than one, the first and how many more, or on phones (narrow field) only how many
            tags.filter { it in chosen }.let { c ->
                when {
                    c.isEmpty() -> all
                    c.size == 1 -> c[0]
                    wideLists() -> stringResource(R.string.tags_more, c[0], c.size - 1)
                    else -> pluralStringResource(R.plurals.tags_count, c.size, c.size)
                }
            },
            stringResource(R.string.tags_filter), open,
        )
        FilterMenu(open, { open = false }) {
            DropdownMenuItem(
                text = { Text(all, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                onClick = { onChange(emptySet()); open = false },
                leadingIcon = { Checkbox(chosen.isEmpty(), onCheckedChange = null) },
                trailingIcon = { CloseFilter { open = false } },
                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
            )
            HorizontalDivider()
            tags.forEach { t ->
                DropdownMenuItem(
                    text = { Text(t, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = { onChange(if (t in chosen) chosen - t else chosen + t) },
                    leadingIcon = { Checkbox(t in chosen, onCheckedChange = null) },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

/**
 * The × of a menu that stays open while choosing (user, 19/11: how to close the filter wasn't clear; a "Done" on a line
 * of its own was one line too many). On the menu's first line, where a long list of tags never scrolls it away.
 */
@Composable
private fun CloseFilter(onClose: () -> Unit) {
    androidx.compose.material3.IconButton(onClick = onClose) {
        androidx.compose.material3.Icon(AppIcons.Close, stringResource(R.string.action_close), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The Songs list's one control for what shows and in what order (user, 19/11: most and least played in the same field
 * as the tags): Show (all, played in the last 30 days, never played), the tags, and the Order (A to Z, most played,
 * least played). Like the tags alone, the menu stays open while choosing; the field sums up what is on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongFilter(
    tags: List<String>, chosen: Set<String>, onTags: (Set<String>) -> Unit,
    show: br.app.stagebook.domain.PlayShow, onShow: (br.app.stagebook.domain.PlayShow) -> Unit,
    order: br.app.stagebook.domain.SongOrder, onOrder: (br.app.stagebook.domain.SongOrder) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val showNames = mapOf(
        br.app.stagebook.domain.PlayShow.ALL to stringResource(R.string.tags_all),
        br.app.stagebook.domain.PlayShow.RECENT to stringResource(R.string.songs_show_recent),
        br.app.stagebook.domain.PlayShow.NEVER to stringResource(R.string.songs_show_never),
    )
    val orderNames = mapOf(
        br.app.stagebook.domain.SongOrder.AZ to stringResource(R.string.songs_order_az),
        br.app.stagebook.domain.SongOrder.MOST to stringResource(R.string.songs_order_most),
        br.app.stagebook.domain.SongOrder.LEAST to stringResource(R.string.songs_order_least),
    )
    val picked = tags.filter { it in chosen }
    val tagPart = when {
        picked.isEmpty() -> null
        picked.size == 1 -> picked[0]
        else -> pluralStringResource(R.plurals.tags_count, picked.size, picked.size)
    }
    val showPart = if (show == br.app.stagebook.domain.PlayShow.RECENT) stringResource(R.string.songs_show_recent_short) else if (show == br.app.stagebook.domain.PlayShow.NEVER) showNames.getValue(show) else null
    val summary = (listOfNotNull(showPart, tagPart).ifEmpty { listOf(showNames.getValue(br.app.stagebook.domain.PlayShow.ALL)) } + orderNames.getValue(order)).joinToString(" · ")
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }, modifier = modifier) {
        FilterField(summary, stringResource(R.string.songs_filter), open)
        FilterMenu(open, { open = false }) {
            @Composable fun heading(text: String) = Text(
                text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = Spacing.m, end = Spacing.m, top = Spacing.s, bottom = Spacing.xs).semantics { heading() },
            )
            // two lines when needed: "Played in the last 30 days" was cut on a phone
            @Composable fun radio(name: String, on: Boolean, enabled: Boolean = true, pick: () -> Unit) = DropdownMenuItem(
                text = { Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                onClick = pick, enabled = enabled, leadingIcon = { RadioButton(on, onClick = null, enabled = enabled) }, contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
            )
            // the first heading carries the ×: closing sits on a line that is already there
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                androidx.compose.foundation.layout.Box(Modifier.weight(1f)) { heading(stringResource(R.string.songs_show)) }
                CloseFilter { open = false }
            }
            showNames.forEach { (value, name) ->
                radio(name, show == value) {
                    onShow(value)
                    // never played: the order goes back to A to Z (the others sort songs that all have zero)
                    val fit = br.app.stagebook.domain.PlayStats.orderFor(value, order)
                    if (fit != order) onOrder(fit)
                }
            }
            if (tags.isNotEmpty()) {
                HorizontalDivider()
                heading(stringResource(R.string.tags_filter))
                tags.forEach { t ->
                    DropdownMenuItem(
                        text = { Text(t, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = { onTags(if (t in chosen) chosen - t else chosen + t) },
                        leadingIcon = { Checkbox(t in chosen, onCheckedChange = null) },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
            HorizontalDivider()
            heading(stringResource(R.string.songs_order))
            val orders = br.app.stagebook.domain.PlayStats.ordersFor(show)
            orderNames.forEach { (value, name) -> radio(name, order == value, enabled = value in orders) { onOrder(value) } }
        }
    }
}

/** One choice among few, in the same field as the tag filter, to sit beside a search (the setlists' order). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ChoiceFilter(label: String, options: List<T>, selected: T, onSelect: (T) -> Unit, nameOf: @Composable (T) -> String, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }, modifier = modifier) {
        FilterField(nameOf(selected), label, open)
        FilterMenu(open, { open = false }) {
            options.forEach { o ->
                DropdownMenuItem(
                    text = { Text(nameOf(o), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = { onSelect(o); open = false },
                    leadingIcon = { RadioButton(o == selected, onClick = null) },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExposedDropdownMenuBoxScope.FilterField(value: String, label: String, open: Boolean) {
    TextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        singleLine = true,
        label = { Text(label) },
        textStyle = MaterialTheme.typography.bodyLarge,
        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
        // the same look as the search field it sits next to
        shape = MaterialTheme.shapes.medium,
        colors = ExposedDropdownMenuDefaults.textFieldColors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
    )
}

// the menu can be wider than the field (on phones with large text the names broke in the middle of words)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExposedDropdownMenuBoxScope.FilterMenu(open: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    br.app.stagebook.ui.components.FollowBusyWhile(open)
    // a tap outside it closes only it: the side menu under it stays (user, 19/11: both closed at once). The filter goes as
    // the finger touches down, and the side menu's dimmed area takes the same touch as it lifts: so the guard stays a
    // moment after the filter closed, and that touch is spent on the filter alone
    val guard = br.app.stagebook.ui.drawer.LocalDrawerGuard.current
    val dismiss by androidx.compose.runtime.rememberUpdatedState(onDismiss)
    val openNow by androidx.compose.runtime.rememberUpdatedState(open)
    val wasOpen = remember { booleanArrayOf(false) }   // never opened yet: nothing held
    androidx.compose.runtime.LaunchedEffect(open) {
        // only the dimmed area's touch is held: a song tapped right after still closes the menu (audit 20/11)
        if (!open && wasOpen[0]) guard?.holdScrim(FILTER_TOUCH_MS)
        wasOpen[0] = open
    }
    androidx.compose.runtime.DisposableEffect(guard) {
        val asks: () -> Boolean = { if (openNow) { dismiss(); true } else false }
        guard?.add(asks)
        onDispose { guard?.remove(asks) }
    }
    ExposedDropdownMenu(
        expanded = open, onDismissRequest = onDismiss, matchAnchorWidth = false,
        modifier = Modifier.widthIn(min = br.app.stagebook.ui.theme.Sizes.tagFilterNarrow, max = br.app.stagebook.ui.theme.Sizes.tagMenu),
        containerColor = AppMenu.color, shadowElevation = AppMenu.shadow, border = AppMenu.border,
        content = content,
    )
}

/**
 * Search with the tag filter beside it (user, 01/11: the filter alone on its line was too big). Without tags, only the
 * search.
 */
@Composable
fun SearchWithTags(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    tags: List<String>,
    chosen: Set<String>,
    onTagsChange: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    SearchWithFilter(
        query, onQueryChange, placeholder, stringResource(R.string.search_hint), modifier,
        filter = if (tags.isEmpty()) null else { m -> TagFilter(tags, chosen, onTagsChange, m) },
    )
}

/** A search and, when there is one, the [filter] beside it; [shortHint] replaces the [placeholder] on phones. */
@Composable
fun SearchWithFilter(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    shortHint: String,
    modifier: Modifier = Modifier,
    filter: (@Composable (Modifier) -> Unit)? = null,
) {
    val gap = br.app.stagebook.ui.theme.Spacing.s
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        // by the room this row has, not by the screen: on a tablet the menu is 420 dp wide, and the row was laid out as on a wide
        // screen there, with the tag filter taking most of it and the search left with a sliver (user, 16/11)
        val wide = wideLists() && maxWidth >= WIDE_FROM
        // the drawer is narrower than a phone (87%): beside the filter the search was left with a sliver and the two ran
        // together, so under 360 dp they stack, each on its own line (user, 15/11)
        val stacked = filter != null && !wide && maxWidth < STACK_BELOW
        if (stacked) {
            androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(gap)) {
                SearchField(query, onQueryChange, placeholder, Modifier.fillMaxWidth())
                filter(Modifier.fillMaxWidth())
            }
        } else androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(gap)) {
            // on phones, beside the filter, the long hint was cut: "Search" goes on top, like "Tags" (user, 01/11)
            if (filter != null && !wide) {
                SearchField(query, onQueryChange, shortHint, Modifier.weight(1f), label = stringResource(R.string.search_label))
            } else {
                SearchField(query, onQueryChange, placeholder, Modifier.weight(1f))
            }
            filter?.invoke(Modifier.width(if (wide) br.app.stagebook.ui.theme.Sizes.tagFilter else br.app.stagebook.ui.theme.Sizes.tagFilterNarrow))
        }
    }
}

private val STACK_BELOW = 360.dp
internal const val FILTER_TOUCH_MS = 500L   // the touch that closed a filter is spent within this, not passed to the side menu
private val WIDE_FROM = 560.dp   // from here the long hint and the wide filter fit side by side

@LightPreview
@Composable
private fun TagFilterLightPreview() = PreviewSurface(dark = false) {
    TagFilter(listOf("Internacional", "Nacional", "Rock"), emptySet(), {})
}

@DarkPreview
@Composable
private fun TagFilterDarkPreview() = PreviewSurface(dark = true) {
    SearchWithTags("", {}, "Buscar música ou artista", listOf("Internacional", "Nacional", "Rock"), setOf("Nacional", "Rock"), {})
}
