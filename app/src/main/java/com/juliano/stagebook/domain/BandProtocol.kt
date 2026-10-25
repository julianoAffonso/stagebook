package com.juliano.stagebook.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.Writer

/**
 * A conversa entre dois aparelhos na rede local: uma linha JSON por mensagem. Os dois começam com [Hello] (quem é: id,
 * nome e chave pública). Sem confiança, quem chega pede [Kind.JOIN] e o outro responde [Kind.ACCEPTED] ou [Kind.DENIED]
 * (o usuário decide, como no Bluetooth). Com confiança, as mensagens seguintes vão cifradas com a chave do par.
 */
object BandProtocol {
    const val SERVICE_TYPE = "_stagebook._tcp."
    const val VERSION = 1

    @Serializable
    data class Hello(val id: String, val name: String, val key: String, val v: Int = VERSION)

    object Kind {
        const val JOIN = "join"
        const val ACCEPTED = "accepted"
        const val DENIED = "denied"
        const val BUSY = "busy"   // o outro está no palco: nada interrompe o show; pedir de novo depois
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
