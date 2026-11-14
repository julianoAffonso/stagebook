package br.app.stagebook

import br.app.stagebook.data.BandSession
import br.app.stagebook.data.dialSocket
import br.app.stagebook.data.model.Song
import br.app.stagebook.domain.BandCrypto
import br.app.stagebook.domain.BandProtocol
import br.app.stagebook.domain.QrMatch
import br.app.stagebook.domain.QrPayload
import br.app.stagebook.domain.SetlistMatcher
import br.app.stagebook.ui.band.whileBandOn
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/** Deep audit 14/11: the band's network edges (socket leak, read limits, matcher, strip gated by the band switch). */
class BandAuditPlainTest {
    // ---- 3: a failed connect closes the socket ----
    @Test fun `a connect that fails closes the socket before the error goes up`() {
        val closed = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }   // nobody listens there now
        val s = Socket()
        val failed = runCatching { dialSocket(s, InetAddress.getLoopbackAddress(), closed, 500) }.isFailure
        assertTrue(failed)
        assertTrue("the socket stayed open", s.isClosed)
    }

    // ---- 4: who isn't in the band is read up to a small limit ----
    private class Device(val name: String) {
        val keys = BandCrypto.newKeyPair()
        val pub = BandCrypto.encode(keys.public)
        val id = BandCrypto.idOf(pub)
        val hello = BandProtocol.Hello(id, name, pub)
    }

    /** [a] serves one connection; [b] sends [msg] as a stranger would (hello, then one line). Returns what a's callbacks saw. */
    private fun send(a: Device, b: Device, member: Boolean, msg: BandProtocol.Message, onPackage: () -> Unit = {}): Pair<Int, BandProtocol.Message?> {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        var asked = 0
        val t = thread {
            runBlocking {
                BandSession(a.hello).serve(
                    server.accept(), isMember = { member && it == b.id }, ask = { asked++; false }, onTrusted = {},
                    onPackage = { _, _ -> onPackage(); true },
                )
            }
        }
        val reply = Socket(InetAddress.getLoopbackAddress(), server.localPort).use { s ->
            s.soTimeout = 5_000
            val input = s.getInputStream().bufferedReader()
            val out = s.getOutputStream().bufferedWriter()
            BandProtocol.write(out, b.hello)
            BandProtocol.readHello(input)
            BandProtocol.write(out, msg)
            runCatching { BandProtocol.readMessage(input) }.getOrNull()
        }
        t.join(5_000)
        server.close()
        return asked to reply
    }

    @Test fun `a stranger's line past the small limit is not read, a join within it is`() {
        val a = Device("A"); val b = Device("B")
        val (askedBig, replyBig) = send(a, b, member = false, BandProtocol.Message(BandProtocol.Kind.JOIN, "x".repeat(BandProtocol.MAX_STRANGER + 1_000)))
        assertEquals("a huge join was read and asked about", 0, askedBig)
        assertNull(replyBig)
        val (asked, reply) = send(a, b, member = false, BandProtocol.Message(BandProtocol.Kind.JOIN))
        assertEquals(1, asked)
        assertEquals(BandProtocol.Kind.DENIED, reply?.kind)
    }

    @Test fun `a code ack from a stranger within the limit still arrives`() {
        val a = Device("A"); val b = Device("B")
        var got = false
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val t = thread { runBlocking { BandSession(a.hello).serve(server.accept(), { false }, { false }, {}, onCodeAck = { _, body -> got = body == "ok"; true }) } }
        val ok = BandSession(b.hello).send(Socket(InetAddress.getLoopbackAddress(), server.localPort), a.id, "ok", BandProtocol.Kind.CODE_ACK)
        t.join(5_000); server.close()
        assertTrue(ok); assertTrue(got)
    }

    @Test fun `a member keeps the big limit`() {
        val a = Device("A"); val b = Device("B")
        var delivered = 0
        val big = "x".repeat(BandProtocol.MAX_STRANGER * 4)
        val (_, reply) = send(a, b, member = true, BandProtocol.Message(BandProtocol.Kind.PACKAGE, big), onPackage = { delivered++ })
        assertEquals(1, delivered)
        assertEquals(BandProtocol.Kind.OK, reply?.kind)
    }

    @Test fun `readMessage stops at the limit it is given`() {
        val line = BandProtocol.Message(BandProtocol.Kind.JOIN, "y".repeat(500))
        val text = kotlinx.serialization.json.Json.encodeToString(BandProtocol.Message.serializer(), line) + "\n"
        assertNull(BandProtocol.readMessage(text.reader().buffered(), 100))
        assertEquals(BandProtocol.Kind.JOIN, BandProtocol.readMessage(text.reader().buffered(), 10_000)?.kind)
    }

    // ---- 10: exact matching is the same rule as match, without the similarity search ----
    @Test fun `matchExact gives what match calls Found, and nothing else`() {
        val lib = listOf(
            Song(id = "1", title = "Faroeste Caboclo", author = "Legião Urbana", text = ""),
            Song(id = "2", title = "It's My Life", author = "Bon Jovi", text = ""),
            Song(id = "3", title = "It's My Life", author = "Talk Talk", text = ""),
            Song(id = "4", title = "Tempo Perdido", author = "Legião Urbana", text = ""),
        )
        val entries = listOf(
            "Faroeste Caboclo" to "", "faroeste caboclo" to "Legião Urbana", "Faroeste Cabocllo" to "",   // exact, spelling, similar only
            "It's My Life" to "Bon Jovi", "It's My Life" to "Talk Talk", "It's My Life" to "", "It's My Life" to "Outro",   // tie broken by artist, or not
            "Tempo Perdid" to "", "Nada a ver" to "", "" to "",
        )
        entries.forEach { (title, artist) ->
            val e = QrPayload.Entry(title, artist)
            assertEquals("$title / $artist", (SetlistMatcher.match(e, lib) as? QrMatch.Found)?.song, SetlistMatcher.matchExact(e, lib))
        }
        assertTrue(SetlistMatcher.match(QrPayload.Entry("Faroeste Cabocllo", ""), lib) is QrMatch.Similar)
        assertNull(SetlistMatcher.matchExact(QrPayload.Entry("Faroeste Cabocllo", ""), lib))
    }

    // ---- 9a: the strip runs only with the band on ----
    @Test fun `what the strip collects is not collected while the band is off`() = runBlocking {
        val on = MutableStateFlow(false)
        var started = 0
        var stopped = 0
        val seen = mutableListOf<Int?>()
        val job = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            whileBandOn(on) { flow<Int?> { started++; emit(7); try { awaitCancellation() } finally { stopped++ } } }.collect { seen += it }
        }
        assertEquals(0, started)
        assertEquals(listOf<Int?>(null), seen)
        on.value = true
        assertEquals(1, started)
        assertEquals(listOf(null, 7), seen)
        on.value = false
        assertEquals(1, stopped)
        assertEquals(null, seen.last())
        job.cancel()
        assertFalse(started > 1)
    }
}
