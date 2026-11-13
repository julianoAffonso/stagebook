package br.app.stagebook

import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.BandConduct
import br.app.stagebook.data.BandConduct.Role
import br.app.stagebook.data.BandLink
import br.app.stagebook.data.DeviceIdentity
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.model.Song
import br.app.stagebook.domain.BandProtocol
import br.app.stagebook.domain.Conduct
import br.app.stagebook.domain.PendingFollow
import br.app.stagebook.ui.band.ConductUi
import br.app.stagebook.ui.band.conductUi
import br.app.stagebook.ui.stage.FollowBreak
import br.app.stagebook.ui.stage.followBreak
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A follower who tapped Leave can come back: before, nothing on the device offered it and the leader's "Look for the
 * band" didn't bring it back either, only ending and starting again did (two devices, 13/11). Leader and follower are
 * joined inside one process here, never over a real network.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class FollowAgainTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val songs = listOf(Conduct.Item("Primeira"), Conduct.Item("Segunda", "Banda"), Conduct.Item("Terceira"))
    private val voice = BandProtocol.Hello("voz", "Voz", "key")

    private inner class Band(val repo: LibraryRepository) {
        var leaderClock = 1_000_000L
        var followerClock = 987_654_321_000L
        val sent = CopyOnWriteArrayList<Conduct.Msg>()
        val arrived = java.util.concurrent.atomic.AtomicInteger()
        private val followerLink = link()
        val leader = BandConduct(link(), repo, DeviceIdentity(context)).also { it.clock = { leaderClock } }
        val follower = BandConduct(followerLink, repo, DeviceIdentity(context)).also { it.clock = { followerClock } }
        val opened = CopyOnWriteArrayList<String>()

        init {
            leader.deliver = { msg -> sent += msg; say(msg); arrived.incrementAndGet(); emptyMap() }
            CoroutineScope(Dispatchers.Unconfined).launch { follower.openSong.collect { opened += it } }
        }

        fun link() = BandLink(
            context, DeviceIdentity(context), br.app.stagebook.data.BandTrust(context), br.app.stagebook.data.StageGate(),
            br.app.stagebook.data.BandInbox(context), repo,
        )

        /** What reaches the follower from the leader's device, as it travels: text. */
        fun say(msg: Conduct.Msg, from: BandProtocol.Hello = voice) = followerLink.conductHandler?.invoke(from, Conduct.decode(Conduct.encode(msg))!!)

        fun told(messages: Int = 1, act: () -> Unit) {
            val before = arrived.get()
            act()
            await { arrived.get() >= before + messages }
        }

        fun started(): Band {
            told { leader.start("s1", "Show", songs, 0) }
            told { leader.open("s1", 0) }
            return this
        }

        fun left() = follower.role.value as Role.Left
        fun strip(now: Long = System.currentTimeMillis(), bandOn: Boolean = true) = conductUi(follower.role.value, repo.library.value.songs, now, bandOn)
    }

    private fun await(what: () -> Boolean) = runBlocking { withTimeout(10_000) { while (!what()) delay(10) } }

    private fun band(vararg have: String): Pair<Band, Map<String, String>> = runBlocking {
        val repo = LibraryRepository(context, CoroutineScope(Dispatchers.IO))
        withTimeout(15_000) { repo.loaded.first { it } }
        repo.updateLook { it.copy(bandOn = true) }
        withTimeout(15_000) { repo.library.first { it.look.bandOn } }
        val ids = have.associateWith { title ->
            repo.saveSong(Song(title = title, author = songs.first { it.title == title }.artist, text = "{key: G}\n[G]La"))
        }
        Band(repo) to ids
    }

    private fun open(index: Int, breakMs: Long = -1) = Conduct.Msg(Conduct.OPEN, from = "Voz", set = "Show", songs = songs, index = index, breakMs = breakMs)

    @Test fun `after leaving, the strip offers to follow again, and song changes and signs of life pull nobody back`() {
        val (b, ids) = band("Primeira", "Segunda")
        b.started()
        assertEquals(listOf(ids.getValue("Primeira")), b.opened)
        b.follower.leave()
        val name = (b.leader.role.value as Role.Conducting).msg.from
        assertEquals(ConductUi.Left(name), b.strip())
        await { b.follower.followingSong.value == null }   // nothing for the stage to follow

        b.told { b.leader.open("s1", 1) }                    // the leader changes songs
        b.say((b.leader.role.value as Role.Conducting).msg)   // a sign of life: the current song again
        b.told { b.leader.rest("s1", 0) }                    // and goes on a break
        assertEquals(1, b.left().msg.index)
        assertEquals(ConductUi.Left(name), b.strip())
        assertEquals("nothing opened by itself", listOf(ids.getValue("Primeira")), b.opened)
    }

    @Test fun `following again opens the song the leader is on now`() {
        val (b, ids) = band("Primeira", "Segunda")
        b.started()
        b.follower.leave()
        b.told { b.leader.open("s1", 1) }
        b.follower.follow()
        val now = b.follower.role.value as Role.Following
        assertEquals(1 to ids["Segunda"], now.msg.index to now.stage)
        assertEquals(listOf(ids.getValue("Primeira"), ids.getValue("Segunda")), b.opened)
        await { b.follower.followingSong.value == ids["Segunda"] }
        assertTrue(b.strip() is ConductUi.Following)
    }

    @Test fun `following again during the leader's break opens the break with the time it has lasted`() {
        val (b, _) = band("Segunda")
        b.started()
        b.follower.leave()
        b.told { b.leader.rest("s1", 5_000) }   // on the break for 5 s when the follower hears of it
        val since = b.followerClock - 5_000
        b.followerClock += 60_000               // a minute later they come back
        b.follower.follow()
        val now = b.follower.role.value as Role.Following
        assertEquals(since, now.breakSince)
        assertEquals(65_000L, b.followerClock - now.breakSince!!)
        // the song before the break isn't here: the stage opens on the break alone
        assertEquals(listOf(BandConduct.BREAK_ONLY), b.opened)
        assertEquals(FollowBreak(since, "Segunda", "G"), followBreak(now, b.repo.library.value.songs))
    }

    @Test fun `the leader's Look for the band brings back who left, but never off a busy screen`() {
        val (b, ids) = band("Primeira", "Segunda")
        b.started()
        b.follower.leave()
        b.told { b.leader.open("s1", 1) }
        b.sent.clear()
        b.told(2) { b.leader.search() }
        // the setlist first (the call), then the current song: both messages the band already knew
        assertEquals(listOf(Conduct.CHECK, Conduct.OPEN), b.sent.take(2).map { it.type })
        val now = b.follower.role.value as Role.Following
        assertEquals(ids["Segunda"], now.stage)
        assertEquals(ids.getValue("Segunda"), b.opened.last())

        // editing, picking songs, receiving or importing: the song waits and opens on coming back
        val pending = PendingFollow()
        assertNull(pending.arrive(b.opened.last(), pulls = false))
        await { b.follower.followingSong.value == ids["Segunda"] }
        assertEquals(ids["Segunda"], pending.release(pulls = true, now = b.follower.followingSong.value))
    }

    @Test fun `the call during a break keeps the break for who was following`() {
        val (b, _) = band("Segunda")
        b.started()
        b.told { b.leader.rest("s1", 0) }
        val since = (b.follower.role.value as Role.Following).breakSince
        assertEquals(listOf(BandConduct.BREAK_ONLY), b.opened)
        b.say(open(0).copy(type = Conduct.CHECK))
        assertEquals(since, (b.follower.role.value as Role.Following).breakSince)
        assertEquals("the break's stage isn't opened again", listOf(BandConduct.BREAK_ONLY), b.opened)
    }

    @Test fun `the strip goes away when the leader ends, or has been silent for as long as a disconnected one`() {
        val (b, _) = band("Primeira")
        b.started()
        b.follower.leave()
        val at = b.left().at
        assertTrue(b.strip(at + BandConduct.GONE_MS) is ConductUi.Left)
        assertNull(b.strip(at + BandConduct.GONE_MS + 1))
        // heard again: the offer is back
        b.say(open(1))
        assertTrue(b.strip(b.left().at + 1) is ConductUi.Left)
        b.told { b.leader.end() }
        assertEquals(Role.None, b.follower.role.value)
        assertNull(b.strip())
        // nothing left to come back to
        b.follower.follow()
        assertEquals(Role.None, b.follower.role.value)
    }

    @Test fun `starting again brings back who left, as it always did`() {
        val (b, ids) = band("Primeira")
        b.started()
        b.follower.leave()
        b.told { b.leader.start("s1", "Show", songs, 0) }
        assertEquals(ids["Primeira"], (b.follower.role.value as Role.Following).stage)
    }

    @Test fun `with the band off there is no strip and no way back`() = runBlocking {
        val (b, _) = band("Primeira")
        b.say(open(0))   // a leader on another device: nobody leads from this library
        b.follower.leave()
        assertTrue(b.strip() is ConductUi.Left)
        assertNull(b.strip(bandOn = false))
        b.repo.updateLook { it.copy(bandOn = false) }
        withTimeout(15_000) { b.repo.library.first { !it.look.bandOn } }
        b.follower.follow()
        assertTrue(b.follower.role.value is Role.Left)
        assertEquals(emptyList<String>(), b.opened.drop(1))
        // and who was following when the band went off isn't offered anything either
        assertNull(conductUi(Role.Following("voz", open(0), null), emptyList(), System.currentTimeMillis(), bandOn = false))
        b.repo.updateLook { it.copy(bandOn = true) }
        Unit
    }
}
