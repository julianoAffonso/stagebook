package br.app.stagebook.data

import br.app.stagebook.domain.BandProtocol
import br.app.stagebook.domain.BandProtocol.Hello
import br.app.stagebook.domain.BandProtocol.Kind
import br.app.stagebook.domain.BandProtocol.Message
import java.net.Socket

/**
 * A conversation between two devices (both sides of the join request), kept apart from the network so tests can use
 * local sockets. The one receiving the connection calls [serve]; the one asking to join calls [join].
 */
class BandSession(private val me: Hello) {
    enum class JoinResult { ACCEPTED, DENIED, BUSY, VERSION, FAILED }

    /**
     * Receives a connection: exchanges the [Hello]s and, if the other asks to join, asks the user ([ask]); if allowed,
     * saves it ([onTrusted]). A band member is accepted without asking (asked again, for example after reinstalling).
     */
    suspend fun serve(
        socket: Socket,
        isMember: (String) -> Boolean,
        ask: suspend (Hello) -> Boolean,
        onTrusted: (Hello) -> Unit,
        busy: () -> Boolean = { false },
        onPackage: (Hello, String) -> Boolean = { _, _ -> false },
        onAsk: (Hello, String) -> String? = { _, _ -> null },
        onCodeAck: (Hello, String) -> Boolean = { _, _ -> false },
        onConduct: (Hello, String) -> String? = { _, _ -> null },
    ) {
        socket.use { s ->
            // whoever connects says who they are quickly: a silent connection doesn't hold a thread for 90 s (this side
            // only reads the hello and one message; waiting for Allow is a write)
            s.soTimeout = HELLO_TIMEOUT_MS
            val input = s.getInputStream().bufferedReader()
            val out = s.getOutputStream().bufferedWriter()
            val them = BandProtocol.readHello(input) ?: return
            if (them.id == me.id) return
            BandProtocol.write(out, me)
            // different protocol versions: doesn't try to read the rest (the format may have changed); both see
            // "update"
            if (them.v != me.v) { BandProtocol.write(out, Message(Kind.VERSION)); return }
            val msg = BandProtocol.readMessage(input) ?: return
            // setlist or songs from a band member (from anyone else, not even read): kept in the inbox, without asking
            // anything
            if (msg.kind == Kind.PACKAGE) {
                val ok = isMember(them.id) && onPackage(them, msg.body)
                BandProtocol.write(out, Message(if (ok) Kind.OK else Kind.DENIED))
                return
            }
            // the 3 words "ok" arriving over the local network: whoever accepted my send joins the band (checked by the
            // code)
            if (msg.kind == Kind.CODE_ACK) {
                BandProtocol.write(out, Message(if (onCodeAck(them, msg.body)) Kind.OK else Kind.DENIED))
                return
            }
            // a band member asks who has the missing songs: answer by itself, without asking anyone. ASK: who has the
            // songs; CONDUCT: "Lead the band" (the answer says what's missing here). Both answer by themselves
            if (msg.kind == Kind.ASK || msg.kind == Kind.CONDUCT) {
                val answer = if (!isMember(them.id)) null else if (msg.kind == Kind.ASK) onAsk(them, msg.body) else onConduct(them, msg.body)
                BandProtocol.write(out, if (answer != null) Message(Kind.ANSWER, answer) else Message(Kind.DENIED))
                return
            }
            if (msg.kind != Kind.JOIN) return
            // on stage: asks nothing (the show isn't interrupted); the requester tries again later
            if (!isMember(them.id) && busy()) { BandProtocol.write(out, Message(Kind.BUSY)); return }
            val ok = isMember(them.id) || ask(them)
            if (ok) onTrusted(them)
            BandProtocol.write(out, Message(if (ok) Kind.ACCEPTED else Kind.DENIED))
        }
    }

    /**
     * Asks to join the band through the device on the other side; if allowed, saves it ([onTrusted]) with its identity.
     */
    fun join(socket: Socket, onTrusted: (Hello) -> Unit): JoinResult = runCatching {
        socket.use { s ->
            s.soTimeout = ANSWER_TIMEOUT_MS
            val input = s.getInputStream().bufferedReader()
            val out = s.getOutputStream().bufferedWriter()
            BandProtocol.write(out, me)
            val them = BandProtocol.readHello(input) ?: return JoinResult.FAILED
            if (them.v != me.v) return JoinResult.VERSION
            BandProtocol.write(out, Message(Kind.JOIN))
            when (BandProtocol.readMessage(input)?.kind) {
                Kind.ACCEPTED -> { onTrusted(them); JoinResult.ACCEPTED }
                Kind.DENIED -> JoinResult.DENIED
                Kind.BUSY -> JoinResult.BUSY
                Kind.VERSION -> JoinResult.VERSION   // the other one is on another version
                else -> JoinResult.FAILED
            }
        }
    }.getOrDefault(JoinResult.FAILED)

    /**
     * Sends a package (already encrypted with the pair key; or a CODE_ACK [kind]) to [expectedId]; true if it stored
     * it.
     */
    fun send(socket: Socket, expectedId: String, body: String, kind: String = Kind.PACKAGE): Boolean = runCatching {
        socket.use { s ->
            s.soTimeout = SEND_TIMEOUT_MS
            val input = s.getInputStream().bufferedReader()
            val out = s.getOutputStream().bufferedWriter()
            BandProtocol.write(out, me)
            val them = BandProtocol.readHello(input) ?: return false
            if (them.id != expectedId) return false   // another device at the same address: don't send
            if (them.v != me.v) return false   // another version: stays in the queue until they update
            BandProtocol.write(out, Message(kind, body))
            BandProtocol.readMessage(input)?.kind == Kind.OK
        }
    }.getOrDefault(false)

    /** Asks [expectedId] who has the songs (request already encrypted); the (encrypted) answer or null. */
    fun ask(socket: Socket, expectedId: String, body: String, kind: String = Kind.ASK): String? = runCatching {
        socket.use { s ->
            s.soTimeout = SEND_TIMEOUT_MS
            val input = s.getInputStream().bufferedReader()
            val out = s.getOutputStream().bufferedWriter()
            BandProtocol.write(out, me)
            val them = BandProtocol.readHello(input) ?: return null
            if (them.id != expectedId || them.v != me.v) return null
            BandProtocol.write(out, Message(kind, body))
            BandProtocol.readMessage(input)?.takeIf { it.kind == Kind.ANSWER }?.body
        }
    }.getOrNull()

    companion object {
        const val SEND_TIMEOUT_MS = 15_000
        const val HELLO_TIMEOUT_MS = 10_000
        const val ANSWER_TIMEOUT_MS = 90_000   // the other one has a minute and a half to tap Allow
    }
}
