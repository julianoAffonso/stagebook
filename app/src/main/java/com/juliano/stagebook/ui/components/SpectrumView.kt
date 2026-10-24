package com.juliano.stagebook.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.juliano.stagebook.domain.Spectrum
import kotlin.math.cos

/**
 * Espectro ao vivo atrás dos faders do EQ: uma onda preenchida, com cada faixa na altura do seu nível
 * e na posição horizontal da sua frequência na linha dos faders (`Spectrum.x`). `levelsDb` é lido só no
 * desenho: redesenha sem recompor a tela. Com `active` falso (lido também no desenho) não desenha nada.
 */
@Composable
fun SpectrumView(
    levelsDb: () -> List<Float>,
    active: () -> Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Canvas(modifier.clipToBounds()) {
        if (!active()) return@Canvas
        val levels = levelsDb()
        if (levels.size != Spectrum.BINS) return@Canvas
        val xs = FloatArray(Spectrum.BINS) { Spectrum.x(Spectrum.centers[it]) * size.width }
        val ys = FloatArray(Spectrum.BINS) { size.height * (1f - Spectrum.fraction(levels[it])) }
        // curva suave: liga os pontos médios entre faixas vizinhas com curvas de 2º grau
        val line = Path().apply {
            moveTo(xs[0], ys[0])
            for (i in 1 until Spectrum.BINS) {
                quadraticTo(xs[i - 1], ys[i - 1], (xs[i - 1] + xs[i]) / 2, (ys[i - 1] + ys[i]) / 2)
            }
            lineTo(xs.last(), ys.last())
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(xs.last(), size.height)
            lineTo(xs[0], size.height)
            close()
        }
        drawPath(fill, color.copy(alpha = FILL_ALPHA))
        drawPath(line, color.copy(alpha = LINE_ALPHA), style = Stroke(width = 2.dp.toPx()))
    }
}

private const val FILL_ALPHA = 0.22f
private const val LINE_ALPHA = 0.7f

private val sample = List(Spectrum.BINS) { b ->
    // forma de música: graves fortes, médios, agudos caindo
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
