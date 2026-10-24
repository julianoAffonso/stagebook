package com.juliano.stagebook.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.juliano.stagebook.ui.editor.SongEditorRoute
import com.juliano.stagebook.ui.home.HomeScreen
import com.juliano.stagebook.ui.importer.ImportRoute
import com.juliano.stagebook.ui.setlists.PickSongsRoute
import com.juliano.stagebook.ui.setlists.SetlistDetailRoute
import com.juliano.stagebook.ui.stage.StageRoute
import com.juliano.stagebook.ui.AppUpdateDialog
import com.juliano.stagebook.data.UpdateOffer

// Voltar e abrir outra tela só valem com a tela ainda na frente: um toque duplo em Salvar/OK/Voltar
// desempilhava duas telas (a segunda era a Home, e o app ficava em branco), e um toque duplo no play
// empilhava dois palcos (e o de baixo voltava com a tela podendo apagar).
private inline fun NavBackStackEntry.ifResumed(go: () -> Unit) {
    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) go()
}

/** [updateOffer]: versão nova do app; avisa por cima de qualquer tela, nunca do palco. */
@Composable
fun StagebookNavHost(
    updateOffer: UpdateOffer? = null,
    updateActions: AppUpdateActions = AppUpdateActions(),
) {
    val nav = rememberNavController()
    if (updateOffer != null) AppUpdateDialog(updateOffer, updateActions.onUpdate, updateActions.onLater, updateActions.onInstall, updateActions.onClose)
    NavHost(
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
            )
        }
        composable<SongEditor> { SongEditorRoute(onDone = dropUnlessResumed { nav.popBackStack() }) }
        composable<SetlistDetail> { entry ->
            SetlistDetailRoute(
                onBack = dropUnlessResumed { nav.popBackStack() },
                onPlay = { setId, start -> entry.ifResumed { nav.navigate(Stage(setId = setId, start = start)) } },
                onAddSongs = { entry.ifResumed { nav.navigate(PickSongs(it)) } },
                onEditSong = { entry.ifResumed { nav.navigate(SongEditor(songId = it)) } },
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
                // o editor substitui o navegador: salvar ou cancelar volta para a lista
                onOpenEditor = { id -> nav.navigate(SongEditor(songId = id, fromImport = true)) { popUpTo<Importer> { inclusive = true } } },
            )
        }
    }
}

/** O que o aviso de versão nova faz (ligado ao AppUpdateOffer pela MainActivity). */
data class AppUpdateActions(
    val onUpdate: () -> Unit = {},
    val onLater: () -> Unit = {},
    val onInstall: () -> Unit = {},
    val onClose: () -> Unit = {},
)
