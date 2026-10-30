package br.app.stagebook.ui.home

import br.app.stagebook.ui.settings.SettingsSection
import br.app.stagebook.ui.components.LocalAppMessage
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.foundation.layout.Column
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import br.app.stagebook.ui.share.InboxViewModel
import br.app.stagebook.ui.share.InboxDialog
import br.app.stagebook.ui.share.InboxBanner
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailItemDefaults
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import br.app.stagebook.R
import br.app.stagebook.ui.components.AppIcons
import br.app.stagebook.ui.components.ScreenInsets
import br.app.stagebook.ui.setlists.SetlistsRoute
import br.app.stagebook.ui.settings.SettingsRoute
import br.app.stagebook.ui.songs.SongsRoute
import br.app.stagebook.ui.theme.Sizes
import br.app.stagebook.ui.theme.Spacing
import kotlinx.coroutines.launch

enum class HomeTab(@StringRes val label: Int, val icon: ImageVector) {
    SONGS(R.string.tab_songs, AppIcons.MusicNote),
    SETLISTS(R.string.tab_setlists, AppIcons.QueueMusic),
    SETTINGS(R.string.tab_settings, AppIcons.Tune),
}

/** Songs, Setlists and Settings tabs: bottom bar on phones, wide rail on tablets (Material 3 Expressive). */
@Composable
fun HomeScreen(
    onOpenSong: (String) -> Unit,
    onEditSong: (String?) -> Unit,
    onImport: (String) -> Unit,
    onOpenSetlist: (String) -> Unit,
    onPlaySetlist: (String) -> Unit,
    onScanSetlist: () -> Unit = {},
    onReceiveCode: (String) -> Unit = {},
    onOpenReceived: (String) -> Unit = {},
    inboxVm: InboxViewModel = hiltViewModel(),
    homeVm: HomeViewModel = hiltViewModel(),
) {
    var tab by rememberSaveable { mutableStateOf(HomeTab.SONGS) }
    // what the band sent: notice at the top of the tabs (the newest not dismissed yet), number on the icon and the list
    val inbox by inboxVm.items.collectAsStateWithLifecycle()
    val hasBand by inboxVm.hasBand.collectAsStateWithLifecycle()
    val message = LocalAppMessage.current
    val syncing = androidx.compose.ui.res.stringResource(R.string.import_syncing)
    val canSync by homeVm.canSync.collectAsStateWithLifecycle()
    val onSync: (() -> Unit)? = if (hasBand && canSync) {
        {
            inboxVm.syncNow()
            message.show(syncing)
        }
    } else null
    val banner = inbox.lastOrNull { !it.seen }
    val essential by homeVm.essential.collectAsStateWithLifecycle()
    val alert by homeVm.alert.collectAsStateWithLifecycle()
    var listOpen by rememberSaveable { mutableStateOf(false) }
    val badges = mapOf(
        HomeTab.SONGS to inbox.count { it.setName.isEmpty() },
        HomeTab.SETLISTS to inbox.count { it.setName.isNotEmpty() },
    )
    // back on a tab that isn't the first goes to Songs (instead of closing the app)
    BackHandler(enabled = tab != HomeTab.SONGS) { tab = HomeTab.SONGS }

    // each tab keeps its own state (search, list position) when switching; no fade, which drew both full screens at
    // once on the Fire
    val tabs = rememberSaveableStateHolder()
    // Settings always opens on the starting list (not the last section seen): each tap on the icon is a new visit
    var settingsVisit by rememberSaveable { mutableIntStateOf(0) }
    var settingsStart by rememberSaveable { mutableStateOf<SettingsSection?>(null) }   // link from the alert: opens right on the section
    val onTab: (HomeTab) -> Unit = { t ->
        if (t == HomeTab.SETTINGS) {
            tabs.removeState("${HomeTab.SETTINGS}$settingsVisit")
            settingsVisit++
            settingsStart = null
        }
        tab = t
    }
    val openEssential = { onTab(HomeTab.SETTINGS); settingsStart = SettingsSection.ESSENTIAL }
    alert?.let { look -> EssentialAlert(look, onDismiss = { homeVm.alertSeen(false) }, onNeverAgain = homeVm::alertSeen, onOpen = { homeVm.alertSeen(false); openEssential() }) }
    HomeScaffold(tab = tab, onTabChange = onTab, badges = badges) { m ->
        Column(m) {
            // essential mode: a quiet reminder that something is off; tapping opens Settings (the section is the first
            // one)
            if (tab != HomeTab.SETTINGS && essential) {
                EssentialBanner(openEssential, Modifier.padding(horizontal = Spacing.m).padding(top = Spacing.s))
            }
            if (tab != HomeTab.SETTINGS && banner != null) {
                InboxBanner(
                    banner, onOpen = { onOpenReceived(banner.id) }, onLater = { inboxVm.later(banner.id) },
                    Modifier.padding(horizontal = Spacing.m).padding(top = Spacing.s),
                )
            }
            Box(Modifier.weight(1f)) {
                tabs.SaveableStateProvider(if (tab == HomeTab.SETTINGS) "${HomeTab.SETTINGS}$settingsVisit" else tab.name) {
                    when (tab) {
                        HomeTab.SONGS -> SongsRoute(
                            onOpenSong, onEditSong, onImport, onScan = onScanSetlist, onReceiveCode = onReceiveCode,
                            received = inbox.size, onReceived = { listOpen = true }, onSync = onSync,
                        )
                        HomeTab.SETLISTS -> SetlistsRoute(onOpenSetlist, onPlaySetlist, onScanSetlist, onReceiveCode, received = inbox.size, onReceived = { listOpen = true }, onSync = onSync)
                        HomeTab.SETTINGS -> SettingsRoute(settingsStart)
                    }
                }
            }
        }
    }
    if (listOpen && inbox.isNotEmpty()) {
        InboxDialog(inbox, onOpen = { listOpen = false; onOpenReceived(it) }, onDiscard = inboxVm::discard, onDismiss = { listOpen = false })
    }
}

/**
 * Tab frame, stateless, in Google's current pattern (Material 3 Expressive): on tablets the wide rail, which the menu
 * button opens to show the names; on phones the bottom bar. The content gets the Modifier with the remaining space.
 */
@Composable
fun HomeScaffold(tab: HomeTab, onTabChange: (HomeTab) -> Unit, badges: Map<HomeTab, Int> = emptyMap(), content: @Composable (Modifier) -> Unit) {
    // number of band news on the tab icon (like new email)
    val badged: @Composable (HomeTab) -> Unit = { t ->
        val n = badges[t] ?: 0
        if (n > 0) BadgedBox(badge = { Badge { Text(n.toString()) } }) { Icon(t.icon, contentDescription = null) }
        else Icon(t.icon, contentDescription = null)
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= Sizes.railBreakpoint) {
            val rail = rememberWideNavigationRailState()
            val scope = rememberCoroutineScope()
            val expanded = rail.targetValue == WideNavigationRailValue.Expanded
            Scaffold(contentWindowInsets = ScreenInsets, containerColor = MaterialTheme.colorScheme.background) { inner ->
                Row(Modifier.padding(inner)) {
                    WideNavigationRail(
                        state = rail,
                        colors = WideNavigationRailDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                        header = {
                            IconButton(onClick = { scope.launch { rail.toggle() } }, modifier = Modifier.padding(start = Spacing.l)) {
                                Icon(
                                    if (expanded) AppIcons.MenuOpen else AppIcons.Menu,
                                    contentDescription = stringResource(if (expanded) R.string.nav_collapse else R.string.nav_expand),
                                )
                            }
                        },
                    ) {
                        HomeTab.entries.forEach { t ->
                            WideNavigationRailItem(
                                selected = tab == t,
                                onClick = { onTabChange(t) },
                                icon = { badged(t) },
                                label = { Text(stringResource(t.label)) },
                                railExpanded = expanded,
                                colors = railColors(),
                            )
                        }
                    }
                    content(Modifier.weight(1f))
                }
            }
        } else {
            Scaffold(
                contentWindowInsets = ScreenInsets,
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    ShortNavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        HomeTab.entries.forEach { t ->
                            ShortNavigationBarItem(
                                selected = tab == t,
                                onClick = { onTabChange(t) },
                                icon = { badged(t) },
                                label = { Text(stringResource(t.label), maxLines = 1) },
                                colors = barColors(),
                            )
                        }
                    }
                },
            ) { inner -> Box(Modifier.padding(inner)) { content(Modifier.fillMaxSize()) } }
        }
    }
}

// selected tab shows only icon and name in the accent color, no background pill (simple icons across the app)
@Composable
private fun railColors() = WideNavigationRailItemDefaults.colors(
    selectedIndicatorColor = Color.Transparent,
    selectedIconColor = MaterialTheme.colorScheme.primary,
    selectedTextColor = MaterialTheme.colorScheme.primary,
)

@Composable
private fun barColors() = ShortNavigationBarItemDefaults.colors(
    selectedIndicatorColor = Color.Transparent,
    selectedIconColor = MaterialTheme.colorScheme.primary,
    selectedTextColor = MaterialTheme.colorScheme.primary,
)

/** "Essential mode on": a quiet line at the top of the tabs, no background. */
@Composable
fun EssentialBanner(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onClick).padding(horizontal = Spacing.s, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Icon(AppIcons.Eco, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.essential_banner), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Alert on open: what is off, as a list, and the link that goes straight to Settings → Essential mode. */
@Composable
fun EssentialAlert(look: br.app.stagebook.data.model.Look, onDismiss: () -> Unit, onNeverAgain: (Boolean) -> Unit, onOpen: () -> Unit = {}) {
    val off = listOfNotNull(
        R.string.essential_band.takeIf { !look.bandOn },
        R.string.essential_internet.takeIf { !look.internetOn },
        R.string.essential_player.takeIf { !look.playerMode },
        R.string.essential_extras.takeIf { !look.extrasOn },
    ).map { stringResource(it) }
    val all = off.size == 4
    br.app.stagebook.ui.components.InfoDialog(
        stringResource(if (all) R.string.essential_alert_title else R.string.essential_alert_some_title),
        stringResource(if (all) R.string.essential_alert_text else R.string.essential_alert_some_text),
        onDismiss = onDismiss, neverAgain = onNeverAgain,
    ) {
        Column(Modifier.padding(top = Spacing.s)) {
            off.forEach { Text("•  $it", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = Spacing.s)) }
            Row(Modifier.padding(top = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.essential_alert_relink), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.essential_alert_route),
                    style = MaterialTheme.typography.bodyLarge.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(MaterialTheme.shapes.small).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onOpen)
                        .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
                )
            }
        }
    }
}
