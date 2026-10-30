package br.app.stagebook

import br.app.stagebook.data.MusicBrainz
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress

/** Busca de artista e ano: com a rede travada, o "carregando" não passa do prazo. */
class MusicBrainzTest {
    private lateinit var server: HttpServer

    @Before fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/lento") { ex ->
            Thread.sleep(5_000)   // servidor que demora a responder (Wi-Fi ruim)
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
}
