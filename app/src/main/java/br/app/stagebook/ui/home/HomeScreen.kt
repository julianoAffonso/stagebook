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
import br.app.stagebook.ui.components.HomeStatus
import br.app.stagebook.ui.components.LocalHomeStatus
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.material3.WideNavigationRail
import androidx.compose.ui.layout.layout
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
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
    // the tab picked on this visit; until one is picked, the start tab (Setlists, or Songs with no setlist yet)
    val start by homeVm.startTab.collectAsStateWithLifecycle()
    var picked by rememberSaveable { mutableStateOf<HomeTab?>(null) }
    val home = start ?: HomeTab.SETLISTS
    val tab = picked ?: home
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
    // About from the rail's logo: its own page over the tab the user was on, which back returns to (user, 03/11)
    var aboutOpen by rememberSaveable { mutableStateOf(false) }
    // under the app's name on the rail: the number only, without the commit ("0.1.674 (7fd22d9d)")
    val context = androidx.compose.ui.platform.LocalContext.current
    val appVersion = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty().substringBefore(" (")
    }
    BackHandler(enabled = aboutOpen) { aboutOpen = false }

    // each tab keeps its own state (search, list position) when switching; no fade, which drew both full screens at
    // once on the Fire
    val tabs = rememberSaveableStateHolder()
    // Settings always opens on the starting list (not the last section seen): each tap on the icon is a new visit
    var settingsVisit by rememberSaveable { mutableIntStateOf(0) }
    var settingsStart by rememberSaveable { mutableStateOf<SettingsSection?>(null) }   // link from the alert: opens right on the section
    val onTab: (HomeTab) -> Unit = { t ->
        aboutOpen = false
        if (t == HomeTab.SETTINGS) {
            tabs.removeState("${HomeTab.SETTINGS}$settingsVisit")
            settingsVisit++
            settingsStart = null
        }
        picked = t
    }
    val openEssential = { onTab(HomeTab.SETTINGS); settingsStart = SettingsSection.ESSENTIAL }
    // a link from another screen (or another section): Settings opens right on it
    val linked by br.app.stagebook.ui.settings.SettingsLinks.pending.collectAsStateWithLifecycle()
    LaunchedEffect(linked) {
        linked?.let { onTab(HomeTab.SETTINGS); settingsStart = it; br.app.stagebook.ui.settings.SettingsLinks.pending.value = null }
    }
    alert?.let { look -> EssentialAlert(look, onDismiss = { homeVm.alertSeen(false) }, onNeverAgain = homeVm::alertSeen, onOpen = { homeVm.alertSeen(false); openEssential() }) }
    HomeScaffold(tab = tab, onTabChange = onTab, badges = badges, version = appVersion, onAbout = { aboutOpen = true }) { m ->
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
        val status = HomeStatus(essential, inbox.size, openEssential, { listOpen = true }, webAddress) { webOpen = true }
        val news = banner?.let { br.app.stagebook.ui.share.itemText(it) }
        val viewLabel = stringResource(R.string.inbox_snack_open)
        LaunchedEffect(banner?.id) {
            val item = banner ?: return@LaunchedEffect
            message.show(news.orEmpty(), long = true, action = viewLabel) { onOpenReceived(item.id) }
            inboxVm.later(item.id)
        }
        Column(m) {
            Box(Modifier.weight(1f)) {
                if (aboutOpen) {
                    SettingsRoute(SettingsSection.ABOUT, onLeave = { aboutOpen = false })
                } else androidx.compose.runtime.CompositionLocalProvider(LocalHomeStatus provides status.takeIf { tab != HomeTab.SETTINGS }) {
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
    }
    if (listOpen && inbox.isNotEmpty()) {
        InboxDialog(inbox, onOpen = { listOpen = false; onOpenReceived(it) }, onDiscard = inboxVm::discard, onDismiss = { listOpen = false })
    }
}

/**
 * Tab frame, stateless, in Google's current pattern (Material 3 Expressive): on tablets the rail, with the app's logo
 * on top ([onAbout] when tapped); on phones the bottom bar. The content gets the Modifier with the remaining space.
 */
@Composable
fun HomeScaffold(
    tab: HomeTab, onTabChange: (HomeTab) -> Unit, badges: Map<HomeTab, Int> = emptyMap(), onAbout: () -> Unit = {},
    version: String = "",
    content: @Composable (Modifier) -> Unit,
) {
    // number of band news on the tab icon (like new email)
    val badged: @Composable (HomeTab) -> Unit = { t ->
        val n = badges[t] ?: 0
        if (n > 0) BadgedBox(badge = { Badge { Text(n.toString()) } }) { Icon(t.icon, contentDescription = null) }
        else Icon(t.icon, contentDescription = null)
    }
    // one content for both layouts: a phone turned sideways moves from the bottom bar to the rail, and the tab's state
    // (selection, search, scroll) goes along instead of starting over (device test B-01)
    val currentContent by rememberUpdatedState(content)
    val shared = remember { movableContentOf<Modifier> { m -> currentContent(m) } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= Sizes.railBreakpoint) {
            Scaffold(contentWindowInsets = ScreenInsets, containerColor = MaterialTheme.colorScheme.background) { inner ->
                Row(Modifier.padding(inner)) {
                    // fixed rail with icon and name, the app's logo on top and the tabs right after it (user, 03/11: no
                    // menu button, opening it only moved the same names beside their icons; centred tabs looked lost)
                    WideNavigationRail(
                        // the rail's own top space (44 dp) left the logo well below the screen's title: moved up so
                        // the logo starts with it (user, 03/11)
                        modifier = Modifier.layout { m, c ->
                            val up = (RailTopSpace - Spacing.s).roundToPx()
                            val p = m.measure(c.copy(minHeight = c.minHeight + up, maxHeight = if (c.hasBoundedHeight) c.maxHeight + up else c.maxHeight))
                            layout(p.width, p.height - up) { p.place(0, -up) }
                        },
                        colors = WideNavigationRailDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                        header = {
                            // the launcher icon as the home screen shows it: its pages on black, in a rounded square,
                            // centred over the tabs' icons (the collapsed rail is 96 dp wide)
                            // and the name below it; tapping them opens About (user, 03/11), announced as a button (audit 03/11)
                            androidx.compose.foundation.layout.Column(
                                Modifier.width(96.dp).clip(MaterialTheme.shapes.medium)
                                    .clickable(onClickLabel = stringResource(R.string.settings_about), role = androidx.compose.ui.semantics.Role.Button, onClick = onAbout)
                                    .padding(vertical = Spacing.xs),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                androidx.compose.foundation.Image(
                                    androidx.compose.ui.res.painterResource(R.mipmap.ic_launcher_foreground),
                                    contentDescription = null,
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier.size(Sizes.railLogo).clip(MaterialTheme.shapes.medium)
                                        .graphicsLayer { scaleX = 1.4f; scaleY = 1.4f },
                                )
                                Text(
                                    stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(top = Spacing.s),
                                )
                                // the installed version, small and quiet (user, 03/11)
                                if (version.isNotEmpty()) {
                                    Text(
                                        version,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                    ) {
                        HomeTab.entries.forEach { t ->
                            WideNavigationRailItem(
                                selected = tab == t,
                                onClick = { onTabChange(t) },
                                icon = { badged(t) },
                                label = { Text(stringResource(t.label)) },
                                railExpanded = false,
                                colors = railColors(),
                            )
                        }
                    }
                    shared(Modifier.weight(1f))
                }
            }
        } else {
            Scaffold(
                contentWindowInsets = ScreenInsets,
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    val lift = br.app.stagebook.ui.components.LocalMessageLift.current
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    androidx.compose.runtime.DisposableEffect(lift) { onDispose { lift.bottom = 0.dp } }
                    ShortNavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        // the app's message shows above the tabs, not over them (B-29)
                        modifier = Modifier.onSizeChanged { lift.bottom = with(density) { it.height.toDp() } },
                    ) {
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
            ) { inner -> Box(Modifier.padding(inner)) { shared(Modifier.fillMaxSize()) } }
        }
    }
}

/** Top space the Material wide rail keeps above its header (collapsed rail token). */
private val RailTopSpace = 44.dp

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
