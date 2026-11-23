package br.app.stagebook.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.app.stagebook.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** The letter that starts a group of an A to Z list, as one more row of it. */
@Composable
fun LetterHeader(letter: String, modifier: Modifier = Modifier) {
    Text(
        letter,
        modifier.fillMaxWidth().padding(start = Spacing.m, top = Spacing.xs).testTag(LETTER_HEADER_TAG + letter).semantics { heading() },
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
    )
}

/**
 * The letters of an A to Z list in a narrow column, for its end edge. A tap on a letter, or a finger sliding along
 * them, calls [onLetter] with the letter's position; [onHeld] says while a finger is on them. [shown] comes from the
 * caller ([rememberLetterIndexShown]): hidden it fades out, draws nothing and takes no touch. [onScreen] gives the
 * positions of the letters whose songs are in view, marked in the column so the eye finds where the list is (user,
 * 19/11); it is read only while the index shows, so scrolling with it hidden recomposes nothing here.
 */
@Composable
fun LetterIndex(letters: List<String>, shown: Boolean, onLetter: (Int) -> Unit, onHeld: (Boolean) -> Unit, modifier: Modifier = Modifier, onScreen: () -> IntRange = { IntRange.EMPTY }) {
    val alpha = animateFloatAsState(if (shown) 1f else 0f, tween(INDEX_FADE_MS), label = "letter index")
    // only the end of the fade recomposes; each frame of it is read when drawing
    val faded by remember(alpha) { derivedStateOf { alpha.value == 0f } }
    if (!shown && faded) return
    val pick by rememberUpdatedState(onLetter)
    val held by rememberUpdatedState(onHeld)
    val touch = if (!shown) Modifier else Modifier.pointerInput(letters.size) {
        awaitEachGesture {
            var last = -1
            fun at(y: Float) {
                val i = (y / size.height * letters.size).toInt().coerceIn(0, letters.size - 1)
                if (i != last) { last = i; pick(i) }   // not once more for every movement inside the same letter
            }
            val down = awaitFirstDown()
            held(true)
            try {
                at(down.position.y)
                down.consume()
                do {
                    val event = awaitPointerEvent()
                    event.changes.forEach { if (it.pressed) { at(it.position.y); it.consume() } }
                } while (event.changes.any { it.pressed })
            } finally {
                held(false)
            }
        }
    }
    val here = onScreen()
    // with little height (the keyboard open, a tablet lying down) every letter no longer fits: one in every few is drawn,
    // each standing for the ones after it, instead of all of them squeezed over each other (user, 23/11). The column
    // keeps its height and a finger along it still reaches every letter
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        val every = if (constraints.hasBoundedHeight && letters.isNotEmpty()) kotlin.math.ceil(INDEX_LETTER_MIN.value * letters.size / maxHeight.value).toInt().coerceAtLeast(1) else 1
        Column(
            Modifier.width(INDEX_WIDTH).graphicsLayer { this.alpha = alpha.value }.testTag(LETTER_INDEX_TAG).then(touch),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            for (i in letters.indices step every) {
                val last = minOf(i + every - 1, letters.lastIndex)
                val count = last - i + 1
                val inView = !here.isEmpty() && here.first <= last && here.last >= i
                // the letters in view form one filled band: rounded only at its two ends
                val before = i - 1 in here
                val after = last + 1 in here
                val shape = androidx.compose.foundation.shape.RoundedCornerShape(
                    topStart = if (before) 0.dp else INDEX_CORNER, topEnd = if (before) 0.dp else INDEX_CORNER,
                    bottomStart = if (after) 0.dp else INDEX_CORNER, bottomEnd = if (after) 0.dp else INDEX_CORNER,
                )
                val letter = letters[i]
                Box(
                    Modifier.weight(count.toFloat(), fill = false).height(INDEX_LETTER * count).fillMaxWidth()
                        .then(if (inView) Modifier.background(MaterialTheme.colorScheme.primary, shape) else Modifier)
                        .testTag(LETTER_INDEX_TAG + "_" + letter).semantics { onClick { pick(i); true }; if (inView) selected = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        letter,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (inView) androidx.compose.ui.text.font.FontWeight.Bold else null,
                        color = if (inView) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                        maxLines = 1, softWrap = false,
                    )
                }
            }
        }
    }
}

/**
 * Whether the letter index of [list] shows: while a finger scrolls the list (and the fling after it) or is [held] on
 * the index, and a moment after. A scroll the app makes by itself (back to the top on a new filter) doesn't show it.
 */
@Composable
fun rememberLetterIndexShown(list: LazyListState, held: Boolean): Boolean {
    var shown by remember(list) { mutableStateOf(false) }
    val dragged by list.interactionSource.collectIsDraggedAsState()
    val heldNow by rememberUpdatedState(held)
    LaunchedEffect(list) {
        var byHand = false
        snapshotFlow { Triple(list.isScrollInProgress, dragged, heldNow) }.collectLatest { (scrolling, dragging, holding) ->
            if (dragging) byHand = true else if (!scrolling) byHand = false
            if ((scrolling && byHand) || holding) shown = true
            else { delay(INDEX_LINGER_MS); shown = false }
        }
    }
    return shown
}

const val LETTER_INDEX_TAG = "letter_index"
const val LETTER_HEADER_TAG = "letter_header_"

/** Wider than its letters: a finger finds it. The caller lets it hang over the list's end margin. */
val INDEX_WIDTH = Spacing.l
private val INDEX_LETTER = 24.dp   // larger letters, easy to read while the list moves (user, 19/11)
private val INDEX_LETTER_MIN = 20.dp   // under this a letter runs into the next one
private val INDEX_CORNER = Spacing.s
private const val INDEX_FADE_MS = 200
internal const val INDEX_LINGER_MS = 3_000L   // time to read where the list stopped before the letters go (user, 19/11)

/**
 * The positions of the groups whose rows (or header) are in view, from the first and last items on screen, for a list
 * where each group's header sits after the rows and headers before it ([groupFirsts]: each group's first row).
 */
fun lettersOnScreen(groupFirsts: List<Int>, firstItem: Int, lastItem: Int): IntRange {
    if (groupFirsts.isEmpty() || lastItem < firstItem) return IntRange.EMPTY
    fun groupOf(item: Int): Int {
        var lo = 0; var hi = groupFirsts.lastIndex
        while (lo < hi) { val mid = (lo + hi + 1) / 2; if (groupFirsts[mid] + mid <= item) lo = mid else hi = mid - 1 }
        return lo
    }
    return groupOf(firstItem)..groupOf(lastItem)
}

@Composable
private fun LetterIndexSample() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        LetterHeader("A")
        Box {
            SongListItem("Canção do Vento", "Banda Fictícia", {}, trailing = { SongFacts("Em", 120, 2, true) })
            LetterIndex(listOf("#", "A", "C", "M"), shown = true, onLetter = {}, onHeld = {}, modifier = Modifier.align(Alignment.CenterEnd), onScreen = { 1..2 })
        }
    }
}

@LightPreview
@Composable
private fun LetterIndexLightPreview() = PreviewSurface(dark = false) { LetterIndexSample() }

@DarkPreview
@Composable
private fun LetterIndexDarkPreview() = PreviewSurface(dark = true) { LetterIndexSample() }
