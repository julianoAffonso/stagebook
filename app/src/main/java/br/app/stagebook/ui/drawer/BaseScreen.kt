package br.app.stagebook.ui.drawer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import br.app.stagebook.R
import br.app.stagebook.ui.components.ActionButton
import br.app.stagebook.ui.components.AppIcons
import br.app.stagebook.ui.components.EmptyState
import br.app.stagebook.ui.components.ScreenInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import br.app.stagebook.ui.theme.Spacing

/**
 * The screen behind the library drawer when there is no stage to show: the first use, or after everything was deleted.
 * The drawer opens by itself, with Songs, so the first thing to do is already on screen.
 */
@Composable
fun BaseScreen(modifier: Modifier = Modifier) {
    val drawer = LocalDrawer.current
    LaunchedEffect(Unit) { drawer?.takeIf { !it.pinned }?.open?.invoke() }
    Box(modifier.fillMaxSize().windowInsetsPadding(ScreenInsets).padding(Spacing.l), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
            EmptyState(AppIcons.MusicNote, stringResource(R.string.base_title), stringResource(R.string.base_text))
            if (drawer != null && !drawer.pinned) ActionButton(stringResource(R.string.drawer_open), drawer.open, icon = AppIcons.Menu)
        }
    }
}
