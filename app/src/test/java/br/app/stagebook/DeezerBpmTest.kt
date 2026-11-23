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

    // the length comes from the same track answer (user, 22/11): the setlist adds it up per set
    @Test fun `reads the length in seconds - none or unreasonable gives nothing`() {
        assertEquals(321, deezer.secondsOf("""{"id":33,"bpm":0,"duration":321}"""))
        assertNull(deezer.secondsOf("""{"id":33,"duration":0}"""))
        assertNull(deezer.secondsOf("""{"id":33,"duration":99999}"""))
        assertNull(deezer.secondsOf("lixo"))
    }

    @Test fun `a track with a length and no BPM still gives its length`() {
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/search") { ex -> val b = search.toByteArray(); ex.sendResponseHeaders(200, b.size.toLong()); ex.responseBody.write(b); ex.close() }
        server.createContext("/track/33") { ex -> val b = """{"id":33,"bpm":0,"duration":321}""".toByteArray(); ex.sendResponseHeaders(200, b.size.toLong()); ex.responseBody.write(b); ex.close() }
        server.start()
        try {
            val d = DeezerBpm().apply { endpoint = "http://127.0.0.1:${server.address.port}/"; pauseMs = 10 }
            assertEquals(DeezerBpm.Facts(0, 321), kotlinx.coroutines.runBlocking { d.factsWithin("Hazard", "Richard Marx", 5_000) })
            assertNull(kotlinx.coroutines.runBlocking { d.lookupWithin("Hazard", "Richard Marx", 5_000) })
        } finally {
            server.stop(0)
        }
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

    @Test fun `a lookup the caller gave up on does not go on trying or sleeping`() {
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        var searches = 0
        server.createContext("/search") { ex -> searches++; ex.sendResponseHeaders(503, -1); ex.close() }
        server.start()
        try {
            val d = DeezerBpm().apply { endpoint = "http://127.0.0.1:${server.address.port}/"; pauseMs = 300 }
            // gives up after 100 ms: the first try went, the pauses between the tries are not slept for nobody
            assertEquals(null, kotlinx.coroutines.runBlocking { d.lookupWithin("Hazard", "Richard Marx", 100) })
            Thread.sleep(1_200)
            assertEquals("the later tries were not made: $searches", true, searches <= 2)
        } finally {
            server.stop(0)
        }
    }
}
