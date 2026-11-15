package br.app.stagebook.ui.drawer

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import br.app.stagebook.navigation.PickSongs
import br.app.stagebook.navigation.SetlistDetail
import br.app.stagebook.navigation.SetlistsList
import br.app.stagebook.ui.setlists.PickSongsRoute
import br.app.stagebook.ui.setlists.SetlistDetailRoute

/** Something outside the drawer asks it to show a setlist (the stage's menu, a setlist just received or imported). */
data class SetlistRequest(val setId: String, val share: Boolean = false, val nonce: Long = System.nanoTime())

/**
 * The Setlists tab of the library drawer: the list, a setlist opened (its songs, their order, breaks and sharing) and the
 * picker that adds songs to it, one over the other with their own back stack, so there is no screen of the app for any of
 * them. [list] draws the setlists and is given the way to open one ([open]: the id, and whether to start on the share sheet).
 * A tap on a song of an open setlist plays it ([onPlay]: setlist, song position).
 */
@Composable
fun SetlistsPane(
    request: SetlistRequest?,
    onRequestHandled: () -> Unit,
    onPlay: (setId: String, index: Int) -> Unit,
    list: @Composable (open: (id: String, share: Boolean) -> Unit) -> Unit,
) {
    val inner = rememberNavController()
    LaunchedEffect(request) {
        request?.let {
            inner.navigate(SetlistDetail(it.setId, it.share)) { launchSingleTop = true }
            onRequestHandled()
        }
    }
    NavHost(inner, startDestination = SetlistsList, enterTransition = { fadeIn() }, exitTransition = { fadeOut() }) {
        composable<SetlistsList> { list { id, share -> inner.navigate(SetlistDetail(id, share)) } }
        composable<SetlistDetail> { entry ->
            SetlistDetailRoute(
                onBack = { inner.popBackStack() },
                onAddSongs = { inner.navigate(PickSongs(it)) },
                onPlayAt = onPlay,
                openShare = remember { entry.toRoute<SetlistDetail>().share },
            )
        }
        composable<PickSongs> { PickSongsRoute(onDone = { inner.popBackStack() }) }
    }
}
