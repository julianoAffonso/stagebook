package br.app.stagebook.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import br.app.stagebook.R
import br.app.stagebook.data.model.PlayEntry
import br.app.stagebook.domain.PlayHistory
import br.app.stagebook.ui.components.ActionButton
import br.app.stagebook.ui.components.AppIcons
import br.app.stagebook.ui.components.Choice
import br.app.stagebook.ui.components.ScreenHeader
import br.app.stagebook.ui.components.SegmentedChoice
import br.app.stagebook.ui.theme.Sizes
import br.app.stagebook.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * What was played, on a screen of its own (user, 23/11: "the report could have a screen, and export to CSV from there"):
 * the songs of the period picked, day by day from the latest show, and the spreadsheet of exactly what is shown.
 */
@Composable
fun PlayReportScreen(
    history: List<PlayEntry>,
    period: PlayHistory.Period,
    onPeriod: (PlayHistory.Period) -> Unit,
    onExport: (PlayHistory.Period, PlayHistory.View) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    view: PlayHistory.View = PlayHistory.View.DAYS,
    onView: (PlayHistory.View) -> Unit = {},
    today: LocalDate = remember { LocalDate.now() },
) {
    val locale = LocalConfiguration.current.locales[0]
    // the latest show first; the spreadsheet keeps the oldest first, as a report is read
    val lines = remember(history, period, today) { PlayHistory.of(history, period, today) }
    val days = remember(lines) { PlayHistory.byDay(lines) }
    // each song once with its count: made only when that view is the one shown
    val counts = remember(lines, view) { if (view == PlayHistory.View.SONGS) PlayHistory.bySong(lines) else emptyList() }
    val dayName = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale) }
    val songs = days.sumOf { it.second.size }
    Box(modifier.fillMaxSize(), Alignment.TopCenter) {
        Column(Modifier.widthIn(max = Sizes.contentMax).fillMaxSize().padding(horizontal = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            ScreenHeader(
                stringResource(R.string.history_title),
                subtitle = if (songs == 0) "" else pluralStringResource(R.plurals.history_summary, songs, songs) + " " + pluralStringResource(R.plurals.history_days, days.size, days.size),
                onBack = onBack,
            )
            SegmentedChoice(
                listOf(
                    Choice(PlayHistory.Period.MONTH, R.string.history_month),
                    Choice(PlayHistory.Period.YEAR, R.string.history_year),
                    Choice(PlayHistory.Period.ALL, R.string.history_all),
                ),
                period, onPeriod, Modifier.fillMaxWidth(),
            )
            if (songs > 0) SegmentedChoice(
                listOf(Choice(PlayHistory.View.DAYS, R.string.history_by_day), Choice(PlayHistory.View.SONGS, R.string.history_by_song)),
                view, onView, Modifier.fillMaxWidth(),
            )
            if (songs == 0) {
                Text(
                    stringResource(if (history.isEmpty()) R.string.history_empty else R.string.history_none_period),
                    Modifier.weight(1f).padding(top = Spacing.l),
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    if (view == PlayHistory.View.SONGS) {
                        items(counts.size, contentType = { "count" }) { i -> CountRow(counts[i]) }
                    } else days.forEach { (day, lines) ->
                        item(key = day, contentType = "day") {
                            Text(
                                // a day that isn't a date (a file made by hand) is shown as it is
                                remember(day, dayName) { runCatching { LocalDate.parse(day).format(dayName).replaceFirstChar { it.titlecase(locale) } }.getOrDefault(day) },
                                Modifier.fillMaxWidth().padding(top = Spacing.l, bottom = Spacing.xs).semantics { heading() },
                                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        items(lines.size, key = { "$day/$it" }, contentType = { "song" }) { i -> PlayRow(lines[i]) }
                    }
                }
                // the screen's one main action, at the end as everywhere
                Row(Modifier.fillMaxWidth().padding(bottom = Spacing.l), horizontalArrangement = Arrangement.End) {
                    ActionButton(stringResource(R.string.history_export), { onExport(period, view) }, icon = AppIcons.Download)
                }
            }
        }
    }
}

/** One song played: its name, then the artist and the setlist it was played in, when there are any. */
@Composable
private fun PlayRow(line: PlayEntry) {
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.s)) {
        Text(line.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val detail = listOf(line.author, line.setlist).filter { it.isNotBlank() }.joinToString(" · ")
        if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** One song of the period and how many times it was played. */
@Composable
private fun CountRow(count: PlayHistory.SongCount) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.s), horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(count.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (count.author.isNotBlank()) Text(count.author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(pluralStringResource(R.plurals.songs_plays, count.times, count.times), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
