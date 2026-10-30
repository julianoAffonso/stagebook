package br.app.stagebook.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.Writer

/**
 * The conversation between two devices on the local network: one JSON line per message. **Version:** both send
 * [VERSION] in the [Hello]; if different, the receiver answers [Kind.VERSION] and nobody tries to guess the other's
 * format (the screen asks to update). Every format change bumps [VERSION]. Both start with [Hello] (who they are: id,
 * name and public key). Without trust, the newcomer asks [Kind.JOIN] and the other answers [Kind.ACCEPTED] or
 * [Kind.DENIED] (the user decides, like Bluetooth). With trust, [Kind.PACKAGE] carries a setlist or songs encrypted
 * with the pair key; the receiver answers [Kind.OK].
 */
object BandProtocol {
    const val SERVICE_TYPE = "_stagebook._tcp."
    const val VERSION = 2   // 2: "Quem conduz" (Kind.CONDUCT)

    /** Who the device is: id, name, public key, the protocol version ([v]) and the app's ([app], for the screen). */
    @Serializable
    data class Hello(val id: String, val name: String, val key: String, val v: Int = VERSION, val app: String = "")

    object Kind {
        const val JOIN = "join"
        const val ACCEPTED = "accepted"
        const val DENIED = "denied"
        const val BUSY = "busy"   // the other one is on stage: nothing interrupts the show; ask again later
        const val PACKAGE = "package"   // setlist or songs, encrypted with the pair key (only between band devices)
        const val OK = "ok"
        const val ASK = "ask"         // "who has these songs?" (encrypted list); the answer comes in ANSWER, also encrypted
        const val ANSWER = "answer"
        const val CODE_ACK = "code_ack" // the 3 words "ok" delivered on the local network (we aren't in each other's band yet)
        const val VERSION = "version"   // we're on different protocol versions: update to the same one
        const val CONDUCT = "conduct"   // "Lead the band": setlist and current song (encrypted Conduct.Msg); the answer comes in ANSWER
    }

    @Serializable
    data class Message(val kind: String, val body: String = "")

    private val json = Json { ignoreUnknownKeys = true }

    fun write(out: Writer, hello: Hello) { out.write(json.encodeToString(hello) + "\n"); out.flush() }

    fun write(out: Writer, msg: Message) { out.write(json.encodeToString(msg) + "\n"); out.flush() }

    /** The [Hello] from the other side, checked: the id must match the key they sent. Null if it doesn't. */
    fun readHello(input: BufferedReader): Hello? = runCatching {
        val h = json.decodeFromString<Hello>(input.readLine() ?: return null)
        h.takeIf { BandCrypto.publicKey(it.key) != null && BandCrypto.idOf(it.key) == it.id && it.name.length <= MAX_NAME }
    }.getOrNull()

    fun readMessage(input: BufferedReader): Message? = runCatching { json.decodeFromString<Message>(input.readLine() ?: return null) }.getOrNull()

    const val MAX_NAME = 40
}
