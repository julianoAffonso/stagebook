package br.app.stagebook.ui.home

import br.app.stagebook.ui.settings.SettingsSection
import br.app.stagebook.ui.components.LocalAppMessage
import androidx.compose.foundation.layout.Column
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import br.app.stagebook.ui.share.InboxViewModel
import br.app.stagebook.ui.share.InboxDialog
import br.app.stagebook.ui.components.HomeStatus
import br.app.stagebook.ui.components.LocalHomeStatus
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import br.app.stagebook.R
import br.app.stagebook.ui.components.AppIcons
import br.app.stagebook.ui.setlists.SetlistsRoute
import br.app.stagebook.ui.songs.SongsRoute
import br.app.stagebook.ui.theme.Spacing
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

enum class HomeTab(@StringRes val label: Int, val icon: ImageVector) {
    SONGS(R.string.tab_songs, AppIcons.MusicNote),
    SETLISTS(R.string.tab_setlists, AppIcons.QueueMusic),
    SETTINGS(R.string.tab_settings, AppIcons.Settings),
}

/**
 * The side menu's three tabs: Songs, Setlists and Settings. Settings shows only its groups and the search here (user,
 * 19/11); a group opens on its own page, full screen like the stage ([onOpenSettings]).
 */
@Composable
fun HomeScreen(
    onOpenSong: (String) -> Unit,
    onEditSong: (String?) -> Unit,
    onImport: (query: String, kind: String) -> Unit,
    onPlaySetlist: (setId: String, start: Int, atBreak: Boolean) -> Unit,   // its stage
    onScanSetlist: () -> Unit = {},
    onReceiveCode: (String) -> Unit = {},
    onOpenReceived: (String) -> Unit = {},
    setlistRequest: br.app.stagebook.ui.drawer.SetlistRequest? = null,   // something asks the drawer to show a setlist
    onRequestHandled: () -> Unit = {},
    pinned: Boolean = false,   // the drawer is pinned beside the stage: the pin shows in its top row
    onPin: (() -> Unit)? = null,
    onOpenSettings: (section: br.app.stagebook.ui.settings.SettingsSection?, alone: Boolean, item: String?) -> Unit = { _, _, _ -> },   // alone: only that section; item: the setting to flash
    inboxVm: InboxViewModel = hiltViewModel(),
    homeVm: HomeViewModel = hiltViewModel(),
) {
    // the tab picked on this visit; until one is picked, the start tab (Setlists, or Songs with no setlist yet)
    val start by homeVm.startTab.collectAsStateWithLifecycle()
    var picked by rememberSaveable { mutableStateOf<HomeTab?>(null) }
    val home = start ?: HomeTab.SETLISTS
    val tab = picked ?: home
    // something outside asks for a setlist: the Setlists tab shows it
    androidx.compose.runtime.LaunchedEffect(setlistRequest) { if (setlistRequest != null) picked = HomeTab.SETLISTS }
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
    // back on a tab that isn't the start one goes to it (instead of closing the app)
    BackHandler(enabled = tab != home) { picked = home }
    // each tab keeps its own state (search, list position) when switching; no fade, which drew both full screens at
    // once on the Fire
    val tabs = rememberSaveableStateHolder()
    // the Setlists tab tapped while it shows goes back to its list (an open setlist has no arrow; user, 19/11)
    var setsToList by rememberSaveable { mutableStateOf(0) }
    val onTab: (HomeTab) -> Unit = { t ->
        when {
            t == tab && t == HomeTab.SETLISTS -> setsToList++
            else -> picked = t
        }
    }
    val openEssential = { onOpenSettings(br.app.stagebook.ui.settings.SettingsSection.DEVICE, true, "essential") }
    alert?.let { look -> EssentialAlert(look, onDismiss = { homeVm.alertSeen(false) }, onNeverAgain = homeVm::alertSeen, onOpen = { homeVm.alertSeen(false); openEssential() }) }
    br.app.stagebook.ui.drawer.DrawerFrame(tab = tab, onTabChange = onTab, badges = badges, pinned = pinned, onPin = onPin) { m ->
        // essential mode and what came from the band: icons next to the tab's title, not strips over the screen (user,
        // 01/11); something new from the band shows once as the short message at the bottom, with "View"
        val webAddress by homeVm.webAddress.collectAsStateWithLifecycle()
        var webOpen by rememberSaveable { mutableStateOf(false) }
        if (webOpen && webAddress.isNotEmpty()) {
            br.app.stagebook.ui.components.ConfirmDialog(
                stringResource(R.string.web_in_use_text, webAddress), stringResource(R.string.web_close),
                { webOpen = false; homeVm.closeWebEditor() }, { webOpen = false }, destructive = false,
            )
        }
        val partial by homeVm.partial.collectAsStateWithLifecycle()
        val lookNow by homeVm.look.collectAsStateWithLifecycle()
        var reminderOpen by rememberSaveable { mutableStateOf(false) }
        // the leaf's reminder: what's off and the way to Settings, with no "don't show again" (it's the user's call to look)
        if (reminderOpen && partial) EssentialAlert(lookNow, onDismiss = { reminderOpen = false }, onNeverAgain = null, onOpen = { reminderOpen = false; openEssential() })
        val status = HomeStatus(
            essential, inbox.size, openEssential, { listOpen = true }, webAddress, { webOpen = true },
            partial = partial, onPartial = { reminderOpen = true },
        )
        val news = banner?.let { br.app.stagebook.ui.share.itemText(it) }
        val viewLabel = stringResource(R.string.inbox_snack_open)
        LaunchedEffect(banner?.id) {
            val item = banner ?: return@LaunchedEffect
            message.show(news.orEmpty(), long = true, action = viewLabel) { onOpenReceived(item.id) }
            inboxVm.later(item.id)
        }
        Column(m) {
            Box(Modifier.weight(1f)) {
                androidx.compose.runtime.CompositionLocalProvider(LocalHomeStatus provides status) {
                tabs.SaveableStateProvider(tab.name) {
                    when (tab) {
                        HomeTab.SONGS -> SongsRoute(
                            onOpenSong, onEditSong, onImport, onScan = onScanSetlist, onReceiveCode = onReceiveCode,
                            received = inbox.size, onReceived = { listOpen = true }, onSync = onSync,
                        )
                        HomeTab.SETLISTS -> br.app.stagebook.ui.drawer.SetlistsPane(
                            setlistRequest, onRequestHandled, onPlay = { id, index -> onPlaySetlist(id, index, false) }, toList = setsToList,
                        ) { open ->
                            SetlistsRoute(
                                { open(it, false, false) }, onPlaySetlist, onScanSetlist, onReceiveCode, onShare = { open(it, true, false) },
                                onEditSongs = { open(it, false, true) },
                                received = inbox.size, onReceived = { listOpen = true }, onSync = onSync,
                            )
                        }
                        // only the groups and the search: each group opens on its own page
                        HomeTab.SETTINGS -> br.app.stagebook.ui.settings.SettingsMenuRoute(onOpen = { section, item -> onOpenSettings(section, true, item) })
                    }
                }
                }
            }
        }
    }
    if (listOpen && inbox.isNotEmpty()) {
        InboxDialog(inbox, onOpen = { listOpen = false; onOpenReceived(it) }, onDiscard = inboxVm::discard, onDismiss = { listOpen = false })
    }
}

/** Alert on open: what is off, as a list, and the link that goes straight to Settings → Essential mode. */
@Composable
fun EssentialAlert(look: br.app.stagebook.data.model.Look, onDismiss: () -> Unit, onNeverAgain: ((Boolean) -> Unit)?, onOpen: () -> Unit = {}) {
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
                    // the accent color says it's a link; no underline (it ran into the "→" and read as one line, 30/10)
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(MaterialTheme.shapes.small).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onOpen)
                        .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
                )
            }
        }
    }
}
