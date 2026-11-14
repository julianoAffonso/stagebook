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
import br.app.stagebook.ui.stage.FollowBreak
import br.app.stagebook.ui.stage.followBreak
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Leading the band through a setlist's break: the followers' stages show the break while the leader's does, with the
 * same time whatever each device's clock says, and leave it when the leader does. An app from before this reads the
 * same messages and stays on the song.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ConductBreakTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val songs = listOf(Conduct.Item("Primeira"), Conduct.Item("Segunda", "Banda"), Conduct.Item("Terceira"))
    private val voice = BandProtocol.Hello("voz", "Voz", "key")

    /** A leader and a follower joined by what the leader sends, each with its own clock (they never agree). */
    private inner class Band(val repo: LibraryRepository) {
        var leaderClock = 1_000_000L
        var followerClock = 987_654_321_000L
        val sent = CopyOnWriteArrayList<Conduct.Msg>()
        val arrived = java.util.concurrent.atomic.AtomicInteger()   // messages the listener has already dealt with
        private val followerLink = link(repo)
        val leader = BandConduct(link(repo), repo, DeviceIdentity(context)).also { it.clock = { leaderClock } }
        val follower = BandConduct(followerLink, repo, DeviceIdentity(context)).also { it.clock = { followerClock } }
        var listening: BandLink = followerLink

        init {
            leader.deliver = { msg ->
                sent += msg
                // over the network it goes as text: what the follower gets is what was written
                listening.conductHandler?.invoke(voice, Conduct.decode(Conduct.encode(msg))!!)
                arrived.incrementAndGet()
                emptyMap()
            }
        }

        fun link(repo: LibraryRepository) = BandLink(
            context, DeviceIdentity(context), br.app.stagebook.data.BandTrust(context), br.app.stagebook.data.StageGate(),
            br.app.stagebook.data.BandInbox(context), repo,
        )

        fun following(who: BandConduct = follower) = who.role.value as Role.Following

        /** [act] and the [messages] it sends ("Look for the band" sends two), already dealt with by the listener. */
        fun told(messages: Int = 1, act: () -> Unit) {
            val before = arrived.get()
            act()
            await { arrived.get() >= before + messages }
        }

        /** Leading from song [index], and the follower already there. */
        fun started(index: Int = 0): Band {
            told { leader.start("s1", "Show", songs, index) }
            told { leader.open("s1", index) }
            return this
        }
    }

    private fun await(what: () -> Boolean) = runBlocking { withTimeout(10_000) { while (!what()) delay(10) } }

    private fun band(): Band = runBlocking {
        val repo = LibraryRepository(context, CoroutineScope(Dispatchers.IO))
        withTimeout(15_000) { repo.loaded.first { it } }
        repo.updateLook { it.copy(bandOn = true) }   // off, leading would end as soon as it starts
        withTimeout(15_000) { repo.library.first { it.look.bandOn } }
        Band(repo)
    }

    /** The leader's message as an app from before the breaks knows it (Conduct.Msg at protocol version 2). */
    @Serializable
    private data class OldMsg(
        val type: String, val from: String = "", val set: String = "", val songs: List<Conduct.Item> = emptyList(), val index: Int = -1,
        val missing: List<Conduct.Lack> = emptyList(),
    )
    private val oldJson = Json { ignoreUnknownKeys = true }

    @Test fun `the leader's break shows on the follower with the same time, whatever the two clocks say`() {
        val b = band().started()
        assertNull(b.following().breakSince)
        b.told { b.leader.rest("s1", 5_000) }   // on the break for 5 s already
        assertEquals(b.followerClock - 5_000, b.following().breakSince)
        assertEquals(0, b.following().msg.index)   // the song on stage is still the one before the break
        assertEquals(5_000L, b.sent.last().breakMs)
        assertEquals(Conduct.OPEN, b.sent.last().type)
    }

    @Test fun `someone who joins in the middle of the break gets the time it has lasted`() {
        val b = band().started()
        b.told { b.leader.rest("s1", 0) }
        // four minutes later another device shows up, and the leader says the current song again
        b.leaderClock += 240_000
        val lateLink = b.link(b.repo)
        val late = BandConduct(lateLink, b.repo, DeviceIdentity(context)).also { it.clock = { 5_000_000L } }
        b.listening = lateLink
        b.told(2) { b.leader.search() }
        assertEquals(5_000_000L - 240_000, b.following(late).breakSince)
        assertEquals(240_000L, b.sent.last().breakMs)
    }

    @Test fun `the same break told again keeps its start, and the slower message doesn't push it later`() {
        val b = band().started()
        b.told { b.leader.rest("s1", 0) }
        val since = b.following().breakSince!!
        // 3 minutes on both devices, and this message takes 700 ms to arrive
        b.leaderClock += 180_000
        b.followerClock += 180_700
        b.told(2) { b.leader.search() }
        assertEquals(180_000L, b.sent.last().breakMs)
        assertEquals(since, b.following().breakSince)
        // a break that started again is another one: it counts from its own start
        b.told { b.leader.rest("s1", null) }
        assertNull(b.following().breakSince)
        b.told { b.leader.rest("s1", 0) }
        assertEquals(b.followerClock, b.following().breakSince)
    }

    @Test fun `going on to the next song ends the break on the follower, with one message`() {
        val b = band().started()
        b.told { b.leader.rest("s1", 0) }
        val before = b.sent.size
        b.told { b.leader.rest("s1", null); b.leader.open("s1", 1) }
        assertNull(b.following().breakSince)
        runBlocking { delay(BandConduct.LEFT_BREAK_MS * 3) }
        // the break's own "it's over" didn't go: arriving late it would take the band back to the first song
        assertEquals(listOf(1), b.sent.drop(before).map { it.index }.distinct())
        assertTrue(b.sent.drop(before).all { it.breakMs == -1L })
        assertEquals(1, b.following().msg.index)
    }

    @Test fun `going back to the song before the break ends it on the follower`() {
        val b = band().started()
        b.told { b.leader.rest("s1", 0) }
        b.told { b.leader.rest("s1", null) }
        assertNull(b.following().breakSince)
        assertEquals(0, b.following().msg.index)
    }

    @Test fun `jumping to a song from the setlist ends the break on the follower`() {
        val b = band().started()
        b.told { b.leader.rest("s1", 0) }
        b.told { b.leader.rest("s1", null); b.leader.open("s1", 2) }
        assertEquals(2, b.following().msg.index)
        assertNull(b.following().breakSince)
    }

    @Test fun `ending Lead the band ends the break too`() {
        val b = band().started()
        b.told { b.leader.rest("s1", 0) }
        b.told { b.leader.end() }
        assertEquals(Role.None, b.follower.role.value)
        // and the next time it leads, no break is left over
        b.started(index = 1)
        assertNull(b.following().breakSince)
        assertTrue(b.sent.takeLast(2).all { it.breakMs == -1L })
    }

    @Test fun `the break screen says the next song as this device has it, or as the leader announced it`() {
        val role = Role.Following("voz", Conduct.Msg(Conduct.OPEN, songs = songs, index = 0), "s1", breakSince = 42L)
        val mine = listOf(Song(id = "a", title = "segunda", author = "Banda", text = "{key: G}\n[G]La"))
        assertEquals(FollowBreak(42L, "segunda", "G"), followBreak(role, mine))
        // without the next song here: the title the leader said, and no key
        assertEquals(FollowBreak(42L, "Segunda", ""), followBreak(role, emptyList()))
        assertNull(followBreak(role.copy(breakSince = null), mine))
    }

    @Test fun `a message from an app before the breaks leaves the follower on the song`() {
        val b = band()
        val old = oldJson.encodeToString(OldMsg.serializer(), OldMsg(Conduct.OPEN, "Voz", "Show", songs, 1))
        assertTrue("breakMs" !in old)
        b.listening.conductHandler!!.invoke(voice, Conduct.decode(old)!!)
        assertEquals(1, b.following().msg.index)
        assertNull(b.following().breakSince)
    }

    @Test fun `an app from before the breaks reads the break's message as the song on stage`() {
        val new = Conduct.encode(Conduct.Msg(Conduct.OPEN, "Voz", "Show", songs, 1, breakMs = 90_000))
        assertTrue("breakMs" in new)
        assertEquals(OldMsg(Conduct.OPEN, "Voz", "Show", songs, 1), oldJson.decodeFromString(OldMsg.serializer(), new))
        // and with no break the message is, byte for byte, the one it always was
        assertEquals(
            oldJson.encodeToString(OldMsg.serializer(), OldMsg(Conduct.OPEN, "Voz", "Show", songs, 1)),
            Conduct.encode(Conduct.Msg(Conduct.OPEN, "Voz", "Show", songs, 1)),
        )
        assertEquals(3, BandProtocol.VERSION)
    }

    @Test fun `a break that makes no sense is no break, and never an error`() {
        fun rest(index: Int, ms: Long, type: String = Conduct.OPEN) = Conduct.Msg(type, songs = songs, index = index, breakMs = ms).rest
        assertEquals(0L, rest(0, 0))
        assertEquals(90_000L, rest(1, 90_000))
        assertNull(rest(0, -1))
        assertNull(rest(0, -5_000))
        assertNull(rest(0, Long.MAX_VALUE))
        assertNull(rest(0, Conduct.MAX_BREAK_MS + 1))
        assertNull(rest(2, 1_000))    // after the last song there's no break
        assertNull(rest(-1, 1_000))
        assertNull(rest(7, 1_000))
        assertNull(rest(0, 1_000, Conduct.CHECK))
        assertNull(Conduct.decode("""{"type":"open","index":0,"breakMs":"soon"}"""))
        assertNull(Conduct.decode("""{"type":"open","index":0,"breakMs":1e40}"""))
        // on the follower: the song opens, no break
        val b = band()
        b.listening.conductHandler!!.invoke(voice, Conduct.Msg(Conduct.OPEN, songs = songs, index = 2, breakMs = 1_000))
        assertNull(b.following().breakSince)
    }

    @Test fun `with the band off, or not leading, a break sends nothing`() = runBlocking {
        val repo = LibraryRepository(context, CoroutineScope(Dispatchers.IO))
        withTimeout(15_000) { repo.loaded.first { it } }
        repo.updateLook { it.copy(bandOn = true) }
        withTimeout(15_000) { repo.library.first { it.look.bandOn } }
        val b = Band(repo)
        b.leader.rest("s1", 0)   // nobody is leading
        b.started()
        b.leader.rest("other setlist", 0)   // a stage of another setlist
        repo.updateLook { it.copy(bandOn = false) }
        withTimeout(15_000) { b.leader.role.first { it == Role.None } }
        delay(300)   // the end's own message leaves
        val before = b.sent.size
        b.leader.rest("s1", 0)
        b.leader.rest("s1", null)
        delay(BandConduct.LEFT_BREAK_MS * 3)
        assertEquals(before, b.sent.size)
        assertTrue(b.sent.none { it.breakMs >= 0 })
        repo.updateLook { it.copy(bandOn = true) }   // as the other tests expect to find it
    }
}
