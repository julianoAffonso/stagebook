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
    androidx.compose.foundation.layout.Row(modifier, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(br.app.stagebook.ui.theme.Spacing.s)) {
        val wide = wideLists()
        // on phones, beside the filter, the long hint was cut: "Search" goes on top, like "Tags" (user, 01/11)
        if (filter != null && !wide) {
            SearchField(query, onQueryChange, shortHint, Modifier.weight(1f), label = stringResource(R.string.search_label))
        } else {
            SearchField(query, onQueryChange, placeholder, Modifier.weight(1f))
        }
        filter?.invoke(Modifier.width(if (wide) br.app.stagebook.ui.theme.Sizes.tagFilter else br.app.stagebook.ui.theme.Sizes.tagFilterNarrow))
    }
}

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
