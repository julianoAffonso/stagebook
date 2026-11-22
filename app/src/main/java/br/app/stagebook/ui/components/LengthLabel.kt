package br.app.stagebook.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import br.app.stagebook.R

/** A set's or a show's length, rounded to the minute: "42 min", "1 h 12 min" (user, 22/11). */
@Composable
fun lengthLabel(seconds: Int): String {
    val minutes = (seconds + 30) / 60
    return if (minutes < 60) stringResource(R.string.length_minutes, minutes.coerceAtLeast(1))
    else stringResource(R.string.length_hours_minutes, minutes / 60, minutes % 60)
}
