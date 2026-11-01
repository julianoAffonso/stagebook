package br.app.stagebook.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import br.app.stagebook.R
import br.app.stagebook.ui.theme.Spacing

/** An action on the selection: its name (tooltip and accessibility) and icon. */
class SelectionAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/**
 * The top bar while selecting, in the shape of Google's apps (Gmail, Files, Photos): ✕, how many, the actions as
 * icons (holding one shows its name) and "Select all" in the ⋮. The same on every list (user, 01/11).
 */
@Composable
fun SelectionTopBar(
    count: Int,
    onClose: () -> Unit,
    actions: List<SelectionAction>,
    onSelectAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        IconButton(onClick = onClose) { Icon(AppIcons.Close, stringResource(R.string.songs_select_cancel)) }
        Text(count.toString(), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).padding(start = Spacing.s))
        actions.forEach { TipIcon(it.label, it.icon, it.onClick) }
        OverflowMenu(listOf(MenuAction(stringResource(R.string.songs_select_all), AppIcons.Check, onSelectAll)))
    }
}

/** An icon button whose name shows when held (Material tooltip), like the action icons in Google's apps. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TipIcon(label: String, icon: ImageVector, onClick: () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick) { Icon(icon, contentDescription = label) }
    }
}

@LightPreview
@Composable
private fun SelectionTopBarLightPreview() = PreviewSurface(dark = false) {
    SelectionTopBar(2, {}, listOf(SelectionAction("Compartilhar", AppIcons.Share) {}, SelectionAction("Apagar", AppIcons.Delete) {}), {})
}

@DarkPreview
@Composable
private fun SelectionTopBarDarkPreview() = PreviewSurface(dark = true) {
    SelectionTopBar(2, {}, listOf(SelectionAction("Compartilhar", AppIcons.Share) {}, SelectionAction("Apagar", AppIcons.Delete) {}), {})
}
