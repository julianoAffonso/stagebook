package br.app.stagebook

import br.app.stagebook.data.BandConduct.Role
import br.app.stagebook.domain.Conduct
import br.app.stagebook.ui.band.ConductUi
import br.app.stagebook.ui.band.conductUi
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The leader's "N didn't answer" follows who is online and who answered NOW: it used to be a number worked out when the
 * message went and kept on the strip after the person answered, left, or the leader looked for the band again.
 */
class ConductNoAnswerNowTest {
    private val msg = Conduct.Msg(Conduct.OPEN, from = "Voz", set = "Show", songs = listOf(Conduct.Item("Primeira")), index = 0)

    private fun noAnswer(answered: Set<String>?, online: Set<String>): Int =
        (conductUi(Role.Conducting("s1", msg, answered), emptyList(), 0L, true, online = online) as ConductUi.Conducting).noAnswer

    @Test fun `who is online and didn't answer is counted`() {
        assertEquals(1, noAnswer(setOf("a"), setOf("a", "b")))
    }

    @Test fun `the person answers later - the strip stops saying it`() {
        assertEquals(1, noAnswer(setOf("a"), setOf("a", "b")))
        assertEquals(0, noAnswer(setOf("a", "b"), setOf("a", "b")))
    }

    @Test fun `the person leaves - the strip stops saying it`() {
        assertEquals(1, noAnswer(setOf("a"), setOf("a", "b")))
        assertEquals(0, noAnswer(setOf("a"), setOf("a")))
    }

    @Test fun `a new person comes online and hasn't answered yet`() {
        assertEquals(1, noAnswer(setOf("a", "b"), setOf("a", "b", "c")))
    }

    @Test fun `before any answer to count against, nothing is said`() {
        assertEquals(0, noAnswer(null, setOf("a", "b")))
    }
}
