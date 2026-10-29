package com.juliano.stagebook.domain

/**
 * O que pode passar entre aparelhos (banda, 3 palavras): só setlist e músicas em texto puro (letra, cifra, tablatura).
 * Tudo o que chega passa por aqui antes de entrar na caixa de entrada; o que não parece letra é descartado em silêncio:
 * texto grande demais, caracteres de controle ou blocos longos sem espaço com cara de arquivo codificado (base64 e afins:
 * o jeito de esconder uma foto ou um programa dentro de "texto"). Linhas de tablatura (`e|---0---3---|`) passam.
 */
object BandContent {
    const val MAX_TITLE = 200
    const val MAX_YEAR = 10
    const val MAX_TEXT = 60_000      // uma cifra longa tem uns 10 mil caracteres
    const val MAX_SONGS = 200
    const val MAX_LIST = 20_000      // o texto do setlist (nome + títulos)
    private const val LONG_TOKEN = 200
    private const val ENCODED_SHARE = 0.9

    /** Texto puro: sem caracteres de controle (fora quebra de linha e tabulação) nem bloco codificado. */
    fun isPlainText(s: String): Boolean =
        s.none { it.isISOControl() && it != '\n' && it != '\r' && it != '\t' } && s.splitToSequence(' ', '\n', '\t', '\r').none(::looksEncoded)

    // bloco longo quase só de letras/números/+/=: dado codificado. Tablatura é quase só '-', e não cai aqui
    private fun looksEncoded(token: String): Boolean {
        if (token.length < LONG_TOKEN) return false
        val encoded = token.count { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' || it == '_' }
        return encoded >= token.length * ENCODED_SHARE
    }

    fun songOk(title: String, author: String, year: String, text: String): Boolean =
        title.isNotBlank() && title.length <= MAX_TITLE && author.length <= MAX_TITLE && year.length <= MAX_YEAR &&
            text.length <= MAX_TEXT && isPlainText(title) && isPlainText(author) && isPlainText(year) && isPlainText(text)

    /** O texto do setlist (formato do QR): tamanho, texto puro e lido como setlist. */
    fun listOk(text: String): Boolean = text.length <= MAX_LIST && isPlainText(text) && QrPayload.parse(text) != null
}
