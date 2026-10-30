package br.app.stagebook

import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.model.Song
import br.app.stagebook.domain.QrPayload
import br.app.stagebook.ui.setlists.ScanProblem
import br.app.stagebook.ui.setlists.ScanSetlistViewModel
import br.app.stagebook.ui.setlists.ScanUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Scanning a setlist QR: checks against the library, flags what's missing and creates (or replaces) the setlist. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ScanSetlistTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun bandLink(repo: LibraryRepository) = br.app.stagebook.data.BandLink(
        context, br.app.stagebook.data.DeviceIdentity(context), br.app.stagebook.data.BandTrust(context),
        br.app.stagebook.data.StageGate(), br.app.stagebook.data.BandInbox(context), repo,
    )

    private fun bandInternet(repo: LibraryRepository) = br.app.stagebook.data.BandInternet(
        context, br.app.stagebook.data.SetlistCloud(repo, br.app.stagebook.data.DeviceIdentity(context)),
        br.app.stagebook.data.BandTrust(context), br.app.stagebook.data.DeviceIdentity(context),
        br.app.stagebook.data.BandInbox(context), br.app.stagebook.data.Connectivity(context), bandLink(repo),
    )

    private fun qr(name: String, vararg titles: String) =
        (listOf(QrPayload.HEADER, titles.size.toString(), name) + titles).joinToString("\n")

    private fun setup(): Triple<LibraryRepository, ScanSetlistViewModel, List<String>> = runBlocking {
        val repo = LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        withTimeout(5_000) { repo.loaded.first { it } }
        val ids = listOf("Canção do Mar", "Noite Clara", "Estrada Longa").map { repo.saveSong(Song(title = it, author = "Banda")) }
        Triple(repo, ScanSetlistViewModel(repo, br.app.stagebook.data.ImportQueue(repo), br.app.stagebook.data.SetlistCloud(repo, br.app.stagebook.data.DeviceIdentity(context)), androidx.lifecycle.SavedStateHandle(mapOf("code" to "", "inbox" to "")), br.app.stagebook.data.BandInbox(context), bandLink(repo), bandInternet(repo), br.app.stagebook.data.BandTrust(context), br.app.stagebook.data.Connectivity(context)), ids)
    }

    @Test fun `QR with every song creates the setlist in the QR order`() {
        val (repo, vm, ids) = setup()
        vm.onText(qr("Show", "Estrada Longa", "cancao do mar"))
        val review = vm.ui.value as ScanUi.Review
        assertTrue(review.missing.isEmpty())
        val id = vm.create()!!
        assertEquals(listOf(ids[2], ids[0]), repo.library.value.setlist(id)!!.items)
        assertEquals("Show", repo.library.value.setlist(id)!!.name)
    }

    @Test fun `a double tap on Create makes one setlist`() {
        val (repo, vm, _) = setup()
        vm.onText(qr("Show", "Noite Clara"))
        val before = repo.library.value.sets.size
        val first = vm.create()
        assertEquals(null, vm.create())   // the second tap
        assertTrue(first != null)
        assertEquals(before + 1, repo.library.value.sets.size)
    }

    @Test fun `a missing song shows before creating and is left out`() {
        val (repo, vm, ids) = setup()
        vm.onText(qr("Show", "Noite Clara", "Outra Coisa"))
        val review = vm.ui.value as ScanUi.Review
        assertEquals(listOf("Outra Coisa"), review.missing.map { it.title })
        val id = vm.create()!!
        assertEquals(listOf(ids[1]), repo.library.value.setlist(id)!!.items)
    }

    @Test fun `repeated name - replace swaps the songs and create another numbers it`() {
        val (repo, vm, ids) = setup()
        val old = repo.createSetlist("Show")
        vm.onText(qr("show", "Noite Clara"))
        assertEquals(old, vm.existing("show"))
        assertEquals(old, vm.create(replaceId = old))
        assertEquals(listOf(ids[1]), repo.library.value.setlist(old)!!.items)
        vm.review(qr("show", "Noite Clara"), emptyList())   // the same QR read again, this time "create another"
        val other = vm.create()!!
        assertNotEquals(old, other)
        assertEquals("show (2)", repo.library.value.setlist(other)!!.name)
    }

    @Test fun `online - the missing one comes along and goes into the library - the one here does not change`() {
        val (repo, vm, ids) = setup()
        val before = repo.song(ids[1])!!.text
        vm.review(
            qr("Show", "Noite Clara", "Música Nova"),
            listOf(
                br.app.stagebook.data.SetlistCloud.SharedSong("Noite Clara", "Banda", text = "[G]outra versão"),
                br.app.stagebook.data.SetlistCloud.SharedSong("Música Nova", "Outra Banda", "2001", "[C]linha nova"),
            ),
        )
        val review = vm.ui.value as ScanUi.Review
        assertTrue(review.missing.isEmpty())
        assertTrue(review.rows[1].carried)
        val id = vm.create()!!
        val items = repo.library.value.setlist(id)!!.items
        assertEquals(2, items.size)
        assertEquals(ids[1], items[0])
        // no key in the received song: the app saves the key computed from the chords (T17)
        assertEquals("{key: C}\n[C]linha nova", repo.song(items[1])!!.text)
        assertEquals(before, repo.song(ids[1])!!.text)
    }

    @Test fun `loose songs - only new ones go into the library and no setlist is created`() {
        val (repo, vm, ids) = setup()
        val sets = repo.library.value.sets.size
        val before = repo.song(ids[0])!!.text
        vm.review(
            qr("", "Canção do Mar", "Música Nova"),
            listOf(
                br.app.stagebook.data.SetlistCloud.SharedSong("Canção do Mar", "Banda", text = "[G]outra versão"),
                br.app.stagebook.data.SetlistCloud.SharedSong("Música Nova", "Outra", text = "[C]nova"),
            ),
        )
        val review = vm.ui.value as ScanUi.Review
        assertTrue(review.songsOnly)
        assertEquals(1, vm.addSongs())
        assertEquals(sets, repo.library.value.sets.size)
        assertEquals(before, repo.song(ids[0])!!.text)
        assertTrue(repo.library.value.songs.any { it.title == "Música Nova" && it.text == "{key: C}\n[C]nova" })
    }

    @Test fun `another version - by default mine stays - choosing theirs comes in without losing my chords`() {
        val (repo, vm, ids) = setup()
        repo.updateSong(ids[0]) { it.copy(text = "[G]Primeira linha\n[C]Segunda linha") }
        val incoming = br.app.stagebook.data.SetlistCloud.SharedSong("Canção do Mar", "Banda", text = "Primeira linha\nSegunda linha corrigida")
        vm.review(qr("", "Canção do Mar"), listOf(incoming))
        assertTrue((vm.ui.value as ScanUi.Review).rows[0].differs)
        assertEquals(2, vm.changes(0).size)
        vm.addSongs()
        assertEquals("[G]Primeira linha\n[C]Segunda linha", repo.song(ids[0])!!.text)   // didn't choose: mine stays

        vm.review(qr("", "Canção do Mar"), listOf(incoming))
        vm.useIncoming(0, true)
        vm.addSongs()
        val text = repo.song(ids[0])!!.text
        assertTrue(text.contains("[G]Primeira linha"))
        assertTrue(text.contains("Segunda linha corrigida"))
    }

    @Test fun `chords mode - refusing one that came with lyrics only sends it to the import queue`() {
        val (repo, _, ids) = setup()
        repo.updateLook { it.copy(chords = true) }
        val queue = br.app.stagebook.data.ImportQueue(repo)
        val vm = ScanSetlistViewModel(
            repo, queue, br.app.stagebook.data.SetlistCloud(repo, br.app.stagebook.data.DeviceIdentity(context)), androidx.lifecycle.SavedStateHandle(mapOf("code" to "", "inbox" to "")),
            br.app.stagebook.data.BandInbox(context), bandLink(repo), bandInternet(repo),
            br.app.stagebook.data.BandTrust(context), br.app.stagebook.data.Connectivity(context),
        )
        vm.review(qr("Show", "Noite Clara", "Só Letra"), listOf(br.app.stagebook.data.SetlistCloud.SharedSong("Só Letra", "", text = "linha um\nlinha dois")))
        val row = (vm.ui.value as ScanUi.Review).rows[1]
        assertTrue(row.carried && row.lyricsOnly)
        vm.searchWithChords(1, true)
        assertTrue(vm.createAndImport())
        assertEquals("Só Letra", queue.state.value!!.current!!.title)
        assertTrue(repo.library.value.songs.none { it.title == "Só Letra" })
        assertEquals(listOf(ids[1]), repo.library.value.setlist(queue.state.value!!.setId)!!.items)
        queue.stop()
    }

    @Test fun `online inbox - both in the pair find the same channel and a third one does not`() {
        val (repo, _, _) = setup()
        val cloud = br.app.stagebook.data.SetlistCloud(repo, br.app.stagebook.data.DeviceIdentity(context))
        val a = br.app.stagebook.domain.BandCrypto.newKeyPair()
        val b = br.app.stagebook.domain.BandCrypto.newKeyPair()
        val c = br.app.stagebook.domain.BandCrypto.newKeyPair()
        val ida = br.app.stagebook.domain.BandCrypto.idOf(br.app.stagebook.domain.BandCrypto.encode(a.public))
        val idb = br.app.stagebook.domain.BandCrypto.idOf(br.app.stagebook.domain.BandCrypto.encode(b.public))
        val idc = br.app.stagebook.domain.BandCrypto.idOf(br.app.stagebook.domain.BandCrypto.encode(c.public))
        val ab = br.app.stagebook.domain.BandCrypto.pairKey(a.private, ida, b.public, idb)
        val ba = br.app.stagebook.domain.BandCrypto.pairKey(b.private, idb, a.public, ida)
        val cb = br.app.stagebook.domain.BandCrypto.pairKey(c.private, idc, b.public, idb)
        assertEquals(cloud.pairTopic(ab, idb), cloud.pairTopic(ba, idb))   // a sends to b; b looks in their inbox
        assertNotEquals(cloud.pairTopic(ab, idb), cloud.pairTopic(cb, idb))
        assertNotEquals(cloud.pairTopic(ab, idb), cloud.pairTopic(ab, ida))  // each one's inbox is separate
        val lines = listOf("""{"id":"x1","event":"message","message":"m"}""", """{"id":"x2","event":"keepalive"}""")
        assertEquals(listOf("x1" to "m"), br.app.stagebook.data.SetlistCloud.messagesWithIds(lines))
    }

    @Test fun `inbox - the same send does not repeat and the same setlist from the same person replaces the old one`() {
        val box = br.app.stagebook.data.BandInbox(context)
        box.items.value.forEach { box.remove(it.id) }
        val v1 = qr("Show", "A")
        box.add("ana", "Ana", v1, emptyList())
        box.add("ana", "Ana", v1, emptyList())          // arrived over Wi-Fi and over the internet
        assertEquals(1, box.items.value.size)
        box.add("ana", "Ana", qr("Show", "A", "B"), emptyList())   // fixed it and sent again
        assertEquals(listOf("2"), box.items.value.map { it.text.split('\n')[1] })
        box.add("beto", "Beto", qr("Show", "C"), emptyList())     // another person, same name: both stay
        assertEquals(2, box.items.value.size)
    }

    @Test fun `cut QR asks to scan again and a QR of something else warns`() {
        val (_, vm, _) = setup()
        vm.onText(listOf(QrPayload.HEADER, "3", "Show", "A").joinToString("\n"))
        assertEquals(ScanUi.Scanning(ScanProblem.INCOMPLETE), vm.ui.value)
        vm.onText("https://exemplo.com")
        assertEquals(ScanUi.Scanning(ScanProblem.NOT_SETLIST), vm.ui.value)
    }

    @Test fun `ask online only shows when the sender is in the band`() {
        val (_, vm, _) = setup()
        vm.review(qr("Show", "Noite Clara", "Não Tenho"), emptyList())
        // came by QR (nobody from the band): no one to ask online
        assertEquals(null, vm.internetFrom.value)
    }

    @Test fun `the sender answers the request with the songs they have`() {
        val (repo, _, ids) = setup()
        val link = bandLink(repo)
        val found = link.songsFor(listOf(br.app.stagebook.data.BandLink.Wanted(repo.song(ids[0])!!.title), br.app.stagebook.data.BandLink.Wanted("Não existe")))
        assertEquals(listOf(ids[0]), found)
    }
}
