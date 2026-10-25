package com.juliano.stagebook

import com.juliano.stagebook.data.BandSession
import com.juliano.stagebook.domain.BandCrypto
import com.juliano.stagebook.domain.BandProtocol
import com.juliano.stagebook.domain.ShareCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/** Banda na rede local: a chave do par sai igual dos dois lados e o pedido de entrada só grava com Autorizar. */
class BandTest {
    private class Device(val name: String) {
        val keys = BandCrypto.newKeyPair()
        val pub = BandCrypto.encode(keys.public)
        val id = BandCrypto.idOf(pub)
        val hello = BandProtocol.Hello(id, name, pub)
        val trusted = mutableListOf<BandProtocol.Hello>()
    }

    @Test fun `os dois chegam à mesma chave do par sem mandá-la - outro aparelho não`() {
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

    // b pede para entrar pelo aparelho de a; [answer] é o que o usuário de a toca
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

    @Test fun `Autorizar grava a confiança nos dois aparelhos`() {
        val a = Device("Tablet da Ana")
        val b = Device("Celular do Beto")
        assertEquals(BandSession.JoinResult.ACCEPTED, join(a, b, answer = true))
        assertEquals(listOf(b.id), a.trusted.map { it.id })
        assertEquals(listOf(a.id), b.trusted.map { it.id })
        assertEquals("Tablet da Ana", b.trusted.single().name)
    }

    @Test fun `Não autorizar não grava nada`() {
        val a = Device("Tablet da Ana")
        val b = Device("Celular do Beto")
        assertEquals(BandSession.JoinResult.DENIED, join(a, b, answer = false))
        assertFalse(a.trusted.isNotEmpty() || b.trusted.isNotEmpty())
    }

    @Test fun `quem já é da banda entra sem perguntar de novo`() {
        val a = Device("Tablet da Ana")
        val b = Device("Celular do Beto")
        assertEquals(BandSession.JoinResult.ACCEPTED, join(a, b, answer = false, aKnowsB = true))
    }

    @Test fun `no palco o pedido não pergunta nada - responde que está ocupado`() {
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

    @Test fun `hello com id que não é da chave é recusado`() {
        val a = Device("Tablet da Ana")
        val fake = BandProtocol.Hello("0".repeat(32), "Falso", a.pub)
        val input = (kotlinx.serialization.json.Json.encodeToString(BandProtocol.Hello.serializer(), fake) + "\n").reader().buffered()
        assertNull(BandProtocol.readHello(input))
    }
}
