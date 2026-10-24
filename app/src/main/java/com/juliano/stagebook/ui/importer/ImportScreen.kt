package com.juliano.stagebook.ui.importer

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
fun ImportRoute(onClose: () -> Unit, onOpenEditor: (String?) -> Unit, viewModel: ImportViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.openEditor.collect { onOpenEditor(it) } }
    ImportScreen(
        uiState = uiState,
        initialQuery = viewModel.initialQuery,
        onClose = onClose,
        onExtracted = viewModel::onExtracted,
        onProblemShown = viewModel::problemShown,
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
) {
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
                override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) { canGoBack = view.canGoBack() }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress }
            }
        }
    LaunchedEffect(Unit) {
        withFrameNanos { }   // deixa a tela aparecer antes
        webView = newWebView().also { it.loadUrl(if (initialQuery.isBlank()) ImportViewModel.HOME else ImportViewModel.urlFor(initialQuery)) }
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
                        keyboardActions = KeyboardActions(onGo = { webView?.loadUrl(ImportViewModel.urlFor(address)) }),
                        shape = MaterialTheme.shapes.medium,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { webView?.loadUrl(ImportViewModel.urlFor(address)) }) {
                        Icon(AppIcons.Search, stringResource(R.string.import_go))
                    }
                }
                PageProgress { progress }
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
