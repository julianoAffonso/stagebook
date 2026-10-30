package br.app.stagebook.ui.theme

import androidx.compose.ui.unit.dp

/** Grade de 8dp. */
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 16.dp
    val l = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}

object Sizes {
    val touch = 48.dp           // alvo mínimo de toque
    val fader = 200.dp          // altura do fader de pé (mixer e EQ no tablet)
    val meter = 8.dp            // largura da barra dos medidores ao vivo
    val button = 56.dp          // botões principais (dedo no palco, tablet longe)
    val row = 72.dp             // linha de música
    val swipeTrigger = 56.dp    // linha aberta pelo arraste: só o botão do ícone (24) + margem (16) de cada lado
    val swatch = 48.dp
    val fontPreview = 160.dp    // caixa fixa da prévia do tamanho da letra (não empurra a tela)
    val settingsList = 360.dp   // lista de seções dos ajustes no tablet
    val contentMax = 840.dp     // largura máxima do conteúdo no tablet deitado
    val pageDot = 28.dp         // bolinha numerada das páginas no rodapé do palco
    val swipe = 64.dp           // arrastar no palco: a partir daqui, vira a página para o lado do arraste
    val railBreakpoint = 600.dp // a partir daqui, menu lateral em vez de barra embaixo
}
