package com.juliano.stagebook.data

import com.juliano.stagebook.domain.BandProtocol
import com.juliano.stagebook.domain.BandProtocol.Hello
import com.juliano.stagebook.domain.BandProtocol.Kind
import com.juliano.stagebook.domain.BandProtocol.Message
import java.net.Socket

/**
 * Uma conversa entre dois aparelhos (os dois lados do pedido de entrada), separada da rede para os testes usarem sockets
 * locais. Quem recebe a conexão chama [serve]; quem pede para entrar chama [join].
 */
class BandSession(private val me: Hello) {
    enum class JoinResult { ACCEPTED, DENIED, BUSY, FAILED }

    /**
     * Recebe uma conexão: troca os [Hello] e, se o outro pede para entrar, pergunta ao usuário ([ask]); autorizado, grava
     * ([onTrusted]). Quem já é da banda é aceito sem perguntar (pediu de novo, por exemplo depois de reinstalar).
     */
    suspend fun serve(
        socket: Socket,
        isMember: (String) -> Boolean,
        ask: suspend (Hello) -> Boolean,
        onTrusted: (Hello) -> Unit,
        busy: () -> Boolean = { false },
        onPackage: (Hello, String) -> Boolean = { _, _ -> false },
    ) {
        socket.use { s ->
            s.soTimeout = ANSWER_TIMEOUT_MS
            val input = s.getInputStream().bufferedReader()
            val out = s.getOutputStream().bufferedWriter()
            val them = BandProtocol.readHello(input) ?: return
            if (them.id == me.id) return
            BandProtocol.write(out, me)
            val msg = BandProtocol.readMessage(input) ?: return
            // setlist ou músicas de alguém da banda (de outro, nem lê): guardado na caixa de entrada, sem perguntar nada
            if (msg.kind == Kind.PACKAGE) {
                val ok = isMember(them.id) && onPackage(them, msg.body)
                BandProtocol.write(out, Message(if (ok) Kind.OK else Kind.DENIED))
                return
            }
            if (msg.kind != Kind.JOIN) return
            // no palco: não pergunta nada (o show não é interrompido); quem pediu tenta de novo depois
            if (!isMember(them.id) && busy()) { BandProtocol.write(out, Message(Kind.BUSY)); return }
            val ok = isMember(them.id) || ask(them)
            if (ok) onTrusted(them)
            BandProtocol.write(out, Message(if (ok) Kind.ACCEPTED else Kind.DENIED))
        }
    }

    /** Pede para entrar na banda pelo aparelho do outro lado; autorizado, grava ([onTrusted]) com a identidade dele. */
    fun join(socket: Socket, onTrusted: (Hello) -> Unit): JoinResult = runCatching {
        socket.use { s ->
            s.soTimeout = ANSWER_TIMEOUT_MS
            val input = s.getInputStream().bufferedReader()
            val out = s.getOutputStream().bufferedWriter()
            BandProtocol.write(out, me)
            val them = BandProtocol.readHello(input) ?: return JoinResult.FAILED
            BandProtocol.write(out, Message(Kind.JOIN))
            when (BandProtocol.readMessage(input)?.kind) {
                Kind.ACCEPTED -> { onTrusted(them); JoinResult.ACCEPTED }
                Kind.DENIED -> JoinResult.DENIED
                Kind.BUSY -> JoinResult.BUSY
                else -> JoinResult.FAILED
            }
        }
    }.getOrDefault(JoinResult.FAILED)

    /** Manda um pacote (já cifrado com a chave do par) para [expectedId]; true se ele guardou. */
    fun send(socket: Socket, expectedId: String, body: String): Boolean = runCatching {
        socket.use { s ->
            s.soTimeout = SEND_TIMEOUT_MS
            val input = s.getInputStream().bufferedReader()
            val out = s.getOutputStream().bufferedWriter()
            BandProtocol.write(out, me)
            val them = BandProtocol.readHello(input) ?: return false
            if (them.id != expectedId) return false   // outro aparelho no mesmo endereço: não manda
            BandProtocol.write(out, Message(Kind.PACKAGE, body))
            BandProtocol.readMessage(input)?.kind == Kind.OK
        }
    }.getOrDefault(false)

    companion object {
        const val SEND_TIMEOUT_MS = 15_000
        const val ANSWER_TIMEOUT_MS = 90_000   // o outro tem 1 min e meio para tocar em Autorizar
    }
}
