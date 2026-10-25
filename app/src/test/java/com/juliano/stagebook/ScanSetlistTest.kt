package com.juliano.stagebook

import androidx.test.core.app.ApplicationProvider
import com.juliano.stagebook.data.LibraryRepository
import com.juliano.stagebook.data.model.Song
import com.juliano.stagebook.domain.QrPayload
import com.juliano.stagebook.ui.setlists.ScanProblem
import com.juliano.stagebook.ui.setlists.ScanSetlistViewModel
import com.juliano.stagebook.ui.setlists.ScanUi
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

/** Ler o QR de um setlist: confere com a biblioteca, avisa o que falta e cria (ou substitui) o setlist. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ScanSetlistTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun bandLink(repo: LibraryRepository) = com.juliano.stagebook.data.BandLink(
        context, com.juliano.stagebook.data.DeviceIdentity(context), com.juliano.stagebook.data.BandTrust(context),
        com.juliano.stagebook.data.StageGate(), com.juliano.stagebook.data.BandInbox(context), repo,
    )

    private fun qr(name: String, vararg titles: String) =
        (listOf(QrPayload.HEADER, titles.size.toString(), name) + titles).joinToString("\n")

    private fun setup(): Triple<LibraryRepository, ScanSetlistViewModel, List<String>> = runBlocking {
        val repo = LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        withTimeout(5_000) { repo.loaded.first { it } }
        val ids = listOf("Canção do Mar", "Noite Clara", "Estrada Longa").map { repo.saveSong(Song(title = it, author = "Banda")) }
        Triple(repo, ScanSetlistViewModel(repo, com.juliano.stagebook.data.ImportQueue(repo), com.juliano.stagebook.data.SetlistCloud(repo), androidx.lifecycle.SavedStateHandle(mapOf("code" to "", "inbox" to "")), com.juliano.stagebook.data.BandInbox(context), bandLink(repo)), ids)
    }

    @Test fun `QR com todas as músicas cria o setlist na ordem do QR`() {
        val (repo, vm, ids) = setup()
        vm.onText(qr("Show", "Estrada Longa", "cancao do mar"))
        val review = vm.ui.value as ScanUi.Review
        assertTrue(review.missing.isEmpty())
        val id = vm.create()!!
        assertEquals(listOf(ids[2], ids[0]), repo.library.value.setlist(id)!!.items)
        assertEquals("Show", repo.library.value.setlist(id)!!.name)
    }

    @Test fun `música que falta aparece antes de criar e fica de fora`() {
        val (repo, vm, ids) = setup()
        vm.onText(qr("Show", "Noite Clara", "Outra Coisa"))
        val review = vm.ui.value as ScanUi.Review
        assertEquals(listOf("Outra Coisa"), review.missing.map { it.title })
        val id = vm.create()!!
        assertEquals(listOf(ids[1]), repo.library.value.setlist(id)!!.items)
    }

    @Test fun `nome repetido - substituir troca as músicas e criar outro numera`() {
        val (repo, vm, ids) = setup()
        val old = repo.createSetlist("Show")
        vm.onText(qr("show", "Noite Clara"))
        assertEquals(old, vm.existing("show"))
        assertEquals(old, vm.create(replaceId = old))
        assertEquals(listOf(ids[1]), repo.library.value.setlist(old)!!.items)
        val other = vm.create()!!
        assertNotEquals(old, other)
        assertEquals("show (2)", repo.library.value.setlist(other)!!.name)
    }

    @Test fun `pela internet - a que falta vem junto e entra na biblioteca - a daqui não muda`() {
        val (repo, vm, ids) = setup()
        val before = repo.song(ids[1])!!.text
        vm.review(
            qr("Show", "Noite Clara", "Música Nova"),
            listOf(
                com.juliano.stagebook.data.SetlistCloud.SharedSong("Noite Clara", "Banda", text = "[G]outra versão"),
                com.juliano.stagebook.data.SetlistCloud.SharedSong("Música Nova", "Outra Banda", "2001", "[C]linha nova"),
            ),
        )
        val review = vm.ui.value as ScanUi.Review
        assertTrue(review.missing.isEmpty())
        assertTrue(review.rows[1].carried)
        val id = vm.create()!!
        val items = repo.library.value.setlist(id)!!.items
        assertEquals(2, items.size)
        assertEquals(ids[1], items[0])
        assertEquals("[C]linha nova", repo.song(items[1])!!.text)
        assertEquals(before, repo.song(ids[1])!!.text)
    }

    @Test fun `músicas avulsas - só as novas entram na biblioteca e nenhum setlist é criado`() {
        val (repo, vm, ids) = setup()
        val sets = repo.library.value.sets.size
        val before = repo.song(ids[0])!!.text
        vm.review(
            qr("", "Canção do Mar", "Música Nova"),
            listOf(
                com.juliano.stagebook.data.SetlistCloud.SharedSong("Canção do Mar", "Banda", text = "[G]outra versão"),
                com.juliano.stagebook.data.SetlistCloud.SharedSong("Música Nova", "Outra", text = "[C]nova"),
            ),
        )
        val review = vm.ui.value as ScanUi.Review
        assertTrue(review.songsOnly)
        assertEquals(1, vm.addSongs())
        assertEquals(sets, repo.library.value.sets.size)
        assertEquals(before, repo.song(ids[0])!!.text)
        assertTrue(repo.library.value.songs.any { it.title == "Música Nova" && it.text == "[C]nova" })
    }

    @Test fun `QR cortado pede para ler de novo e QR de outra coisa avisa`() {
        val (_, vm, _) = setup()
        vm.onText(listOf(QrPayload.HEADER, "3", "Show", "A").joinToString("\n"))
        assertEquals(ScanUi.Scanning(ScanProblem.INCOMPLETE), vm.ui.value)
        vm.onText("https://exemplo.com")
        assertEquals(ScanUi.Scanning(ScanProblem.NOT_SETLIST), vm.ui.value)
    }
}
