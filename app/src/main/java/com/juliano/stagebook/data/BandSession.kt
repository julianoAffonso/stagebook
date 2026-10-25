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
    enum class JoinResult { ACCEPTED, DENIED, BUSY, VERSION, FAILED }

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
        onAsk: (Hello, String) -> String? = { _, _ -> null },
        onCodeAck: (Hello, String) -> Boolean = { _, _ -> false },
        onConduct: (Hello, String) -> String? = { _, _ -> null },
    ) {
        socket.use { s ->
            s.soTimeout = ANSWER_TIMEOUT_MS
            val input = s.getInputStream().bufferedReader()
            val out = s.getOutputStream().bufferedWriter()
            val them = BandProtocol.readHello(input) ?: return
            if (them.id == me.id) return
            BandProtocol.write(out, me)
            // versões diferentes do protocolo: não tenta ler o resto (o formato pode ter mudado); os dois veem "atualizem"
            if (them.v != me.v) { BandProtocol.write(out, Message(Kind.VERSION)); return }
            val msg = BandProtocol.readMessage(input) ?: return
            // setlist ou músicas de alguém da banda (de outro, nem lê): guardado na caixa de entrada, sem perguntar nada
            if (msg.kind == Kind.PACKAGE) {
                val ok = isMember(them.id) && onPackage(them, msg.body)
                BandProtocol.write(out, Message(if (ok) Kind.OK else Kind.DENIED))
                return
            }
            // o "ok" das 3 palavras chegando pela rede local: quem aceitou o meu envio vira da banda (conferido pelo código)
            if (msg.kind == Kind.CODE_ACK) {
                BandProtocol.write(out, Message(if (onCodeAck(them, msg.body)) Kind.OK else Kind.DENIED))
                return
            }
            // alguém da banda pergunta quem tem as músicas que faltam: responde sozinho, sem perguntar nada a ninguém
            // ASK: quem tem as músicas; CONDUCT: "Quem conduz" (a resposta diz o que falta aqui). Os dois respondem sozinhos
            if (msg.kind == Kind.ASK || msg.kind == Kind.CONDUCT) {
                val answer = if (!isMember(them.id)) null else if (msg.kind == Kind.ASK) onAsk(them, msg.body) else onConduct(them, msg.body)
                BandProtocol.write(out, if (answer != null) Message(Kind.ANSWER, answer) else Message(Kind.DENIED))
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
            if (them.v != me.v) return JoinResult.VERSION
            BandProtocol.write(out, Message(Kind.JOIN))
            when (BandProtocol.readMessage(input)?.kind) {
                Kind.ACCEPTED -> { onTrusted(them); JoinResult.ACCEPTED }
                Kind.DENIED -> JoinResult.DENIED
                Kind.BUSY -> JoinResult.BUSY
                Kind.VERSION -> JoinResult.VERSION   // o outro está em outra versão
                else -> JoinResult.FAILED
            }
        }
    }.getOrDefault(JoinResult.FAILED)

    /** Manda um pacote (já cifrado com a chave do par; ou um [kind] CODE_ACK) para [expectedId]; true se ele guardou. */
    fun send(socket: Socket, expectedId: String, body: String, kind: String = Kind.PACKAGE): Boolean = runCatching {
        socket.use { s ->
            s.soTimeout = SEND_TIMEOUT_MS
            val input = s.getInputStream().bufferedReader()
            val out = s.getOutputStream().bufferedWriter()
            BandProtocol.write(out, me)
            val them = BandProtocol.readHello(input) ?: return false
            if (them.id != expectedId) return false   // outro aparelho no mesmo endereço: não manda
            if (them.v != me.v) return false   // outra versão: fica na fila até atualizarem
            BandProtocol.write(out, Message(kind, body))
            BandProtocol.readMessage(input)?.kind == Kind.OK
        }
    }.getOrDefault(false)

    /** Pergunta a [expectedId] quem tem as músicas (pedido já cifrado); a resposta (cifrada) ou null. */
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
        const val ANSWER_TIMEOUT_MS = 90_000   // o outro tem 1 min e meio para tocar em Autorizar
    }
}
