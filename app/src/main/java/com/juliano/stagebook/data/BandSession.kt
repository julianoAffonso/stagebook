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
    enum class JoinResult { ACCEPTED, DENIED, FAILED }

    /**
     * Recebe uma conexão: troca os [Hello] e, se o outro pede para entrar, pergunta ao usuário ([ask]); autorizado, grava
     * ([onTrusted]). Quem já é da banda é aceito sem perguntar (pediu de novo, por exemplo depois de reinstalar).
     */
    suspend fun serve(socket: Socket, isMember: (String) -> Boolean, ask: suspend (Hello) -> Boolean, onTrusted: (Hello) -> Unit) {
        socket.use { s ->
            s.soTimeout = ANSWER_TIMEOUT_MS
            val input = s.getInputStream().bufferedReader()
            val out = s.getOutputStream().bufferedWriter()
            val them = BandProtocol.readHello(input) ?: return
            if (them.id == me.id) return
            BandProtocol.write(out, me)
            val msg = BandProtocol.readMessage(input) ?: return
            if (msg.kind != Kind.JOIN) return
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
                else -> JoinResult.FAILED
            }
        }
    }.getOrDefault(JoinResult.FAILED)

    companion object {
        const val ANSWER_TIMEOUT_MS = 90_000   // o outro tem 1 min e meio para tocar em Autorizar
    }
}
