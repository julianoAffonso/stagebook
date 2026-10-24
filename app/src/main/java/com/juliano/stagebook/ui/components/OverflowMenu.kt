package com.juliano.stagebook.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.juliano.stagebook.R

/** Uma ação do menu ⋮. `danger`: em vermelho (apagar). */
data class MenuAction(val label: String, val icon: ImageVector, val onClick: () -> Unit, val danger: Boolean = false)

/**
 * Menu ⋮ da barra de cima, como nos apps do Google (Fotos, Gmail): as ações secundárias ficam aqui e
 * a principal continua à vista, para o título não ser cortado no celular.
 */
@Composable
fun OverflowMenu(actions: List<MenuAction>, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { open = true }) { Icon(AppIcons.MoreVert, stringResource(R.string.action_more)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            actions.forEach { a ->
                val color = if (a.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                DropdownMenuItem(
                    text = { Text(a.label, color = color) },
                    leadingIcon = { Icon(a.icon, contentDescription = null, tint = color) },
                    onClick = { open = false; a.onClick() },
                )
            }
        }
    }
}

private val previewActions = listOf(
    MenuAction("Renomear", AppIcons.Edit, {}),
    MenuAction("Excluir setlist", AppIcons.Delete, {}, danger = true),
)

@LightPreview
@Composable
private fun OverflowMenuLightPreview() = PreviewSurface(dark = false) { OverflowMenu(previewActions) }

@DarkPreview
@Composable
private fun OverflowMenuDarkPreview() = PreviewSurface(dark = true) { OverflowMenu(previewActions) }
