package br.app.stagebook

import br.app.stagebook.data.DeezerBpm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** BPM sugerido pelo Deezer: acha a faixa certa da busca e lê o BPM (0 ou absurdo = sem sugestão). */
class DeezerBpmTest {
    private val deezer = DeezerBpm()
    private val search = """{"data":[
        {"id":11,"title":"Hazard (Live)","artist":{"name":"Richard Marx"}},
        {"id":22,"title":"Hazard","artist":{"name":"Outro Artista"}},
        {"id":33,"title":"Hazard","artist":{"name":"Richard Marx"}}]}"""

    @Test fun `picks the track with the same title and the right artist`() {
        assertEquals(33L, deezer.pickTrack(search, "Hazard", "Richard Marx"))
        assertEquals(22L, deezer.pickTrack(search, "Hazard", ""))   // sem artista: a primeira com o mesmo título
        assertNull(deezer.pickTrack(search, "Outra", "Richard Marx"))
    }

    @Test fun `reads the rounded BPM - zero or unreasonable suggests nothing`() {
        assertEquals(125, deezer.bpmOf("""{"id":33,"bpm":124.9}"""))
        assertNull(deezer.bpmOf("""{"id":33,"bpm":0}"""))
        assertNull(deezer.bpmOf("""{"id":33,"bpm":999}"""))
        assertNull(deezer.bpmOf("lixo"))
    }
}
