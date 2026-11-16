package br.app.stagebook

import br.app.stagebook.data.DeezerBpm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** BPM suggested by Deezer: finds the right track in the search and reads the BPM (0 or absurd = no suggestion). */
class DeezerBpmTest {
    private val deezer = DeezerBpm()
    private val search = """{"data":[
        {"id":11,"title":"Hazard (Live)","artist":{"name":"Richard Marx"}},
        {"id":22,"title":"Hazard","artist":{"name":"Outro Artista"}},
        {"id":33,"title":"Hazard","artist":{"name":"Richard Marx"}}]}"""

    @Test fun `picks the track with the same title and the right artist`() {
        assertEquals(33L, deezer.pickTrack(search, "Hazard", "Richard Marx"))
        assertEquals(22L, deezer.pickTrack(search, "Hazard", ""))   // no artist: the first with the same title
        assertNull(deezer.pickTrack(search, "Outra", "Richard Marx"))
    }

    @Test fun `reads the rounded BPM - zero or unreasonable suggests nothing`() {
        assertEquals(125, deezer.bpmOf("""{"id":33,"bpm":124.9}"""))
        assertNull(deezer.bpmOf("""{"id":33,"bpm":0}"""))
        assertNull(deezer.bpmOf("""{"id":33,"bpm":999}"""))
        assertNull(deezer.bpmOf("lixo"))
    }

    @Test fun `a busy answer or a quota notice is tried again and the BPM comes`() {
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        var searches = 0
        server.createContext("/search") { ex ->
            val body = if (++searches == 1) null else search
            if (body == null) { ex.sendResponseHeaders(503, -1) } else { val b = body.toByteArray(); ex.sendResponseHeaders(200, b.size.toLong()); ex.responseBody.write(b) }
            ex.close()
        }
        var tracks = 0
        server.createContext("/track/33") { ex ->
            // the first answer is Deezer's quota notice inside a 200
            val body = if (++tracks == 1) """{"error":{"type":"Exception","message":"Quota limit exceeded","code":4}}""" else """{"id":33,"bpm":96.2}"""
            val b = body.toByteArray(); ex.sendResponseHeaders(200, b.size.toLong()); ex.responseBody.write(b); ex.close()
        }
        server.start()
        try {
            val d = DeezerBpm().apply { endpoint = "http://127.0.0.1:${server.address.port}/"; pauseMs = 10 }
            assertEquals(96, kotlinx.coroutines.runBlocking { d.lookupWithin("Hazard", "Richard Marx", 5_000) })
            assertEquals(2, searches)
            assertEquals(2, tracks)
        } finally {
            server.stop(0)
        }
    }
}
