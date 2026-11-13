package br.app.stagebook.ui.band

import br.app.stagebook.ui.components.InfoDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import br.app.stagebook.R
import br.app.stagebook.data.BandConduct
import br.app.stagebook.data.BandConduct.Role
import br.app.stagebook.domain.Conduct
import br.app.stagebook.ui.components.AppIcons
import br.app.stagebook.ui.components.DarkPreview
import br.app.stagebook.ui.components.LightPreview
import br.app.stagebook.ui.components.PreviewSurface
import br.app.stagebook.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The "Lead the band" strip (on top of any screen, at the bottom): the leader and the followers see the same alert. */
@Immutable
sealed interface ConductUi {
    /** [nowMissing]: who lacks the current song; [lacks]: all missing songs (the "See what's missing"). */
    @Immutable
    data class Conducting(val set: String, val noAnswer: Int, val nowMissing: List<String>, val lacks: List<Conduct.Lack>) : ConductUi

    /**
     * [missingHere]: the current song isn't on this device; [gone]: the leader is gone (10 min with no news).
     * [breakSince]: the leader is on a break since then (this device's clock); the strip then says so, with its time,
     * and [song], [missingHere] and [nowMissing] are about the song that comes after the break, the one that matters now.
     */
    @Immutable
    data class Following(
        val from: String,
        val song: String,
        val missingHere: Boolean,
        val gone: Boolean,
        val nowMissing: List<String>,
        val lacks: List<Conduct.Lack>,
        val breakSince: Long? = null,
    ) : ConductUi

    /** Left [from], who goes on leading: one action, to follow again. */
    @Immutable
    data class Left(val from: String) : ConductUi
}

/**
 * What the strip shows for [role], or null for no strip: with the band off, with nobody leading, and for a leader this
 * device left once it has been silent for as long as a followed one takes to count as disconnected.
 */
internal fun conductUi(role: Role, songs: List<br.app.stagebook.data.model.Song>, now: Long, bandOn: Boolean): ConductUi? = when {
    !bandOn -> null
    role is Role.Conducting -> ConductUi.Conducting(role.msg.set, role.noAnswer, Conduct.missingAt(role.msg, role.msg.index), role.msg.missing)
    role is Role.Following -> followingUi(role, songs, now)
    role is Role.Left && !role.gone(now) -> ConductUi.Left(role.msg.from)
    else -> null
}

/** What the strip tells a follower: the leader's song, or the leader's break and the song after it. */
internal fun followingUi(role: Role.Following, songs: List<br.app.stagebook.data.model.Song>, now: Long): ConductUi.Following {
    val msg = role.msg
    val next = role.breakSince?.let { msg.songs.getOrNull(msg.index + 1) }
    if (next != null) {
        val own = Conduct.find(next, songs)
        return ConductUi.Following(msg.from, own?.title ?: next.title, own == null, role.gone(now), Conduct.missingAt(msg, msg.index + 1), msg.missing, role.breakSince)
    }
    return ConductUi.Following(
        msg.from, msg.songs.getOrNull(msg.index)?.title.orEmpty(), role.songId == null, role.gone(now),
        Conduct.missingAt(msg, msg.index), msg.missing,
    )
}

@HiltViewModel
class ConductViewModel @Inject constructor(private val conduct: BandConduct, repository: br.app.stagebook.data.LibraryRepository) : ViewModel() {
    // clock for "gone" (every 30 s; only while the strip is on screen)
    private val tick = flow { while (true) { emit(System.currentTimeMillis()); delay(30_000) } }

    // the library too: on a break the strip says whether the next song is here (looked up only then)
    val ui: StateFlow<ConductUi?> = combine(conduct.role, tick, repository.library) { role, now, library ->
        conductUi(role, library.songs, now, library.look.bandOn)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun end() = conduct.end()
    fun leave() = conduct.leave()
    fun follow() = conduct.follow()
    fun search() = conduct.search()
}

@Composable
fun ConductRoute(modifier: Modifier = Modifier, viewModel: ConductViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    AnimatedVisibility(ui != null, modifier) {
        ui?.let { ConductBar(it, viewModel::end, viewModel::leave, viewModel::search, viewModel::follow) }
    }
}

@Composable
fun ConductBar(ui: ConductUi, onEnd: () -> Unit, onLeave: () -> Unit, onSearch: () -> Unit, onFollow: () -> Unit, modifier: Modifier = Modifier) {
    var showLacks by rememberSaveable { mutableStateOf(false) }
    val lacks = when (ui) { is ConductUi.Conducting -> ui.lacks; is ConductUi.Following -> ui.lacks; is ConductUi.Left -> emptyList() }
    val nowMissing = when (ui) { is ConductUi.Conducting -> ui.nowMissing; is ConductUi.Following -> ui.nowMissing; is ConductUi.Left -> emptyList() }
    // someone is missing a song now (or the leader is gone): the whole strip turns red, so the band sees it before
    // starting
    val alert = nowMissing.isNotEmpty() || (ui is ConductUi.Following && (ui.gone || ui.missingHere))
    @Composable
    fun Buttons(lacksButton: Boolean) {
        if (lacksButton && lacks.isNotEmpty()) TextButton({ showLacks = true }) { Text(stringResource(R.string.conduct_see_lacks)) }
        when (ui) {
            is ConductUi.Conducting -> {
                // the search runs 30 s with nothing else on screen: the button says so meanwhile, otherwise
                // nothing seems to happen
                var searching by remember { mutableStateOf(false) }
                LaunchedEffect(searching) { if (searching) { kotlinx.coroutines.delay(SEARCH_MS); searching = false } }
                TextButton({ searching = true; onSearch() }, enabled = !searching) {
                    Text(stringResource(if (searching) R.string.conduct_searching else R.string.conduct_search))
                }
                TextButton(onEnd) { Text(stringResource(R.string.conduct_end)) }
            }
            is ConductUi.Following -> TextButton(onLeave) { Text(stringResource(R.string.conduct_leave)) }
            is ConductUi.Left -> TextButton(onFollow) { Text(stringResource(R.string.conduct_follow)) }
        }
    }
    Surface(
        color = if (alert) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (alert) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        // on a phone the leader's two buttons don't fit next to the text ("End" broke letter by letter and the title
        // vanished; user, 02/11): they go on a line of their own. The follower's Leave stays beside the text, and on a
        // phone "See what's missing" is a tap on the text (with an arrow): as a button it pushed both onto a third line
        // and left the strip tall and half empty (user, 02/11)
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
            val small = maxWidth < NARROW
            val narrow = small && ui is ConductUi.Conducting
            val lacksInText = small && lacks.isNotEmpty()
            Column(Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Icon(if (alert) AppIcons.Warning else AppIcons.Group, null)
                    Column(
                        Modifier.weight(1f).then(
                            if (lacksInText) Modifier.clickable(onClickLabel = stringResource(R.string.conduct_see_lacks)) { showLacks = true } else Modifier,
                        ),
                    ) {
                        Text(
                            when (ui) {
                                is ConductUi.Conducting -> stringResource(R.string.conduct_conducting, ui.set)
                                is ConductUi.Following -> if (ui.gone) stringResource(R.string.conduct_gone, ui.from) else stringResource(R.string.conduct_following, ui.from)
                                is ConductUi.Left -> stringResource(R.string.conduct_left, ui.from)
                            },
                            style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        val rest = (ui as? ConductUi.Following)?.takeIf { it.breakSince != null }
                        val detail = when {
                            // the leader's break, for a follower on any screen: its time and what comes next (or that
                            // the next song isn't here), in place of the song
                            rest?.breakSince != null -> stringResource(
                                R.string.conduct_break, br.app.stagebook.ui.components.breakClock(rest.breakSince),
                                stringResource(if (rest.missingHere) R.string.conduct_missing_here else R.string.stage_break_next, rest.song),
                            )
                            ui is ConductUi.Following && ui.missingHere -> stringResource(R.string.conduct_missing_here, ui.song)
                            nowMissing.isNotEmpty() -> stringResource(R.string.conduct_missing_now, nowMissing.joinToString(", "))
                            ui is ConductUi.Conducting && ui.noAnswer > 0 -> pluralStringResource(R.plurals.conduct_no_answer, ui.noAnswer, ui.noAnswer)
                            lacksInText -> stringResource(R.string.conduct_see_lacks)
                            else -> null
                        }
                        detail?.let {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                if (lacksInText) Icon(AppIcons.ChevronRight, null, Modifier.size(16.dp))
                            }
                        }
                    }
                    if (!narrow) Buttons(lacksButton = !lacksInText)
                }
                if (narrow) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Buttons(lacksButton = !lacksInText) }
            }
        }
    }
    if (showLacks) {
        val resources = androidx.compose.ui.platform.LocalResources.current
        InfoDialog(
            stringResource(R.string.conduct_lacks_title),
            lacks.joinToString("\n") { resources.getString(R.string.conduct_lack_line, it.who, it.titles.joinToString(", ")) },
            onDismiss = { showLacks = false },
        )
    }
}

private val sampleLacks = listOf(Conduct.Lack("Baixo", listOf("Evidências", "Asa Branca")))

@Composable
private fun ConductBarSamples() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        ConductBar(ConductUi.Conducting("Show de sábado", 1, emptyList(), sampleLacks), {}, {}, {}, {})
        ConductBar(ConductUi.Conducting("Show de sábado", 0, listOf("Baixo"), sampleLacks), {}, {}, {}, {})
        ConductBar(ConductUi.Following("Voz", "Garota de Ipanema", false, false, emptyList(), emptyList()), {}, {}, {}, {})
        ConductBar(ConductUi.Following("Voz", "Evidências", true, false, listOf("Baixo"), sampleLacks), {}, {}, {}, {})
        ConductBar(ConductUi.Following("Voz", "", false, true, emptyList(), emptyList()), {}, {}, {}, {})
        // the leader's break, two minutes in, with the song that comes next
        val since = remember { System.currentTimeMillis() - 125_000L }
        ConductBar(ConductUi.Following("Voz", "Garota de Ipanema", false, false, emptyList(), emptyList(), since), {}, {}, {}, {})
        // left, and the leader goes on: the way back
        ConductBar(ConductUi.Left("Voz"), {}, {}, {}, {})
    }
}

@LightPreview
@Composable
private fun ConductBarLightPreview() = PreviewSurface(dark = false) { ConductBarSamples() }

@DarkPreview
@Composable
private fun ConductBarDarkPreview() = PreviewSurface(dark = true) { ConductBarSamples() }

/** For the screenshots (ScreenshotTest). */
@Composable
internal fun ConductBarSamplesForTest() = ConductBarSamples()

private const val SEARCH_MS = 30_000L   // BandConduct.search: the band search lasts this long

private val NARROW = androidx.compose.ui.unit.Dp(560f)   // below this the conduct bar puts its buttons under the text
