package br.app.stagebook

import br.app.stagebook.data.BandSession
import br.app.stagebook.domain.BandCrypto
import br.app.stagebook.domain.BandProtocol
import br.app.stagebook.domain.ShareCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Band on the local network: the pair key comes out the same on both sides and a join request only saves with Allow.
 */
class BandTest {
    private class Device(val name: String) {
        val keys = BandCrypto.newKeyPair()
        val pub = BandCrypto.encode(keys.public)
        val id = BandCrypto.idOf(pub)
        val hello = BandProtocol.Hello(id, name, pub)
        val trusted = mutableListOf<BandProtocol.Hello>()
    }

    @Test fun `both reach the same pair key without sending it - another device does not`() {
        val a = Device("Tablet da Ana")
        val b = Device("Celular do Beto")
        val c = Device("Intruso")
        val ab = BandCrypto.pairKey(a.keys.private, a.id, b.keys.public, b.id)
        val ba = BandCrypto.pairKey(b.keys.private, b.id, a.keys.public, a.id)
        assertArrayEquals(ab, ba)
        val msg = ShareCode.encrypt(ab, "setlist".toByteArray())
        assertEquals("setlist", String(ShareCode.decrypt(ba, msg)!!))
        val cb = BandCrypto.pairKey(c.keys.private, c.id, b.keys.public, b.id)
        assertNull(ShareCode.decrypt(cb, msg))
    }

    // b asks to join through a's device; [answer] is what a's user taps
    private fun join(a: Device, b: Device, answer: Boolean, aKnowsB: Boolean = false): BandSession.JoinResult {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        var asked = 0
        val t = thread {
            runBlocking {
                BandSession(a.hello).serve(
                    server.accept(),
                    isMember = { aKnowsB && it == b.id },
                    ask = { asked++; answer },
                    onTrusted = { a.trusted += it },
                )
            }
        }
        val r = BandSession(b.hello).join(Socket(InetAddress.getLoopbackAddress(), server.localPort)) { b.trusted += it }
        t.join(5_000)
        server.close()
        if (aKnowsB) assertEquals(0, asked)
        return r
    }

    @Test fun `Allow saves the trust on both devices`() {
        val a = Device("Tablet da Ana")
        val b = Device("Celular do Beto")
        assertEquals(BandSession.JoinResult.ACCEPTED, join(a, b, answer = true))
        assertEquals(listOf(b.id), a.trusted.map { it.id })
        assertEquals(listOf(a.id), b.trusted.map { it.id })
        assertEquals("Tablet da Ana", b.trusted.single().name)
    }

    @Test fun `Don't allow saves nothing`() {
        val a = Device("Tablet da Ana")
        val b = Device("Celular do Beto")
        assertEquals(BandSession.JoinResult.DENIED, join(a, b, answer = false))
        assertFalse(a.trusted.isNotEmpty() || b.trusted.isNotEmpty())
    }

    @Test fun `a band member joins again without being asked`() {
        val a = Device("Tablet da Ana")
        val b = Device("Celular do Beto")
        assertEquals(BandSession.JoinResult.ACCEPTED, join(a, b, answer = false, aKnowsB = true))
    }

    @Test fun `on stage the request asks nothing - answers busy`() {
        val a = Device("Tablet da Ana")
        val b = Device("Celular do Beto")
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        var asked = 0
        val t = thread {
            runBlocking {
                BandSession(a.hello).serve(server.accept(), { false }, { asked++; true }, { a.trusted += it }, busy = { true })
            }
        }
        val r = BandSession(b.hello).join(Socket(InetAddress.getLoopbackAddress(), server.localPort)) { b.trusted += it }
        t.join(5_000)
        server.close()
        assertEquals(BandSession.JoinResult.BUSY, r)
        assertEquals(0, asked)
        assertFalse(a.trusted.isNotEmpty() || b.trusted.isNotEmpty())
    }

    // b sends a package to a; [bIsMember]: a has b in the band
    private fun sendPackage(a: Device, b: Device, bIsMember: Boolean): Pair<Boolean, String?> {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        var got: String? = null
        val t = thread {
            runBlocking {
                BandSession(a.hello).serve(server.accept(), { bIsMember && it == b.id }, { false }, {}, onPackage = { _, body -> got = body; true })
            }
        }
        val key = BandCrypto.pairKey(b.keys.private, b.id, a.keys.public, a.id)
        val ok = BandSession(b.hello).send(Socket(InetAddress.getLoopbackAddress(), server.localPort), a.id, ShareCode.encrypt(key, "setlist".toByteArray()))
        t.join(5_000)
        server.close()
        return ok to got?.let { body -> ShareCode.decrypt(BandCrypto.pairKey(a.keys.private, a.id, b.keys.public, b.id), body)?.decodeToString() }
    }

    @Test fun `a band member delivers the package and the other opens it with the pair key`() {
        val (ok, text) = sendPackage(Device("Tablet da Ana"), Device("Celular do Beto"), bIsMember = true)
        assertTrue(ok)
        assertEquals("setlist", text)
    }

    @Test fun `someone outside the band delivers nothing`() {
        val (ok, text) = sendPackage(Device("Tablet da Ana"), Device("Estranho"), bIsMember = false)
        assertFalse(ok)
        assertNull(text)
    }

    @Test fun `ask the band - members answer by themselves - outsiders get no answer`() {
        val a = Device("Tablet da Ana")
        val b = Device("Celular do Beto")
        fun ask(member: Boolean): String? {
            val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
            val t = thread {
                runBlocking {
                    BandSession(a.hello).serve(server.accept(), { member }, { error("não pergunta nada") }, {}, onAsk = { _, body -> "resposta:$body" })
                }
            }
            val r = BandSession(b.hello).ask(Socket(InetAddress.getLoopbackAddress(), server.localPort), a.id, "pedido")
            t.join(5_000)
            server.close()
            return r
        }
        assertEquals("resposta:pedido", ask(member = true))
        assertNull(ask(member = false))
    }

    @Test fun `lead the band - followers answer what they miss - and outsiders get nothing`() {
        val a = Device("Tablet da Ana")
        val b = Device("Celular do Beto")
        fun conduct(member: Boolean, onStage: Boolean): String? {
            val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
            val t = thread {
                runBlocking {
                    BandSession(a.hello).serve(server.accept(), { member }, { error("não pergunta nada") }, {},
                        busy = { onStage }, onConduct = { _, body -> "falta:$body" })
                }
            }
            val r = BandSession(b.hello).ask(Socket(InetAddress.getLoopbackAddress(), server.localPort), a.id, "setlist", BandProtocol.Kind.CONDUCT)
            t.join(5_000)
            server.close()
            return r
        }
        assertEquals("falta:setlist", conduct(member = true, onStage = false))
        // on stage a follower keeps answering: that's where they are on purpose
        assertEquals("falta:setlist", conduct(member = true, onStage = true))
        assertNull(conduct(member = false, onStage = false))
    }

    @Test fun `different protocol versions - the request answers version and nothing is saved`() {
        val a = Device("Tablet da Ana")
        val b = Device("Celular do Beto")
        val newer = b.hello.copy(v = BandProtocol.VERSION + 1, app = "9.9")
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val t = thread { runBlocking { BandSession(a.hello).serve(server.accept(), { false }, { true }, { a.trusted += it }) } }
        val r = BandSession(newer).join(Socket(InetAddress.getLoopbackAddress(), server.localPort)) { b.trusted += it }
        t.join(5_000)
        server.close()
        assertEquals(BandSession.JoinResult.VERSION, r)
        assertTrue(a.trusted.isEmpty() && b.trusted.isEmpty())
    }

    @Test fun `hello with an id that does not match the key is refused`() {
        val a = Device("Tablet da Ana")
        val fake = BandProtocol.Hello("0".repeat(32), "Falso", a.pub)
        val input = (kotlinx.serialization.json.Json.encodeToString(BandProtocol.Hello.serializer(), fake) + "\n").reader().buffered()
        assertNull(BandProtocol.readHello(input))
    }

    @Test fun `a line longer than the limit is refused - a normal one still reads`() {
        val endless = "x".repeat(BandProtocol.MAX_HELLO + 1000).reader().buffered()
        assertNull(BandProtocol.readHello(endless))
        assertNull(BandProtocol.readLine("abcdef\n".reader().buffered(), 3))
        val two = "um\r\ndois\n".reader().buffered()
        assertEquals("um", BandProtocol.readLine(two, 10))
        assertEquals("dois", BandProtocol.readLine(two, 10))
        assertNull(BandProtocol.readLine(two, 10))
    }
}
