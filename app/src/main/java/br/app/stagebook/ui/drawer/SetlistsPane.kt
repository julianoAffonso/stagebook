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
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
    toList: Int = 0,   // a new number = the Setlists tab tapped again while it shows: back to the list
    list: @Composable (open: (id: String, share: Boolean) -> Unit) -> Unit,
) {
    val inner = rememberNavController()
    LaunchedEffect(request) {
        request?.let {
            // from the list, so another setlist (or the same, on its share sheet) opens as a new entry with its own view model
            inner.popBackStack<SetlistsList>(inclusive = false)
            inner.navigate(SetlistDetail(it.setId, it.share))
            onRequestHandled()
        }
    }
    // an open setlist leaves by its own way (it may ask about an edit); the song picker goes straight back to the list
    var toListSeen by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(toList) }
    var detailToList by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
    LaunchedEffect(toList) {
        if (toList == toListSeen) return@LaunchedEffect
        toListSeen = toList
        val here = inner.currentDestination
        when {
            here?.hasRoute<SetlistDetail>() == true -> detailToList++
            here?.hasRoute<SetlistsList>() == false -> inner.popBackStack<SetlistsList>(inclusive = false)
        }
    }
    NavHost(inner, startDestination = SetlistsList, enterTransition = { fadeIn() }, exitTransition = { fadeOut() }) {
        // only while the screen is in front, as the app's own navigation does: a screen fading out still takes taps, and
        // a double tap on "back" popped the list too (the tab went blank), on a row opened two setlists (audit 22/11)
        composable<SetlistsList> { entry -> list { id, share -> if (entry.inFront()) inner.navigate(SetlistDetail(id, share)) } }
        composable<SetlistDetail> { entry ->
            SetlistDetailRoute(
                onBack = { if (entry.inFront()) inner.popBackStack<SetlistDetail>(inclusive = true) },
                onAddSongs = { if (entry.inFront()) inner.navigate(PickSongs(it)) },
                onPlayAt = onPlay,
                openShare = remember { entry.toRoute<SetlistDetail>().share },
                toList = detailToList,
            )
        }
        composable<PickSongs> { entry -> PickSongsRoute(onDone = { if (entry.inFront()) inner.popBackStack<PickSongs>(inclusive = true) }) }
    }
}

private fun androidx.navigation.NavBackStackEntry.inFront(): Boolean = lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
