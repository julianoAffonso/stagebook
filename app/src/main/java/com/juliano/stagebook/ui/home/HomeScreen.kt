package com.juliano.stagebook.ui.home

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailItemDefaults
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.juliano.stagebook.R
import com.juliano.stagebook.ui.components.AppIcons
import com.juliano.stagebook.ui.components.ScreenInsets
import com.juliano.stagebook.ui.setlists.SetlistsRoute
import com.juliano.stagebook.ui.settings.SettingsRoute
import com.juliano.stagebook.ui.songs.SongsRoute
import com.juliano.stagebook.ui.theme.Sizes
import com.juliano.stagebook.ui.theme.Spacing
import kotlinx.coroutines.launch

enum class HomeTab(@StringRes val label: Int, val icon: ImageVector) {
    SONGS(R.string.tab_songs, AppIcons.MusicNote),
    SETLISTS(R.string.tab_setlists, AppIcons.QueueMusic),
    SETTINGS(R.string.tab_settings, AppIcons.Tune),
}

/** Abas Músicas, Setlists e Ajustes: barra baixa no celular, lateral larga no tablet (Material 3 Expressive). */
@Composable
fun HomeScreen(
    onOpenSong: (String) -> Unit,
    onEditSong: (String?) -> Unit,
    onImport: (String) -> Unit,
    onOpenSetlist: (String) -> Unit,
    onPlaySetlist: (String) -> Unit,
    onScanSetlist: () -> Unit = {},
) {
    var tab by rememberSaveable { mutableStateOf(HomeTab.SONGS) }
    // voltar numa aba que não é a primeira volta para Músicas (em vez de fechar o app)
    BackHandler(enabled = tab != HomeTab.SONGS) { tab = HomeTab.SONGS }

    // cada aba guarda o próprio estado (busca, posição da lista) ao trocar; sem fade, que desenhava as
    // duas telas cheias ao mesmo tempo no Fire
    val tabs = rememberSaveableStateHolder()
    // Ajustes abre sempre na lista do começo (não na última seção vista): cada toque no ícone é uma visita nova
    var settingsVisit by rememberSaveable { mutableIntStateOf(0) }
    val onTab: (HomeTab) -> Unit = { t ->
        if (t == HomeTab.SETTINGS) {
            tabs.removeState("${HomeTab.SETTINGS}$settingsVisit")
            settingsVisit++
        }
        tab = t
    }
    HomeScaffold(tab = tab, onTabChange = onTab) { m ->
        Box(m) {
            tabs.SaveableStateProvider(if (tab == HomeTab.SETTINGS) "${HomeTab.SETTINGS}$settingsVisit" else tab.name) {
                when (tab) {
                    HomeTab.SONGS -> SongsRoute(onOpenSong, onEditSong, onImport)
                    HomeTab.SETLISTS -> SetlistsRoute(onOpenSetlist, onPlaySetlist, onScanSetlist)
                    HomeTab.SETTINGS -> SettingsRoute()
                }
            }
        }
    }
}

/**
 * Moldura das abas, sem estado, no padrão atual do Google (Material 3 Expressive): no tablet a lateral
 * larga, que abre com o botão de menu para mostrar os nomes; no celular a barra baixa. O conteúdo recebe o
 * Modifier com o espaço que sobra.
 */
@Composable
fun HomeScaffold(tab: HomeTab, onTabChange: (HomeTab) -> Unit, content: @Composable (Modifier) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= Sizes.railBreakpoint) {
            val rail = rememberWideNavigationRailState()
            val scope = rememberCoroutineScope()
            val expanded = rail.targetValue == WideNavigationRailValue.Expanded
            Scaffold(contentWindowInsets = ScreenInsets, containerColor = MaterialTheme.colorScheme.background) { inner ->
                Row(Modifier.padding(inner)) {
                    WideNavigationRail(
                        state = rail,
                        colors = WideNavigationRailDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                        header = {
                            IconButton(onClick = { scope.launch { rail.toggle() } }, modifier = Modifier.padding(start = Spacing.l)) {
                                Icon(
                                    if (expanded) AppIcons.MenuOpen else AppIcons.Menu,
                                    contentDescription = stringResource(if (expanded) R.string.nav_collapse else R.string.nav_expand),
                                )
                            }
                        },
                    ) {
                        HomeTab.entries.forEach { t ->
                            WideNavigationRailItem(
                                selected = tab == t,
                                onClick = { onTabChange(t) },
                                icon = { Icon(t.icon, contentDescription = null) },
                                label = { Text(stringResource(t.label)) },
                                railExpanded = expanded,
                                colors = railColors(),
                            )
                        }
                    }
                    content(Modifier.weight(1f))
                }
            }
        } else {
            Scaffold(
                contentWindowInsets = ScreenInsets,
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    ShortNavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        HomeTab.entries.forEach { t ->
                            ShortNavigationBarItem(
                                selected = tab == t,
                                onClick = { onTabChange(t) },
                                icon = { Icon(t.icon, contentDescription = null) },
                                label = { Text(stringResource(t.label), maxLines = 1) },
                                colors = barColors(),
                            )
                        }
                    }
                },
            ) { inner -> Box(Modifier.padding(inner)) { content(Modifier.fillMaxSize()) } }
        }
    }
}

// aba selecionada só com ícone e nome na cor de destaque, sem a pílula de fundo (ícones simples no app todo)
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
