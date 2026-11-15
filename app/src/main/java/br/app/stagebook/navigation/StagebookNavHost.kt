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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.app.stagebook.data.message
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import br.app.stagebook.ui.components.LocalAppMessage
import br.app.stagebook.ui.components.rememberAppMessage
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavDestination.Companion.hasRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.toRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import br.app.stagebook.ui.editor.SongEditorRoute
import br.app.stagebook.ui.importer.ImportRoute
import br.app.stagebook.ui.stage.StageRoute
import br.app.stagebook.ui.AppUpdateDialog
import br.app.stagebook.data.UpdateOffer
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

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

/**
 * [updateOffer]: new app version; shows on top of any screen, never on stage. [receiveCodes]: codes of receive links
 * (tapped in a message or copied by the site) that open the receive screen. [openedFiles]: `.stagebook` files tapped
 * outside the app, already checked, that open Settings → Library → Open file.
 */
@Composable
fun StagebookNavHost(
    updateOffer: UpdateOffer? = null,
    updateActions: AppUpdateActions = AppUpdateActions(),
    followSongs: Flow<String> = emptyFlow(),
    followingSong: kotlinx.coroutines.flow.StateFlow<String?> = kotlinx.coroutines.flow.MutableStateFlow(null),
    receiveCodes: Flow<String> = emptyFlow(),
    onStage: kotlinx.coroutines.flow.StateFlow<Boolean> = kotlinx.coroutines.flow.MutableStateFlow(false),
    installNotices: Flow<br.app.stagebook.domain.InstallNotice> = emptyFlow(),
    openedFiles: Flow<br.app.stagebook.data.IncomingFiles.Result> = emptyFlow(),
    resume: String? = null,
    openedPdfs: Flow<android.net.Uri> = emptyFlow(),   // PDFs opened with the app or shared to it: each comes in as a song
) {
    val nav = rememberNavController()
    // at launch the app opens on the stage that was open last, in place of the empty screen (the stage is the root: back
    // from it leaves the app). Coming back from an update it goes on to the place it was (below), over that stage. Only
    // once per start: a turned screen doesn't open it again
    val start: br.app.stagebook.ui.home.StartViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
    val libraryRead by start.loaded.collectAsStateWithLifecycle()
    val pdfContext = androidx.compose.ui.platform.LocalContext.current
    var opened by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    // a receive link: the receive screen with its code, in place of another receive screen already open. On the stage
    // it waits and opens on leaving; a code already open there is left alone (it would discard the review)
    val pendingLink = remember { br.app.stagebook.domain.PendingLink() }
    val linkKept = androidx.compose.runtime.rememberUpdatedState(androidx.compose.ui.res.stringResource(br.app.stagebook.R.string.stage_link_kept))
    val linkKeptEditing = androidx.compose.runtime.rememberUpdatedState(androidx.compose.ui.res.stringResource(br.app.stagebook.R.string.editor_link_kept))
    // where a link or file waits instead of opening: the stage, or a song editor anywhere in the back stack (opening
    // pops back to Home and would drop what was typed without the "discard?" question, user 09/11)
    val holdScope = androidx.compose.runtime.rememberCoroutineScope()
    val hold = remember(nav, onStage) {
        // public API: on each screen change, is there a SongEditor entry anywhere in the back stack (throws when not)
        val editing = nav.currentBackStackEntryFlow.map { runCatching { nav.getBackStackEntry<SongEditor>() }.isSuccess }
        kotlinx.coroutines.flow.combine(onStage, editing) { stage, edit -> br.app.stagebook.domain.HoldPlace.of(stage, edit) }
            .distinctUntilChanged()
            .stateIn(holdScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, br.app.stagebook.domain.HoldPlace.of(onStage.value, false))
    }
    fun openCode(): String? = nav.currentBackStackEntry?.takeIf { it.destination.hasRoute<ScanSetlist>() }
        ?.toRoute<ScanSetlist>()?.code?.takeIf { it.isNotEmpty() }
    fun openReceive(code: String) = nav.navigate(ScanSetlist(code, fromLink = true)) { popUpTo<ScanSetlist> { inclusive = true } }
    val message = rememberAppMessage()
    // a PDF opened with the app: a song is made of it right away (nothing on screen is disturbed), and the message says so, or why not
    LaunchedEffect(openedPdfs) {
        openedPdfs.collect { uri -> message.show(start.importPdfs(listOf(uri)).message(pdfContext), long = true) }
    }
    // the update going in: the system takes over, so the person is told what is happening, and only what is true
    val installStartedText = androidx.compose.runtime.rememberUpdatedState(androidx.compose.ui.res.stringResource(br.app.stagebook.R.string.update_installing))
    val installFailedText = androidx.compose.runtime.rememberUpdatedState(androidx.compose.ui.res.stringResource(br.app.stagebook.R.string.update_install_failed))
    LaunchedEffect(installNotices) {
        installNotices.collect { n ->
            message.show(if (n == br.app.stagebook.domain.InstallNotice.STARTED) installStartedText.value else installFailedText.value, long = true)
        }
    }
    LaunchedEffect(receiveCodes) {
        receiveCodes.collect { code ->
            val place = hold.value
            when (pendingLink.arrive(code, place.holds, openCode())) {
                br.app.stagebook.domain.PendingLink.Arrival.OPEN -> openReceive(code)
                br.app.stagebook.domain.PendingLink.Arrival.HELD ->
                    message.show(if (place == br.app.stagebook.domain.HoldPlace.EDITOR) linkKeptEditing.value else linkKept.value)
                br.app.stagebook.domain.PendingLink.Arrival.IGNORED -> Unit
            }
        }
    }
    LaunchedEffect(hold) {
        hold.collect { place -> if (!place.holds) pendingLink.release(openCode())?.let(::openReceive) }
    }
    // the leader changed songs: the follower's stage opens on it, or waits while the person is mid-action (FollowPull.kt)
    // the setlist screen busy (renaming, sharing, deleting, selecting, dragging): the song waits for it
    val followGate = remember { br.app.stagebook.ui.components.FollowGate() }
    FollowToStage(nav, followSongs, followingSong, followGate)
    val appContext = androidx.compose.ui.platform.LocalContext.current
    val context = appContext
    // asks the system only when the install is at hand: the button shows there and nowhere else in this window
    val installing = updateOffer is UpdateOffer.Ready
    val autoBlocker = remember(appContext, installing) {
        if (installing && br.app.stagebook.data.device.AutoBlocker.available(appContext)) ({ br.app.stagebook.data.device.AutoBlocker.open(appContext) }) else null
    }
    if (updateOffer != null) AppUpdateDialog(updateOffer, updateActions.onUpdate, updateActions.onLater, updateActions.onInstall, updateActions.onClose, autoBlocker)
    // the "Lead the band" strip sits below any screen (the stage included) while the mode is on. Short messages
    // (snackbar) from any screen, at the bottom, above the "Lead the band" strip
    // a link to a Settings section from any screen: the Settings page over it, on that section; back returns to where it
    // was (the stage with its break, its page and its counters; an editor with what was typed)
    val openSettings: (br.app.stagebook.ui.settings.SettingsSection) -> Unit = remember(nav) {
        { section -> nav.navigate(Settings(section.name)) { launchSingleTop = true } }
    }
    // a .stagebook file tapped outside the app: Open file with its parts, as if picked there. On the stage it waits
    // and opens on leaving (the show is never interrupted); a file that isn't Stagebook's only gets the short message
    val heldFile = remember { br.app.stagebook.domain.PendingFile<android.net.Uri>() }
    val fileKept = androidx.compose.runtime.rememberUpdatedState(androidx.compose.ui.res.stringResource(br.app.stagebook.R.string.stage_file_kept))
    val fileKeptEditing = androidx.compose.runtime.rememberUpdatedState(androidx.compose.ui.res.stringResource(br.app.stagebook.R.string.editor_file_kept))
    val notStagebook = androidx.compose.runtime.rememberUpdatedState(androidx.compose.ui.res.stringResource(br.app.stagebook.R.string.backup_not_backup))
    val openFile: (android.net.Uri) -> Unit = { uri ->
        br.app.stagebook.ui.settings.SettingsLinks.file.value = uri
        openSettings(br.app.stagebook.ui.settings.SettingsSection.LIBRARY)
    }
    LaunchedEffect(openedFiles) {
        openedFiles.collect { r ->
            val uri = (r as? br.app.stagebook.data.IncomingFiles.Result.Accepted)?.uri
            val place = hold.value
            when (heldFile.arrive(uri, place.holds)) {
                br.app.stagebook.domain.PendingFile.Arrival.OPEN -> uri?.let(openFile)
                br.app.stagebook.domain.PendingFile.Arrival.HELD ->
                    message.show(if (place == br.app.stagebook.domain.HoldPlace.EDITOR) fileKeptEditing.value else fileKept.value)
                br.app.stagebook.domain.PendingFile.Arrival.REJECTED -> message.show(notStagebook.value)
            }
        }
    }
    LaunchedEffect(hold) {
        hold.collect { place -> if (!place.holds) heldFile.release()?.let(openFile) }
    }
    // the screen in use, as a route to come back to after an update installed from inside the app (user, 09/11)
    androidx.compose.runtime.DisposableEffect(nav) {
        val listener = NavController.OnDestinationChangedListener { _, dest, args ->
            br.app.stagebook.data.ResumeAfterUpdate.currentRoute = dest.route?.let { pattern ->
                val keys = Regex("\\{([^}]+)\\}").findAll(pattern).map { it.groupValues[1] }.toList()
                @Suppress("DEPRECATION")
                br.app.stagebook.domain.ResumePoint.fill(pattern, keys.associateWith { k -> args?.get(k)?.toString() }) { android.net.Uri.encode(it) }
            }
        }
        nav.addOnDestinationChangedListener(listener)
        onDispose { nav.removeOnDestinationChangedListener(listener) }
    }
    // the start, once the library is read: the stage that was open last; then, just updated, back where it was (Settings →
    // About, or the screen the update window was over), over that stage and not over the empty screen
    LaunchedEffect(libraryRead) {
        if (!libraryRead || opened) return@LaunchedEffect
        opened = true
        start.route()?.let { route -> nav.navigate(route) { popUpTo<Home> { inclusive = true } } }
        val where = resume ?: return@LaunchedEffect
        if (where.startsWith(br.app.stagebook.domain.ResumePoint.SETTINGS)) {
            val section = runCatching { br.app.stagebook.ui.settings.SettingsSection.fromSaved(where.removePrefix(br.app.stagebook.domain.ResumePoint.SETTINGS)) }.getOrNull()
            if (section != null) openSettings(section)
        } else if (where != br.app.stagebook.data.ResumeAfterUpdate.HOME && !where.contains("Home")) {
            // a stage it was on takes the place of the one just opened (usually the same), and stays the root
            runCatching { nav.navigate(where) { if (where.contains("Stage")) popUpTo(nav.graph.id) { inclusive = true } } }
        }
    }
    var drawerShown by remember { androidx.compose.runtime.mutableStateOf(false) }
    CompositionLocalProvider(LocalAppMessage provides message, br.app.stagebook.ui.components.LocalOpenSettings provides openSettings, br.app.stagebook.ui.components.LocalFollowGate provides followGate) {
    BandStripLayout(liftIme = !drawerShown, screens = {
    LibraryDrawerHost(nav, onShown = { drawerShown = it }) {
    NavHost(
        modifier = Modifier.fillMaxSize(),
        navController = nav,
        startDestination = Home,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() },
    ) {
        composable<Home> {
            // waits for the library: with a last stage to open, the empty screen never opens the drawer
            val noStage = remember(libraryRead) { libraryRead && start.route() == null }
            br.app.stagebook.ui.drawer.BaseScreen(openDrawer = noStage)
        }
        composable<Settings> { entry ->
            val route = remember { entry.toRoute<Settings>() }
            val drawer = br.app.stagebook.ui.drawer.LocalDrawer.current
            // not a screen the leader's song pulls away from (pullsToStage): it waits here and the strip says so
            androidx.compose.material3.Scaffold(contentWindowInsets = br.app.stagebook.ui.components.ScreenInsets, containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background) { inner ->
                Box(Modifier.padding(inner)) {
                    // from the side menu, back brings the menu again (user, 15/11)
                    val back = dropUnlessResumed { nav.popBackStack(); if (route.fromMenu) drawer?.takeIf { !it.pinned }?.open?.invoke() }
                    val section = br.app.stagebook.ui.settings.SettingsSection.fromSaved(route.section)
                    if (route.alone && section != null) br.app.stagebook.ui.settings.SettingsRoute(section, onLeave = back)
                    else br.app.stagebook.ui.settings.SettingsRoute(section, onBack = back)
                }
            }
        }
        composable<SongEditor> { entry ->
            val drawer = br.app.stagebook.ui.drawer.LocalDrawer.current
            SongEditorRoute(
                onDone = dropUnlessResumed { nav.popBackStack() },
                onSaved = { id -> entry.ifResumed { nav.showSaved(id) } },
                // import queue: saves, opens the next song in the importer; when done, opens the setlist
                onNextImport = { nav.navigate(Importer(queued = true)) { popUpTo<Importer> { inclusive = true } } },
                onSetlistReady = { id -> nav.popBackStack<Importer>(inclusive = true); drawer?.openSetlist(id, false) },
                // previous or next song: the editor of that song in place of this one (back still goes to where it came from)
                onMoved = { id -> entry.ifResumed { nav.navigate(SongEditor(songId = id)) { popUpTo<SongEditor> { inclusive = true } } } },
            )
        }
        composable<ScanSetlist> {
            val drawer = br.app.stagebook.ui.drawer.LocalDrawer.current
            ScanSetlistRoute(
                // created: the scanner goes and the drawer opens on the new setlist
                onDone = { id -> nav.popBackStack<ScanSetlist>(inclusive = true); drawer?.openSetlist(id, false) },
                onBack = dropUnlessResumed { nav.popBackStack() },
                // the missing ones: the importer opens as a queue in place of the scanner
                onImport = { nav.navigate(Importer(queued = true)) { popUpTo<ScanSetlist> { inclusive = true } } },
            )
        }
        composable<Stage> { entry ->
            val drawer = br.app.stagebook.ui.drawer.LocalDrawer.current
            StageRoute(
                fromList = entry.toRoute<Stage>().fromList,
                onEditSong = { id, cursor -> entry.ifResumed { nav.navigate(SongEditor(songId = id, cursor = cursor)) } },
                onBack = dropUnlessResumed { if (nav.previousBackStackEntry != null) nav.popBackStack() else (context as? android.app.Activity)?.finish() },
                // the library lost every song under the stage: the empty screen, with the drawer open
                onEmptied = { nav.navigate(Home) { popUpTo<Stage> { inclusive = true } } },
            )
        }
        composable<Importer> { entry ->
            val drawer = br.app.stagebook.ui.drawer.LocalDrawer.current
            ImportRoute(
                onClose = dropUnlessResumed { nav.popBackStack() },
                // the editor replaces the browser: save or cancel goes back to the list. Only from the open browser: a
                // late answer can't open a second editor (audit 01/11)
                onOpenEditor = { id -> entry.ifResumed { nav.navigate(SongEditor(songId = id, fromImport = true)) { popUpTo<Importer> { inclusive = true } } } },
                onOpenEditorQueued = { id -> entry.ifResumed { nav.navigate(SongEditor(songId = id, fromImport = true)) } },
                onSetlistReady = { id -> entry.ifResumed { nav.popBackStack<Importer>(inclusive = true); drawer?.openSetlist(id, false) } },
            )
        }
    }
    }
    SnackbarHost(message.host, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    // over any screen, the stage included: this device's USB can't carry the interface just opened
    val hostWarning by br.app.stagebook.player.StagePlayer.hostWarning.collectAsStateWithLifecycle()
    hostWarning?.let { br.app.stagebook.ui.components.HostWarningDialog(it, br.app.stagebook.player.StagePlayer::dismissHostWarning) }
    }, strip = { br.app.stagebook.ui.band.ConductRoute() })
    }
}

/** What the new version notice does (wired to AppUpdateOffer by MainActivity). */
data class AppUpdateActions(
    val onUpdate: () -> Unit = {},
    val onLater: () -> Unit = {},
    val onInstall: () -> Unit = {},
    val onClose: () -> Unit = {},
)
