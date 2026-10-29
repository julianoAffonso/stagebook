package com.juliano.stagebook.ui.importer

import androidx.core.net.toUri
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberUpdatedState
import com.juliano.stagebook.data.ImportQueue
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
import com.juliano.stagebook.R
import com.juliano.stagebook.data.ExtractScript
import com.juliano.stagebook.ui.components.ActionButton
import com.juliano.stagebook.ui.components.AppIcons
import com.juliano.stagebook.ui.components.LoadingOverlay
import com.juliano.stagebook.ui.components.ScreenInsets
import com.juliano.stagebook.ui.theme.Spacing

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
    // na fila, o importador fica embaixo do editor (cancelar volta para a mesma música)
    LaunchedEffect(viewModel) { viewModel.openEditor.collect { if (viewModel.queueState.value != null) onOpenEditorQueued(it) else onOpenEditor(it) } }
    // a página tem tablatura: importar junto? (pergunta a cada vez)
    if (uiState.askTab) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { viewModel.answerTab(false) },
            title = { Text(stringResource(R.string.import_tab_title)) },
            text = { Text(stringResource(R.string.import_tab_message)) },
            confirmButton = { TextButton(onClick = { viewModel.answerTab(true) }) { Text(stringResource(R.string.import_tab_yes)) } },
            dismissButton = { TextButton(onClick = { viewModel.answerTab(false) }) { Text(stringResource(R.string.import_tab_no)) } },
        )
    }
    ImportScreen(
        uiState = uiState,
        initialQuery = viewModel.initialQuery,
        home = viewModel.home,
        urlFor = viewModel::urlFor,
        // sair no meio da fila: para a fila e abre o setlist com o que já entrou
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

/** Navegador dentro do app: o vocalista abre a página da letra e toca "Importar esta letra". */
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
    // importação em lote para um setlist (fila): a busca da música da vez e o failover entre os sites da lista
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
    // só a busca que a fila abriu decide o failover (uma busca que o usuário digitou não)
    var queueQuery by remember { mutableStateOf(startUrl?.let { it.toUri().getQueryParameter("q") }) }
    fun loadQueue(view: WebView?, url: String) { queueQuery = url.toUri().getQueryParameter("q"); view?.loadUrl(url) }
    var exhausted by rememberSaveable { mutableStateOf(false) }   // nenhum site da lista tem a música da vez
    val context = LocalContext.current
    var address by rememberSaveable { mutableStateOf(initialQuery) }
    var progress by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    // o primeiro WebView do app carrega o Chromium inteiro (até ~1 s no Fire): a tela aparece primeiro
    // (barra de endereço e "carregando") e o navegador é criado no quadro seguinte
    var webView by remember { mutableStateOf<WebView?>(null) }
    fun newWebView() =
        WebView(context).apply {
            settings.javaScriptEnabled = true   // a extração da letra roda como script na página
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) { address = url }
                // fila: a busca no Google restrita ao site não trouxe nada do site → tenta o próximo da lista sozinho
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
        withFrameNanos { }   // deixa a tela aparecer antes
        webView = newWebView().also { it.loadUrl(startUrl ?: if (initialQuery.isBlank()) home else urlFor(initialQuery)) }
    }
    // sites de letra têm anúncios e scripts: com o app em segundo plano ou a tela apagada, param
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

    val notFound = stringResource(R.string.import_not_found)
    val unreadable = stringResource(R.string.import_unreadable)
    LaunchedEffect(uiState.problem) {
        val p = uiState.problem ?: return@LaunchedEffect
        snackbar.showSnackbar(if (p == ImportProblem.NOT_FOUND) notFound else unreadable)
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
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.s, vertical = Spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    // seta de voltar, como em todas as telas internas (padrão do Google Fotos)
                    IconButton(onClick = onClose) { Icon(AppIcons.Back, stringResource(R.string.action_back)) }
                    TextField(
                        value = address,
                        onValueChange = { address = it },
                        placeholder = { Text(stringResource(R.string.import_address_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { webView?.loadUrl(urlFor(address)) }),
                        shape = MaterialTheme.shapes.medium,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { webView?.loadUrl(urlFor(address)) }) {
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
                // uso pessoal: o app não guarda nem distribui músicas (aviso curto, dá para fechar)
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

/** Progresso da página: lido aqui dentro, cada atualização (dezenas por página) só recompõe a barra. */
@Composable
private fun PageProgress(progress: () -> Int) {
    val p = progress()
    AnimatedVisibility(visible = p in 1..99) {
        LinearProgressIndicator(progress = { p / 100f }, modifier = Modifier.fillMaxWidth())
    }
}

// a faixa da fila: para qual setlist, qual música (2 de 5), em qual site está buscando; Pular e Parar
@Composable
private fun QueueBar(q: ImportQueue.State, site: String, exhausted: Boolean, onSkip: () -> Unit, onStop: () -> Unit) {
    val item = q.current ?: return
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                stringResource(R.string.queue_title, q.setName, q.position, q.total, item.title),
                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                if (exhausted) stringResource(R.string.queue_none) else stringResource(R.string.queue_searching, site),
                style = MaterialTheme.typography.bodySmall,
                color = if (exhausted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                TextButton(onClick = onSkip) { Text(stringResource(R.string.queue_skip)) }
                TextButton(onClick = onStop) { Text(stringResource(R.string.queue_stop)) }
            }
        }
    }
}

private fun isGoogleSearch(url: String): Boolean = url.toUri().let { it.host.orEmpty().contains("google.") && it.path == "/search" }

// quantos resultados da busca levam ao site (direto ou pelo /url?q= do Google); só leitura, nada é clicado
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
