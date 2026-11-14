package br.app.stagebook

import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.BandInbox
import br.app.stagebook.data.BandLink
import br.app.stagebook.data.DeviceIdentity
import br.app.stagebook.data.ImportQueue
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.SetlistCloud
import br.app.stagebook.data.model.Song
import br.app.stagebook.domain.BandContent
import br.app.stagebook.domain.BandProtocol
import br.app.stagebook.domain.QrPayload
import br.app.stagebook.ui.setlists.ScanSetlistViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A setlist's breaks go with it by the 3 words and through the band (the network and each pair's inbox), and the
 * setlist created on the other side has them between the same songs. The QR stays as it was, and an app from before
 * the breaks reads every one of these and gets the setlist without them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class SetlistBreaksShareTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val identity = DeviceIdentity(context)

    private fun repo() = LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        .also { r -> runBlocking { withTimeout(5_000) { r.loaded.first { it } }; r.restore(emptyList(), emptyList()) } }

    private fun LibraryRepository.set(id: String) = library.value.setlist(id)!!
    private fun LibraryRepository.songs(vararg titles: String) = titles.map { saveSong(Song(title = it, author = "x", text = it)) }

    /** "Show": A B | C D | E. */
    private fun LibraryRepository.show(name: String = "Show"): String {
        val s = createSetlist(name)
        addToSetlist(s, songs("A", "B", "C", "D", "E"))
        addBreak(s)          // before E
        addBreak(s)          // before D
        moveRow(s, 3, 2)     // dragged up to before C
        assertEquals(listOf(2, 4), set(s).breaks)
        return s
    }

    private fun link(repo: LibraryRepository, inbox: BandInbox = BandInbox(context)) =
        BandLink(context, identity, br.app.stagebook.data.BandTrust(context), br.app.stagebook.data.StageGate(), inbox, repo)

    private fun scan(repo: LibraryRepository, queue: ImportQueue = ImportQueue(repo), inbox: BandInbox = BandInbox(context), from: String = ""): ScanSetlistViewModel {
        val link = link(repo, inbox)
        val cloud = SetlistCloud(repo, identity)
        val internet = br.app.stagebook.data.BandInternet(
            context, cloud, br.app.stagebook.data.BandTrust(context), identity, inbox, br.app.stagebook.data.Connectivity(context), link,
        )
        return ScanSetlistViewModel(
            repo, queue, cloud, androidx.lifecycle.SavedStateHandle(mapOf("code" to "", "inbox" to from)), inbox, link, internet,
            br.app.stagebook.data.BandTrust(context), br.app.stagebook.data.Connectivity(context),
        )
    }

    private fun list(name: String, vararg titles: String) = (listOf(QrPayload.HEADER, titles.size.toString(), name) + titles).joinToString("\n")

    private val json = Json { ignoreUnknownKeys = true }

    // ---- what goes out ----

    @Test fun `the QR of a setlist with breaks is the same as without them`() {
        val repo = repo()
        val s = repo.show()
        val with = repo.qrPayload(s)!!
        repo.removeBreak(s, 2); repo.removeBreak(s, 4)
        assertEquals(emptyList<Int>(), repo.set(s).breaks)
        assertArrayEquals(repo.qrPayload(s)!!, with)
    }

    @Test fun `by the 3 words the breaks arrive with their setlist, also with several setlists in one send`() {
        val repo = repo()
        val cloud = SetlistCloud(repo, identity)
        val show = cloud.setlistPackage(repo.show(), withSongs = false)!!.first
        val plain = repo.createSetlist("Ensaio").also { repo.addToSetlist(it, repo.library.value.songs.take(3).map { s -> s.id }) }
        val rehearsal = cloud.setlistPackage(plain, withSongs = false)!!.first
        val got = cloud.read(listOf(show, rehearsal)) as SetlistCloud.Received.Ok
        assertEquals(listOf(2, 4), got.breaks[got.text])
        assertEquals(emptyList<Int>(), got.breaks[got.others.single()])   // it said: no breaks
    }

    @Test fun `an older app reads the 3 words' messages as before - the setlist, nothing newer`() {
        val repo = repo()
        val cloud = SetlistCloud(repo, identity)
        val sent = listOf(SetlistCloud.pack(byteArrayOf('I'.code.toByte(), '1'.code.toByte()), json.encodeToString(BandProtocol.Hello.serializer(), identity.hello())), cloud.setlistPackage(repo.show(), false)!!.first)
        // the reading as it was before the breaks (SetlistCloud.read): every message that unpacks as a setlist's text
        val lists = sent.mapNotNull { QrPayload.unpack(it) }.filter(BandContent::listOk).distinct()
        assertEquals(listOf(list("Show", "A", "B", "C", "D", "E")), lists)
        // the sender's version was raised (user, 14/11): an app on version 2 says "update" instead of guessing
        assertEquals(3, BandProtocol.VERSION)
        assertTrue(cloud.read(sent) is SetlistCloud.Received.Ok)
    }

    @Test fun `through the band on the network the breaks arrive, and an older app reads the same package`() {
        val repo = repo()
        val cloud = SetlistCloud(repo, identity)
        val link = link(repo)
        val (packed, songs) = cloud.setlistPackage(repo.show(), withSongs = true)!!
        val wire = link.packageBytes(packed, songs)
        val got = link.openPackage(wire)!!
        assertEquals(list("Show", "A", "B", "C", "D", "E"), got.text)
        assertEquals(listOf(2, 4), got.breaks)
        assertEquals(5, got.songs.size)
        // the reading as it was before the breaks (BandLink.receive): the list out of the package, and its text
        val old = json.parseToJsonElement(wire.decodeToString()).jsonObject
        assertEquals(setOf("list", "songs", "v"), old.keys + "v")   // no new key in the package
        assertEquals(got.text, QrPayload.unpack(java.util.Base64.getDecoder().decode(old["list"]!!.jsonPrimitive.content)))
        assertEquals(BandProtocol.VERSION, old["v"]?.jsonPrimitive?.int ?: BandProtocol.VERSION)
    }

    @Test fun `through a pair's inbox the breaks arrive, and an older app reads the same package`() {
        val repo = repo()
        val cloud = SetlistCloud(repo, identity)
        val (packed, songs) = cloud.setlistPackage(repo.show(), withSongs = true)!!
        val wire = cloud.pairPackage(packed, songs)
        val got = cloud.openPairPackage(wire)!!
        assertEquals(list("Show", "A", "B", "C", "D", "E"), got.text)
        assertEquals(listOf(2, 4), got.breaks)
        // the reading as it was before the breaks (SetlistCloud.fetchPair)
        val old = json.parseToJsonElement(SetlistCloud.unpack(byteArrayOf('P'.code.toByte(), '1'.code.toByte()), wire, SetlistCloud.MAX_PACKAGE)!!).jsonObject
        assertNull(old["v"])   // "v" is a default and never goes in the package: an app on version 2 reads it as its own, with no warning
        assertEquals(got.text, QrPayload.unpack(java.util.Base64.getDecoder().decode(old["list"]!!.jsonPrimitive.content)))
    }

    @Test fun `a setlist sent by an older app arrives with nothing said about breaks, on every way`() {
        val repo = repo()
        val cloud = SetlistCloud(repo, identity)
        val old = repo.qrPayload(repo.show())!!   // what an older app sends: the list alone
        assertTrue((cloud.read(listOf(old)) as SetlistCloud.Received.Ok).breaks.isEmpty())
        assertNull(link(repo).openPackage(link(repo).packageBytes(old, emptyList()))!!.breaks)
        assertNull(cloud.openPairPackage(cloud.pairPackage(old, emptyList()))!!.breaks)
    }

    @Test fun `loose songs carry no breaks`() {
        val repo = repo()
        val cloud = SetlistCloud(repo, identity)
        val (packed, _) = cloud.songsPackage(repo.songs("A", "B", "C"))!!
        assertTrue((cloud.read(listOf(packed)) as SetlistCloud.Received.Ok).breaks.isEmpty())
    }

    // ---- what comes in ----

    @Test fun `the inbox keeps the breaks, only where the setlist can have one`() {
        val inbox = BandInbox(context)
        inbox.add("ana", "Ana", list("Com intervalo", "A", "B", "C"), emptyList(), listOf(9, 2, 2, -1, 0, 1))
        assertEquals(listOf(1, 2), inbox.items.value.single { it.setName == "Com intervalo" }.breaks)
        // the same setlist again with other breaks is a new version, not a repeat
        inbox.add("ana", "Ana", list("Com intervalo", "A", "B", "C"), emptyList(), listOf(1))
        assertEquals(listOf(1), inbox.items.value.single { it.setName == "Com intervalo" }.breaks)
        inbox.add("ana", "Ana", list("Sem dizer", "A", "B"), emptyList())
        assertNull(inbox.items.value.single { it.setName == "Sem dizer" }.breaks)
        // what was saved before the breaks existed still opens
        val saved = Json { ignoreUnknownKeys = true }.decodeFromString(BandInbox.Item.serializer(), """{"id":"1","fromId":"ana","fromName":"Ana","text":"x"}""")
        assertNull(saved.breaks)
    }

    @Test fun `a received setlist is created with the breaks between the same songs`() {
        val repo = repo()
        repo.songs("A", "B", "C", "D", "E")
        val vm = scan(repo)
        vm.review(list("Show", "A", "B", "C", "D", "E"), emptyList(), listOf(2, 4))
        assertEquals(listOf(2, 4), repo.set(vm.create()!!).breaks)
    }

    @Test fun `from the inbox the breaks reach the new setlist`() {
        val repo = repo()
        repo.songs("A", "B", "C")
        val inbox = BandInbox(context)
        inbox.add("ana", "Ana", list("Da banda", "A", "B", "C"), emptyList(), listOf(2))
        val vm = scan(repo, inbox = inbox, from = inbox.items.value.single { it.setName == "Da banda" }.id)
        assertEquals(listOf(2), repo.set(vm.create()!!).breaks)
    }

    @Test fun `more setlists in the same send wait in the inbox with their own breaks`() {
        val repo = repo()
        val inbox = BandInbox(context)
        val vm = scan(repo, inbox = inbox)
        val other = list("Outro show", "A", "B", "C")
        vm.park(listOf(other), emptyList(), mapOf(other to listOf(1)))
        assertEquals(listOf(1), inbox.items.value.single { it.setName == "Outro show" }.breaks)
    }

    @Test fun `without a song, its break stays before the next one that came in, or goes away`() {
        val repo = repo()
        repo.songs("A", "B", "D")
        val vm = scan(repo)
        vm.review(list("Show", "A", "B", "C", "D", "E"), emptyList(), listOf(2, 4))   // A B | C D | E, and C and E aren't here
        val set = repo.set(vm.create()!!)
        assertEquals(listOf("A", "B", "D"), set.items.map { repo.song(it)!!.title })
        assertEquals(listOf(2), set.breaks)   // A B | D: the one before E has no song after it
    }

    @Test fun `a missing song imported later takes its break back`() {
        val repo = repo()
        repo.songs("A", "B", "D", "E")
        val queue = ImportQueue(repo)
        val vm = scan(repo, queue)
        vm.review(list("Show", "A", "B", "C", "D", "E"), emptyList(), listOf(2, 4))
        assertTrue(vm.createAndImport())
        val id = queue.state.value!!.setId
        assertEquals(listOf(2, 3), repo.set(id).breaks)   // A B | D | E
        queue.saved(repo.saveSong(Song(title = "C", author = "x")))
        assertEquals(listOf("A", "B", "C", "D", "E"), repo.set(id).items.map { repo.song(it)!!.title })
        assertEquals(listOf(2, 4), repo.set(id).breaks)   // A B | C D | E, as it was sent
    }

    @Test fun `replacing a setlist of the same name, the received breaks take the place of its own`() {
        val repo = repo()
        val mine = repo.show()   // A B | C D | E
        val vm = scan(repo)
        vm.review(list("Show", "E", "D", "C", "B", "A"), emptyList(), listOf(1))
        assertEquals(mine, vm.create(vm.existing("Show")))
        assertEquals(listOf(1), repo.set(mine).breaks)
        // and one sent with no breaks at all leaves none
        val again = scan(repo)
        again.review(list("Show", "A", "B", "C", "D", "E"), emptyList(), emptyList())
        again.create(mine)
        assertEquals(emptyList<Int>(), repo.set(mine).breaks)
    }

    @Test fun `a QR or an older app's setlist over mine keeps my breaks between the same songs`() {
        val repo = repo()
        val mine = repo.show()   // A B | C D | E
        val vm = scan(repo)
        vm.review(list("Show", "A", "B", "C", "D", "E"), emptyList())   // nothing said about breaks
        vm.create(mine)
        assertEquals(listOf(2, 4), repo.set(mine).breaks)
    }

    @Test fun `breaks from another device are never taken as they come`() {
        val repo = repo()
        repo.songs("A", "B", "C")
        val vm = scan(repo)
        vm.review(list("Show", "A", "B", "C"), emptyList(), listOf(Int.MAX_VALUE, 2, 2, Int.MIN_VALUE, 0, 3, 1))
        assertEquals(listOf(1, 2), repo.set(vm.create()!!).breaks)
        // loose songs have no setlist to put a break in
        val loose = scan(repo)
        loose.review(list("", "A", "B", "C"), emptyList(), listOf(1))
        assertFalse(loose.addSongs() > 0)
    }
}
