package com.juliano.stagebook.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.juliano.stagebook.ui.theme.Sizes
import com.juliano.stagebook.ui.theme.Spacing

/**
 * Fader vertical de mesa (o mesmo do the previous app): arrastar ou tocar no ponto; a faixa acesa vai de [origin]
 * (0 no equalizador, o fim de baixo no volume) até o valor; a marca fina fica no 0 dB. `onValueChange` recebe o valor contínuo; quem chama arredonda o passo.
 * Com `enabled = false` (travado), não responde ao toque e fica apagado. `height` é a altura mínima:
 * com `Modifier.fillMaxHeight()` o fader cresce até o espaço que tiver.
 */
@Composable
fun VerticalFader(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = -12f..12f,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    height: Dp = Sizes.fader,
    origin: Float = 0f,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val centerColor = MaterialTheme.colorScheme.outline
    val alpha = if (enabled) 1f else DISABLED_ALPHA
    val thumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
    val fillColor = activeColor.copy(alpha = alpha)
    val latest by rememberUpdatedState(onValueChange)
    val span = valueRange.endInclusive - valueRange.start
    fun valueAt(y: Float, height: Float) =
        (valueRange.endInclusive - (y / height).coerceIn(0f, 1f) * span)

    Canvas(
        modifier
            .width(Sizes.touch)
            .height(height)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value, valueRange)
                if (enabled) setProgress { v -> latest(v.coerceIn(valueRange)); true }
            }
            .pointerInput(valueRange, enabled) {
                if (enabled) detectTapGestures { latest(valueAt(it.y, size.height.toFloat())) }
            }
            .pointerInput(valueRange, enabled) {
                if (enabled) {
                    detectVerticalDragGestures { change, _ ->
                        change.consume()
                        latest(valueAt(change.position.y, size.height.toFloat()))
                    }
                }
            },
    ) {
        val trackW = 6.dp.toPx()
        val x = size.width / 2
        val yOf = { v: Float -> (valueRange.endInclusive - v) / span * size.height }
        val radius = CornerRadius(trackW / 2)
        drawRoundRect(trackColor, Offset(x - trackW / 2, 0f), Size(trackW, size.height), radius)
        val yZero = yOf(0f.coerceIn(valueRange))
        drawLine(centerColor, Offset(x - 12.dp.toPx(), yZero), Offset(x + 12.dp.toPx(), yZero), 2.dp.toPx())
        val yVal = yOf(value.coerceIn(valueRange))
        val yOrigin = yOf(origin.coerceIn(valueRange))
        val top = minOf(yVal, yOrigin)
        drawRoundRect(fillColor, Offset(x - trackW / 2, top), Size(trackW, kotlin.math.abs(yVal - yOrigin)), radius)
        val thumbH = 14.dp.toPx()
        val thumbW = 36.dp.toPx()
        drawRoundRect(thumbColor, Offset(x - thumbW / 2, yVal - thumbH / 2), Size(thumbW, thumbH), CornerRadius(4.dp.toPx()))
        drawLine(fillColor, Offset(x - thumbW / 2 + 4.dp.toPx(), yVal), Offset(x + thumbW / 2 - 4.dp.toPx(), yVal), 2.dp.toPx())
    }
}

@Composable
private fun FaderSamples() {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        VerticalFader(6f, {})
        VerticalFader(0f, {})
        VerticalFader(-9.5f, {}, enabled = false)
        VerticalFader(-12f, {}, valueRange = -60f..6f, origin = -60f)
    }
}

private const val DISABLED_ALPHA = 0.38f

@LightPreview
@Composable
private fun VerticalFaderLightPreview() = PreviewSurface(dark = false) { FaderSamples() }

@DarkPreview
@Composable
private fun VerticalFaderDarkPreview() = PreviewSurface(dark = true) { FaderSamples() }
