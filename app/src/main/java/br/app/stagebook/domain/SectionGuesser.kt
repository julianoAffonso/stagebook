package br.app.stagebook.domain

import br.app.stagebook.data.model.SectionLang

/**
 * Letra sem títulos de seção: descobre o que é cada estrofe pela repetição.
 * - Refrão: a estrofe que mais se repete (quase igual também conta).
 * - Pré-refrão: outra estrofe repetida que vem sempre logo antes do refrão.
 * - Ponte: estrofe única que aparece depois de pelo menos dois refrões e antes do último.
 * - Verso: o resto, numerado; um verso que volta igual mantém o número.
 * Sem nenhuma estrofe repetida não dá para saber qual é o refrão: devolve null (letra fica como está).
 */
object SectionGuesser {
    private const val SAME = 0.6   // fração de linhas iguais para duas estrofes contarem como a mesma

    fun guess(text: String, lang: SectionLang): String? {
        val lines = text.replace("\r", "").split('\n')
        if (lines.any { Sections.detect(it) != null }) return null   // já tem títulos: só padronizar
        val stanzas = splitStanzas(lines)
        if (stanzas.size < 2) return null

        val group = groupSimilar(stanzas)
        val counts = group.groupingBy { it }.eachCount()
        val repeated = counts.filterValues { it >= 2 }.keys
        if (repeated.isEmpty()) return null
        // refrão: o grupo que mais aparece; empate = o que aparece primeiro mais tarde (o pré-refrão vem antes)
        val chorus = repeated.maxWith(compareBy<Int> { counts[it] }.thenBy { g -> group.indexOf(g) })
        val chorusAt = group.indices.filter { group[it] == chorus }
        val preChorus = repeated.filter { g ->
            g != chorus && group.indices.filter { group[it] == g }.all { it + 1 < group.size && group[it + 1] == chorus }
        }.toSet()

        val kinds = arrayOfNulls<Sections.Kind>(stanzas.size)
        val verseNumber = IntArray(stanzas.size)
        val verseOfGroup = HashMap<Int, Int>()
        var verses = 0
        for (i in stanzas.indices) {
            val g = group[i]
            kinds[i] = when {
                g == chorus -> Sections.Kind.CHORUS
                g in preChorus -> Sections.Kind.PRE_CHORUS
                g !in repeated && chorusAt.count { it < i } >= 2 && chorusAt.any { it > i } -> Sections.Kind.BRIDGE
                else -> {
                    verseNumber[i] = verseOfGroup.getOrPut(g) { ++verses }
                    Sections.Kind.VERSE
                }
            }
        }
        val target = if (lang == SectionLang.ORIGINAL) SectionLang.PT else lang
        return stanzas.indices.joinToString("\n\n") { i ->
            val kind = kinds[i]!!
            val number = if (kind == Sections.Kind.VERSE && verses > 1) verseNumber[i].toString() else ""
            "[" + Sections.label(Sections.Header(kind, number, "", ""), target) + "]\n" + stanzas[i].joinToString("\n")
        }
    }

    private fun splitStanzas(lines: List<String>): List<List<String>> {
        val out = ArrayList<List<String>>()
        var cur = ArrayList<String>()
        for (l in lines) {
            if (l.isBlank()) {
                if (cur.isNotEmpty()) { out += cur; cur = ArrayList() }
            } else cur += l.trimEnd()
        }
        if (cur.isNotEmpty()) out += cur
        return out
    }

    /** Número do grupo de cada estrofe: estrofes parecidas recebem o mesmo número. */
    private fun groupSimilar(stanzas: List<List<String>>): List<Int> {
        val keys = stanzas.map { s -> s.map { TextKey.of(it) } }
        val group = IntArray(stanzas.size) { -1 }
        var next = 0
        for (i in stanzas.indices) {
            if (group[i] >= 0) continue
            group[i] = next
            for (j in i + 1 until stanzas.size) if (group[j] < 0 && similarity(keys[i], keys[j]) >= SAME) group[j] = next
            next++
        }
        return group.toList()
    }

    private fun similarity(a: List<String>, b: List<String>): Double {
        val pool = b.toMutableList()
        val common = a.count { pool.remove(it) }
        return common.toDouble() / maxOf(a.size, b.size)
    }
}
