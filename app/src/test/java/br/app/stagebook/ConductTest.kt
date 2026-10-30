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

    @Test fun `finds the song by title, ignoring accents and case`() {
        assertEquals("2", Conduct.find(Conduct.Item("asa branca"), library)?.id)
    }

    @Test fun `repeated title is settled by the artist`() {
        assertEquals("3", Conduct.find(Conduct.Item("Trem Bala", "Ana Vilela"), library)?.id)
    }

    @Test fun `similar does not count - becomes a missing song in the alert`() {
        assertNull(Conduct.find(Conduct.Item("Asa Brancas"), library))
        assertEquals(listOf("Asa Brancas", "Evidências"),
            Conduct.missing(listOf(Conduct.Item("Asa Brancas"), Conduct.Item("Garota de Ipanema"), Conduct.Item("Evidências")), library))
    }

    @Test fun `missing songs for the current song show who lacks it`() {
        val msg = Conduct.Msg(
            Conduct.OPEN, songs = listOf(Conduct.Item("A"), Conduct.Item("B")), index = 1,
            missing = listOf(Conduct.Lack("Baixo", listOf("B")), Conduct.Lack("Teclado", listOf("A"))),
        )
        assertEquals(listOf("Baixo"), Conduct.missingAt(msg, 1))
        assertEquals(listOf("Teclado"), Conduct.missingAt(msg, 0))
        assertEquals(emptyList<String>(), Conduct.missingAt(msg, 5))
    }

    @Test fun `message goes and comes back - and an odd type is refused`() {
        val msg = Conduct.Msg(Conduct.CHECK, from = "Voz", set = "Show", songs = listOf(Conduct.Item("A", "X")))
        assertEquals(msg, Conduct.decode(Conduct.encode(msg)))
        assertNull(Conduct.decode(Conduct.encode(msg.copy(type = "apagar"))))
        assertNull(Conduct.decode("lixo"))
    }

    @Test fun `followers answer what they miss - and the alert only shows who lacks something`() {
        val msg = Conduct.Msg(Conduct.CHECK, songs = listOf(Conduct.Item("Asa Branca"), Conduct.Item("Evidências")))
        assertEquals(Conduct.Lack("Baixo", listOf("Evidências")), Conduct.lackFor("Baixo", msg, library))
        val lacks = Conduct.lacks(listOf(Conduct.Lack("Teclado", listOf("A")), Conduct.Lack("Bateria", emptyList()), Conduct.Lack("Baixo", listOf("B"))))
        assertEquals(listOf("Baixo", "Teclado"), lacks.map { it.who })
    }
}
