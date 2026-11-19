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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import br.app.stagebook.R
import br.app.stagebook.ui.components.AppIcons
import br.app.stagebook.ui.home.HomeTab
import br.app.stagebook.ui.theme.Sizes
import br.app.stagebook.ui.theme.Spacing

/**
 * The library drawer's frame (user, 15/11, as SongbookPro's): the tab's list and the three tabs at the bottom. No row of
 * its own on top (user, 19/11: the logo and the name took the list's room; About is in Settings): the pin, when given,
 * and the number of waiting requests go at the end of the tab's title, through [LocalDrawerTitleActions]. Stateless: the
 * tab and what it shows come from the caller.
 */
@Composable
fun DrawerFrame(
    tab: HomeTab,
    onTabChange: (HomeTab) -> Unit,
    modifier: Modifier = Modifier,
    badges: Map<HomeTab, Int> = emptyMap(),
    pinned: Boolean = false,
    onPin: (() -> Unit)? = null,
    content: @Composable (Modifier) -> Unit,
) {
    val waiting = LocalWaitingRequests.current
    val attend = LocalAttendRequest.current
    val titleActions: @Composable () -> Unit = {
        // the pinned menu is always open, so a request to join has no "opening" to wait for: its number is here, and a tap
        // brings the window up (the stage's handle carries it too when the menu is not pinned)
        val waitingLabel = androidx.compose.ui.res.pluralStringResource(R.plurals.drawer_requests, waiting, waiting)
        if (waiting > 0) IconButton(onClick = attend, modifier = Modifier.semantics { contentDescription = waitingLabel }) {
            BadgedBox(badge = { Badge { Text(waiting.toString()) } }) { Icon(AppIcons.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        }
        if (onPin != null) IconButton(onClick = onPin) {
            Icon(
                AppIcons.Pin, stringResource(if (pinned) R.string.drawer_unpin else R.string.drawer_pin),
                tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Column(modifier.fillMaxSize()) {
        androidx.compose.runtime.CompositionLocalProvider(LocalDrawerTitleActions provides titleActions) {
            Box(Modifier.weight(1f)) { content(Modifier.fillMaxSize()) }
        }
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
    // icon and name on one line, the icon small on the left (user, 19/11): the bar is a finger tall, not two lines
    Row(
        modifier
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick)
            .heightIn(min = Sizes.touch)
            .padding(horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.CenterHorizontally),
    ) {
        Icon(tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(Sizes.tabIcon))
        Text(stringResource(tab.label), style = MaterialTheme.typography.labelLarge, color = tint, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        // the number of band news after the name (like new email): on the small icon it covered the drawing
        if (badge > 0) Badge { Text(badge.toString()) }
    }
}
