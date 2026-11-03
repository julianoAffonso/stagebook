package br.app.stagebook.ui.importer

import br.app.stagebook.data.ImportProblem
import br.app.stagebook.ui.components.QuestionDialog
import androidx.core.net.toUri
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberUpdatedState
import br.app.stagebook.data.ImportQueue
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.app.stagebook.R
import br.app.stagebook.data.ExtractScript
import br.app.stagebook.ui.components.ActionButton
import br.app.stagebook.ui.components.AppIcons
import br.app.stagebook.ui.components.LoadingOverlay
import br.app.stagebook.ui.components.ScreenInsets
import br.app.stagebook.ui.theme.Spacing

@Composable
fun ImportRoute(
    onClose: () -> Unit,
    onOpenEditor: (String?) -> Unit,
    viewModel: ImportViewModel = hiltViewModel(),
    onSetlistReady: (String) -> Unit = { onClose() },
    onOpenEditorQueued: (String?) -> Unit = onOpenEditor,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val queue by viewModel.queueState.collectAsStateWithLifecycle()
    // in the queue, the importer sits below the editor (cancel goes back to the same song)
    val appMessage = br.app.stagebook.ui.components.LocalAppMessage.current
    val offlineNote = stringResource(R.string.import_offline_note)
    LaunchedEffect(viewModel) { viewModel.offlineImport.collect { appMessage.show(offlineNote, long = true) } }
    LaunchedEffect(viewModel) { viewModel.openEditor.collect { if (viewModel.queueState.value != null) onOpenEditorQueued(it) else onOpenEditor(it) } }
    // the page has tablature: import it too? (asks every time)
    if (uiState.askTab) {
        QuestionDialog(
            stringResource(R.string.import_tab_title), stringResource(R.string.import_tab_message),
            stringResource(R.string.import_tab_yes), { viewModel.answerTab(true) },
            stringResource(R.string.import_tab_no), { viewModel.answerTab(false) },
        )
    }
    ImportScreen(
        uiState = uiState,
        initialQuery = viewModel.initialQuery,
        home = viewModel.home,
        urlFor = viewModel::urlFor,
        // leaving mid-queue: stops the queue and opens the setlist with what came in
        onClose = { viewModel.queueState.value?.let { q -> viewModel.queueStop(); onSetlistReady(q.setId) } ?: onClose() },
        onExtracted = viewModel::onExtracted,
        onProblemShown = viewModel::problemShown,
        queue = queue,
        startUrl = viewModel.queueSearch(),
        queueSite = viewModel::queueSiteName,
        queueHost = viewModel::queueHost,
        onQueueFailover = viewModel::queueFailover,
        onQueueSkip = viewModel::queueSkip,
        onQueueStop = { setId -> viewModel.queueStop(); onSetlistReady(setId) },
        onQueueDone = onSetlistReady,
    )
}

/** Browser inside the app: the singer opens the lyrics page and taps "Import these lyrics". */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ImportScreen(
    uiState: ImportUiState,
    initialQuery: String,
    onClose: () -> Unit,
    onExtracted: (String?) -> Unit,
    onProblemShown: () -> Unit,
    modifier: Modifier = Modifier,
    home: String = ImportViewModel.HOME,
    urlFor: (String) -> String = { ImportViewModel.urlFor(it) },
    // batch import for a setlist (queue): the search for the current song and the failover between the sites in the
    // list
    queue: ImportQueue.State? = null,
    startUrl: String? = null,
    queueSite: () -> String = { "" },
    queueHost: () -> String = { "" },
    onQueueFailover: () -> String? = { null },
    onQueueSkip: () -> String? = { null },
    onQueueStop: (setId: String) -> Unit = {},
    onQueueDone: (setId: String) -> Unit = {},
) {
    val latestQueue by rememberUpdatedState(queue)
    // only the search the queue opened decides the failover (a search the user typed doesn't)
    var queueQuery by remember { mutableStateOf(startUrl?.let { it.toUri().getQueryParameter("q") }) }
    fun loadQueue(view: WebView?, url: String) { queueQuery = url.toUri().getQueryParameter("q"); view?.loadUrl(url) }
    var exhausted by rememberSaveable { mutableStateOf(false) }   // no site in the list has the current song
    val context = LocalContext.current
    var address by rememberSaveable { mutableStateOf(initialQuery) }
    var progress by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    // the app's first WebView loads all of Chromium (up to ~1 s on the Fire): the screen shows first (address bar and
    // "loading") and the browser is created on the next frame
    var webView by remember { mutableStateOf<WebView?>(null) }
    fun newWebView() =
        WebView(context).apply {
            settings.javaScriptEnabled = true   // lyrics extraction runs as a script on the page
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) { address = url }
                // queue: the Google search limited to the site brought nothing from it → tries the next one in the list
                // on its own
                override fun onPageFinished(view: WebView, url: String) {
                    if (latestQueue == null || !isGoogleSearch(url) || url.toUri().getQueryParameter("q") != queueQuery) return
                    val host = queueHost()
                    if (host.isEmpty()) return
                    view.evaluateJavascript(resultsScript(host)) { n ->
                        if (n?.trim('"') != "0") return@evaluateJavascript
                        val next = onQueueFailover()
                        if (next != null) loadQueue(view, next) else exhausted = true
                    }
                }
                override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) { canGoBack = view.canGoBack() }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress }
            }
        }
    LaunchedEffect(Unit) {
        withFrameNanos { }   // let the screen show first
        webView = newWebView().also { it.loadUrl(startUrl ?: if (initialQuery.isBlank()) home else urlFor(initialQuery)) }
    }
    // lyrics sites have ads and scripts: with the app in the background or the screen off, they stop
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(webView, lifecycle) {
        val webView = webView ?: return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> { webView.onPause(); webView.pauseTimers() }
                Lifecycle.Event.ON_RESUME -> { webView.resumeTimers(); webView.onResume() }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            webView.stopLoading()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
    }
    BackHandler(enabled = canGoBack) { webView?.goBack() }

    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val notFound = stringResource(R.string.import_not_found)
    val unreadable = stringResource(R.string.import_unreadable)
    val notSong = stringResource(R.string.not_song)
    val searchPage = stringResource(R.string.import_search_page)
    LaunchedEffect(uiState.problem) {
        val p = uiState.problem ?: return@LaunchedEffect
        snackbar.showSnackbar(
            when (p) {
                ImportProblem.NOT_FOUND -> notFound
                ImportProblem.NOT_SONG -> notSong
                ImportProblem.UNREADABLE -> unreadable
                ImportProblem.SEARCH_PAGE -> searchPage
            },
        )
        onProblemShown()
    }

    Scaffold(
        contentWindowInsets = ScreenInsets,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                ActionButton(
                    stringResource(R.string.import_this),
                    onClick = { webView?.evaluateJavascript(ExtractScript.SOURCE, onExtracted) },
                    modifier = Modifier.fillMaxWidth().padding(Spacing.m),
                    icon = AppIcons.Download,
                    enabled = !uiState.busy,
                )
            }
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = Spacing.m, end = Spacing.s, top = Spacing.s, bottom = Spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    // back arrow, like on every inner screen (Google Photos pattern)
                    br.app.stagebook.ui.components.BackButton(onClose, tint = androidx.compose.material3.LocalContentColor.current)
                    TextField(
                        value = address,
                        onValueChange = { address = it },
                        placeholder = { Text(stringResource(R.string.import_address_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        // the page shows up whole: the keyboard closes on Go (device test B-03)
                        keyboardActions = KeyboardActions(onGo = { webView?.loadUrl(urlFor(address)); keyboard?.hide(); focus.clearFocus() }),
                        shape = MaterialTheme.shapes.medium,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { webView?.loadUrl(urlFor(address)); keyboard?.hide(); focus.clearFocus() }) {
                        Icon(AppIcons.Search, stringResource(R.string.import_go))
                    }
                }
                PageProgress { progress }
                queue?.let { q ->
                    QueueBar(
                        q, queueSite(), exhausted,
                        onSkip = {
                            exhausted = false
                            val next = onQueueSkip()
                            if (next != null) loadQueue(webView, next) else onQueueDone(q.setId)
                        },
                        onStop = { onQueueStop(q.setId) },
                    )
                }
                // personal use: the app doesn't store or distribute songs (short notice, can be closed)
                var notice by rememberSaveable { mutableStateOf(true) }
                AnimatedVisibility(notice) {
                    Row(Modifier.fillMaxWidth().padding(start = Spacing.m), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Icon(AppIcons.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.import_personal_use), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        IconButton(onClick = { notice = false }) { Icon(AppIcons.Close, stringResource(R.string.action_close)) }
                    }
                }
                webView?.let { w -> AndroidView(factory = { w }, modifier = Modifier.fillMaxWidth().weight(1f)) }
                    ?: Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            LoadingOverlay(visible = uiState.busy, message = stringResource(R.string.import_busy))
        }
    }
}

/** Page progress: read in here, each update (dozens per page) only recomposes the bar. */
@Composable
private fun PageProgress(progress: () -> Int) {
    val p = progress()
    AnimatedVisibility(visible = p in 1..99) {
        LinearProgressIndicator(progress = { p / 100f }, modifier = Modifier.fillMaxWidth())
    }
}

// the queue strip: for which setlist, which song (2 of 5), which site it's searching; Skip and Stop
@Composable
private fun QueueBar(q: ImportQueue.State, site: String, exhausted: Boolean, onSkip: () -> Unit, onStop: () -> Unit) {
    val item = q.current ?: return
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                stringResource(R.string.queue_title, q.setName, q.position, q.total, item.title),
                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (exhausted) br.app.stagebook.ui.components.StatusBox(br.app.stagebook.ui.components.StatusTone.PROBLEM) {
                br.app.stagebook.ui.components.SettingsLinkText(
                    stringResource(R.string.queue_none), stringResource(R.string.queue_sites_link),
                    br.app.stagebook.ui.settings.SettingsSection.IMPORT, style = MaterialTheme.typography.bodyMedium,
                    color = androidx.compose.material3.LocalContentColor.current,
                )
            } else Text(
                stringResource(R.string.queue_searching, site),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                TextButton(onClick = onSkip) { Text(stringResource(R.string.queue_skip)) }
                TextButton(onClick = onStop) { Text(stringResource(R.string.queue_stop)) }
            }
        }
    }
}

private fun isGoogleSearch(url: String): Boolean = url.toUri().let { it.host.orEmpty().contains("google.") && it.path == "/search" }

// how many search results lead to the site (directly or through Google's /url?q=); read only, nothing is clicked
private fun resultsScript(host: String) = """
    (function(h){
      return Array.from(document.querySelectorAll('a[href]')).filter(function(a){
        try {
          var u = new URL(a.href);
          if (u.host.indexOf('google') < 0) return u.host === h || u.host.endsWith('.' + h);
          var q = u.searchParams.get('q') || u.searchParams.get('url') || '';
          return u.pathname === '/url' && q.indexOf(h) >= 0;
        } catch (e) { return false; }
      }).length;
    })(${'"'}$host${'"'})
""".trimIndent()
