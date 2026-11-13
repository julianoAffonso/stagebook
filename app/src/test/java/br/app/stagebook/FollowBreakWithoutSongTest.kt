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
import br.app.stagebook.domain.PendingFollow
import br.app.stagebook.ui.band.followingUi
import br.app.stagebook.ui.stage.FollowBreak
import br.app.stagebook.ui.stage.followBreak
import br.app.stagebook.ui.stage.leaderBreakOver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The leader's break reaches a follower who doesn't have the song before it: with no song the stage never opened, so
 * the break never showed (user, 13/11). Now the stage opens on the break alone, and the strip says the break too.
 * Leader and follower are joined inside one process here, never over a real network.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class FollowBreakWithoutSongTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val songs = listOf(Conduct.Item("Primeira"), Conduct.Item("Segunda", "Banda"), Conduct.Item("Terceira"))
    private val voice = BandProtocol.Hello("voz", "Voz", "key")

    /** A leader and a follower joined by what the leader sends; [opened] is what the follower's stage was asked to open. */
    private inner class Band(val repo: LibraryRepository) {
        var leaderClock = 1_000_000L
        var followerClock = 987_654_321_000L
        val arrived = java.util.concurrent.atomic.AtomicInteger()
        private val followerLink = link()
        val leader = BandConduct(link(), repo, DeviceIdentity(context)).also { it.clock = { leaderClock } }
        val follower = BandConduct(followerLink, repo, DeviceIdentity(context)).also { it.clock = { followerClock } }
        var listening: BandLink = followerLink
        val opened = CopyOnWriteArrayList<String>()

        init {
            leader.deliver = { msg ->
                listening.conductHandler?.invoke(voice, Conduct.decode(Conduct.encode(msg))!!)
                arrived.incrementAndGet()
                emptyMap()
            }
            watch(follower, opened)
        }

        fun link() = BandLink(
            context, DeviceIdentity(context), br.app.stagebook.data.BandTrust(context), br.app.stagebook.data.StageGate(),
            br.app.stagebook.data.BandInbox(context), repo,
        )

        fun watch(who: BandConduct, into: MutableList<String>) {
            CoroutineScope(Dispatchers.Unconfined).launch { who.openSong.collect { into += it } }
        }

        fun following(who: BandConduct = follower) = who.role.value as Role.Following

        fun told(messages: Int = 1, act: () -> Unit) {
            val before = arrived.get()
            act()
            await { arrived.get() >= before + messages }
        }

        fun started(index: Int = 0): Band {
            told { leader.start("s1", "Show", songs, index) }
            told { leader.open("s1", index) }
            return this
        }

        fun library() = repo.library.value.songs
    }

    private fun await(what: () -> Boolean) = runBlocking { withTimeout(10_000) { while (!what()) delay(10) } }

    /** A band whose follower has only the songs named in [have]; returns their ids by title too. */
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

    @Test fun `without the song before the break, the stage opens on the break alone, with the leader's time and the next song as it is here`() {
        val (b, ids) = band("Segunda")
        b.started()
        assertNull("no song here: no stage", b.following().stage)
        assertEquals(emptyList<String>(), b.opened)
        b.told { b.leader.rest("s1", 5_000) }   // on the break for 5 s already
        assertEquals(listOf(BandConduct.BREAK_ONLY), b.opened)
        assertEquals(BandConduct.BREAK_ONLY, b.following().stage)
        await { b.follower.followingSong.value == BandConduct.BREAK_ONLY }
        assertEquals(b.followerClock - 5_000, b.following().breakSince)
        assertEquals(FollowBreak(b.followerClock - 5_000, "Segunda", "G"), followBreak(b.following(), b.library()))
        assertFalse(leaderBreakOver(b.following()))
        // the strip: the break, and the next song is here
        val strip = followingUi(b.following(), b.library(), System.currentTimeMillis())
        assertEquals(b.followerClock - 5_000, strip.breakSince)
        assertEquals("Segunda" to false, strip.song to strip.missingHere)

        // the same break told again (a sign of life) opens nothing again
        b.told(2) { b.leader.search() }
        assertEquals(listOf(BandConduct.BREAK_ONLY), b.opened)

        // the leader goes on: the next song opens, as it always did
        b.told { b.leader.rest("s1", null); b.leader.open("s1", 1) }
        assertEquals(listOf(BandConduct.BREAK_ONLY, ids.getValue("Segunda")), b.opened)
        assertEquals(ids["Segunda"], b.following().stage)
        assertNull(b.following().breakSince)
        assertFalse("the song's stage takes the break's place", leaderBreakOver(b.following()))
    }

    @Test fun `without the next song either, the break still shows, with the title the leader said, and the strip says it's missing`() {
        val (b, _) = band()
        b.started()
        b.told { b.leader.rest("s1", 0) }
        assertEquals(listOf(BandConduct.BREAK_ONLY), b.opened)
        assertEquals(FollowBreak(b.followerClock, "Segunda", ""), followBreak(b.following(), b.library()))
        val strip = followingUi(b.following(), b.library(), System.currentTimeMillis())
        assertEquals("Segunda" to true, strip.song to strip.missingHere)

        // the leader goes on to a song that isn't here: the break's stage is over, and only the red strip is left
        b.told { b.leader.rest("s1", null); b.leader.open("s1", 1) }
        assertNull(b.following().stage)
        assertTrue(leaderBreakOver(b.following()))
        assertEquals(listOf(BandConduct.BREAK_ONLY), b.opened)
        await { b.follower.followingSong.value == null }
        val after = followingUi(b.following(), b.library(), System.currentTimeMillis())
        assertEquals(Triple("Segunda", true, null), Triple(after.song, after.missingHere, after.breakSince))
    }

    @Test fun `the leader going back to the song ends the break's stage`() {
        val (b, _) = band("Segunda")
        b.started()
        b.told { b.leader.rest("s1", 0) }
        b.told { b.leader.rest("s1", null) }
        assertNull(b.following().stage)
        assertTrue(leaderBreakOver(b.following()))
        // and the same break again opens the stage again
        b.told { b.leader.rest("s1", 0) }
        assertEquals(listOf(BandConduct.BREAK_ONLY, BandConduct.BREAK_ONLY), b.opened)
    }

    @Test fun `joining in the middle of the break without the song opens the break with the time it has lasted`() {
        val (b, _) = band("Segunda")
        b.started()
        b.told { b.leader.rest("s1", 0) }
        b.leaderClock += 240_000   // four minutes later another device shows up
        val lateLink = b.link()
        val late = BandConduct(lateLink, b.repo, DeviceIdentity(context)).also { it.clock = { 5_000_000L } }
        val opened = CopyOnWriteArrayList<String>()
        b.watch(late, opened)
        b.listening = lateLink
        b.told(2) { b.leader.search() }
        assertEquals(listOf(BandConduct.BREAK_ONLY), opened)
        assertEquals(5_000_000L - 240_000, b.following(late).breakSince)
        assertEquals(FollowBreak(5_000_000L - 240_000, "Segunda", "G"), followBreak(b.following(late), b.library()))
    }

    @Test fun `with the song before the break here, the break opens no other stage`() {
        val (b, ids) = band("Primeira")
        b.started()
        assertEquals(listOf(ids.getValue("Primeira")), b.opened)
        b.told { b.leader.rest("s1", 0) }
        assertEquals(listOf(ids.getValue("Primeira")), b.opened)
        assertEquals(ids["Primeira"], b.following().stage)
        // the strip says the break, and that the next song isn't here
        val strip = followingUi(b.following(), b.library(), System.currentTimeMillis())
        assertEquals(Triple("Segunda", true, b.followerClock), Triple(strip.song, strip.missingHere, strip.breakSince))
    }

    @Test fun `leaving the band, or the leader ending, ends the break's stage`() {
        val (b, _) = band()
        b.started()
        b.told { b.leader.rest("s1", 0) }
        b.follower.leave()
        assertTrue(leaderBreakOver(b.follower.role.value))
        assertTrue(leaderBreakOver(Role.None))
    }

    // ---- the stage's own state, with the real view model ----

    // the view model's scope runs on the main thread, which the test itself holds
    private fun onStage(test: suspend () -> Unit) {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try { runBlocking { test() } } finally { Dispatchers.resetMain() }
    }

    @Test fun `a stage opened for the break alone shows the leader's break, and is over when the leader moves on`() = onStage {
        val (b, _) = band("Segunda")
        val stage = testStage(context, b.repo, SavedStateHandle(mapOf("leaderBreak" to true, "start" to 0)), conduct = b.follower)
        assertTrue(stage.breakOnly)
        b.started()
        b.told { b.leader.rest("s1", 65_000) }
        val shown = withTimeout(10_000) { stage.uiState.first { !it.loading && it.followBreak != null } }
        assertEquals(FollowBreak(b.followerClock - 65_000, "Segunda", "G"), shown.followBreak)
        assertFalse(shown.breakOver)
        assertTrue("leaving asks first, as on any show", shown.live)
        assertEquals(emptyList<Any>(), shown.songs)
        // back to the song that isn't here: nothing left to show
        b.told { b.leader.rest("s1", null) }
        val over = withTimeout(10_000) { stage.uiState.first { it.breakOver } }
        assertNull(over.followBreak)
    }

    @Test fun `a stage of the follower's own song shows no break alone, and a song's stage is never over that way`() = onStage {
        val (b, ids) = band("Primeira")
        val stage = testStage(context, b.repo, SavedStateHandle(mapOf("songId" to ids.getValue("Primeira"), "start" to 0)), conduct = b.follower)
        assertFalse(stage.breakOnly)
        b.started()
        b.told { b.leader.rest("s1", 0) }
        val shown = withTimeout(10_000) { stage.uiState.first { !it.loading && it.followBreak != null } }
        assertFalse(shown.breakOver)
        b.told { b.leader.end() }
        assertFalse(withTimeout(10_000) { stage.uiState.first { it.followBreak == null } }.breakOver)
    }

    // ---- a follower who is editing, picking songs, receiving or importing isn't taken off that screen ----

    @Test fun `on a busy screen the break waits, and opens on coming back only while it is still on`() {
        val pending = PendingFollow()
        assertNull("not pulled out", pending.arrive(BandConduct.BREAK_ONLY, pulls = false))
        assertNull(pending.release(pulls = false, now = BandConduct.BREAK_ONLY))   // another busy screen
        assertEquals(BandConduct.BREAK_ONLY, pending.release(pulls = true, now = BandConduct.BREAK_ONLY))
        assertNull("opened once", pending.release(pulls = true, now = BandConduct.BREAK_ONLY))

        // the break ended while they were busy: nothing opens on coming back
        pending.arrive(BandConduct.BREAK_ONLY, pulls = false)
        assertNull(pending.release(pulls = true, now = null))
        assertNull(pending.waiting)

        // the leader went on to a song meanwhile: that one opens, not the break
        pending.arrive(BandConduct.BREAK_ONLY, pulls = false)
        pending.arrive("song", pulls = false)
        assertEquals("song", pending.release(pulls = true, now = "song"))

        // on a screen that can be left it opens at once, and what waited before is forgotten
        pending.arrive("old", pulls = false)
        assertEquals(BandConduct.BREAK_ONLY, pending.arrive(BandConduct.BREAK_ONLY, pulls = true))
        assertNull(pending.release(pulls = true, now = "old"))
    }
}
