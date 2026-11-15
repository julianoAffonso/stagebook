package br.app.stagebook

import br.app.stagebook.data.MusicBrainz
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress

/** Artist and year lookup: with a stuck network, "loading" doesn't go past the deadline. */
class MusicBrainzTest {
    private lateinit var server: HttpServer

    @Before fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/lento") { ex ->
            Thread.sleep(5_000)   // server slow to answer (bad Wi-Fi)
            ex.sendResponseHeaders(200, -1)
            ex.close()
        }
        server.executor = java.util.concurrent.Executors.newCachedThreadPool()
        server.start()
    }

    @After fun stop() = server.stop(0)

    @Test fun `stuck network - gives up on time instead of waiting for the connection read`() {
        val mb = MusicBrainz().apply { endpoint = "http://127.0.0.1:${server.address.port}/lento" }
        val started = System.currentTimeMillis()
        val r = runBlocking { mb.lookupWithin("Título", "Artista", 300) }
        val took = System.currentTimeMillis() - started
        assertNull(r)
        assertTrue("levou $took ms", took < 2_000)
    }

    private val body = """{"recordings":[{"score":100,"title":"Camila, Camila","first-release-date":"1989","artist-credit":[{"name":"Nenhum De Nós","joinphrase":""}]},""" +
        """{"score":100,"title":"Camila, Camila","first-release-date":"1987","artist-credit":[{"name":"Nenhum De Nós","joinphrase":""}]}]}"""

    @Test fun `a 503 is tried again and the year comes`() {
        var calls = 0
        server.createContext("/ocupado") { ex ->
            if (++calls == 1) { ex.sendResponseHeaders(503, -1) } else {
                val b = body.toByteArray(); ex.sendResponseHeaders(200, b.size.toLong()); ex.responseBody.write(b)
            }
            ex.close()
        }
        val mb = MusicBrainz().apply { endpoint = "http://127.0.0.1:${server.address.port}/ocupado"; spacingMs = 50 }
        val info = runBlocking { mb.lookup("Camila, Camila", "Nenhum de Nós") }
        assertEquals("the oldest release", "1987", info?.year)
        assertEquals(2, calls)
    }

    @Test fun `lookups go one at a time, a second apart`() {
        val times = java.util.Collections.synchronizedList(mutableListOf<Long>())
        server.createContext("/fila") { ex ->
            times += System.currentTimeMillis()
            val b = body.toByteArray(); ex.sendResponseHeaders(200, b.size.toLong()); ex.responseBody.write(b); ex.close()
        }
        val mb = MusicBrainz().apply { endpoint = "http://127.0.0.1:${server.address.port}/fila"; spacingMs = 400 }
        runBlocking {
            val a = async { mb.lookup("Camila, Camila", "Nenhum de Nós") }
            val b = async { mb.lookup("Camila, Camila", "Nenhum de Nós") }
            a.await(); b.await()
        }
        assertEquals(2, times.size)
        assertTrue("spaced: ${times[1] - times[0]} ms", times[1] - times[0] >= 380)
    }

    @Test fun `a request that fails for another reason gives nothing and does not retry`() {
        var calls = 0
        server.createContext("/erro") { ex -> calls++; ex.sendResponseHeaders(500, -1); ex.close() }
        val mb = MusicBrainz().apply { endpoint = "http://127.0.0.1:${server.address.port}/erro"; spacingMs = 10 }
        assertNull(runBlocking { mb.lookup("x", "y") })
        assertEquals(1, calls)
    }
}
