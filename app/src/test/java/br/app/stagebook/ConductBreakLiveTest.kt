package br.app.stagebook

import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.BandConduct
import br.app.stagebook.data.BandConduct.Role
import br.app.stagebook.data.BandLink
import br.app.stagebook.data.DeviceIdentity
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.model.Song
import br.app.stagebook.domain.BandProtocol
import br.app.stagebook.domain.Conduct
import br.app.stagebook.ui.stage.followBreak
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The break's counter live between two devices (device test v0.1.1123: the leader stopped at 44:27, then zeroed at
 * 00:04, and the follower said 45:18). The leader's real stage (view model) pauses, resumes and zeroes the break; the
 * follower's real [BandConduct] gets what goes out, in memory, as text.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ConductBreakLiveTest {
    @Before fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun reset() = Dispatchers.resetMain()

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val voice = BandProtocol.Hello("voz", "Voz", "key")

    private inner class Pair2 {
        val repo = LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO)).also { r ->
            runBlocking {
                withTimeout(15_000) { r.loaded.first { it } }
                r.restore(emptyList(), emptyList())
                r.updateLook { it.copy(bandOn = true) }
                withTimeout(15_000) { r.library.first { it.look.bandOn } }
            }
        }
        val set: String
        val items: List<Conduct.Item>
        init {
            val ids = listOf("A", "B", "C").map { repo.saveSong(Song(title = it, author = "x", text = "{key: G}\n$it")) }
            set = repo.createSetlist("Show").also { repo.addToSetlist(it, ids); repo.addBreak(it) }   // the break is before B
            items = listOf("A", "B", "C").map { Conduct.Item(it, "x") }
        }
        val sent = CopyOnWriteArrayList<Conduct.Msg>()
        val arrived = java.util.concurrent.atomic.AtomicInteger()
        var followerClock = 987_654_321_000L
        private fun link() = BandLink(
            context, DeviceIdentity(context), br.app.stagebook.data.BandTrust(context), br.app.stagebook.data.StageGate(),
            br.app.stagebook.data.BandInbox(context), repo,
        )
        val followerLink = link()
        val follower = BandConduct(followerLink, repo, DeviceIdentity(context)).also { it.clock = { followerClock } }
        val leader = BandConduct(link(), repo, DeviceIdentity(context)).also { c ->
            c.deliver = { msg ->
                sent += msg
                followerLink.conductHandler?.invoke(voice, Conduct.decode(Conduct.encode(msg))!!)
                arrived.incrementAndGet()
                emptyMap()
            }
        }
        val vm = testStage(context, repo, SavedStateHandle(mapOf("setId" to set, "start" to 0)), conduct = leader)

        /** [act] and the messages it sends, already dealt with by the follower. */
        fun told(messages: Int = 1, act: () -> Unit) {
            val before = arrived.get()
            act()
            runBlocking { withTimeout(10_000) { while (arrived.get() < before + messages) delay(10) } }
        }

        fun onBreak(): Pair2 {
            told { leader.start(set, "Show", items, 0) }
            told { leader.open(set, 0) }
            vm.songShown(0)
            told { vm.setBreak(true) }
            return this
        }

        fun following() = follower.role.value as Role.Following
    }

    @Test fun `pausing the leader's break stops the follower's counter at the same time`() {
        val p = Pair2().onBreak()
        assertNull(p.following().breakFrozen)
        Thread.sleep(50)
        p.told { p.vm.pauseBreak() }
        val frozen = p.vm.breakTimer.value!!.accumulated
        assertTrue(frozen >= 50)
        assertEquals(frozen, p.following().breakFrozen)
        assertFalse(p.following().breakTimer!!.running)
        // ten minutes later it still says the same
        p.followerClock += 600_000
        assertEquals(frozen, p.following().breakTimer!!.elapsed(p.followerClock))
        assertEquals(true, p.sent.last().breakPaused)
    }

    @Test fun `resuming the leader's break makes the follower's counter run again from where it stopped`() {
        val p = Pair2().onBreak()
        Thread.sleep(50)
        p.told { p.vm.pauseBreak() }
        val frozen = p.vm.breakTimer.value!!.accumulated
        Thread.sleep(50)
        p.told { p.vm.resumeBreak() }
        val timer = p.following().breakTimer!!
        assertTrue(timer.running)
        assertTrue(timer.elapsed(p.followerClock) - frozen in 0..20)   // counts from what it had
        assertEquals(false, p.sent.last().breakPaused)
        p.followerClock += 30_000
        assertTrue(p.following().breakTimer!!.elapsed(p.followerClock) - (frozen + 30_000) in 0..20)
    }

    @Test fun `zeroing the leader's break zeroes the follower's, running or paused`() {
        val p = Pair2().onBreak()
        Thread.sleep(1_200)   // longer than the tolerance for a break told again
        p.followerClock += 44 * 60_000   // 44 minutes on the break, as in the device test
        assertTrue(p.following().breakTimer!!.elapsed(p.followerClock) >= 44 * 60_000)
        p.told { p.vm.resetBreak() }
        assertTrue(p.following().breakTimer!!.running)
        assertTrue(p.following().breakTimer!!.elapsed(p.followerClock) < 20)
        // paused and zeroed: standing at 00:00
        Thread.sleep(50)
        p.told { p.vm.pauseBreak() }
        p.told { p.vm.resetBreak() }
        assertEquals(0L, p.following().breakFrozen)
        assertEquals(0L, p.following().breakTimer!!.elapsed(p.followerClock + 60_000))
    }

    @Test fun `the break told again, as the keep-alive does, stays paused`() {
        val p = Pair2().onBreak()
        p.told { p.vm.pauseBreak() }
        p.told(2) { p.leader.search() }
        assertEquals(p.vm.breakTimer.value!!.accumulated, p.following().breakFrozen)
        assertTrue(p.sent.takeLast(2).all { it.type != Conduct.OPEN || it.breakPaused })
    }

    @Test fun `leaving the break ends it on the follower, paused or not`() {
        val p = Pair2().onBreak()
        p.told { p.vm.pauseBreak() }
        p.told { p.vm.setBreak(false) }
        runBlocking { delay(BandConduct.LEFT_BREAK_MS * 3) }
        assertNull(p.following().breakSince)
        assertNull(p.following().breakFrozen)
    }

    @Test fun `the break screen of a paused leader shows it standing still`() {
        val role = Role.Following("voz", Conduct.Msg(Conduct.OPEN, songs = listOf(Conduct.Item("A"), Conduct.Item("B")), index = 0), "s1", breakSince = 42L, breakFrozen = 90_000L)
        val rest = followBreak(role, emptyList())!!
        assertFalse(rest.timer.running)
        assertEquals(90_000L, rest.timer.elapsed(5_000_000L))
        assertTrue(followBreak(role.copy(breakFrozen = null), emptyList())!!.timer.running)
    }

    /** What an app from before the pause field knows of the message. */
    @Serializable
    private data class OldMsg(
        val type: String, val from: String = "", val set: String = "", val songs: List<Conduct.Item> = emptyList(), val index: Int = -1,
        val missing: List<Conduct.Lack> = emptyList(), val breakMs: Long = -1,
    )

    @Test fun `an app from before the pause field reads the message and goes on counting by itself`() {
        val songs = listOf(Conduct.Item("A"), Conduct.Item("B"))
        val new = Conduct.encode(Conduct.Msg(Conduct.OPEN, "Voz", "Show", songs, 0, breakMs = 44_000, breakPaused = true))
        assertTrue("breakPaused" in new)
        val old = Json { ignoreUnknownKeys = true }.decodeFromString(OldMsg.serializer(), new)
        assertEquals(OldMsg(Conduct.OPEN, "Voz", "Show", songs, 0, breakMs = 44_000), old)
        // running: the message is, byte for byte, the one that went before
        assertFalse("breakPaused" in Conduct.encode(Conduct.Msg(Conduct.OPEN, "Voz", "Show", songs, 0, breakMs = 44_000)))
    }

    @Test fun `a leader from before the pause field never says paused, and the follower counts by itself`() {
        val p = Pair2()
        val songs = p.items
        val old = Json { ignoreUnknownKeys = true }.encodeToString(OldMsg.serializer(), OldMsg(Conduct.OPEN, "Voz", "Show", songs, 0, breakMs = 5_000))
        p.followerLink.conductHandler!!.invoke(voice, Conduct.decode(old)!!)
        assertNotNull(p.following().breakSince)
        assertNull(p.following().breakFrozen)
        assertTrue(p.following().breakTimer!!.running)
    }

    @Test fun `the pause field does not change the protocol's version, and a newer one is still refused`() {
        // the field is only added and ignored by whoever doesn't know it, so devices on the same version keep talking
        assertEquals(2, BandProtocol.VERSION)
        // a message that makes no sense: paused with no break is no break
        assertNull(Conduct.Msg(Conduct.OPEN, songs = listOf(Conduct.Item("A"), Conduct.Item("B")), index = 0, breakMs = -1, breakPaused = true).rest)
        assertFalse(Conduct.Msg(Conduct.OPEN, songs = listOf(Conduct.Item("A"), Conduct.Item("B")), index = 0, breakMs = -1, breakPaused = true).restPaused)
    }
}
