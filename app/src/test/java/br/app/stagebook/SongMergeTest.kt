package br.app.stagebook

import br.app.stagebook.domain.SongMerge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Versões diferentes da mesma música: aceitar a recebida nunca apaga a cifra de quem recebe. */
class SongMergeTest {
    private val mine = "{key: G}\n[G]Linha um da can[D]ção\n[Em]Linha dois\nLinha três"

    @Test fun `letra nova sem cifra entra com os meus acordes`() {
        val incoming = "Linha um da canção\nLinha dois corrigida\nLinha três"
        val merged = SongMerge.accept(mine, incoming)
        assertTrue(merged.contains("{key: G}"))
        assertTrue(merged.contains("[G]Linha um da can[D]ção"))
        assertTrue(merged.contains("Linha dois corrigida"))
    }

    @Test fun `recebida com cifra entra inteira`() {
        val incoming = "[A]Linha um da canção\nLinha dois"
        assertEquals(incoming, SongMerge.accept(mine, incoming))
    }

    @Test fun `mostra só as linhas que mudam - e nada quando só a cifra muda`() {
        val changes = SongMerge.changes(mine, "Linha um da canção\nLinha dois corrigida\nLinha três")
        assertEquals(listOf(SongMerge.Change(SongMerge.Kind.REMOVED, "Linha dois"), SongMerge.Change(SongMerge.Kind.ADDED, "Linha dois corrigida")), changes)
        assertTrue(SongMerge.changes(mine, "[C]Linha um da canção\nLinha dois\nLinha três").isEmpty())
    }

    @Test fun `espaço no fim e linha em branco no fim não contam como diferença`() {
        assertTrue(SongMerge.same("a\nb", "a  \nb\n\n"))
        assertFalse(SongMerge.same("a\nb", "a\nc"))
    }
}
