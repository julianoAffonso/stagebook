package br.app.stagebook.ui.band

import br.app.stagebook.ui.components.InfoDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
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

    /** [missingHere]: the current song isn't on this device; [gone]: the leader is gone (10 min with no news). */
    @Immutable
    data class Following(
        val from: String,
        val song: String,
        val missingHere: Boolean,
        val gone: Boolean,
        val nowMissing: List<String>,
        val lacks: List<Conduct.Lack>,
    ) : ConductUi
}

@HiltViewModel
class ConductViewModel @Inject constructor(private val conduct: BandConduct) : ViewModel() {
    // clock for "gone" (every 30 s; only while the strip is on screen)
    private val tick = flow { while (true) { emit(System.currentTimeMillis()); delay(30_000) } }

    val ui: StateFlow<ConductUi?> = combine(conduct.role, tick) { role, now ->
        when (role) {
            Role.None -> null
            is Role.Conducting -> ConductUi.Conducting(role.msg.set, role.noAnswer, Conduct.missingAt(role.msg, role.msg.index), role.msg.missing)
            is Role.Following -> ConductUi.Following(
                role.msg.from, role.msg.songs.getOrNull(role.msg.index)?.title.orEmpty(), role.songId == null, role.gone(now),
                Conduct.missingAt(role.msg, role.msg.index), role.msg.missing,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun end() = conduct.end()
    fun leave() = conduct.leave()
    fun search() = conduct.search()
}

@Composable
fun ConductRoute(modifier: Modifier = Modifier, viewModel: ConductViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    AnimatedVisibility(ui != null, modifier) {
        ui?.let { ConductBar(it, viewModel::end, viewModel::leave, viewModel::search) }
    }
}

@Composable
fun ConductBar(ui: ConductUi, onEnd: () -> Unit, onLeave: () -> Unit, onSearch: () -> Unit, modifier: Modifier = Modifier) {
    var showLacks by rememberSaveable { mutableStateOf(false) }
    val lacks = when (ui) { is ConductUi.Conducting -> ui.lacks; is ConductUi.Following -> ui.lacks }
    val nowMissing = when (ui) { is ConductUi.Conducting -> ui.nowMissing; is ConductUi.Following -> ui.nowMissing }
    // someone is missing a song now (or the leader is gone): the whole strip turns red, so the band sees it before
    // starting
    val alert = nowMissing.isNotEmpty() || (ui is ConductUi.Following && (ui.gone || ui.missingHere))
    Surface(
        color = if (alert) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (alert) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = Spacing.m, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Icon(if (alert) AppIcons.Warning else AppIcons.Group, null)
            Column(Modifier.weight(1f)) {
                Text(
                    when (ui) {
                        is ConductUi.Conducting -> stringResource(R.string.conduct_conducting, ui.set)
                        is ConductUi.Following -> if (ui.gone) stringResource(R.string.conduct_gone, ui.from) else stringResource(R.string.conduct_following, ui.from)
                    },
                    style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val detail = when {
                    ui is ConductUi.Following && ui.missingHere -> stringResource(R.string.conduct_missing_here, ui.song)
                    nowMissing.isNotEmpty() -> stringResource(R.string.conduct_missing_now, nowMissing.joinToString(", "))
                    ui is ConductUi.Conducting && ui.noAnswer > 0 -> pluralStringResource(R.plurals.conduct_no_answer, ui.noAnswer, ui.noAnswer)
                    else -> null
                }
                detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
            if (lacks.isNotEmpty()) TextButton({ showLacks = true }) { Text(stringResource(R.string.conduct_see_lacks)) }
            when (ui) {
                is ConductUi.Conducting -> {
                    TextButton(onSearch) { Text(stringResource(R.string.conduct_search)) }
                    TextButton(onEnd) { Text(stringResource(R.string.conduct_end)) }
                }
                is ConductUi.Following -> TextButton(onLeave) { Text(stringResource(R.string.conduct_leave)) }
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
        ConductBar(ConductUi.Conducting("Show de sábado", 1, emptyList(), sampleLacks), {}, {}, {})
        ConductBar(ConductUi.Conducting("Show de sábado", 0, listOf("Baixo"), sampleLacks), {}, {}, {})
        ConductBar(ConductUi.Following("Voz", "Garota de Ipanema", false, false, emptyList(), emptyList()), {}, {}, {})
        ConductBar(ConductUi.Following("Voz", "Evidências", true, false, listOf("Baixo"), sampleLacks), {}, {}, {})
        ConductBar(ConductUi.Following("Voz", "", false, true, emptyList(), emptyList()), {}, {}, {})
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
