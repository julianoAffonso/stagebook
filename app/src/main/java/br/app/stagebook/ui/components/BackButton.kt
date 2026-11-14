package br.app.stagebook.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.app.stagebook.R
import br.app.stagebook.ui.theme.Sizes

/** Room the back arrow takes in a header row: the drawn arrow and a little after it. */
val BackButtonWidth = 28.dp

/** An icon button's own space on each side of its 24 dp icon: header icons move out by it to sit on the margin. */
val HeaderIconInset = (Sizes.touch - 24.dp) / 2

/**
 * The back arrow of a header (Google Photos pattern): the drawn arrow lines up with the screen's margin and the title
 * comes right after it, while the touch area keeps 48 dp, reaching into the margin (user, 03/11: the arrow inside a whole
 * button after the margin pushed the title far right on every screen).
 */
@Composable
fun BackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    closes: Boolean = false,   // an ✕ in the arrow's place: it leaves a mode of the screen, not the screen
) {
    Box(modifier.width(BackButtonWidth)) {
        IconButton(onClick = onClick, modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true).offset(x = -HeaderIconInset)) {
            if (closes) Icon(AppIcons.Close, contentDescription = stringResource(R.string.action_close), tint = tint)
            else Icon(AppIcons.Back, contentDescription = stringResource(R.string.action_back), tint = tint)
        }
    }
}

@LightPreview
@Composable
private fun BackButtonLightPreview() = PreviewSurface(dark = false) { ScreenHeader("Show de sábado", onBack = {}) }

@DarkPreview
@Composable
private fun BackButtonDarkPreview() = PreviewSurface(dark = true) { ScreenHeader("Show de sábado", onBack = {}) }
