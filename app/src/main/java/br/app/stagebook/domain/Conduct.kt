package br.app.stagebook.domain

import br.app.stagebook.data.model.Song
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * "Quem conduz": um aparelho da banda manda o setlist e a música da vez; quem acompanha abre a música sozinho, sem precisar
 * ter o setlist, só a música. Antes de começar, quem conduz manda [CHECK] e cada um responde o que falta; as faltas de
 * todos voltam em cada mensagem ([Msg.missing]) para a banda inteira ver o alerta, senão a música começa e alguém fica sem
 * letra. Viaja cifrado como [BandProtocol.Kind.CONDUCT].
 */
object Conduct {
    const val CHECK = "check"   // este é o setlist: o que te falta?
    const val OPEN = "open"     // abram a música [Msg.index]
    const val END = "end"       // acabou: cada um volta a ser só ele

    @Serializable
    data class Item(val title: String, val artist: String = "")

    /** O que falta para alguém da banda ([who] = nome do aparelho). */
    @Serializable
    data class Lack(val who: String, val titles: List<String>)

    @Serializable
    data class Msg(
        val type: String,
        val from: String = "",
        val set: String = "",
        val songs: List<Item> = emptyList(),
        val index: Int = -1,
        val missing: List<Lack> = emptyList(),
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(msg: Msg): String = json.encodeToString(Msg.serializer(), msg)

    fun decode(body: String): Msg? = runCatching { json.decodeFromString(Msg.serializer(), body) }.getOrNull()
        ?.takeIf { it.type in setOf(CHECK, OPEN, END) && it.songs.size <= MAX_SONGS }

    /** A música na biblioteca de quem acompanha. Só "achada" conta: parecida vira falta, para o alerta errar para o lado seguro. */
    fun find(item: Item, library: List<Song>): Song? =
        (SetlistMatcher.match(QrPayload.Entry(item.title, item.artist), library) as? QrMatch.Found)?.song

    fun missing(songs: List<Item>, library: List<Song>): List<String> = songs.filter { find(it, library) == null }.map { it.title }

    /** As faltas que valem para a música [index] (para o alerta na hora de abrir). */
    fun missingAt(msg: Msg, index: Int): List<String> {
        val title = msg.songs.getOrNull(index)?.title ?: return emptyList()
        return msg.missing.filter { title in it.titles }.map { it.who }
    }

    /** O que quem acompanha responde: as músicas do setlist que não estão aqui (lista vazia = tem todas). */
    fun lackFor(me: String, msg: Msg, library: List<Song>): Lack = Lack(me, missing(msg.songs, library))

    /** As faltas que valem o alerta (quem tem tudo fica de fora), na ordem dos nomes. */
    fun lacks(replies: Collection<Lack>): List<Lack> = replies.filter { it.titles.isNotEmpty() }.sortedBy { it.who }

    const val MAX_SONGS = 200
}
