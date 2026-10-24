package com.juliano.stagebook.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.juliano.stagebook.domain.Meters
import com.juliano.stagebook.ui.theme.Sizes
import com.juliano.stagebook.ui.theme.Spacing

/**
 * Barra de pico vertical (dBFS, de -48 a +3), com a marca do maior pico recente. `levelDb` e `holdDb`
 * são lidos só no desenho: a barra redesenha a cada leitura sem recompor a tela. Acima de 0 dBFS, a
 * parte de cima fica na cor de erro (a saída recebe cortado). [vertical] = false: barra deitada, crescendo para a
 * direita (ao lado do fader deitado do celular).
 */
@Composable
fun PeakMeter(
    levelDb: () -> Float,
    holdDb: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    height: Dp = Sizes.fader,
    vertical: Boolean = true,
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val clip = MaterialTheme.colorScheme.error
    val zero = MaterialTheme.colorScheme.outline
    Canvas(if (vertical) modifier.width(Sizes.meter).height(height) else modifier.fillMaxWidth().height(Sizes.meter / 2)) {
        val radius = CornerRadius(2.dp.toPx())
        drawRoundRect(track, Offset.Zero, size, radius)
        if (!vertical) {
            fun x(db: Float) = size.width * Meters.fraction(db)
            val x0 = x(0f)
            val xLevel = x(levelDb())
            if (xLevel > 0f) {
                drawRoundRect(color, Offset.Zero, Size(minOf(xLevel, x0), size.height), radius)
                if (xLevel > x0) drawRect(clip, Offset(x0, 0f), Size(xLevel - x0, size.height))
            }
            drawLine(zero, Offset(x0, 0f), Offset(x0, size.height), 1.dp.toPx())
            val hold = holdDb()
            if (hold > Meters.FLOOR_DB) drawLine(if (hold > 0f) clip else color, Offset(x(hold), 0f), Offset(x(hold), size.height), 2.dp.toPx())
            return@Canvas
        }
        fun y(db: Float) = size.height * (1f - Meters.fraction(db))
        val y0 = y(0f)
        val yLevel = y(levelDb())
        if (yLevel < size.height) {
            val top = maxOf(yLevel, y0)
            drawRoundRect(color, Offset(0f, top), Size(size.width, size.height - top), radius)
            if (yLevel < y0) drawRect(clip, Offset(0f, yLevel), Size(size.width, y0 - yLevel))
        }
        drawLine(zero, Offset(0f, y0), Offset(size.width, y0), 1.dp.toPx())
        val hold = holdDb()
        if (hold > Meters.FLOOR_DB) {
            val yh = y(hold)
            drawLine(if (hold > 0f) clip else color, Offset(0f, yh), Offset(size.width, yh), 2.dp.toPx())
        }
    }
}

@Composable
private fun PeakMeterSamples() {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        PeakMeter({ -12f }, { -6f })
        PeakMeter({ -30f }, { -20f }, color = MaterialTheme.colorScheme.tertiary)
        PeakMeter({ 1.5f }, { 2f })
        PeakMeter({ Meters.FLOOR_DB }, { Meters.FLOOR_DB })
    }
}

@LightPreview
@Composable
private fun PeakMeterLightPreview() = PreviewSurface(dark = false) { PeakMeterSamples() }

@DarkPreview
@Composable
private fun PeakMeterDarkPreview() = PreviewSurface(dark = true) { PeakMeterSamples() }
