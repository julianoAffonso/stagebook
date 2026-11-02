package br.app.stagebook

import br.app.stagebook.data.model.Instrument
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** Names in the code are English, but what was saved keeps its old name: libraries and backups from before still open. */
class SavedNamesTest {
    @Test fun `instruments keep the saved names`() {
        val saved = mapOf(
            "violao" to Instrument.ACOUSTIC_GUITAR,
            "guitarra" to Instrument.ELECTRIC_GUITAR,
            "ukulele" to Instrument.UKULELE,
            "cavaquinho" to Instrument.CAVAQUINHO,
            "teclado" to Instrument.KEYBOARD,
        )
        saved.forEach { (name, instrument) ->
            assertEquals(instrument, Json.decodeFromString<Instrument>("\"$name\""))
            assertEquals("\"$name\"", Json.encodeToString(instrument))
        }
    }
}
