package br.app.stagebook

import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.BandConduct
import br.app.stagebook.data.BandConduct.Role
import br.app.stagebook.data.BandInbox
import br.app.stagebook.data.BandInternet
import br.app.stagebook.data.BandLink
import br.app.stagebook.data.BandTrust
import br.app.stagebook.data.DeviceIdentity
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.SetlistCloud
import br.app.stagebook.data.model.Song
import br.app.stagebook.domain.BandProtocol
import br.app.stagebook.domain.Conduct
import br.app.stagebook.domain.ShareCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

/** Deep audit 14/11: races and stale state in "Lead the band", the internet queue and the pair inbox. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class BandAuditResilienceTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val identity = DeviceIdentity(context)
    private val trust = BandTrust(context)
    private val voice = VOICE
    private val sent = CopyOnWriteArrayList<Conduct.Msg>()
    private val songs = listOf(Conduct.Item("Primeira"), Conduct.Item("Segunda"), Conduct.Item("Terceira"))

    private val repo = runBlocking {
        testRepo(bandOn = true)
    }
    private val link = testLink(repo = repo, identity = identity, trust = trust)
    private val conduct = testConduct(repo = repo, link = link, identity = identity).also { it.deliver = { m -> sent += m; emptyMap() } }

    @After fun clean() {
        trust.remove("sem-membro")
        conduct.end()
        repo.library.value.sets.forEach { repo.deleteSetlist(it.id) }
        repo.library.value.songs.forEach { repo.deleteSong(it.id) }
        repo.updateLook { it.copy(bandOn = true) }
    }

    private fun await(what: String, ms: Long = 5_000, cond: () -> Boolean) = await(what, ms, { "" }, cond)
    private fun await(what: String, ms: Long, diag: () -> String, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + ms
        while (!cond()) { check(System.currentTimeMillis() < end) { "timeout: $what ${diag()}" }; Thread.sleep(10) }
    }

    private fun conducting() = conduct.role.value as Role.Conducting
    // the band off, and the link's collector has dealt with it (it is the one that would act when the band comes back)
    private fun bandOffSeen() { bandOff(); await("the link saw the band off", 20_000) { link.seenOn == false } }
    private fun bandOff() { repo.updateLook { it.copy(bandOn = false) }; runBlocking { withTimeout(TEST_WAIT_MS) { repo.library.first { !it.look.bandOn } } } }

    // ---- 1: "Look for the band" sends the song as it is after the (slow) call ----
    @Test fun `the search sends the song the leader is on after the call, not the one before it`() {
        conduct.start("s1", "Show", songs, 0)
        await("start delivered") { sent.isNotEmpty() && conduct.role.value is Role.Conducting }
        Thread.sleep(200)
        val gate = CompletableDeferred<Unit>()
        var waiting = false
        conduct.deliver = { m ->
            sent += m
            if (m.type == Conduct.CHECK) { waiting = true; gate.await() }   // a member out of reach: the call takes seconds
            emptyMap()
        }
        conduct.search()
        await("the search call in flight") { waiting }
        conduct.open("s1", 2)   // the leader turns the page meanwhile
        await("page change sent") { sent.any { it.type == Conduct.OPEN && it.index == 2 } }
        gate.complete(Unit)
        await("the search's own song sent") { sent.count { it.type == Conduct.OPEN } >= 2 }
        Thread.sleep(300)
        assertEquals(listOf(2, 2), sent.filter { it.type == Conduct.OPEN }.map { it.index }.take(2))
        assertEquals(2, conducting().msg.index)   // and the leader's own role did not go back either
    }

    // ---- 2: armed, then the band off ----
    @Test fun `armed and then the band off, nothing stays armed and tapping a setlist does not lead`() {
        trust.add("sem-membro", "Baixo", "key")
        val setId = repo.createSetlist("Show").also { id -> repo.addToSetlist(id, listOf("A", "B").map { repo.saveSong(Song(title = it, author = "x", text = it)) }) }
        conduct.arm()
        assertTrue(conduct.armed.value)
        bandOff()
        await("disarmed") { !conduct.armed.value }
        assertFalse(conduct.startIfArmed(setId, 0))
        assertFalse(conduct.startSetlist(setId, 0))
        assertEquals(Role.None, conduct.role.value)
        conduct.arm()
        assertFalse("armed with the band off", conduct.armed.value)
    }

    // ---- 7: the leader zeroes the break within 2 s ----
    @Test fun `a break zeroed within two seconds of the last mark reaches the follower`() {
        var now = 5_000_000L
        conduct.clock = { now }
        fun msg(rest: Long) = Conduct.decode(Conduct.encode(Conduct.Msg(Conduct.OPEN, "Voz", "Show", songs, 0, breakMs = rest)))!!
        link.conductHandler!!.invoke(voice, msg(1_500))
        assertEquals(now - 1_500, (conduct.role.value as Role.Following).breakSince)
        link.conductHandler!!.invoke(voice, msg(0))   // the counter went back to zero
        assertEquals(now, (conduct.role.value as Role.Following).breakSince)
        // the same break told again late (trip time) still keeps its earliest start
        now += 1_000
        link.conductHandler!!.invoke(voice, msg(1_100))
        val start = now - 1_100
        now += 600
        link.conductHandler!!.invoke(voice, msg(1_600))   // the trip made it look 100 ms later
        assertEquals(start, (conduct.role.value as Role.Following).breakSince)
    }

    // ---- 8: a tap on Leave between the read and the write is not overwritten ----
    @Test fun `leaving while a keep-alive arrives stays left`() {
        val open = Conduct.Msg(Conduct.OPEN, "Voz", "Show", songs, 0)
        link.conductHandler!!.invoke(voice, open)
        assertTrue(conduct.role.value is Role.Following)
        var once = true
        conduct.beforeCommit = { if (once) { once = false; conduct.leave() } }   // the person taps "Leave" right there
        link.conductHandler!!.invoke(voice, open)
        assertTrue("went back to following: ${conduct.role.value}", conduct.role.value is Role.Left)
    }

    // ---- 9b: the Wi-Fi callback only with the band on, worked out from the state now ----
    @Test fun `the network callback is registered only in front and with the band on`() {
        val cm = context.getSystemService(android.net.ConnectivityManager::class.java)
        val callbacks = { shadowOf(cm).networkCallbacks.size }
        bandOffSeen()
        link.setForeground(true)
        assertEquals("registered with the band off", 0, callbacks())
        repo.updateLook { it.copy(bandOn = true) }
        await("registered once the band is on", 20_000, { "callbacks=${callbacks()}" }) { callbacks() == 1 }
        repo.updateLook { it.copy(bandOn = false) }
        await("unregistered when it goes off", 20_000) { callbacks() == 0 }
        repo.updateLook { it.copy(bandOn = true) }
        await("registered again", 20_000) { callbacks() == 1 }
        link.setForeground(false)
        assertEquals(0, callbacks())
    }

    @Test fun `off and on again at once ends with exactly one callback, and off with none`() {
        val cm = context.getSystemService(android.net.ConnectivityManager::class.java)
        val callbacks = { shadowOf(cm).networkCallbacks.size }
        link.setForeground(true)
        await("registered", 20_000) { callbacks() == 1 }
        repeat(20) {   // too quick for the link's collector to see each state
            repo.updateLook { it.copy(bandOn = false) }
            repo.updateLook { it.copy(bandOn = true) }
        }
        await("the library is on again", 20_000) { repo.library.value.look.bandOn }
        await("exactly one", 20_000) { link.syncNetCallback(); callbacks() == 1 }
        repeat(3) { link.syncNetCallback() }   // reconciling again changes nothing
        assertEquals(1, callbacks())
        bandOff()
        link.syncNetCallback()   // whatever the collector has seen: the state now is off
        assertEquals(0, callbacks())
        link.syncNetCallback()
        assertEquals(0, callbacks())
    }

    // ---- 9c: the keep-alive lives only while leading ----
    @Test fun `the keep-alive loop runs only while leading`() {
        assertFalse(conduct.keepAliveRunning)
        conduct.start("s1", "Show", songs, 0)
        assertTrue(conduct.keepAliveRunning)
        conduct.end()
        assertFalse(conduct.keepAliveRunning)
    }

    // ---- 11: the LAN pass writes nothing when nothing went ----
    @Test fun `settling on the Wi-Fi with nothing to send writes nothing`() = runBlocking {
        val cloud = SetlistCloud(repo, identity)
        val band = BandInternet(context, cloud, trust, identity, BandInbox(context), br.app.stagebook.data.Connectivity(context), link)
        val out = java.io.File(context.filesDir, "band_outbox.json").also { it.delete() }
        val prefs = context.getSharedPreferences("band_net", android.content.Context.MODE_PRIVATE)
        prefs.edit().remove("accepted").commit()
        band.settleOnLan(listOf(BandLink.Peer("x", "X", java.net.InetAddress.getLoopbackAddress(), 1)))
        assertFalse("the queue was rewritten", out.exists())
        assertFalse("accepted was rewritten", prefs.contains("accepted"))
    }

    // ---- band without members ----
    @Test fun `with nobody saved in the band it neither arms nor leads, with members all offline it does as before`() {
        val setId = repo.createSetlist("Show").also { id -> repo.addToSetlist(id, listOf(repo.saveSong(Song(title = "A", author = "x", text = "a")))) }
        conduct.arm()
        assertFalse(conduct.armed.value)
        assertFalse(conduct.startSetlist(setId, 0))
        assertEquals(Role.None, conduct.role.value)
        trust.add("so-offline", "Baixo", "key")   // saved, and not on the network
        try {
            conduct.arm()
            assertTrue(conduct.armed.value)
            assertTrue(conduct.startIfArmed(setId, 0))
        } finally { trust.remove("so-offline") }
    }

    // ---- an automatic sync waits for the stage to close ----
    private fun internet(stage: br.app.stagebook.data.StageGate): BandInternet {
        val l = testLink(repo = repo, identity = identity, trust = trust, stage = stage)
        return BandInternet(context, SetlistCloud(repo, identity), trust, identity, BandInbox(context), br.app.stagebook.data.Connectivity(context), l)
    }

    @Test fun `with the stage open the automatic sync waits, and runs once when it closes`() {
        val stage = br.app.stagebook.data.StageGate()
        val band = internet(stage)
        stage.enter()
        band.onAppOpen(); band.onAppOpen()
        Thread.sleep(400)
        assertEquals(0, band.syncRuns.get())
        stage.leave()
        await("the put-off sync ran") { band.syncRuns.get() >= 1 }
        Thread.sleep(400)
        assertEquals(1, band.syncRuns.get())
    }

    @Test fun `the person's own sync runs even with the stage open`() {
        val stage = br.app.stagebook.data.StageGate()
        val band = internet(stage)
        stage.enter()
        band.syncNow()
        await("forced sync ran") { band.syncRuns.get() == 1 }
        stage.leave()
        Thread.sleep(400)
        assertEquals(1, band.syncRuns.get())   // nothing was put off
    }

    // ---- 5 and 6: the pair's inbox ----
    private val pairKey = ByteArray(32) { it.toByte() }
    private fun line(id: String, json: String) = """{"event":"message","id":"$id","message":"${ShareCode.encrypt(pairKey, json.toByteArray())}"}"""

    @Test fun `a malformed chunk does not stop the sync, and what comes after it is read`() = runBlocking {
        val cloud = SetlistCloud(repo, identity)
        val wanted = java.util.Base64.getEncoder().encodeToString("""[{"title":"Faltou"}]""".toByteArray())
        cloud.getHook = {
            listOf(
                line("1", """{"p":"ruim","i":0,"n":1,"d":"!!! não é base64 !!!","k":"pkg"}"""),
                line("2", """{"p":"","k":"req","d":"$wanted"}"""),
            )
        }
        val got = cloud.fetchPair(pairKey, null)
        assertTrue(got != null)
        assertEquals(emptyList<String>(), got!!.packages.map { it.first })
        assertEquals(listOf("Faltou"), got.requests.flatten().map { it.title })
        assertEquals("2", got.last)   // the cursor moves on: the bad one isn't fetched again
    }

    @Test fun `a request that went out counts even if the notice beside it fails`() = runBlocking {
        val cloud = SetlistCloud(repo, identity)
        var posts = 0
        cloud.postHook = { _, _ -> posts++ == 0 }   // the request goes, the notice fails
        assertTrue(cloud.requestFromPair(pairKey, "id", listOf(BandLink.Wanted("A"))))
        assertEquals(2, posts)
        cloud.postHook = { _, _ -> false }
        assertFalse(cloud.requestFromPair(pairKey, "id", listOf(BandLink.Wanted("A"))))
    }
}
