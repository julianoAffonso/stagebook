package br.app.stagebook.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * The bar under a list of the drawer: what adds to the list at the start ([add], the + menu) and the checklist that
 * turns the boxes on at the end, lit while [selecting]. The songs' list and the setlists' use it (audit 22/11: two copies).
 */
@Composable
fun ListBottomBar(selecting: Boolean, selectLabel: String, onSelect: () -> Unit, modifier: Modifier = Modifier, add: @Composable () -> Unit) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        add()
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSelect) {
            Icon(AppIcons.Checklist, selectLabel, tint = if (selecting) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@LightPreview
@Composable
private fun ListBottomBarLightPreview() = PreviewSurface(dark = false) {
    ListBottomBar(selecting = false, selectLabel = "Selecionar", onSelect = {}) { IconButton({}) { Icon(AppIcons.Add, "Novo") } }
}

@DarkPreview
@Composable
private fun ListBottomBarDarkPreview() = PreviewSurface(dark = true) {
    ListBottomBar(selecting = true, selectLabel = "Selecionar", onSelect = {}) { IconButton({}) { Icon(AppIcons.Add, "Novo") } }
}
