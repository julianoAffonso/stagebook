package br.app.stagebook.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import br.app.stagebook.ui.drawer.SetlistRequest
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import br.app.stagebook.ui.drawer.DrawerControl
import br.app.stagebook.ui.drawer.LocalDrawer
import br.app.stagebook.ui.home.HomeScreen
import br.app.stagebook.ui.home.StartViewModel
import br.app.stagebook.ui.theme.Sizes
import kotlinx.coroutines.launch

/** The drawer takes this much of a phone's width (user, 15/11); the room left shows the stage dimmed, and a tap on it closes the drawer. */
private const val DRAWER_SHARE = 0.87f
private val DRAWER_MAX = 420.dp
private val PINNED_WIDTH = 360.dp

/**
 * The library drawer around the app's screens (the stage is behind it): Songs, Setlists and the way to Settings, as
 * SongbookPro's side menu. Only the stage's first button opens it (an edge gesture would turn songs in the middle of a
 * show); pinned, on a wide screen, it stays open beside the stage. Choosing something closes it and goes there.
 */
@Composable
internal fun LibraryDrawerHost(nav: NavController, onShown: (Boolean) -> Unit = {}, content: @Composable () -> Unit) {
    val guard = remember { br.app.stagebook.ui.drawer.DrawerGuard() }
    // a scrim tap or a swipe that would close it goes through the guard too
    val state = rememberDrawerState(DrawerValue.Closed, confirmStateChange = { v -> !(v == DrawerValue.Closed && guard.asks?.invoke() == true) })
    val scope = rememberCoroutineScope()
    val start: StartViewModel = hiltViewModel()
    val pinned by start.pinned.collectAsStateWithLifecycle()
    val width = LocalConfiguration.current.screenWidthDp
    val wide = width >= Sizes.pinBreakpoint.value
    // pinned beside the stage only where there is a stage to be beside: the editor, the importer and Settings are whole
    // screens (a tap in the list there would take the stage in place of what was being typed)
    val destination by nav.currentBackStackEntryAsState()
    val besideStage = destination?.destination?.let { it.hasRoute<Stage>() || it.hasRoute<Home>() } != false
    val fixed = pinned && wide && besideStage
    // something outside asks the drawer to show a setlist (the stage's menu, a setlist just received or imported)
    var request by remember { mutableStateOf<SetlistRequest?>(null) }
    val drawer = remember(state, fixed) {
        DrawerControl(
            { scope.launch { state.open() } }, { if (guard.asks?.invoke() != true) scope.launch { state.close() } }, pinned = fixed,
            openSetlist = { id, share -> request = SetlistRequest(id, share); scope.launch { state.open() } },
        )
    }
    // pinned, the sheet is not a sheet any more: it closes by itself (a left-open state held the pedal and Back for good)
    LaunchedEffect(fixed) { if (fixed) state.snapTo(DrawerValue.Closed) }
    val open = state.isOpen && !fixed
    val shown = !fixed && (state.currentValue != DrawerValue.Closed || state.targetValue != DrawerValue.Closed)
    LaunchedEffect(shown) { onShown(shown) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { onShown(false) } }
    BackHandler(enabled = open) { drawer.close() }
    // the leader's song waits while the drawer is open over the stage (a setlist is being looked at, or songs picked: the
    // stage behind it must not be replaced under the person's hands): a window of ours is open, as for a menu. Pinned, the
    // stage is in view and goes on following
    br.app.stagebook.ui.components.FollowBusyWhile(open)
    // and the pedal does not turn the stage behind it (composed only while it is open: the last handler registered gets the press)
    val pedalKeys by start.pedalKeys.collectAsStateWithLifecycle()
    if (open) br.app.stagebook.ui.PedalHandler { press -> br.app.stagebook.domain.pedalTakenByDrawer(press, pedalKeys) }

    // a tap only counts while the screen under it is in front (a double tap would open two screens)
    fun go(block: () -> Unit) {
        if (nav.currentBackStackEntry?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != true) return
        if (!fixed) drawer.close()
        block()
    }
    // a song or a setlist opens as the stage, in place of the one open (or of the empty screen at the start)
    fun openStage(route: Stage) = go {
        val atStart = nav.currentDestination?.hasRoute<Home>() == true
        nav.navigate(route) {
            if (atStart) popUpTo<Home> { inclusive = true } else popUpTo<Stage> { inclusive = true }
            launchSingleTop = true
        }
    }
    // what the tabs had (the tab, the search, where the list was) survives the drawer leaving the composition while closed
    val holder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val library: @Composable () -> Unit = { holder.SaveableStateProvider("library") { HomeScreen(
            onOpenSong = { openStage(Stage(songId = it, fromList = true)) },
            onEditSong = { go { nav.navigate(SongEditor(songId = it, fromMenu = true)) } },
            onImport = { query, kind -> go { nav.navigate(Importer(query, kind = kind, fromMenu = true)) } },
            onPlaySetlist = { id, start0, atBreak -> openStage(Stage(setId = id, start = start0, atBreak = atBreak)) },
            onScanSetlist = { go { nav.navigate(ScanSetlist()) } },
            onReceiveCode = { code -> go { nav.navigate(ScanSetlist(code)) } },
            onOpenReceived = { id -> go { nav.navigate(ScanSetlist(inbox = id)) } },
            setlistRequest = request,
            onRequestHandled = { request = null },
            pinned = fixed,
            onPin = if (wide) start::togglePin else null,
            onOpenSettings = { section, alone -> go { nav.navigate(Settings(section?.name.orEmpty(), alone, fromMenu = true)) { launchSingleTop = true } } },
        ) }
    }
    CompositionLocalProvider(LocalDrawer provides drawer, br.app.stagebook.ui.drawer.LocalDrawerGuard provides guard) {
        if (fixed) {
            Row {
                Surface(Modifier.width(PINNED_WIDTH).fillMaxHeight(), color = MaterialTheme.colorScheme.surfaceContainerLow) { library() }
                Box(Modifier.weight(1f)) { content() }
            }
        } else {
            val sheet = (width * DRAWER_SHARE).dp.coerceAtMost(DRAWER_MAX)
            ModalNavigationDrawer(
                drawerState = state,
                // opened by the button only; once open, a drag or a tap outside may close it
                gesturesEnabled = state.isOpen,
                // composed only while it is open or moving: closed, it holds no list, no collectors and no network callbacks
                // (the stage is what runs in a show, and the Fire is the ruler)
                drawerContent = {
                    Surface(Modifier.width(sheet).fillMaxHeight().imePadding(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        if (state.currentValue != DrawerValue.Closed || state.targetValue != DrawerValue.Closed) library()
                    }
                },
                content = content,
            )
        }
    }
}
