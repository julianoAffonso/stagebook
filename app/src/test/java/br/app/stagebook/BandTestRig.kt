package br.app.stagebook

import br.app.stagebook.data.BandConduct
import br.app.stagebook.data.BandConduct.Role
import br.app.stagebook.data.BandLink
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.domain.BandProtocol
import br.app.stagebook.domain.Conduct
import br.app.stagebook.ui.band.conductUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * A leader and a follower joined by what the leader sends, each with its own clock (they never agree). One rig for the
 * conduct tests (audit 22/11: the same class was written in each of them). [watchOpened]: also keeps the songs the
 * follower is told to open.
 */
internal open class TestBand(val repo: LibraryRepository, private val songs: List<Conduct.Item>, watchOpened: Boolean = false) {
    var leaderClock = 1_000_000L
    var followerClock = 987_654_321_000L
    val sent = CopyOnWriteArrayList<Conduct.Msg>()
    val arrived = AtomicInteger()   // messages the listener has already dealt with
    val followerLink = link()
    val leader = testConduct(repo = repo, link = link()).also { it.clock = { leaderClock } }
    val follower = testConduct(repo = repo, link = followerLink).also { it.clock = { followerClock } }
    var listening: BandLink = followerLink
    val opened = CopyOnWriteArrayList<String>()

    init {
        leader.deliver = { msg ->
            sent += msg
            // over the network it goes as text: what the follower gets is what was written
            listening.conductHandler?.invoke(VOICE, Conduct.decode(Conduct.encode(msg))!!)
            arrived.incrementAndGet()
            emptyMap()
        }
        if (watchOpened) watch(follower, opened)
    }

    fun link(of: LibraryRepository = repo) = testLink(repo = of)

    fun watch(who: BandConduct, into: MutableList<String>) {
        CoroutineScope(Dispatchers.Unconfined).launch { who.openSong.collect { into += it } }
    }

    /** What reaches the follower from the leader's device, as it travels: text. */
    fun say(msg: Conduct.Msg, from: BandProtocol.Hello = VOICE) = followerLink.conductHandler?.invoke(from, Conduct.decode(Conduct.encode(msg))!!)

    fun following(who: BandConduct = follower) = who.role.value as Role.Following
    fun left() = follower.role.value as Role.Left
    fun strip(now: Long = System.currentTimeMillis(), bandOn: Boolean = true) = conductUi(follower.role.value, repo.library.value.songs, now, bandOn)
    fun library() = repo.library.value.songs

    /** [act] and the [messages] it sends ("Look for the band" sends two), already dealt with by the listener. */
    fun told(messages: Int = 1, act: () -> Unit) {
        val before = arrived.get()
        act()
        awaitTrue(10_000) { arrived.get() >= before + messages }
    }

    /** Leading from song [index], and the follower already there. */
    fun started(index: Int = 0): TestBand {
        told { leader.start("s1", "Show", songs, index) }
        told { leader.open("s1", index) }
        return this
    }
}
