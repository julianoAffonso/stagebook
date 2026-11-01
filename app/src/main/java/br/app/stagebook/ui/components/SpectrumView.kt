package br.app.stagebook.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import br.app.stagebook.domain.Spectrum
import kotlin.math.cos

/**
 * Live spectrum behind the EQ faders: a filled wave, with each band at the height of its level and at the horizontal
 * position of its frequency on the fader row (`Spectrum.x`). `levelsDb` is read only when drawing: it redraws without
 * recomposing the screen. With `active` false (also read when drawing) it draws nothing.
 */
@Composable
fun SpectrumView(
    levelsDb: () -> List<Float>,
    active: () -> Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    // the band positions, the paths and the stroke are made once per size; each frame (~15/s) only refills the paths
    // (it allocated two arrays, two paths and a stroke on every frame; audit 01/11)
    Spacer(
        modifier.clipToBounds().drawWithCache {
            val xs = FloatArray(Spectrum.BINS) { Spectrum.x(Spectrum.centers[it]) * size.width }
            val ys = FloatArray(Spectrum.BINS)
            val line = Path()
            val fill = Path()
            val stroke = Stroke(width = 2.dp.toPx())
            onDrawBehind {
                if (!active()) return@onDrawBehind
                val levels = levelsDb()
                if (levels.size != Spectrum.BINS) return@onDrawBehind
                for (i in 0 until Spectrum.BINS) ys[i] = size.height * (1f - Spectrum.fraction(levels[i]))
                // smooth curve: joins the midpoints between neighboring bands with quadratic curves
                line.reset()
                line.moveTo(xs[0], ys[0])
                for (i in 1 until Spectrum.BINS) {
                    line.quadraticTo(xs[i - 1], ys[i - 1], (xs[i - 1] + xs[i]) / 2, (ys[i - 1] + ys[i]) / 2)
                }
                line.lineTo(xs.last(), ys.last())
                fill.reset()
                fill.addPath(line)
                fill.lineTo(xs.last(), size.height)
                fill.lineTo(xs[0], size.height)
                fill.close()
                drawPath(fill, color.copy(alpha = FILL_ALPHA))
                drawPath(line, color.copy(alpha = LINE_ALPHA), style = stroke)
            }
        },
    )
}

private const val FILL_ALPHA = 0.22f
private const val LINE_ALPHA = 0.7f

private val sample = List(Spectrum.BINS) { b ->
    // music-like shape: strong lows, mids, falling highs
    -18f - b * 0.6f + 6f * cos(b / 5f).toFloat()
}

@LightPreview
@Composable
private fun SpectrumLightPreview() = PreviewSurface(dark = false) {
    SpectrumView({ sample }, active = { true }, Modifier.fillMaxWidth().height(160.dp))
}

@DarkPreview
@Composable
private fun SpectrumDarkPreview() = PreviewSurface(dark = true) {
    SpectrumView({ sample }, active = { true }, Modifier.fillMaxWidth().height(160.dp))
}
