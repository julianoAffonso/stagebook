package br.app.stagebook

import br.app.stagebook.data.model.Song
import br.app.stagebook.domain.Conduct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConductTest {
    private val library = listOf(
        Song(id = "1", title = "Garota de Ipanema", author = "Tom Jobim"),
        Song(id = "2", title = "Asa Branca", author = "Luiz Gonzaga"),
        Song(id = "3", title = "Trem Bala", author = "Ana Vilela"),
        Song(id = "4", title = "Trem Bala", author = "Outro"),
    )

    @Test fun `acha a música pelo título, sem acento nem caixa`() {
        assertEquals("2", Conduct.find(Conduct.Item("asa branca"), library)?.id)
    }

    @Test fun `título repetido desempata pelo artista`() {
        assertEquals("3", Conduct.find(Conduct.Item("Trem Bala", "Ana Vilela"), library)?.id)
    }

    @Test fun `parecida não conta - vira falta para o alerta`() {
        assertNull(Conduct.find(Conduct.Item("Asa Brancas"), library))
        assertEquals(listOf("Asa Brancas", "Evidências"),
            Conduct.missing(listOf(Conduct.Item("Asa Brancas"), Conduct.Item("Garota de Ipanema"), Conduct.Item("Evidências")), library))
    }

    @Test fun `faltas da música da vez mostram quem não tem`() {
        val msg = Conduct.Msg(
            Conduct.OPEN, songs = listOf(Conduct.Item("A"), Conduct.Item("B")), index = 1,
            missing = listOf(Conduct.Lack("Baixo", listOf("B")), Conduct.Lack("Teclado", listOf("A"))),
        )
        assertEquals(listOf("Baixo"), Conduct.missingAt(msg, 1))
        assertEquals(listOf("Teclado"), Conduct.missingAt(msg, 0))
        assertEquals(emptyList<String>(), Conduct.missingAt(msg, 5))
    }

    @Test fun `mensagem vai e volta - e tipo estranho é recusado`() {
        val msg = Conduct.Msg(Conduct.CHECK, from = "Voz", set = "Show", songs = listOf(Conduct.Item("A", "X")))
        assertEquals(msg, Conduct.decode(Conduct.encode(msg)))
        assertNull(Conduct.decode(Conduct.encode(msg.copy(type = "apagar"))))
        assertNull(Conduct.decode("lixo"))
    }

    @Test fun `quem acompanha responde as que faltam - e o alerta só traz quem tem falta`() {
        val msg = Conduct.Msg(Conduct.CHECK, songs = listOf(Conduct.Item("Asa Branca"), Conduct.Item("Evidências")))
        assertEquals(Conduct.Lack("Baixo", listOf("Evidências")), Conduct.lackFor("Baixo", msg, library))
        val lacks = Conduct.lacks(listOf(Conduct.Lack("Teclado", listOf("A")), Conduct.Lack("Bateria", emptyList()), Conduct.Lack("Baixo", listOf("B"))))
        assertEquals(listOf("Baixo", "Teclado"), lacks.map { it.who })
    }
}
