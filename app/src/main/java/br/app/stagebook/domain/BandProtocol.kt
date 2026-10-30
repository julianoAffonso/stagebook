package br.app.stagebook.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.Writer

/**
 * A conversa entre dois aparelhos na rede local: uma linha JSON por mensagem. **Versão:** os dois mandam [VERSION] no
 * [Hello]; diferente, quem recebe responde [Kind.VERSION] e ninguém tenta adivinhar o formato do outro (a tela pede para
 * atualizar). Toda mudança de formato aumenta [VERSION]. Os dois começam com [Hello] (quem é: id,
 * nome e chave pública). Sem confiança, quem chega pede [Kind.JOIN] e o outro responde [Kind.ACCEPTED] ou [Kind.DENIED]
 * (o usuário decide, como no Bluetooth). Com confiança, [Kind.PACKAGE] leva um setlist ou músicas cifrados com a chave do
 * par; quem recebe responde [Kind.OK].
 */
object BandProtocol {
    const val SERVICE_TYPE = "_stagebook._tcp."
    const val VERSION = 2   // 2: "Quem conduz" (Kind.CONDUCT)

    /** Quem é o aparelho: id, nome, chave pública, a versão do protocolo ([v]) e a do app ([app], para a tela). */
    @Serializable
    data class Hello(val id: String, val name: String, val key: String, val v: Int = VERSION, val app: String = "")

    object Kind {
        const val JOIN = "join"
        const val ACCEPTED = "accepted"
        const val DENIED = "denied"
        const val BUSY = "busy"   // o outro está no palco: nada interrompe o show; pedir de novo depois
        const val PACKAGE = "package"   // setlist ou músicas, cifrado com a chave do par (só entre aparelhos da banda)
        const val OK = "ok"
        const val ASK = "ask"         // "quem tem estas músicas?" (lista cifrada); a resposta vem em ANSWER, também cifrada
        const val ANSWER = "answer"
        const val CODE_ACK = "code_ack" // o "ok" das 3 palavras entregue na rede local (ainda não somos da banda um do outro)
        const val VERSION = "version"   // estamos em versões diferentes do protocolo: atualizem para a mesma
        const val CONDUCT = "conduct"   // "Quem conduz": setlist e música da vez (Conduct.Msg cifrada); a resposta vem em ANSWER
    }

    @Serializable
    data class Message(val kind: String, val body: String = "")

    private val json = Json { ignoreUnknownKeys = true }

    fun write(out: Writer, hello: Hello) { out.write(json.encodeToString(hello) + "\n"); out.flush() }

    fun write(out: Writer, msg: Message) { out.write(json.encodeToString(msg) + "\n"); out.flush() }

    /** O [Hello] do outro lado, conferido: o id tem de ser o da chave que ele mandou. Null se não for. */
    fun readHello(input: BufferedReader): Hello? = runCatching {
        val h = json.decodeFromString<Hello>(input.readLine() ?: return null)
        h.takeIf { BandCrypto.publicKey(it.key) != null && BandCrypto.idOf(it.key) == it.id && it.name.length <= MAX_NAME }
    }.getOrNull()

    fun readMessage(input: BufferedReader): Message? = runCatching { json.decodeFromString<Message>(input.readLine() ?: return null) }.getOrNull()

    const val MAX_NAME = 40
}
