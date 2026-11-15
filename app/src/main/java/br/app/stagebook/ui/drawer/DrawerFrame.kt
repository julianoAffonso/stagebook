package br.app.stagebook.ui.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.app.stagebook.R
import br.app.stagebook.ui.components.AppIcons
import br.app.stagebook.ui.home.HomeTab
import br.app.stagebook.ui.theme.Sizes
import br.app.stagebook.ui.theme.Spacing

/**
 * The library drawer's frame (user, 15/11, as SongbookPro's): the app's logo and name on top (a tap opens About; the pin,
 * when given, keeps the drawer open beside the stage), the tab's list in the middle and the three tabs at the bottom, each
 * with its icon and name. Stateless: the tab and what it shows come from the caller.
 */
@Composable
fun DrawerFrame(
    tab: HomeTab,
    onTabChange: (HomeTab) -> Unit,
    modifier: Modifier = Modifier,
    badges: Map<HomeTab, Int> = emptyMap(),
    onAbout: () -> Unit = {},
    pinned: Boolean = false,
    onPin: (() -> Unit)? = null,
    content: @Composable (Modifier) -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = Spacing.m, end = Spacing.s, top = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            // the launcher icon as the home screen shows it: its pages on black, in a rounded square; tapping logo and name opens About
            Row(
                Modifier.weight(1f).clip(MaterialTheme.shapes.medium)
                    .clickable(onClickLabel = stringResource(R.string.settings_about), role = Role.Button, onClick = onAbout)
                    .padding(vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                androidx.compose.foundation.Image(
                    painterResource(R.mipmap.ic_launcher_foreground), contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(Sizes.drawerLogo).clip(MaterialTheme.shapes.small).graphicsLayer { scaleX = 1.4f; scaleY = 1.4f },
                )
                // the logo and the name, beside each other: no version under it (user, 15/11; it is in Settings → About)
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
            }
            if (onPin != null) IconButton(onClick = onPin) {
                Icon(
                    AppIcons.Pin, stringResource(if (pinned) R.string.drawer_unpin else R.string.drawer_pin),
                    tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(Modifier.weight(1f)) { content(Modifier.fillMaxSize()) }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            HomeTab.entries.forEachIndexed { i, t ->
                if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
                DrawerTabButton(t, selected = tab == t, badge = badges[t] ?: 0, onClick = { onTabChange(t) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DrawerTabButton(tab: HomeTab, selected: Boolean, badge: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Column(
        modifier
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(vertical = Spacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // the number of band news on the tab icon (like new email)
        if (badge > 0) BadgedBox(badge = { Badge { Text(badge.toString()) } }) { Icon(tab.icon, contentDescription = null, tint = tint) }
        else Icon(tab.icon, contentDescription = null, tint = tint)
        Text(stringResource(tab.label), style = MaterialTheme.typography.labelMedium, color = tint, maxLines = 1)
    }
}
