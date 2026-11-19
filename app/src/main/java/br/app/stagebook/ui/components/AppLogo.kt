package br.app.stagebook.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import br.app.stagebook.R
import br.app.stagebook.ui.theme.Sizes

/** Tag of [AppLogo], for tests. */
const val LOGO_TAG = "app-logo"

/**
 * The app's logo as the home screen shows it: its pages on black, in a rounded square. It left the top of the menu (user,
 * 19/11) and shows where it doesn't take room from playing: Settings → About and the empty first-use screens.
 * [description] for a logo that stands alone; null where the name is written beside it.
 */
@Composable
fun AppLogo(modifier: Modifier = Modifier, size: Dp = Sizes.logo, description: String? = null) {
    Image(
        painterResource(R.mipmap.ic_launcher_foreground), contentDescription = description, contentScale = ContentScale.Crop,
        // the foreground has the adaptive icon's safe margin around the drawing: scaled up so the pages fill the square
        modifier = modifier.testTag(LOGO_TAG).size(size).clip(MaterialTheme.shapes.large).graphicsLayer { scaleX = 1.4f; scaleY = 1.4f },
    )
}

@LightPreview
@Composable
private fun AppLogoLightPreview() = PreviewSurface(dark = false) { AppLogo() }

@DarkPreview
@Composable
private fun AppLogoDarkPreview() = PreviewSurface(dark = true) { AppLogo() }
