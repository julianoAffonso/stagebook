package com.juliano.stagebook.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.juliano.stagebook.R
import com.juliano.stagebook.ui.theme.Sizes
import com.juliano.stagebook.ui.theme.Spacing

/**
 * Bordas que as telas respeitam: o app esconde as barras do sistema, então só a câmera nas laterais (celular
 * deitado); em cima o conteúdo sobe até o topo (no S26 a folga da câmera em pé desperdiçava espaço).
 */
val ScreenInsets: WindowInsets
    @Composable get() = WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)

/**
 * Cabeçalho de tela: voltar (opcional), título grande, subtítulo e ações à direita. No celular o subtítulo
 * corre numa linha própria abaixo do cabeçalho, na largura toda (entre o título e os ícones ele ficava
 * espremido em várias linhas); no tablet fica embaixo do título.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val wide = wideLists()
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(AppIcons.Back, contentDescription = stringResource(R.string.action_back)) }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    // padrão do Google: tela principal com título grande; tela interna (com a seta) no celular com o
                    // título menor da barra de cima, para não ocupar duas linhas; no tablet sempre grande (cabe)
                    style = when {
                        wide -> MaterialTheme.typography.headlineLarge
                        onBack != null -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.headlineMedium
                    },
                    color = MaterialTheme.colorScheme.onBackground,
                    // barra de cima da tela interna no celular: uma linha só, com "…" (como o Google faz)
                    maxLines = if (!wide && onBack != null) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (wide && subtitle.isNotEmpty()) Subtitle(subtitle)
            }
            actions()
        }
        if (!wide && subtitle.isNotEmpty()) {
            // alinhado com o título (depois do botão de voltar)
            Subtitle(subtitle, Modifier.padding(start = if (onBack != null) Sizes.touch + Spacing.s else 0.dp))
        }
    }
}

@Composable
private fun Subtitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** Conteúdo centralizado com largura máxima: no tablet deitado as linhas não ficam esticadas demais. */
@Composable
fun CenteredContent(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = Sizes.contentMax).fillMaxWidth(), content = content)
    }
}

@LightPreview
@Composable
private fun ScreenHeaderLightPreview() = PreviewSurface(dark = false) {
    ScreenHeader("Músicas", subtitle = "42 músicas")
}

@DarkPreview
@Composable
private fun ScreenHeaderDarkPreview() = PreviewSurface(dark = true) {
    ScreenHeader("Show de sábado", subtitle = "12 músicas", onBack = {}) {
        IconButton(onClick = {}) { Icon(AppIcons.Edit, contentDescription = null) }
    }
}
