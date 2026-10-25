package com.juliano.stagebook.data

import androidx.compose.runtime.Immutable
import com.juliano.stagebook.domain.BandProtocol
import com.juliano.stagebook.domain.Conduct
import kotlinx.coroutines.channels.BufferOverflow
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
        data class Conducting(val msg: Conduct.Msg, val noAnswer: Int = 0) : Role

        /** [songId]: a música da vez aqui (null = falta aqui). */
        @Immutable
        data class Following(val fromId: String, val msg: Conduct.Msg, val songId: String?) : Role
    }

    private val _role = MutableStateFlow<Role>(Role.None)
    val role: StateFlow<Role> = _role.asStateFlow()

    private val _openSong = MutableSharedFlow<String>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    /** Quem acompanha: a música para abrir no palco agora. */
    val openSong: SharedFlow<String> = _openSong

    @Volatile private var leftFrom: String? = null

    init { link.conductHandler = ::onMessage }

    /** Liga com o setlist: pergunta a todos o que falta (o alerta aparece antes da primeira música). */
    suspend fun start(set: String, songs: List<Conduct.Item>, index: Int = 0) =
        send(Conduct.Msg(Conduct.CHECK, from = identity.name.value, set = set, songs = songs, index = index))

    suspend fun open(index: Int) {
        val now = (_role.value as? Role.Conducting)?.msg ?: return
        send(now.copy(type = Conduct.OPEN, index = index))
    }

    suspend fun end() {
        val now = (_role.value as? Role.Conducting)?.msg ?: return
        _role.value = Role.None
        link.conduct(now.copy(type = Conduct.END, missing = emptyList()))
    }

    /** Quem acompanha: para de seguir (volta se quem conduz começar de novo com [start]). */
    fun leave() {
        leftFrom = (_role.value as? Role.Following)?.fromId
        _role.value = Role.None
    }

    private suspend fun send(msg: Conduct.Msg) {
        val online = link.onlineMembers.value.size
        _role.value = Role.Conducting(msg)
        val replies = link.conduct(msg)
        val withLacks = msg.copy(missing = Conduct.lacks(replies.values))
        _role.value = Role.Conducting(withLacks, noAnswer = (online - replies.size).coerceAtLeast(0))
        // as faltas mudaram: manda de novo para todos verem o mesmo alerta
        if (withLacks.missing != msg.missing) link.conduct(withLacks)
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
}
