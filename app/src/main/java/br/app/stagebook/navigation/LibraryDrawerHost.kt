package br.app.stagebook.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
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
internal fun LibraryDrawerHost(nav: NavController, content: @Composable () -> Unit) {
    val state = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val start: StartViewModel = hiltViewModel()
    val pinned by start.pinned.collectAsStateWithLifecycle()
    val width = LocalConfiguration.current.screenWidthDp
    val wide = width >= Sizes.pinBreakpoint.value
    val fixed = pinned && wide
    val drawer = remember(state, fixed) { DrawerControl({ scope.launch { state.open() } }, { scope.launch { state.close() } }, pinned = fixed) }
    BackHandler(enabled = state.isOpen) { drawer.close() }

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
    val library: @Composable () -> Unit = {
        HomeScreen(
            onOpenSong = { openStage(Stage(songId = it, fromList = true)) },
            onEditSong = { go { nav.navigate(SongEditor(songId = it)) } },
            onImport = { query, kind -> go { nav.navigate(Importer(query, kind = kind)) } },
            onOpenSetlist = { go { nav.navigate(SetlistDetail(it)) } },
            onPlaySetlist = { id, start0, atBreak -> openStage(Stage(setId = id, start = start0, atBreak = atBreak)) },
            onScanSetlist = { go { nav.navigate(ScanSetlist()) } },
            onReceiveCode = { code -> go { nav.navigate(ScanSetlist(code)) } },
            onOpenReceived = { id -> go { nav.navigate(ScanSetlist(inbox = id)) } },
            onShareSetlist = { id -> go { nav.navigate(SetlistDetail(id, share = true)) } },
            pinned = fixed,
            onPin = if (wide) start::togglePin else null,
            onOpenSettings = { section, alone -> go { nav.navigate(Settings(section?.name.orEmpty(), alone)) { launchSingleTop = true } } },
        )
    }
    CompositionLocalProvider(LocalDrawer provides drawer) {
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
                drawerContent = { Surface(Modifier.width(sheet).fillMaxHeight(), color = MaterialTheme.colorScheme.surfaceContainerLow) { library() } },
                content = content,
            )
        }
    }
}
