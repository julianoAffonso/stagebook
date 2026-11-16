package br.app.stagebook.ui.band

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.app.stagebook.data.BandConduct
import br.app.stagebook.data.BandConduct.Role
import br.app.stagebook.data.BandInternet
import br.app.stagebook.data.BandLink
import br.app.stagebook.data.BandTrust
import br.app.stagebook.data.ImportQueue
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.domain.Conduct
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What a follower can do about the songs of the leader's setlist it doesn't have (user, 15/11), the same three things the
 * review of a shared setlist offers: [bandDevices] on this network to ask, [internetFrom] (the leader, when it is in the band
 * and the internet is on) to ask online, and the sites to import them from.
 */
@Immutable
data class AskOptions(val bandDevices: Int = 0, val internetFrom: String? = null, val defaultKind: String = br.app.stagebook.domain.AskKind.CHORDS)

@HiltViewModel
class ConductAskViewModel @Inject constructor(
    private val conduct: BandConduct,
    private val repository: LibraryRepository,
    private val bandInternet: BandInternet,
    private val trust: BandTrust,
    private val importQueue: ImportQueue,
) : ViewModel() {
    val options: StateFlow<AskOptions> = combine(conduct.role, conduct.online, repository.library) { role, online, lib ->
        val following = role as? Role.Following
        AskOptions(
            bandDevices = if (following != null && lib.look.bandOn) online.size else 0,
            defaultKind = br.app.stagebook.domain.AskKind.default(lib.look.chords),
            internetFrom = following?.takeIf { lib.look.bandOn && lib.look.internetOn && trust.isMember(it.fromId) }?.let { trust.member(it.fromId)?.name },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AskOptions())

    /** Every song of the setlist not here, asked of the band on this network. */
    fun askBand(kind: String) = conduct.askAll(kind)

    private val internet = Channel<Pair<String, Boolean>>(Channel.BUFFERED)
    /** (the leader's name, whether the request went) after "ask online". */
    val internetResult = internet.receiveAsFlow()

    /** The songs of the setlist not here, asked of the leader online: they arrive in "Received from the band" when it opens the app. */
    fun askInternet(kind: String) {
        val following = conduct.role.value as? Role.Following ?: return
        val name = trust.member(following.fromId)?.name ?: return
        val wanted = missingItems(following).map { BandLink.Wanted(it.title, it.artist, kind) }
        if (wanted.isEmpty()) return
        viewModelScope.launch { internet.send(name to bandInternet.requestSongs(following.fromId, wanted)) }
    }

    private fun missingItems(following: Role.Following) =
        following.msg.songs.filter { Conduct.find(it, repository.library.value.songs) == null }.distinctBy { it.title to it.artist }

    /**
     * Makes the setlist here (the leader's, under its name: the one with that name if there is one) with the songs this device
     * has, and starts the batch import of the missing ones from the sites. False when nothing is missing.
     */
    fun importMissing(): Boolean {
        val following = conduct.role.value as? Role.Following ?: return false
        val songs = repository.library.value.songs
        val slots = following.msg.songs.map { Conduct.find(it, songs)?.id }
        val missing = slots.withIndex().filter { it.value == null }.map { (i, _) -> ImportQueue.Item(following.msg.songs[i].title, following.msg.songs[i].artist, i) }
        if (missing.isEmpty()) return false
        val name = following.msg.set.ifBlank { following.msg.songs.firstOrNull()?.title.orEmpty() }
        val existing = repository.library.value.sets.firstOrNull { br.app.stagebook.domain.TextKey.of(it.name) == br.app.stagebook.domain.TextKey.of(name) }?.id
        val setId = existing ?: repository.createSetlist(name)
        repository.setReceivedSetlist(setId, slots, null)
        importQueue.start(setId, name, slots, missing)
        return true
    }
}
