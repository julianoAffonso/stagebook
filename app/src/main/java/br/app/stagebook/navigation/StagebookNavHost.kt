package br.app.stagebook.navigation

import br.app.stagebook.ui.setlists.ScanSetlistRoute
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import br.app.stagebook.ui.components.LocalAppMessage
import br.app.stagebook.ui.components.rememberAppMessage
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavDestination.Companion.hasRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import br.app.stagebook.ui.editor.SongEditorRoute
import br.app.stagebook.ui.home.HomeScreen
import br.app.stagebook.ui.importer.ImportRoute
import br.app.stagebook.ui.setlists.PickSongsRoute
import br.app.stagebook.ui.setlists.SetlistDetailRoute
import br.app.stagebook.ui.stage.StageRoute
import br.app.stagebook.ui.AppUpdateDialog
import br.app.stagebook.data.UpdateOffer

// Back and opening another screen only count while the screen is still in front: a double tap on Save/OK/Back popped
// two screens (the second was Home, and the app went blank), and a double tap on play stacked two stages (and the one
// below came back with the screen allowed to dim).
private inline fun NavBackStackEntry.ifResumed(go: () -> Unit) {
    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) go()
}

/**
 * Saved: the song on stage, in place of the editor, so back goes to where the editor was opened from. Opened from the
 * stage it just returns to it: that stage already knows its origin, a setlist or a single song.
 */
internal fun NavController.showSaved(songId: String) {
    if (previousBackStackEntry?.destination?.hasRoute<Stage>() == true) popBackStack()
    else navigate(Stage(songId = songId)) { popUpTo<SongEditor> { inclusive = true } }
}

// screens the leader's song can take the follower away from: nothing is being typed or chosen there
private fun androidx.navigation.NavDestination.pullsToStage() = hasRoute<Home>() || hasRoute<Stage>() || hasRoute<SetlistDetail>()

/**
 * [updateOffer]: new app version; shows on top of any screen, never on stage. [receiveCodes]: codes of receive links
 * (tapped in a message or copied by the site) that open the receive screen.
 */
@Composable
fun StagebookNavHost(
    updateOffer: UpdateOffer? = null,
    updateActions: AppUpdateActions = AppUpdateActions(),
    followSongs: Flow<String> = emptyFlow(),
    followingSong: kotlinx.coroutines.flow.StateFlow<String?> = kotlinx.coroutines.flow.MutableStateFlow(null),
    receiveCodes: Flow<String> = emptyFlow(),
) {
    val nav = rememberNavController()
    // a receive link: the receive screen with its code, in place of another receive screen already open
    LaunchedEffect(receiveCodes) {
        receiveCodes.collect { code -> nav.navigate(ScanSetlist(code)) { popUpTo<ScanSetlist> { inclusive = true } } }
    }
    // The leader changed songs: the follower's stage opens on it (replacing the open stage, without stacking). While
    // editing, picking songs, receiving or importing, it doesn't pull the person off the screen: the "Following" strip
    // shows the current song. Just looking at a setlist does pull (the S26 sat on the setlist while the band played, 02/11)
    // A song asked for while on one of those screens waits and opens on coming back to a screen that pulls, if it's
    // still the leader's current song: before, coming back showed nothing until the next song (hotspot test, 02/11)
    var pendingFollow by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    // the setlist screen busy (renaming, sharing, deleting, selecting, dragging): the song waits for it
    val followHold = remember { androidx.compose.runtime.mutableStateOf(false) }
    fun pulls(dest: androidx.navigation.NavDestination) = dest.pullsToStage() && !(dest.hasRoute<SetlistDetail>() && followHold.value)
    LaunchedEffect(followSongs) {
        followSongs.collect { id ->
            val here = nav.currentBackStackEntry?.destination ?: return@collect
            if (pulls(here)) {
                pendingFollow = null
                nav.navigate(Stage(songId = id)) { popUpTo<Stage> { inclusive = true }; launchSingleTop = true }
            } else {
                pendingFollow = id
            }
        }
    }
    LaunchedEffect(nav) {
        nav.currentBackStackEntryFlow.collect { entry ->
            val id = pendingFollow ?: return@collect
            if (pulls(entry.destination)) {
                pendingFollow = null
                if (followingSong.value == id) nav.navigate(Stage(songId = id)) { launchSingleTop = true }
            }
        }
    }
    // the setlist screen done with what it was doing: a song that waited for it opens now
    LaunchedEffect(nav) {
        androidx.compose.runtime.snapshotFlow { followHold.value }.collect { busy ->
            val id = pendingFollow ?: return@collect
            val here = nav.currentBackStackEntry?.destination ?: return@collect
            if (!busy && here.hasRoute<SetlistDetail>()) {
                pendingFollow = null
                if (followingSong.value == id) nav.navigate(Stage(songId = id)) { launchSingleTop = true }
            }
        }
    }
    val appContext = androidx.compose.ui.platform.LocalContext.current
    // asks the system only when the install is at hand: the button shows there and nowhere else in this window
    val installing = updateOffer is UpdateOffer.Ready
    val autoBlocker = remember(appContext, installing) {
        if (installing && br.app.stagebook.data.device.AutoBlocker.available(appContext)) ({ br.app.stagebook.data.device.AutoBlocker.open(appContext) }) else null
    }
    if (updateOffer != null) AppUpdateDialog(updateOffer, updateActions.onUpdate, updateActions.onLater, updateActions.onInstall, updateActions.onClose, autoBlocker)
    // the "Lead the band" strip sits below any screen (the stage included) while the mode is on. Short messages
    // (snackbar) from any screen, at the bottom, above the "Lead the band" strip
    val message = rememberAppMessage()
    // a link to a Settings section from any screen: back to Home, which opens Settings on that section (user, 01/11)
    val openSettings: (br.app.stagebook.ui.settings.SettingsSection) -> Unit = remember(nav) {
        { section ->
            br.app.stagebook.ui.settings.SettingsLinks.pending.value = section
            nav.popBackStack<Home>(inclusive = false)
        }
    }
    val messageLift = remember { br.app.stagebook.ui.components.MessageLift() }
    CompositionLocalProvider(LocalAppMessage provides message, br.app.stagebook.ui.components.LocalMessageLift provides messageLift, br.app.stagebook.ui.components.LocalOpenSettings provides openSettings, br.app.stagebook.ui.components.LocalFollowHold provides followHold) {
    androidx.compose.foundation.layout.Column {
    Box(Modifier.weight(1f)) {
    NavHost(
        modifier = Modifier.fillMaxSize(),
        navController = nav,
        startDestination = Home,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() },
    ) {
        composable<Home> { entry ->
            HomeScreen(
                onOpenSong = { entry.ifResumed { nav.navigate(Stage(songId = it)) } },
                onEditSong = { entry.ifResumed { nav.navigate(SongEditor(songId = it)) } },
                onImport = { entry.ifResumed { nav.navigate(Importer(it)) } },
                onOpenSetlist = { entry.ifResumed { nav.navigate(SetlistDetail(it)) } },
                onPlaySetlist = { entry.ifResumed { nav.navigate(Stage(setId = it)) } },
                onScanSetlist = { entry.ifResumed { nav.navigate(ScanSetlist()) } },
                onReceiveCode = { code -> entry.ifResumed { nav.navigate(ScanSetlist(code)) } },
                onOpenReceived = { id -> entry.ifResumed { nav.navigate(ScanSetlist(inbox = id)) } },
            )
        }
        composable<SongEditor> { entry ->
            SongEditorRoute(
                onDone = dropUnlessResumed { nav.popBackStack() },
                onSaved = { id -> entry.ifResumed { nav.showSaved(id) } },
                // import queue: saves, opens the next song in the importer; when done, opens the setlist
                onNextImport = { nav.navigate(Importer(queued = true)) { popUpTo<Importer> { inclusive = true } } },
                onSetlistReady = { id -> nav.navigate(SetlistDetail(id)) { popUpTo<Importer> { inclusive = true } } },
            )
        }
        composable<SetlistDetail> { entry ->
            SetlistDetailRoute(
                onBack = dropUnlessResumed { nav.popBackStack() },
                onPlay = { setId, start -> entry.ifResumed { nav.navigate(Stage(setId = setId, start = start)) } },
                onAddSongs = { entry.ifResumed { nav.navigate(PickSongs(it)) } },
            )
        }
        composable<ScanSetlist> {
            ScanSetlistRoute(
                // created: opens the setlist in place of the scanner (back goes to the list)
                onDone = { id -> nav.navigate(SetlistDetail(id)) { popUpTo<ScanSetlist> { inclusive = true } } },
                onBack = dropUnlessResumed { nav.popBackStack() },
                // the missing ones: the importer opens as a queue in place of the scanner
                onImport = { nav.navigate(Importer(queued = true)) { popUpTo<ScanSetlist> { inclusive = true } } },
            )
        }
        composable<PickSongs> { PickSongsRoute(onDone = dropUnlessResumed { nav.popBackStack() }) }
        composable<Stage> { entry ->
            StageRoute(
                onEditSong = { id, cursor -> entry.ifResumed { nav.navigate(SongEditor(songId = id, cursor = cursor)) } },
                onBack = dropUnlessResumed { nav.popBackStack() },
            )
        }
        composable<Importer> { entry ->
            ImportRoute(
                onClose = dropUnlessResumed { nav.popBackStack() },
                // the editor replaces the browser: save or cancel goes back to the list. Only from the open browser: a
                // late answer can't open a second editor (audit 01/11)
                onOpenEditor = { id -> entry.ifResumed { nav.navigate(SongEditor(songId = id, fromImport = true)) { popUpTo<Importer> { inclusive = true } } } },
                onOpenEditorQueued = { id -> entry.ifResumed { nav.navigate(SongEditor(songId = id, fromImport = true)) } },
                onSetlistReady = { id -> entry.ifResumed { nav.navigate(SetlistDetail(id)) { popUpTo<Importer> { inclusive = true } } } },
            )
        }
    }
    SnackbarHost(message.host, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = messageLift.bottom))
    }
    br.app.stagebook.ui.band.ConductRoute()
    }
    }
}

/** What the new version notice does (wired to AppUpdateOffer by MainActivity). */
data class AppUpdateActions(
    val onUpdate: () -> Unit = {},
    val onLater: () -> Unit = {},
    val onInstall: () -> Unit = {},
    val onClose: () -> Unit = {},
)
