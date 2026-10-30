package br.app.stagebook.domain

import java.util.Locale

/**
 * Cleaning lyrics imported from a site: removes loose chords and the translation (only the original language stays).
 */
object LyricsCleaner {
    private const val CHORD = "[A-G][#b]?(m|maj|min|dim|aug|sus|add|M|º|°|\\+)?[0-9]*(\\([^)]*\\))?(/[A-G][#b]?)?"
    private val chordLine = Regex("($CHORD\\s*)+")
    private val trailingSpace = Regex("\\s+$")
    private val manyBlank = Regex("\n{3,}")

    fun clean(raw: String): String {
        val kept = raw.replace("\r", "").split('\n')
            .map { it.replace(trailingSpace, "") }
            .filterNot { it.isNotBlank() && chordLine.matches(it.trim()) }
        return keepOriginalLanguage(kept.joinToString("\n")).replace(manyBlank, "\n\n").trim()
    }

    enum class Lang { EN, PT }

    // common words with no ambiguity between English and Portuguese ("a", "no", "me", "do", "as", "so" are left out)
    private val english = setOf(
        "the", "you", "i", "i'm", "im", "and", "to", "my", "of", "it", "it's", "is", "your", "that", "on", "for",
        "we", "be", "don't", "dont", "love", "with", "all", "this", "know", "like", "was", "will", "just", "when",
        "she", "he", "they", "are", "have", "but", "baby", "never", "down", "up", "out", "there", "now", "go",
        "can't", "you're", "if", "at", "from", "one", "way", "say", "want", "need", "night", "what", "who", "how",
        "why", "where", "could", "would", "should", "been", "into", "heart", "got", "get", "let", "make", "feel",
        "see", "take", "come", "time", "day", "every", "more", "only", "still", "away", "again", "our", "us",
        "him", "her", "them", "their", "an", "by", "or", "not", "can", "won't", "i'll", "i've", "i'd", "gonna",
        "wanna", "nothing", "something", "everything", "tonight", "here", "back", "said", "tell", "give",
    )
    private val portuguese = setOf(
        "o", "os", "de", "da", "dos", "das", "que", "e", "eu", "você", "voce", "vc", "não", "nao", "meu", "minha",
        "meus", "minhas", "um", "uma", "com", "por", "pra", "pro", "para", "é", "tu", "seu", "sua", "quando",
        "como", "tudo", "nada", "amor", "vida", "quero", "sei", "foi", "vou", "vai", "está", "esta", "estou",
        "ser", "sem", "mas", "nos", "isso", "esse", "essa", "ela", "ele", "aqui", "onde", "sempre", "nunca",
        "agora", "só", "coração", "te", "também", "mais", "então", "porque", "hoje", "dia", "noite", "mim",
        "comigo", "contigo", "nós", "eles", "elas", "lá", "já", "ainda", "bem", "muito", "cada", "outra",
        "outro", "sim", "assim", "seja", "quem", "teu", "tua", "vem", "ter", "tem", "fazer", "faz", "quer",
        "pode", "posso", "há", "num", "numa", "pelo", "pela", "sobre", "depois", "antes", "ao", "aos", "à",
        "em", "na", "se", "eram", "era", "minha", "estava", "fosse", "seus", "suas", "deixa", "deixe",
    )
    private val wordSplit = Regex("[^\\p{L}']+")

    fun langOf(line: String): Lang? {
        var en = 0
        var pt = 0
        for (w in line.lowercase(Locale.ROOT).replace('’', '\'').split(wordSplit)) {
            val word = w.trim('\'')
            if (word.isEmpty()) continue
            if (word in english) en++
            if (word in portuguese) pt++
        }
        return when {
            en > pt -> Lang.EN
            pt > en -> Lang.PT
            else -> null
        }
    }

    /**
     * Page with lyrics + translation (in blocks or line by line, in any order): English stays, as it's the original
     * language when the site shows English and Portuguese together (the translation is into Portuguese).
     * Single-language text doesn't change.
     */
    fun keepOriginalLanguage(text: String): String {
        val lines = text.split('\n')
        val langs = lines.map { langOf(it) }
        val en = langs.count { it == Lang.EN }
        val pt = langs.count { it == Lang.PT }
        if (minOf(en, pt) < maxOf(3, (en + pt) / 5)) return text
        val original = Lang.EN

        val out = ArrayList<String>(lines.size)
        var i = 0
        while (i < lines.size) {
            if (lines[i].isBlank()) { out += lines[i]; i++; continue }
            val end = (i until lines.size).firstOrNull { lines[it].isBlank() } ?: lines.size
            val stanza = (i until end)
            val orig = stanza.count { langs[it] == original }
            val trans = stanza.count { langs[it] != null && langs[it] != original }
            // one or two misrecognized words don't change the decision for the whole verse
            when {
                trans * 3 < orig || trans == 0 -> stanza.mapTo(out) { lines[it] }   // estrofe original
                orig * 3 < trans -> Unit                                            // translation verse
                else -> {                                                           // linhas intercaladas
                    var previous = ""
                    for (k in stanza) {
                        val l = langs[k]
                        val same = TextKey.of(lines[k]) == TextKey.of(previous)
                        if (l == original || (l == null && !same)) out += lines[k]
                        previous = lines[k]
                    }
                }
            }
            i = end
        }
        return out.joinToString("\n")
    }
}
