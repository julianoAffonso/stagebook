package com.juliano.stagebook.domain

import com.juliano.stagebook.data.model.SectionLang

/**
 * Títulos de seção (refrão, verso, ponte…) em português e inglês: reconhece a linha inteira, com ou
 * sem colchetes/parênteses/dois-pontos, número e "2x", para mostrar como título em destaque no palco.
 */
object Sections {
    enum class Kind(val pt: String, val en: String, val keys: List<String>) {
        INTRO("Intro", "Intro", listOf("intro", "introducao", "introduction", "abertura")),
        VERSE("Verso", "Verse", listOf("verse", "verso", "estrofe", "parte", "stanza", "primeira parte", "segunda parte", "terceira parte")),
        PRE_CHORUS("Pré-refrão", "Pre-Chorus", listOf("pre chorus", "prechorus", "pre refrao", "prerefrao", "pre")),
        CHORUS("Refrão", "Chorus", listOf("chorus", "refrao", "refrain", "coro")),
        BRIDGE("Ponte", "Bridge", listOf("bridge", "ponte")),
        SOLO("Solo", "Solo", listOf("solo", "guitar solo", "solo de guitarra")),
        INTERLUDE("Interlúdio", "Interlude", listOf("interlude", "interludio", "instrumental", "break", "passagem")),
        OUTRO("Fim", "Outro", listOf("outro", "final", "fim", "ending", "coda", "encerramento")),   // "Fim": padrão do another app do usuário
    }

    data class Header(val kind: Kind, val number: String, val repeat: String, val original: String)

    /** Uma linha do texto: ou letra, ou título de seção. */
    data class Line(val text: String, val header: Header?)

    private val shape = Regex("^[\\[(*#\\s]*(.+?)[\\])*\\s]*:?\\s*$")
    private val tail = Regex("^(.*?)\\s*(\\d+)?\\s*(\\(?\\s*(?:x\\s*\\d+|\\d+\\s*x|\\d+\\s*vezes)\\s*\\)?)?\\s*$")
    private val brackets = Regex("[\\])(\\[]")
    private val digits = Regex("(\\d+)")

    /** A linha inteira é um título de seção? (só palavra-chave, número e repetição; nada de letra) */
    fun detect(line: String): Header? {
        val t = line.trim()
        if (t.isEmpty() || t.length > 40) return null
        val inner = shape.matchEntire(t)?.groupValues?.get(1)?.replace(brackets, " ")?.trim() ?: return null
        val m = tail.matchEntire(TextKey.of(inner)) ?: return null
        val word = m.groupValues[1].trim()
        val kind = Kind.entries.firstOrNull { word in it.keys } ?: return null
        val number = when {
            word.startsWith("primeira") -> "1"
            word.startsWith("segunda") -> "2"
            word.startsWith("terceira") -> "3"
            else -> m.groupValues[2]
        }
        val repeat = digits.find(m.groupValues[3])?.let { it.value + "x" }.orEmpty()
        return Header(kind, number, repeat, inner)
    }

    /** Nome mostrado no idioma escolhido ("como escrito" = sem colchetes e dois-pontos). */
    fun label(h: Header, lang: SectionLang): String {
        if (lang == SectionLang.ORIGINAL) return h.original
        return buildString {
            append(if (lang == SectionLang.EN) h.kind.en else h.kind.pt)
            if (h.number.isNotEmpty()) append(' ').append(h.number)
            if (h.repeat.isNotEmpty()) append("  ").append(h.repeat)
        }
    }

    /** [name] (o nome da seção na língua do app) com o número e a repetição do título original. */
    fun label(h: Header, name: String): String = buildString {
        append(name)
        if (h.number.isNotEmpty()) append(' ').append(h.number)
        if (h.repeat.isNotEmpty()) append("  ").append(h.repeat)
    }

    fun parse(text: String): List<Line> = text.split('\n').map { Line(it, detect(it)) }

    /** Reescreve os títulos de seção no formato "[Refrão]", "[Verso 2]" (botão "Padronizar"). */
    fun normalize(text: String, lang: SectionLang): String {
        val target = if (lang == SectionLang.ORIGINAL) SectionLang.PT else lang
        return parse(text).joinToString("\n") { line ->
            line.header?.let { "[" + label(it, target).replace("  ", " ") + "]" } ?: line.text
        }
    }
}
