package br.app.stagebook

import br.app.stagebook.data.model.Song
import br.app.stagebook.domain.QrMatch
import br.app.stagebook.domain.QrPayload
import br.app.stagebook.domain.SetlistMatcher
import br.app.stagebook.domain.TitleKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The library's title keys are worked out once per library and give the same matches (audit 30/10). */
class TitleKeysTest {
    private val library = listOf(
        Song(id = "1", title = "Tempo Perdido", author = "Legião Urbana"),
        Song(id = "2", title = "Tempo perdido", author = "Outra"),
        Song(id = "3", title = "Hazard", author = "Richard Marx"),
    )

    @Test fun `kept per library list, redone when the list changes`() {
        val a = TitleKeys.of(library)
        assertSame(a, TitleKeys.of(library))
        assertTrue(a !== TitleKeys.of(library + Song(id = "4", title = "Nova")))
        assertEquals(1, TitleKeys.of(library).repeated.size)   // "Tempo Perdido" twice
    }

    @Test fun `matching still finds, ties by artist and suggests similar`() {
        assertEquals("3", (SetlistMatcher.match(QrPayload.Entry("hazard", ""), library) as QrMatch.Found).song.id)
        assertEquals("2", (SetlistMatcher.match(QrPayload.Entry("Tempo Perdido", "Outra"), library) as QrMatch.Found).song.id)
        assertTrue(SetlistMatcher.match(QrPayload.Entry("Hazzard", ""), library) is QrMatch.Similar)
        assertEquals(QrMatch.Missing, SetlistMatcher.match(QrPayload.Entry("Nada a ver", ""), library))
    }
}
