package br.app.stagebook.navigation

import br.app.stagebook.ui.setlists.ScanSetlistRoute
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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

/** [updateOffer]: new app version; shows on top of any screen, never on stage. */
@Composable
fun StagebookNavHost(
    updateOffer: UpdateOffer? = null,
    updateActions: AppUpdateActions = AppUpdateActions(),
    followSongs: Flow<String> = emptyFlow(),
) {
    val nav = rememberNavController()
    // The leader changed songs: the follower's stage opens on it (replacing the open stage, without stacking). While
    // editing or importing, it doesn't pull the person off the screen: the "Following" strip shows the current song
    LaunchedEffect(followSongs) {
        followSongs.collect { id ->
            val here = nav.currentBackStackEntry?.destination ?: return@collect
            if (here.hasRoute<Home>() || here.hasRoute<Stage>()) {
                nav.navigate(Stage(songId = id)) { popUpTo<Stage> { inclusive = true }; launchSingleTop = true }
            }
        }
    }
    if (updateOffer != null) AppUpdateDialog(updateOffer, updateActions.onUpdate, updateActions.onLater, updateActions.onInstall, updateActions.onClose)
    // the "Lead the band" strip sits below any screen (the stage included) while the mode is on. Short messages
    // (snackbar) from any screen, at the bottom, above the "Lead the band" strip
    val message = rememberAppMessage()
    CompositionLocalProvider(LocalAppMessage provides message) {
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
        composable<SongEditor> {
            SongEditorRoute(
                onDone = dropUnlessResumed { nav.popBackStack() },
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
                onEditSong = { entry.ifResumed { nav.navigate(SongEditor(songId = it)) } },
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
        composable<Importer> {
            ImportRoute(
                onClose = dropUnlessResumed { nav.popBackStack() },
                // the editor replaces the browser: save or cancel goes back to the list
                onOpenEditor = { id -> nav.navigate(SongEditor(songId = id, fromImport = true)) { popUpTo<Importer> { inclusive = true } } },
                onOpenEditorQueued = { id -> nav.navigate(SongEditor(songId = id, fromImport = true)) },
                onSetlistReady = { id -> nav.navigate(SetlistDetail(id)) { popUpTo<Importer> { inclusive = true } } },
            )
        }
    }
    SnackbarHost(message.host, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
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
