package br.app.stagebook

import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.BandConduct
import br.app.stagebook.data.BandConduct.Notice
import br.app.stagebook.data.BandConduct.Role
import br.app.stagebook.data.BandInbox
import br.app.stagebook.data.BandLink
import br.app.stagebook.data.DeviceIdentity
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.SetlistCloud
import br.app.stagebook.data.model.Song
import br.app.stagebook.domain.BandProtocol
import br.app.stagebook.domain.Conduct
import br.app.stagebook.domain.PendingFollow
import br.app.stagebook.domain.QrPayload
import br.app.stagebook.ui.band.ConductUi
import br.app.stagebook.ui.band.conductUi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
 * Missing songs while the band is led: the leader sends each follower what it lacks, a follower asks the band for the
 * song on its strip, and only those go straight into the library. The leader and the follower each have a library of
 * their own and are joined inside one process: what travels is the real package and message text, never a real network.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ConductMissingSongsTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val songs = listOf(Conduct.Item("Primeira"), Conduct.Item("Segunda", "Banda"), Conduct.Item("Terceira"))
    private val voice = BandProtocol.Hello("voz", "Voz", "key")
    private val chords = "{key: G}\n[G]La la [D]la\n[C]La la la"
    private val lyrics = "La la la\nLa la la\nLa la la"

    // a device's own storage: its library and its inbox
    private fun device(name: String): android.content.Context = object : android.content.ContextWrapper(context) {
        override fun getFilesDir() = java.io.File(context.filesDir, name).apply { mkdirs() }
        override fun getApplicationContext() = this
    }

    private fun await(what: () -> Boolean) = runBlocking { withTimeout(10_000) { while (!what()) delay(10) } }

    private fun repo(on: android.content.Context, vararg have: Pair<String, String>): LibraryRepository = runBlocking {
        val repo = LibraryRepository(on, CoroutineScope(Dispatchers.IO))
        withTimeout(15_000) { repo.loaded.first { it } }
        repo.updateLook { it.copy(bandOn = true, chords = true) }
        have.forEach { (title, text) -> repo.saveSong(Song(title = title, author = songs.firstOrNull { it.title == title }?.artist.orEmpty(), text = text)) }
        repo
    }

    private inner class Band(leaderHas: List<String> = songs.map { it.title }, vararg followerHas: Pair<String, String>) {
        private val followerDevice = device("follower")
        val leaderRepo = repo(device("leader"), *leaderHas.map { it to chords }.toTypedArray())
        val repo = repo(followerDevice, *followerHas)
        val inbox = BandInbox(followerDevice)
        val leaderLink = BandLink(context, DeviceIdentity(context), br.app.stagebook.data.BandTrust(context), br.app.stagebook.data.StageGate(), BandInbox(device("leader")), leaderRepo)
        val link = BandLink(context, DeviceIdentity(context), br.app.stagebook.data.BandTrust(context), br.app.stagebook.data.StageGate(), inbox, repo)
        val leader = BandConduct(leaderLink, leaderRepo, DeviceIdentity(context))
        val follower = BandConduct(link, repo, DeviceIdentity(context))

        /** Another following device, known only by what it answers. */
        var guitar: Conduct.Lack? = Conduct.Lack("Guitarra", listOf("Segunda"))
        val arrived = java.util.concurrent.atomic.AtomicInteger()
        val sent = CopyOnWriteArrayList<Pair<String, List<String>>>()   // device → the titles sent to it
        val asked = CopyOnWriteArrayList<List<BandLink.Wanted>>()
        val opened = CopyOnWriteArrayList<String>()
        val notices = CopyOnWriteArrayList<Notice>()
        val leaderNotices = CopyOnWriteArrayList<Notice>()
        var answers: suspend () -> List<BandLink.Answer> = { emptyList() }

        init {
            leader.deliver = { msg ->
                val lack = link.conductHandler?.invoke(voice, Conduct.decode(Conduct.encode(msg))!!)
                arrived.incrementAndGet()
                listOfNotNull(lack?.let { "f" to it }, guitar?.let { "g" to it }).toMap()
            }
            // the local path: the package as it travels, opened by the follower's receiving code
            leader.sendSongs = { id, list ->
                sent += id to list.map { it.title }
                val (packed, shared) = SetlistCloud.looseSongs(list)!!
                id != "f" || link.accept(voice.id, voice.name, leaderLink.packageBytes(packed, shared))
            }
            follower.request = { wanted -> asked += wanted; answers() }
            CoroutineScope(Dispatchers.Unconfined).launch { follower.openSong.collect { opened += it } }
            CoroutineScope(Dispatchers.Unconfined).launch { follower.notices.collect { notices += it } }
            CoroutineScope(Dispatchers.Unconfined).launch { leader.notices.collect { leaderNotices += it } }
        }

        fun started(index: Int = 0): Band {
            leader.start("s1", "Show", songs, index)
            await { arrived.get() >= 1 && (leader.role.value as? Role.Conducting)?.lackers?.isNotEmpty() == true }
            return this
        }

        fun lackers() = (leader.role.value as Role.Conducting).lackers
        fun strip() = conductUi(follower.role.value, repo.library.value.songs, System.currentTimeMillis(), repo.library.value.look.bandOn, follower.chordless.value, repo.library.value.look.chords)
        fun has(title: String) = repo.library.value.songs.firstOrNull { it.title == title }

        /** A package from a band member reaching the follower, as its receiving code gets it once decrypted. */
        fun arrives(from: BandProtocol.Hello, vararg shared: SetlistCloud.SharedSong, set: String = ""): Boolean {
            val list = if (set.isEmpty()) SetlistCloud.looseList(shared.map { it.title to it.author })
            else (listOf(QrPayload.HEADER, shared.size.toString(), set) + shared.map { it.title }).joinToString("\n")
            return link.accept(from.id, from.name, link.packageBytes(QrPayload.pack(list), shared.toList()))
        }
    }

    private fun shared(title: String, text: String = chords, artist: String = songs.firstOrNull { it.title == title }?.artist.orEmpty()) =
        SetlistCloud.SharedSong(title, artist, "", text)

    private fun answer(song: SetlistCloud.SharedSong, from: String = "Baixo") = BandLink.Answer(song, from.lowercase(), from)

    // ---- A: the leader sends what is missing ----

    @Test fun `the leader knows each device that lacks songs, and sends one device exactly its songs`() {
        val b = Band(followerHas = arrayOf("Primeira" to chords)).started()
        assertEquals(mapOf("f" to listOf("Segunda", "Terceira"), "g" to listOf("Segunda")), b.lackers().mapValues { it.value.titles })
        val ui = conductUi(b.leader.role.value, emptyList(), 0, true) as ConductUi.Conducting
        assertEquals(listOf("g" to "Guitarra", "f" to b.lackers().getValue("f").who).sortedBy { it.second }, ui.lackers.map { it.id to it.who })

        b.leader.sendLacking(setOf("g"))
        await { b.leaderNotices.isNotEmpty() }
        assertEquals(listOf("g" to listOf("Segunda")), b.sent.toList())
        assertEquals(listOf<Notice>(Notice.Sent("Guitarra")), b.leaderNotices.toList())
        assertNull("nothing went to the other device", b.has("Segunda"))
    }

    @Test fun `sending to all gives each device its own songs, and who got them leaves the list by itself`() {
        val b = Band(followerHas = arrayOf("Primeira" to chords)).started()
        b.leader.sendLacking()
        await { b.leaderNotices.isNotEmpty() && "f" !in b.lackers() }
        assertEquals(setOf("f" to listOf("Segunda", "Terceira"), "g" to listOf("Segunda")), b.sent.toSet())
        assertEquals(listOf<Notice>(Notice.SentMany(2)), b.leaderNotices.toList())
        // the guitar still says it lacks the song: its line stays
        assertEquals(setOf("g"), b.lackers().keys)
        assertEquals(listOf("Guitarra"), (b.leader.role.value as Role.Conducting).msg.missing.map { it.who })
        b.guitar = Conduct.Lack("Guitarra", emptyList())
        b.leader.search()
        await { b.lackers().isEmpty() }
        assertTrue((conductUi(b.leader.role.value, emptyList(), 0, true) as ConductUi.Conducting).lackers.isEmpty())
    }

    @Test fun `a song the leader doesn't have either is left out, and nothing goes with the band off`() {
        val b = Band(leaderHas = listOf("Primeira", "Segunda"), followerHas = arrayOf("Primeira" to chords)).started()
        b.guitar = null
        b.leader.sendLacking(setOf("f"))
        await { b.leaderNotices.isNotEmpty() }
        assertEquals(listOf("f" to listOf("Segunda")), b.sent.toList())

        val off = Band(followerHas = arrayOf("Primeira" to chords)).started()
        val role = off.leader.role.value
        off.leaderRepo.updateLook { it.copy(bandOn = false) }
        // the role is looked at as it was: the switch alone stops the send
        off.leader.sendLacking()
        runBlocking { delay(200) }
        assertTrue(role is Role.Conducting && off.sent.isEmpty() && off.leaderNotices.isEmpty())
    }

    @Test fun `songs only go to a band device on this network`() = runBlocking {
        val b = Band()
        // in the band or not, a device that isn't on this network now gets nothing: there is no other way out
        assertTrue(b.leaderLink.onlineMembers.value.isEmpty())
        assertFalse(b.leaderLink.sendSongsTo("away", b.leaderRepo.library.value.songs))
    }

    // ---- B: the follower asks ----

    @Test fun `asking sends the band's request for the strip's song once, and says when nobody has it`() {
        val b = Band(followerHas = arrayOf("Primeira" to chords)).started(index = 1)
        assertTrue((b.strip() as ConductUi.Following).missingHere)
        val gate = CompletableDeferred<List<BandLink.Answer>>()
        b.answers = { gate.await() }
        b.follower.ask()
        await { b.asked.isNotEmpty() }
        assertTrue("busy while the band answers", b.follower.asking.value)
        b.follower.ask()   // a second tap sends nothing
        gate.complete(emptyList())
        await { !b.follower.asking.value }
        assertEquals(listOf(listOf(BandLink.Wanted("Segunda", "Banda"))), b.asked.toList())
        assertEquals(listOf<Notice>(Notice.Nobody(chords = false)), b.notices.toList())
        // and it can be asked again
        b.answers = { emptyList() }
        b.follower.ask()
        await { b.asked.size == 2 }
    }

    @Test fun `with the band off there is no strip and nothing is asked`() {
        val b = Band(followerHas = arrayOf("Primeira" to chords)).started(index = 1)
        b.repo.updateLook { it.copy(bandOn = false) }
        await { !b.repo.library.value.look.bandOn }
        assertNull(b.strip())
        b.follower.ask()
        runBlocking { delay(200) }
        assertTrue(b.asked.isEmpty())
        // and what the leader sends waits to be reviewed
        assertTrue(b.arrives(voice, shared("Segunda")))
        assertNull(b.has("Segunda"))
        assertEquals(1, b.inbox.items.value.size)
    }

    // ---- C: the copy with chords ----

    @Test fun `of two copies answered, the one with chords is taken`() {
        val b = Band()
        val plain = answer(shared("Segunda", lyrics), "Voz")
        val full = answer(shared("Segunda", chords), "Baixo")
        assertEquals(listOf("Baixo"), b.link.pick(listOf(plain, full)).map { it.fromName })
        assertEquals(listOf("Baixo"), b.link.pick(listOf(full, plain)).map { it.fromName })

        // each member spelled the artist its own way, so both copies come back: still the one with chords
        val f = Band(followerHas = arrayOf("Primeira" to chords)).started(index = 1)
        f.answers = { listOf(answer(shared("Segunda", lyrics, artist = ""), "Voz"), answer(shared("Segunda", chords, artist = "A Banda"), "Baixo")) }
        f.follower.ask()
        await { f.has("Segunda") != null }
        assertTrue(br.app.stagebook.domain.Chords.hasChords(f.has("Segunda")!!.text))
        await { !f.follower.asking.value }
        assertTrue("no notice: it is the song on stage and has chords", f.notices.isEmpty())
    }

    @Test fun `a copy without chords is told on a profile that shows chords, and asking again takes only one with chords`() {
        val b = Band(followerHas = arrayOf("Primeira" to chords)).started(index = 1)
        b.answers = { listOf(answer(shared("Segunda", lyrics), "Voz")) }
        b.follower.ask()
        await { b.notices.isNotEmpty() }
        assertEquals(listOf<Notice>(Notice.NoChords("Segunda")), b.notices.toList())
        val strip = b.strip() as ConductUi.Following
        assertTrue(strip.noChords && !strip.missingHere)

        // the same copy again is not what was asked for
        await { !b.follower.asking.value }
        b.follower.ask()
        await { b.notices.size == 2 }
        assertEquals(Notice.Nobody(chords = true), b.notices.last())
        assertTrue(b.inbox.items.value.isEmpty())

        // one with chords: it waits to be reviewed, the song here stays as it is
        await { !b.follower.asking.value }
        b.answers = { listOf(answer(shared("Segunda", chords), "Baixo")) }
        b.follower.ask()
        await { b.notices.size == 3 }
        assertEquals(Notice.ChordsKept("Segunda", "Baixo"), b.notices.last())
        assertFalse(br.app.stagebook.domain.Chords.hasChords(b.has("Segunda")!!.text))
        assertEquals(listOf(shared("Segunda", chords)), b.inbox.items.value.single().songs)
        assertFalse("nothing left to ask", (b.strip() as ConductUi.Following).noChords)
    }

    @Test fun `on the lyrics only profile any copy is taken and nothing is said about chords`() {
        val b = Band(followerHas = arrayOf("Primeira" to chords))
        b.repo.updateLook { it.copy(chords = false) }
        b.started(index = 1)
        b.answers = { listOf(answer(shared("Segunda", lyrics), "Voz")) }
        b.follower.ask()
        await { b.has("Segunda") != null && !b.follower.asking.value }
        assertTrue(b.notices.isEmpty() && b.follower.chordless.value.isEmpty())
        assertFalse((b.strip() as ConductUi.Following).noChords)
    }

    @Test fun `the leader's copy without chords is told too, and the strip offers to ask`() {
        val b = Band(followerHas = arrayOf("Primeira" to chords))
        b.leaderRepo.updateSong(b.leaderRepo.library.value.songs.first { it.title == "Segunda" }.id) { it.copy(text = lyrics) }
        b.started(index = 1)
        b.leader.sendLacking(setOf("f"))
        await { b.notices.isNotEmpty() }
        assertEquals(Notice.NoChords("Segunda"), b.notices.first())
        assertTrue((b.strip() as ConductUi.Following).noChords)
    }

    @Test fun `the messages are the ones the published app reads`() {
        val b = Band()
        val json = Json { ignoreUnknownKeys = true }
        assertEquals("no version was raised", 2, BandProtocol.VERSION)
        // what the leader sends: a band package as before, with a list that has no name (loose songs)
        val (packed, shared) = SetlistCloud.looseSongs(b.leaderRepo.library.value.songs)!!
        val wire = json.parseToJsonElement(b.leaderLink.packageBytes(packed, shared).decodeToString()).jsonObject
        assertEquals(setOf("list", "songs", "v"), wire.keys + "v")
        val list = QrPayload.parse(QrPayload.unpack(java.util.Base64.getDecoder().decode(wire["list"]!!.jsonPrimitive.content))!!)!!
        assertEquals("" to songs.map { it.title }, list.name to list.songs.map { it.title })
        // what the follower asks, and what it reads back: the lists the request for missing songs always used
        val ask = json.encodeToString(ListSerializer(BandLink.Wanted.serializer()), listOf(BandLink.Wanted("Segunda", "Banda")))
        assertEquals("""[{"title":"Segunda","artist":"Banda"}]""", ask)
        val reply = json.encodeToString(ListSerializer(SetlistCloud.SharedSong.serializer()), listOf(shared("Segunda"), shared("Outra", "\u0000")))
        assertEquals("what may not travel is dropped", listOf(shared("Segunda")), b.link.readAnswer(reply.toByteArray()))
        assertTrue(b.link.readAnswer("not an answer".toByteArray()).isEmpty())
    }

    // ---- D: what goes straight in ----

    @Test fun `not following, what the band sends waits to be reviewed`() {
        val b = Band(followerHas = arrayOf("Primeira" to chords))
        assertTrue(b.arrives(voice, shared("Segunda")))
        assertNull(b.has("Segunda"))
        assertEquals(listOf(shared("Segunda")), b.inbox.items.value.single().songs)

        // and after leaving the leader too
        val left = Band(followerHas = arrayOf("Primeira" to chords)).started(index = 1)
        left.follower.leave()
        assertTrue(left.arrives(voice, shared("Segunda")))
        assertNull(left.has("Segunda"))
        assertEquals(1, left.inbox.items.value.size)
    }

    @Test fun `what wasn't asked for and doesn't come from the leader waits to be reviewed`() {
        val b = Band(followerHas = arrayOf("Primeira" to chords)).started(index = 1)
        // another band member sends the very song that is missing
        assertTrue(b.arrives(BandProtocol.Hello("baixo", "Baixo", "key"), shared("Segunda")))
        // the leader sends a song that isn't in its setlist, and a setlist
        assertTrue(b.arrives(voice, shared("Fora do show")))
        assertTrue(b.arrives(voice, shared("Segunda"), shared("Terceira"), set = "Outro show"))
        assertNull(b.has("Segunda"))
        assertNull(b.has("Fora do show"))
        assertEquals(3, b.inbox.items.value.size)
        assertTrue(b.opened.isEmpty() && b.notices.isEmpty())
        assertTrue((b.strip() as ConductUi.Following).missingHere)
    }

    @Test fun `what may not travel is dropped as always`() {
        val b = Band(followerHas = arrayOf("Primeira" to chords)).started(index = 1)
        val encoded = shared("Segunda", "A".repeat(400))   // a block that looks like a file
        assertFalse(encoded.allowed())
        assertTrue(b.arrives(voice, encoded))
        assertNull(b.has("Segunda"))
        assertTrue("kept by nobody", b.inbox.items.value.all { it.songs.isEmpty() })
        // asked for, the same: it counts as nobody having it
        b.answers = { listOf(answer(encoded)) }
        b.follower.ask()
        await { b.notices.isNotEmpty() }
        assertEquals(listOf<Notice>(Notice.Nobody(chords = false)), b.notices.toList())
        assertNull(b.has("Segunda"))
    }

    @Test fun `a song already here with that title and artist is never replaced`() {
        val b = Band(followerHas = arrayOf("Primeira" to chords, "Terceira" to "minha versão\nminha versão\nminha versão")).started(index = 1)
        assertTrue(b.arrives(voice, shared("Terceira"), shared("Segunda")))
        assertEquals("minha versão\nminha versão\nminha versão", b.has("Terceira")!!.text)
        assertEquals(1, b.repo.library.value.songs.count { it.title == "Terceira" })
        // the other song of the same package went in; this one waits
        assertTrue(b.has("Segunda") != null)
        assertEquals(listOf(shared("Terceira")), b.inbox.items.value.single().songs)
    }

    @Test fun `the leader's song goes straight in, the strip clears and the stage opens on it`() {
        val b = Band(followerHas = arrayOf("Primeira" to chords)).started(index = 1)
        assertTrue((b.strip() as ConductUi.Following).missingHere)
        assertTrue(b.opened.isEmpty())
        b.leader.sendLacking(setOf("f"))
        await { b.opened.isNotEmpty() }
        val id = b.has("Segunda")!!.id
        assertEquals(listOf(id), b.opened.toList())
        assertEquals(id, (b.follower.role.value as Role.Following).songId)
        assertFalse((b.strip() as ConductUi.Following).missingHere)
        assertTrue("nothing to review", b.inbox.items.value.isEmpty())
        // the other one sent isn't the song on stage: it enters with a short notice
        assertEquals(listOf<Notice>(Notice.Arrived("Terceira", "Voz")), b.notices.toList())
        // the leader asks again who lacks what: the stage isn't opened twice
        await { "f" !in b.lackers() }
        assertEquals(listOf(id), b.opened.toList())
    }

    @Test fun `the song asked for goes straight in and opens, and someone busy is not pulled out`() {
        val b = Band(followerHas = arrayOf("Primeira" to chords)).started(index = 1)
        b.answers = { listOf(answer(shared("Segunda"))) }
        b.follower.ask()
        await { b.opened.isNotEmpty() }
        val id = b.has("Segunda")!!.id
        assertEquals(listOf(id), b.opened.toList())
        assertTrue(b.inbox.items.value.isEmpty())
        // editing, picking songs, receiving or importing: it waits and opens on coming back
        val pending = PendingFollow()
        assertNull(pending.arrive(b.opened.last(), pulls = false))
        await { b.follower.followingSong.value == id }
        assertEquals(id, pending.release(pulls = true, now = b.follower.followingSong.value))
    }

    @Test fun `on the leader's break the song that comes next is the one asked for`() {
        val b = Band(followerHas = arrayOf("Primeira" to chords)).started()
        val before = b.arrived.get()
        b.leader.rest("s1", 0)
        await { b.arrived.get() > before && (b.follower.role.value as Role.Following).breakSince != null }
        b.answers = { listOf(answer(shared("Segunda"))) }
        b.follower.ask()
        await { b.has("Segunda") != null }
        assertEquals(listOf(BandLink.Wanted("Segunda", "Banda")), b.asked.single())
        assertFalse((b.strip() as ConductUi.Following).missingHere)
    }
}
