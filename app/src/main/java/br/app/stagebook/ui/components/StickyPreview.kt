package br.app.stagebook.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.zIndex
import br.app.stagebook.ui.theme.Spacing

/**
 * A preview over the cards it previews (user, 22/11): it stays at the top while those cards scroll under it, and once
 * they end it goes up with the rest of the page. Placed directly in the column that [scroll] scrolls. The preview's
 * position is read when placing, never when composing: scrolling moves it without composing anything again.
 */
@Composable
fun StickyPreview(scroll: ScrollState, preview: @Composable () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    // where this group starts in the scrolled column (its own position there, not on screen)
    var top by remember { mutableFloatStateOf(0f) }
    Layout(
        content = {
            Box(Modifier.zIndex(1f).background(MaterialTheme.colorScheme.background).padding(bottom = Spacing.m)) { preview() }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m), content = content)
        },
        modifier = modifier.fillMaxWidth().onPlaced { top = it.positionInParent().y },
    ) { measurables, constraints ->
        val loose = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val head = measurables[0].measure(loose)
        val body = measurables[1].measure(loose)
        val height = head.height + body.height
        layout(constraints.maxWidth, height) {
            body.placeRelative(0, head.height)
            // pinned while the group's cards pass under it, and never past the group's end
            head.placeRelative(0, stickyOffset(scroll.value, top.toInt(), body.height))
        }
    }
}

/** How far down the group the preview sits when the page is scrolled [scrolled] and the group starts at [top]. */
fun stickyOffset(scrolled: Int, top: Int, bodyHeight: Int): Int = (scrolled - top).coerceIn(0, bodyHeight.coerceAtLeast(0))

@LightPreview
@Composable
private fun StickyPreviewLightPreview() = PreviewSurface(dark = false) {
    val scroll = rememberScrollState()
    Column(Modifier.verticalScroll(scroll)) {
        StickyPreview(scroll, { Text("Prévia") }) { repeat(3) { Text("Cartão $it") } }
    }
}

@DarkPreview
@Composable
private fun StickyPreviewDarkPreview() = PreviewSurface(dark = true) {
    val scroll = rememberScrollState()
    Column(Modifier.verticalScroll(scroll)) {
        StickyPreview(scroll, { Text("Prévia") }) { repeat(3) { Text("Cartão $it") } }
    }
}
