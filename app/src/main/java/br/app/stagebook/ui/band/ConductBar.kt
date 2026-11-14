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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.text
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The "Lead the band" strip (on top of any screen, at the bottom): the leader and the followers see the same alert. */
@Immutable
sealed interface ConductUi {
    /** A following device that lacks songs: the leader can send them to it ([id]). */
    @Immutable
    data class Lacker(val id: String, val who: String, val titles: List<String>)

    /**
     * [nowMissing]: who lacks the current song; [lacks]: all missing songs (the "See what's missing"); [lackers]: the
     * same, by device, for sending them.
     */
    @Immutable
    data class Conducting(
        val set: String, val noAnswer: Int, val nowMissing: List<String>, val lacks: List<Conduct.Lack>, val lackers: List<Lacker> = emptyList(),
    ) : ConductUi

    /**
     * [missingHere]: the current song isn't on this device; [gone]: the leader is gone (10 min with no news).
     * [nowMissing]: the other devices that lack the song; [lacksHere]: the songs of the leader's setlist that aren't on
     * this device, the follower's own list to see ahead of time (who lacks what in the band is the leader's window).
     * [breakSince]: the leader is on a break since then (this device's clock); the strip then says so, with its time,
     * and [song], [missingHere] and [nowMissing] are about the song that comes after the break, the one that matters now.
     * [noChords]: [song] came from the band during this show without chords, on a profile that shows them.
     */
    @Immutable
    data class Following(
        val from: String,
        val song: String,
        val missingHere: Boolean,
        val gone: Boolean,
        val nowMissing: List<String>,
        val lacksHere: List<String>,
        val breakSince: Long? = null,
        val noChords: Boolean = false,
    ) : ConductUi

    /** Left [from], who goes on leading: one action, to follow again. */
    @Immutable
    data class Left(val from: String) : ConductUi
}

/**
 * What the strip shows for [role], or null for no strip: with the band off, with nobody leading, and for a leader this
 * device left once it has been silent for as long as a followed one takes to count as disconnected.
 */
internal fun conductUi(
    role: Role, songs: List<br.app.stagebook.data.model.Song>, now: Long, bandOn: Boolean, chordless: Set<String> = emptySet(), chords: Boolean = true,
    me: String = "", lacksHere: List<String>? = null,
): ConductUi? = when {
    !bandOn -> null
    role is Role.Conducting -> ConductUi.Conducting(
        role.msg.set, role.noAnswer, Conduct.missingAt(role.msg, role.msg.index), role.msg.missing,
        role.lackers.map { (id, lack) -> ConductUi.Lacker(id, lack.who, lack.titles) }.sortedBy { it.who },
    )
    role is Role.Following -> followingUi(role, songs, now, chordless.takeIf { chords }.orEmpty(), me, lacksHere)
    role is Role.Left && !role.gone(now) -> ConductUi.Left(role.msg.from)
    else -> null
}

/**
 * What the strip tells a follower: the leader's song, or the leader's break and the song after it. [me]: this device's
 * name, left out of who lacks the song: what is here is known here, and the leader's list only learns of a song that
 * arrived with its next message (the strip stayed red on a follower that had just got the song, two devices, 13/11).
 * [lacksHere]: the setlist's songs that aren't here, when already looked up (null = looked up now).
 */
internal fun followingUi(
    role: Role.Following, songs: List<br.app.stagebook.data.model.Song>, now: Long, chordless: Set<String> = emptySet(),
    me: String = "", lacksHere: List<String>? = null,
): ConductUi.Following {
    val msg = role.msg
    val lacks = lacksHere ?: Conduct.missing(msg.songs, songs)
    fun others(index: Int) = Conduct.missingAt(msg, index).filter { it != me }
    val next = role.breakSince?.let { msg.songs.getOrNull(msg.index + 1) }
    if (next != null) {
        val own = Conduct.find(next, songs)
        return ConductUi.Following(msg.from, own?.title ?: next.title, own == null, role.gone(now), others(msg.index + 1), lacks, role.breakSince)
    }
    return ConductUi.Following(
        msg.from, msg.songs.getOrNull(msg.index)?.title.orEmpty(), role.songId == null, role.gone(now),
        others(msg.index), lacks,
        // still without chords: once the musician writes them in, the strip stops saying so
        noChords = role.songId in chordless && songs.firstOrNull { it.id == role.songId }?.let { !br.app.stagebook.domain.Chords.hasChords(it.text) } == true,
    )
}

/** The short message for what happened to the missing songs. */
internal fun BandConduct.Notice.text(resources: android.content.res.Resources): String = when (this) {
    is BandConduct.Notice.Sent -> resources.getString(R.string.conduct_sent_to, to)
    is BandConduct.Notice.SentMany -> resources.getQuantityString(R.plurals.share_band_sent, devices, devices)
    BandConduct.Notice.NotSent -> resources.getString(R.string.conduct_not_sent)
    is BandConduct.Notice.Arrived -> resources.getString(R.string.conduct_arrived, title, from)
    is BandConduct.Notice.NoChords -> resources.getString(R.string.conduct_no_chords, title)
    is BandConduct.Notice.Nobody -> resources.getString(if (chords) R.string.conduct_nobody_has_chords else R.string.conduct_nobody_has)
    is BandConduct.Notice.ChordsKept -> resources.getString(R.string.conduct_chords_kept, title, from)
    is BandConduct.Notice.ChordsCame -> resources.getString(R.string.conduct_chords_came, title, from)
}

@HiltViewModel
class ConductViewModel @Inject constructor(
    private val conduct: BandConduct, repository: br.app.stagebook.data.LibraryRepository, identity: br.app.stagebook.data.DeviceIdentity,
) : ViewModel() {
    // clock for "gone" (every 30 s; only while the strip is on screen)
    private val tick = flow { while (true) { emit(System.currentTimeMillis()); delay(30_000) } }

    // the library too: on a break the strip says whether the next song is here (looked up only then)
    // following: the leader's songs that aren't here. Looked up again only when the setlist or the library's titles
    // change, not on every save (the stage saves a song's size while it opens)
    private val lacksHere = combine(
        conduct.role.map { (it as? Role.Following)?.msg?.songs }.distinctUntilChanged(),
        repository.library.map { it.songs.map { s -> s.title to s.author } }.distinctUntilChanged(),
    ) { items, _ -> items?.let { Conduct.missing(it, repository.library.value.songs) } }.flowOn(kotlinx.coroutines.Dispatchers.Default)

    val ui: StateFlow<ConductUi?> = combine(conduct.role, tick, repository.library, conduct.chordless, lacksHere) { role, now, library, chordless, lacks ->
        conductUi(role, library.songs, now, library.look.bandOn, chordless, library.look.chords, identity.name.value, lacks)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val asking: StateFlow<Boolean> = conduct.asking
    val sending: StateFlow<Boolean> = conduct.sending
    val notices = conduct.notices

    fun end() = conduct.end()
    fun leave() = conduct.leave()
    fun follow() = conduct.follow()
    fun search() = conduct.search()
    fun ask() = conduct.ask()

    /** Sends one device ([memberId]) the songs it lacks, or every listed device (null). */
    fun send(memberId: String?) = conduct.sendLacking(memberId?.let(::setOf))
}

@Composable
fun ConductRoute(modifier: Modifier = Modifier, viewModel: ConductViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val asking by viewModel.asking.collectAsStateWithLifecycle()
    val sending by viewModel.sending.collectAsStateWithLifecycle()
    val message = br.app.stagebook.ui.components.LocalAppMessage.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    // what happened to the missing songs, as the app's short message (it waits; nothing runs until one comes)
    LaunchedEffect(viewModel, message, resources) {
        viewModel.notices.collect { message.show(it.text(resources), long = it is BandConduct.Notice.ChordsKept) }
    }
    AnimatedVisibility(ui != null, modifier) {
        ui?.let {
            ConductBar(it, viewModel::end, viewModel::leave, viewModel::search, viewModel::follow, onAsk = viewModel::ask, asking = asking, onSend = viewModel::send, sending = sending)
        }
    }
}

@Composable
fun ConductBar(
    ui: ConductUi, onEnd: () -> Unit, onLeave: () -> Unit, onSearch: () -> Unit, onFollow: () -> Unit, modifier: Modifier = Modifier,
    onAsk: () -> Unit = {}, asking: Boolean = false, onSend: (String?) -> Unit = {}, sending: Boolean = false,
) {
    var showLacks by rememberSaveable { mutableStateOf(false) }
    // the leader's list is who lacks what in the band; a follower's is what this device lacks
    val hasLacks = when (ui) { is ConductUi.Conducting -> ui.lacks.isNotEmpty(); is ConductUi.Following -> ui.lacksHere.isNotEmpty(); is ConductUi.Left -> false }
    val seeLacks = stringResource(if (ui is ConductUi.Following) R.string.conduct_see_lacks_here else R.string.conduct_see_lacks)
    // the last one arrived: the list doesn't come back open by itself when another goes missing
    LaunchedEffect(hasLacks) { if (!hasLacks) showLacks = false }
    val nowMissing = when (ui) { is ConductUi.Conducting -> ui.nowMissing; is ConductUi.Following -> ui.nowMissing; is ConductUi.Left -> emptyList() }
    // someone is missing a song now (or the leader is gone): the whole strip turns red, so the band sees it before
    // starting
    val alert = nowMissing.isNotEmpty() || (ui is ConductUi.Following && (ui.gone || ui.missingHere))
    // the song isn't here, or came without chords: ask the band on this Wi-Fi
    val canAsk = ui is ConductUi.Following && (ui.missingHere || ui.noChords)
    @Composable
    fun Buttons(lacksButton: Boolean) {
        if (lacksButton && hasLacks) TextButton({ showLacks = true }) { ActionText(seeLacks) }
        when (ui) {
            is ConductUi.Conducting -> {
                // the search runs 30 s with nothing else on screen: the button says so meanwhile, otherwise
                // nothing seems to happen
                var searching by remember { mutableStateOf(false) }
                LaunchedEffect(searching) { if (searching) { kotlinx.coroutines.delay(SEARCH_MS); searching = false } }
                TextButton({ searching = true; onSearch() }, enabled = !searching) {
                    ActionText(stringResource(if (searching) R.string.conduct_searching else R.string.conduct_search))
                }
                TextButton(onEnd) { ActionText(stringResource(R.string.conduct_end)) }
            }
            is ConductUi.Following -> {
                // busy while the answer comes
                if (canAsk) {
                    TextButton(onAsk, enabled = !asking) { ActionText(stringResource(if (asking) R.string.conduct_asking else R.string.conduct_ask)) }
                }
                TextButton(onLeave) { ActionText(stringResource(R.string.conduct_leave)) }
            }
            is ConductUi.Left -> TextButton(onFollow) { ActionText(stringResource(R.string.conduct_follow)) }
        }
    }
    Surface(
        color = if (alert) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (alert) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        // on a phone two buttons don't fit next to the text: they go on a line of their own. The leader's "End" broke
        // letter by letter and the title vanished (user, 02/11); a follower's Ask and Leave left the missing song's
        // title two letters (two devices, 13/11). One button (Leave, Follow) stays beside the text, and on a phone
        // "See what's missing" is a tap on the text (with an arrow): as a button it pushed both onto a third line
        // and left the strip tall and half empty (user, 02/11)
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
            val small = maxWidth < NARROW
            val narrow = small && (ui is ConductUi.Conducting || canAsk)
            val lacksInText = small && hasLacks
            Column(Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Icon(if (alert) AppIcons.Warning else AppIcons.Group, null)
                    Column(
                        Modifier.weight(1f).then(
                            if (lacksInText) Modifier.clickable(onClickLabel = seeLacks) { showLacks = true } else Modifier,
                        ),
                    ) {
                        // each line is one line, and what gets cut is the name in it (a setlist, a device, a song):
                        // on a phone with bigger text the break's line wrapped and a long title pushed its own
                        // sentence off (two devices, 13/11)
                        CutLine(
                            when (ui) {
                                is ConductUi.Conducting -> cutLine(R.string.conduct_conducting, ui.set)
                                is ConductUi.Following -> cutLine(if (ui.gone) R.string.conduct_gone else R.string.conduct_following, ui.from)
                                is ConductUi.Left -> cutLine(R.string.conduct_left, ui.from)
                            },
                            MaterialTheme.typography.titleSmall, "conductTitle",
                        )
                        val rest = (ui as? ConductUi.Following)?.takeIf { it.breakSince != null }
                        val detail = when {
                            // the leader's break, for a follower on any screen: its time and what comes next (or that
                            // the next song isn't here), in place of the song. The time is never cut
                            rest?.breakSince != null -> cutLine(
                                R.string.conduct_break, stringResource(if (rest.missingHere) R.string.conduct_missing_here else R.string.stage_break_next, rest.song),
                                br.app.stagebook.ui.components.breakClock(rest.breakSince),
                            )
                            ui is ConductUi.Following && ui.missingHere -> cutLine(R.string.conduct_missing_here, ui.song)
                            ui is ConductUi.Following && ui.noChords -> cutLine(R.string.conduct_no_chords, ui.song)
                            nowMissing.isNotEmpty() -> cutLine(R.string.conduct_missing_now, nowMissing.joinToString(", "))
                            ui is ConductUi.Conducting && ui.noAnswer > 0 -> CutText(cut = pluralStringResource(R.plurals.conduct_no_answer, ui.noAnswer, ui.noAnswer))
                            lacksInText -> CutText(cut = seeLacks)
                            else -> null
                        }
                        detail?.let {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CutLine(it, MaterialTheme.typography.bodySmall, "conductLine", Modifier.weight(1f, fill = false))
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
    // the leader's list has actions (send what's missing); a follower only reads its own
    if (showLacks && ui is ConductUi.Conducting) {
        LacksDialog(ui.lackers, sending, onSend, onDismiss = { showLacks = false })
    } else if (showLacks && ui is ConductUi.Following && ui.lacksHere.isNotEmpty()) {
        InfoDialog(stringResource(R.string.conduct_lacks_here_title), ui.lacksHere.joinToString("\n"), onDismiss = { showLacks = false })
    }
}

/** A strip line in three pieces: only [cut] gives way when the line doesn't fit; [start] and [end] stay whole. */
@Immutable
internal data class CutText(val start: String = "", val cut: String, val end: String = "") {
    val whole: String get() = start + cut + end
}

/**
 * The sentence [id] with [name] in it, split around the name so that the name is what gets cut. [first], when the
 * sentence has two values, is the one before it and is never cut (the break's time).
 */
@Composable
private fun cutLine(id: Int, name: String, first: String? = null): CutText {
    val sentence = if (first == null) stringResource(id, CUT_HERE) else stringResource(id, first, CUT_HERE)
    // the space before the name goes with it: a text's trailing space isn't sure to be measured
    val start = sentence.substringBefore(CUT_HERE)
    val kept = start.trimEnd()
    return CutText(kept, start.substring(kept.length) + name, sentence.substringAfter(CUT_HERE, ""))
}

@Composable
private fun CutLine(text: CutText, style: androidx.compose.ui.text.TextStyle, tag: String, modifier: Modifier = Modifier) {
    // read and found as the one sentence it is
    Row(modifier.semantics { this.text = androidx.compose.ui.text.AnnotatedString(text.whole) }) {
        if (text.start.isNotEmpty()) Text(text.start, Modifier.clearAndSetSemantics { testTag = tag + "Start" }, style = style, maxLines = 1, softWrap = false)
        Text(text.cut, Modifier.weight(1f, fill = false).clearAndSetSemantics { testTag = tag + "Cut" }, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (text.end.isNotEmpty()) Text(text.end, Modifier.clearAndSetSemantics { testTag = tag + "End" }, style = style, maxLines = 1, softWrap = false)
    }
}

/** An action's text: whole, on one line, whatever is beside it. */
@Composable
private fun ActionText(text: String) = Text(text, maxLines = 1, softWrap = false)

private const val CUT_HERE = "\u2060"   // stands for the name while the sentence is split around it

private val sampleLacks = listOf(Conduct.Lack("Baixo", listOf("Evidências", "Asa Branca")))
internal val sampleLackers = listOf(
    ConductUi.Lacker("b", "Baixo", listOf("Evidências", "Asa Branca")),
    ConductUi.Lacker("g", "Guitarra", listOf("Evidências")),
)

@Composable
private fun ConductBarSamples() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        ConductBar(ConductUi.Conducting("Show de sábado", 1, emptyList(), sampleLacks), {}, {}, {}, {})
        ConductBar(ConductUi.Conducting("Show de sábado", 0, listOf("Baixo"), sampleLacks), {}, {}, {}, {})
        ConductBar(ConductUi.Following("Voz", "Garota de Ipanema", false, false, emptyList(), emptyList()), {}, {}, {}, {})
        ConductBar(ConductUi.Following("Voz", "Evidências", true, false, listOf("Baixo"), listOf("Evidências", "Asa Branca")), {}, {}, {}, {})
        ConductBar(ConductUi.Following("Voz", "", false, true, emptyList(), emptyList()), {}, {}, {}, {})
        // the song came from the band without chords
        ConductBar(ConductUi.Following("Voz", "Evidências", false, false, emptyList(), emptyList(), noChords = true), {}, {}, {}, {})
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
