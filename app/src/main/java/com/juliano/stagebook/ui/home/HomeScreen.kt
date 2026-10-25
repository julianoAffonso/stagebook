package com.juliano.stagebook.ui.home

import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.foundation.layout.Column
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.juliano.stagebook.ui.share.InboxViewModel
import com.juliano.stagebook.ui.share.InboxDialog
import com.juliano.stagebook.ui.share.InboxBanner
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
    onReceiveCode: (String) -> Unit = {},
    onOpenReceived: (String) -> Unit = {},
    inboxVm: InboxViewModel = hiltViewModel(),
) {
    var tab by rememberSaveable { mutableStateOf(HomeTab.SONGS) }
    // o que a banda mandou: aviso no topo das abas (o mais novo ainda não dispensado), número no ícone e a lista
    val inbox by inboxVm.items.collectAsStateWithLifecycle()
    val hasBand by inboxVm.hasBand.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val onSync: (() -> Unit)? = if (hasBand) {
        {
            inboxVm.syncNow()
            android.widget.Toast.makeText(context, R.string.import_syncing, android.widget.Toast.LENGTH_SHORT).show()
        }
    } else null
    val banner = inbox.lastOrNull { !it.seen }
    var listOpen by rememberSaveable { mutableStateOf(false) }
    val badges = mapOf(
        HomeTab.SONGS to inbox.count { it.setName.isEmpty() },
        HomeTab.SETLISTS to inbox.count { it.setName.isNotEmpty() },
    )
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
    HomeScaffold(tab = tab, onTabChange = onTab, badges = badges) { m ->
        Column(m) {
            if (tab != HomeTab.SETTINGS && banner != null) {
                InboxBanner(
                    banner, onOpen = { onOpenReceived(banner.id) }, onLater = { inboxVm.later(banner.id) },
                    Modifier.padding(horizontal = Spacing.m).padding(top = Spacing.s),
                )
            }
            Box(Modifier.weight(1f)) {
                tabs.SaveableStateProvider(if (tab == HomeTab.SETTINGS) "${HomeTab.SETTINGS}$settingsVisit" else tab.name) {
                    when (tab) {
                        HomeTab.SONGS -> SongsRoute(
                            onOpenSong, onEditSong, onImport, onScan = onScanSetlist, onReceiveCode = onReceiveCode,
                            received = inbox.size, onReceived = { listOpen = true }, onSync = onSync,
                        )
                        HomeTab.SETLISTS -> SetlistsRoute(onOpenSetlist, onPlaySetlist, onScanSetlist, onReceiveCode, received = inbox.size, onReceived = { listOpen = true }, onSync = onSync)
                        HomeTab.SETTINGS -> SettingsRoute()
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
 * Moldura das abas, sem estado, no padrão atual do Google (Material 3 Expressive): no tablet a lateral
 * larga, que abre com o botão de menu para mostrar os nomes; no celular a barra baixa. O conteúdo recebe o
 * Modifier com o espaço que sobra.
 */
@Composable
fun HomeScaffold(tab: HomeTab, onTabChange: (HomeTab) -> Unit, badges: Map<HomeTab, Int> = emptyMap(), content: @Composable (Modifier) -> Unit) {
    // número de novidades da banda no ícone da aba (como e-mail novo)
    val badged: @Composable (HomeTab) -> Unit = { t ->
        val n = badges[t] ?: 0
        if (n > 0) BadgedBox(badge = { Badge { Text(n.toString()) } }) { Icon(t.icon, contentDescription = null) }
        else Icon(t.icon, contentDescription = null)
    }
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
                                icon = { badged(t) },
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
                                icon = { badged(t) },
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
