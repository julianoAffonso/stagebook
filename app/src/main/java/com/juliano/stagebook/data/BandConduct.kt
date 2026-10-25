package com.juliano.stagebook.data

import androidx.compose.runtime.Immutable
import com.juliano.stagebook.domain.BandProtocol
import com.juliano.stagebook.domain.Conduct
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Quem conduz" / "acompanha" (pela rede local, [BandLink.conduct]). Quem conduz liga com o setlist ([start]: cada um diz
 * o que falta), troca de música ([open]) e encerra ([end]); as faltas de todos vão em cada mensagem, então a banda inteira
 * vê o alerta. Quem acompanha não liga nada: a primeira mensagem já o coloca acompanhando e a música abre sozinha
 * ([openSong]); [leave] para de acompanhar até quem conduz começar de novo.
 */
@Singleton
class BandConduct @Inject constructor(
    private val link: BandLink,
    private val repository: LibraryRepository,
    private val identity: DeviceIdentity,
) {
    sealed interface Role {
        data object None : Role

        /** [noAnswer]: da banda online, quem não respondeu (outra versão, fora do app). */
        @Immutable
        data class Conducting(val setId: String, val msg: Conduct.Msg, val noAnswer: Int = 0) : Role

        /** [songId]: a música da vez aqui (null = falta aqui); [at]: última notícia de quem conduz (relógio do aparelho). */
        @Immutable
        data class Following(val fromId: String, val msg: Conduct.Msg, val songId: String?, val at: Long = System.currentTimeMillis()) : Role {
            /** Sem notícia há mais de [GONE_MS]: quem conduz sumiu (bateria, app fechado, fora do Wi-Fi). */
            fun gone(now: Long = System.currentTimeMillis()) = now - at > GONE_MS
        }
    }

    private val _role = MutableStateFlow<Role>(Role.None)
    val role: StateFlow<Role> = _role.asStateFlow()

    private val _openSong = MutableSharedFlow<String>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    /** Quem acompanha: a música para abrir no palco agora. */
    val openSong: SharedFlow<String> = _openSong

    @Volatile private var leftFrom: String? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var searchedAt = -1   // a música em que a falha de entrega já ligou a procura (uma vez por música)

    init {
        link.conductHandler = ::onMessage
        // conduzindo: sinal de vida a cada 3 min (a música da vez de novo; quem acompanha não pula de música por isso)
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(ALIVE_MS)
                val now = _role.value as? Role.Conducting ?: continue
                send(now.setId, now.msg.copy(type = Conduct.OPEN))
            }
        }
        // conduzindo: alguém da banda voltou para o Wi-Fi (achado numa procura curta) → recebe a música da vez, sem toque
        scope.launch {
            var before = emptySet<String>()
            link.onlineMembers.collect { peers ->
                val ids = peers.map { it.id }.toSet()
                val conducting = _role.value as? Role.Conducting
                if (conducting != null && (ids - before).isNotEmpty()) send(conducting.setId, conducting.msg.copy(type = Conduct.OPEN))
                before = ids
            }
        }
    }

    /** Liga com o setlist: pergunta a todos o que falta (o alerta aparece antes da primeira música) e procura a banda. */
    fun start(setId: String, set: String, songs: List<Conduct.Item>, index: Int = 0) {
        searchedAt = -1
        _role.value = Role.Conducting(setId, Conduct.Msg(Conduct.CHECK, from = identity.name.value, set = set, songs = songs, index = index))
        link.burst(evenOnStage = true)
        scope.launch { send(setId, (_role.value as Role.Conducting).msg) }
    }

    /** Quem conduz mudou de música no palco do setlist [setId]. */
    fun open(setId: String, index: Int) {
        val now = _role.value as? Role.Conducting ?: return
        if (now.setId != setId || (now.msg.index == index && now.msg.type == Conduct.OPEN)) return
        scope.launch { send(setId, now.msg.copy(type = Conduct.OPEN, index = index)) }
    }

    /** "Procurar a banda": 30 s de procura no palco; quem aparecer recebe a música da vez. */
    fun search() = link.burst(evenOnStage = true)

    fun end() {
        val now = (_role.value as? Role.Conducting)?.msg ?: return
        _role.value = Role.None
        scope.launch { link.conduct(now.copy(type = Conduct.END, missing = emptyList())) }
    }

    /** Quem acompanha: para de seguir (volta se quem conduz começar de novo com [start]). */
    fun leave() {
        leftFrom = (_role.value as? Role.Following)?.fromId
        _role.value = Role.None
    }

    private suspend fun send(setId: String, msg: Conduct.Msg) {
        val members = link.onlineMembers.value.size
        _role.value = Role.Conducting(setId, msg)
        val replies = link.conduct(msg)
        if (_role.value !is Role.Conducting) return   // encerrado enquanto mandava
        val withLacks = msg.copy(missing = Conduct.lacks(replies.values))
        val noAnswer = (members - replies.size).coerceAtLeast(0)
        _role.value = Role.Conducting(setId, withLacks, noAnswer)
        // as faltas mudaram: manda de novo para todos verem o mesmo alerta
        if (withLacks.missing != msg.missing) link.conduct(withLacks)
        // alguém não recebeu: uma procura curta por música (pode ter trocado de endereço no Wi-Fi)
        if (noAnswer > 0 && searchedAt != msg.index) { searchedAt = msg.index; link.burst(evenOnStage = true) }
    }

    // quem acompanha: chega pela rede (outra linha de execução); responde o que falta aqui
    private fun onMessage(from: BandProtocol.Hello, msg: Conduct.Msg): Conduct.Lack? {
        if (msg.type == Conduct.END) {
            if ((_role.value as? Role.Following)?.fromId == from.id) _role.value = Role.None
            leftFrom = null
            return Conduct.Lack(identity.name.value, emptyList())
        }
        if (msg.type == Conduct.CHECK) leftFrom = null
        if (leftFrom == from.id || _role.value is Role.Conducting) return null
        val library = repository.library.value.songs
        val song = msg.songs.getOrNull(msg.index)?.let { Conduct.find(it, library) }
        val before = _role.value as? Role.Following
        _role.value = Role.Following(from.id, msg, song?.id)
        if (song != null && (before?.songId != song.id || before.msg.index != msg.index)) _openSong.tryEmit(song.id)
        return Conduct.lackFor(identity.name.value, msg, library)
    }

    companion object {
        const val ALIVE_MS = 3 * 60_000L
        const val GONE_MS = 10 * 60_000L
    }
}
