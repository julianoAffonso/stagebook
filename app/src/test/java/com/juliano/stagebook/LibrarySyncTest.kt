package com.juliano.stagebook

import androidx.test.core.app.ApplicationProvider
import com.juliano.stagebook.data.AppUpdater
import com.juliano.stagebook.data.LibraryRepository
import com.juliano.stagebook.data.LibrarySync
import com.juliano.stagebook.data.LibraryCodec
import com.juliano.stagebook.data.SyncKind
import com.juliano.stagebook.data.StageGate
import com.juliano.stagebook.data.syncIsSafe
import com.juliano.stagebook.data.SyncResult
import com.juliano.stagebook.data.lyricsHash
import com.juliano.stagebook.data.metaHash
import com.juliano.stagebook.data.model.Library
import com.juliano.stagebook.data.model.Setlist
import com.juliano.stagebook.data.model.Song
import com.juliano.stagebook.data.syncSongs
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.InetSocketAddress

/** Músicas do Drive baixadas sozinhas ao instalar/atualizar, sem perder edições nem zooms. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class LibrarySyncTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var server: HttpServer
    private var body = ""
    private var hits = 0
    private var announced: String? = null   // marca anunciada diferente do arquivo (Drive no meio da troca)
    private var appCode = 1L                 // versão do app anunciada (1 = mais velha que a instalada)
    private val order by lazy { com.juliano.stagebook.data.UpdateOrder(context) }
    private var n = 0
    private val newId = { "id${++n}" }

    @Before fun start() {
        File(context.filesDir, "stagebook.json").delete()
        File(context.filesDir, "stagebook.json.bak").delete()
        File(context.filesDir, "sincronia").deleteRecursively()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        // o arquivo de versão anuncia a marca da biblioteca (como o publicar.sh grava)
        server.createContext("/versao") { ex ->
            val sha = announced ?: java.security.MessageDigest.getInstance("SHA-256").digest(body.toByteArray()).joinToString("") { "%02x".format(it) }
            val b = """{"versionCode":$appCode,"versionName":"teste","library":"$sha"}""".toByteArray()
            ex.sendResponseHeaders(200, b.size.toLong())
            ex.responseBody.use { it.write(b) }
        }
        server.createContext("/biblioteca") { ex ->
            hits++
            val b = body.toByteArray()
            ex.sendResponseHeaders(if (body.isEmpty()) 404 else 200, if (b.isEmpty()) -1 else b.size.toLong())
            ex.responseBody.use { it.write(b) }
        }
        server.start()
    }

    @After fun stop() = server.stop(0)

    private fun repo() = LibraryRepository(context, scope).also { r -> runBlocking { withTimeout(5_000) { r.loaded.first { it } } } }

    // ---- regras da junção ----

    @Test fun `biblioteca vazia - entram todas, já marcadas como vindas do Drive`() {
        val (lib, r) = Library().syncSongs(listOf(Song(title = "A", text = "a1"), Song(title = "B", text = "b1")), newId)
        assertEquals(2, r.added)
        assertEquals(listOf("A", "B"), lib.songs.map { it.title })
        assertEquals(lyricsHash("a1"), lib.songs[0].driveHash)
    }

    @Test fun `letra não editada aqui é atualizada, zoom e id ficam`() {
        val lib = Library(songs = listOf(Song("x", "A", text = "a1", zoom = 1.4f, driveHash = lyricsHash("a1"))))
        val (out, r) = lib.syncSongs(listOf(Song(title = "A", text = "a2")), newId)
        val s = out.songs.single()
        assertEquals("a2", s.text)
        assertEquals("x", s.id)
        assertEquals(1.4f, s.zoom)
        assertEquals(lyricsHash("a2"), s.driveHash)
        assertEquals(1, r.updated)
    }

    @Test fun `letra editada aqui fica, e continua protegida na próxima sincronia`() {
        val lib = Library(songs = listOf(Song("x", "A", text = "a1 com [Refrão] marcado", zoom = 0.8f, driveHash = lyricsHash("a1"))))
        val (once, r) = lib.syncSongs(listOf(Song(title = "A", text = "a2")), newId)
        assertEquals("a1 com [Refrão] marcado", once.songs.single().text)
        assertEquals(0.8f, once.songs.single().zoom)
        assertEquals(1, r.keptEdited)
        val (twice, _) = once.syncSongs(listOf(Song(title = "A", text = "a3")), newId)
        assertEquals("a1 com [Refrão] marcado", twice.songs.single().text)
    }

    @Test fun `música de antes da sincronia - igual ao Drive passa a ser acompanhada, diferente fica`() {
        val lib = Library(songs = listOf(Song("x", "A", text = "a1"), Song("y", "B", text = "minha versão")))
        val (out, r) = lib.syncSongs(listOf(Song(title = "A", text = "a1"), Song(title = "B", text = "b1")), newId)
        assertEquals(lyricsHash("a1"), out.songs[0].driveHash)
        assertEquals("minha versão", out.songs[1].text)
        assertEquals("", out.songs[1].driveHash)
        assertEquals(1, r.keptEdited)
        assertEquals("a próxima letra nova do Drive chega", "a2", out.syncSongs(listOf(Song(title = "A", text = "a2")), newId).first.songs[0].text)
    }

    @Test fun `só formatação diferente - é a mesma letra, fica a daqui e passa a acompanhar o Drive`() {
        val local = "[Chorus]\nlinha um  \n\n\nlinha dois"
        val drive = "[Refrão]\nlinha um\n\nlinha dois"
        val (out, r) = Library(songs = listOf(Song("x", "A", text = local))).syncSongs(listOf(Song(title = "A", text = drive)), newId)
        assertEquals(local, out.songs.single().text)
        assertEquals(SyncResult(), r)
        val (next, _) = out.syncSongs(listOf(Song(title = "A", text = "[Refrão]\nlinha um corrigida\n\nlinha dois")), newId)
        assertEquals("não foi mexida aqui: a correção chega", "[Refrão]\nlinha um corrigida\n\nlinha dois", next.songs.single().text)
    }

    @Test fun `seções marcadas aqui ficam, mesmo sem a marca do Drive`() {
        val local = "[Verso]\nlinha um\n[Refrão]\nlinha dois"
        val (out, r) = Library(songs = listOf(Song("x", "A", text = local, zoom = 1.2f)))
            .syncSongs(listOf(Song(title = "A", text = "linha um\nlinha dois")), newId)
        assertEquals(local, out.songs.single().text)
        assertEquals(1.2f, out.songs.single().zoom)
        assertEquals(1, r.keptSections)
    }

    @Test fun `letra daqui sem seções recebe as seções do Drive com as mesmas palavras`() {
        val drive = "[Verso]\nlinha um\n[Refrão]\nlinha dois"
        val (out, r) = Library(songs = listOf(Song("x", "A", text = "linha um\nlinha dois")))
            .syncSongs(listOf(Song(title = "A", text = drive)), newId)
        assertEquals(drive, out.songs.single().text)
        assertEquals(1, r.sectionsAdded)
    }

    @Test fun `seções não mexidas aqui seguem as novas do Drive`() {
        val v1 = "[Verso]\nlinha um\nlinha dois"
        val v2 = "[Verso]\nlinha um\n[Refrão]\nlinha dois"
        val (out, _) = Library(songs = listOf(Song("x", "A", text = v1, driveHash = lyricsHash(v1))))
            .syncSongs(listOf(Song(title = "A", text = v2)), newId)
        assertEquals(v2, out.songs.single().text)
    }

    @Test fun `setlists, aparência, músicas só daqui e artista editado ficam`() {
        val lib = Library(
            songs = listOf(Song("x", "A", author = "Banda", year = "", text = "a1"), Song("z", "Só minha", text = "m")),
            sets = listOf(Setlist("s", "Show", listOf("z", "x"))),
        )
        val (out, r) = lib.syncSongs(listOf(Song(title = "A", author = "Banda", year = "1999", text = "a1"), Song(title = "C", text = "c1")), newId)
        assertEquals(listOf("A", "Só minha", "C"), out.songs.map { it.title })
        assertEquals("1999", out.songs[0].year)
        assertEquals(lib.sets, out.sets)
        assertEquals(lib.look, out.look)
        assertEquals(1, r.added)
    }

    @Test fun `conferência recusa junção que perderia música, zoom ou setlist`() {
        val old = Library(songs = listOf(Song("x", "A", text = "a", zoom = 1.3f)), sets = listOf(Setlist("s", "Show", listOf("x"))))
        val (ok, r) = old.syncSongs(listOf(Song(title = "B", text = "b")), newId)
        assertTrue(syncIsSafe(old, ok, r))
        assertFalse("sumiu música", syncIsSafe(old, ok.copy(songs = ok.songs.drop(1)), r))
        assertFalse("zoom mudou", syncIsSafe(old, ok.copy(songs = ok.songs.map { it.copy(zoom = 1f) }), r))
        assertFalse("setlist mudou", syncIsSafe(old, ok.copy(sets = emptyList()), r))
        assertFalse("letra trocada sem a junção decidir", syncIsSafe(old, ok.copy(songs = ok.songs.map { it.copy(text = "outra") }), r))
    }

    // ---- sincronia de verdade ----

    private val gate = StageGate()
    private var internet = true

    private fun sync(r: LibraryRepository) = LibrarySync(context, r, AppUpdater(context).apply {
        libraryUrl = "http://127.0.0.1:${server.address.port}/biblioteca"
        versionUrl = "http://127.0.0.1:${server.address.port}/versao"
    }, gate, order, scope, Dispatchers.IO).apply { online = { internet } }

    private fun Job?.await() = runBlocking { withTimeout(10_000) { this@await?.join() } }

    /** Espera o aviso chegar (vem de outra corrotina ao sair do palco). */
    private fun LibrarySync.waitNotice(): com.juliano.stagebook.data.SyncNotice? =
        runBlocking { withTimeout(5_000) { while (notice.value == null) delay(20) }; notice.value }

    @Test fun `instalação do zero - baixa e aplica já, sem aviso e sem nada para revisar`() {
        body = """{"songs":[{"title":"A","text":"a1"},{"title":"B","text":"b1"}]}"""
        val r = repo()
        val s = sync(r)
        s.onEnter().await()
        assertEquals(2, r.library.value.songs.size)
        assertNull(s.notice.value)
        val reopened = sync(r)
        reopened.onEnter().await()   // mesma biblioteca anunciada: não baixa de novo
        assertNull("a carga inicial não é atualização", reopened.notice.value)
        assertNull("nem entra na revisão", runBlocking { reopened.details() })
        assertEquals(1, hits)
    }

    @Test fun `no palco - baixa em silêncio, só troca as letras e avisa ao sair dele`() {
        body = """{"songs":[{"title":"A","text":"a1"}]}"""
        val r = repo()
        r.saveSong(Song(title = "Minha", text = "m", zoom = 1.5f))
        val s = sync(r)
        gate.enter()
        s.onEnter()
        runBlocking { withTimeout(5_000) { while (hits == 0) delay(20) } }
        Thread.sleep(300)
        assertEquals("baixou, mas no palco nada muda", listOf("Minha"), r.library.value.songs.map { it.title })
        assertNull(s.notice.value)
        s.onEnter()   // voltou para o app ainda no palco: sem aviso
        assertNull(s.notice.value)
        gate.leave()
        assertEquals(1, s.waitNotice()?.added)
        assertEquals(listOf("Minha", "A"), r.library.value.songs.map { it.title })
        assertEquals(1.5f, r.library.value.songs[0].zoom)
        assertTrue("guardou a biblioteca de antes", File(context.filesDir, "stagebook.antes-da-sincronia.json").exists())
        assertEquals(1, hits)
    }

    @Test fun `fora do palco - aplica em segundo plano e avisa na hora, uma vez só`() {
        body = """{"songs":[{"title":"A","text":"a1"}]}"""
        val r = repo()
        r.saveSong(Song(title = "Minha", text = "m"))
        val s = sync(r)
        s.onEnter().await()
        assertEquals(2, r.library.value.songs.size)
        assertEquals(1, s.notice.value?.added)
        s.dismissNotice()
        assertNull("o aviso aparece uma vez só", sync(r).apply { onEnter() }.notice.value)
    }

    @Test fun `atualização a caminho - a revisão sabe que a lista é a anterior`() {
        body = """{"songs":[{"title":"A","text":"a1"}]}"""
        val r = repo()
        r.saveSong(Song(title = "Minha", text = "m"))
        val s = sync(r)
        assertFalse(s.updating.value)
        gate.enter()
        s.onEnter()
        runBlocking { withTimeout(5_000) { s.updating.first { it } } }
        assertTrue("baixada e esperando o palco fechar", s.updating.value)
        gate.leave()
        runBlocking { withTimeout(5_000) { s.updating.first { !it } } }
        assertEquals(2, r.library.value.songs.size)
    }

    @Test fun `palco aberto no meio da gravação - não troca a biblioteca`() {
        val r = repo()
        r.saveSong(Song(title = "Minha", text = "m"))
        Thread.sleep(400)
        val before = File(context.filesDir, "stagebook.json").readText()
        val out = runBlocking { r.commitSync(listOf(Song(title = "A", text = "a1")), canSwap = { false }) }
        assertNull(out)
        assertEquals(listOf("Minha"), r.library.value.songs.map { it.title })
        assertEquals("o arquivo também fica como estava", before, File(context.filesDir, "stagebook.json").readText())
    }

    @Test fun `gravação falhando - nada muda, nada é marcado e tenta de novo`() {
        body = """{"songs":[{"title":"A","text":"a1"}]}"""
        val r = repo()
        r.saveSong(Song(title = "Minha", text = "m"))
        Thread.sleep(400)
        val tmp = File(context.filesDir, "letras.json.tmp").apply { deleteRecursively(); mkdirs() }   // disco recusando
        val s = sync(r)
        s.onEnter().await()
        assertEquals(listOf("Minha"), r.library.value.songs.map { it.title })
        assertNull(s.notice.value)
        tmp.deleteRecursively()
        s.onEnter().await()
        assertEquals(listOf("Minha", "A"), r.library.value.songs.map { it.title })
        assertEquals("usou o arquivo já baixado", 1, hits)
    }

    @Test fun `gravação adiada - tenta de novo ao sair do palco, sem esperar reabrir o app`() {
        body = """{"songs":[{"title":"A","text":"a1"}]}"""
        val r = repo()
        r.saveSong(Song(title = "Minha", text = "m"))
        Thread.sleep(400)
        val tmp = File(context.filesDir, "letras.json.tmp").apply { deleteRecursively(); mkdirs() }   // disco recusando
        val s = sync(r)
        s.onEnter().await()
        assertEquals(1, r.library.value.songs.size)
        tmp.deleteRecursively()
        gate.enter(); gate.leave()   // tocou uma música e saiu do palco
        runBlocking { withTimeout(5_000) { while (r.library.value.songs.size < 2) delay(20) } }
        assertEquals(listOf("Minha", "A"), r.library.value.songs.map { it.title })
    }

    @Test fun `sem internet ou arquivo estranho - nada muda e tenta depois`() {
        val r = repo()
        r.saveSong(Song(title = "Minha", text = "m"))
        body = ""
        sync(r).onEnter().await()
        body = "<html>aviso do Drive</html>"
        sync(r).onEnter().await()
        body = """{"songs":[]}"""
        sync(r).onEnter().await()
        assertEquals(listOf("Minha"), r.library.value.songs.map { it.title })
        body = """{"songs":[{"title":"A","text":"a1"}]}"""
        sync(r).onEnter().await()
        assertEquals(listOf("Minha", "A"), r.library.value.songs.map { it.title })
    }

    @Test fun `abriu sem internet - a internet volta e baixa sozinho, uma vez só`() {
        body = """{"songs":[{"title":"A","text":"a1"}]}"""
        internet = false
        val r = repo()
        val s = sync(r)
        assertNull("sem internet não começa", s.onEnter())
        assertEquals(0, hits)
        internet = true
        s.onNetwork(true)
        runBlocking { withTimeout(10_000) { while (r.library.value.songs.isEmpty()) delay(20) } }
        assertEquals(listOf("A"), r.library.value.songs.map { it.title })
        s.onNetwork(true)   // sinal do Wi-Fi mudou: não é volta da internet
        s.onNetwork(false)
        s.onNetwork(true)   // caiu e voltou: já sincronizou, nada a fazer
        Thread.sleep(300)
        assertEquals(1, hits)
    }

    // ---- detalhes e desfazer ----

    @Test fun `a sincronia guarda o que mudou, música a música`() {
        val lib = Library(songs = listOf(
            Song("x", "A", text = "a1", driveHash = lyricsHash("a1")),
            Song("y", "B", text = "linha um\nlinha dois"),
        ))
        val (_, r) = lib.syncSongs(listOf(
            Song(title = "A", text = "a2"),
            Song(title = "B", text = "[Refrão]\nlinha um\nlinha dois"),
            Song(title = "C", text = "c1"),
        ), newId)
        assertEquals(listOf("A" to SyncKind.UPDATED, "B" to SyncKind.SECTIONS, "C" to SyncKind.ADDED), r.changes.map { it.title to it.kind })
        assertEquals("a1", r.changes[0].before)
        assertEquals("a2", r.changes[0].after)
    }

    @Test fun `desfazer volta a letra de antes e a próxima sincronia não troca de novo`() {
        body = """{"songs":[{"title":"A","text":"a2"}]}"""
        val r = repo()
        r.saveSong(Song(title = "A", text = "a1", zoom = 1.3f, driveHash = lyricsHash("a1")))
        Thread.sleep(400)
        val s = sync(r)
        s.onEnter().await()
        assertEquals("a2", r.findSong("A", "")!!.text)
        val d = runBlocking { s.details() }!!
        assertEquals(listOf(SyncKind.UPDATED), d.changes.map { it.kind })
        assertTrue(runBlocking { s.revert(d.changes.single().key) })
        Thread.sleep(200)
        assertEquals("a1", r.findSong("A", "")!!.text)
        assertEquals(1.3f, r.findSong("A", "")!!.zoom)
        assertTrue("fica marcada como desfeita", runBlocking { s.details() }!!.changes.single().reverted)
        val (again, _) = r.library.value.syncSongs(listOf(Song(title = "A", text = "a2")), newId)
        assertEquals("protegida: conta como editada", "a1", again.songs.single().text)
    }

    @Test fun `desfazer não passa por cima de uma edição feita depois da sincronia`() {
        body = """{"songs":[{"title":"A","text":"a2"}]}"""
        val r = repo()
        r.saveSong(Song(title = "A", text = "a1", driveHash = lyricsHash("a1")))
        Thread.sleep(400)
        val s = sync(r)
        s.onEnter().await()
        val id = r.findSong("A", "")!!.id
        r.updateSong(id) { it.copy(text = "a2 editada à mão") }
        assertFalse(runBlocking { s.revert("$id:UPDATED") })
        assertEquals("a2 editada à mão", r.findSong("A", "")!!.text)
    }

    @Test fun `sincronia de antes deste registro - detalhes refeitos da cópia, sem contar edição do usuário`() {
        val r = repo()
        // como estava antes da sincronia
        File(context.filesDir, "stagebook.antes-da-sincronia.json").writeText(LibraryCodec.encode(Library(songs = listOf(
            Song("x", "Atualizada", text = "x1"),
            Song("y", "Editada depois", text = "y1"),
        ))))
        // como está agora: x veio do Drive; y foi editada à mão depois; z entrou pela sincronia; w criada à mão
        r.saveSong(Song("x", "Atualizada", text = "x2", driveHash = lyricsHash("x2")))
        r.saveSong(Song("y", "Editada depois", text = "y2 à mão", driveHash = lyricsHash("y-drive")))
        r.saveSong(Song("z", "Nova", text = "z1", driveHash = lyricsHash("z1")))
        r.saveSong(Song("w", "Minha", text = "w1"))
        val d = runBlocking { sync(r).details() }!!
        assertTrue(d.rebuilt)
        assertEquals(listOf("Atualizada" to SyncKind.UPDATED, "Nova" to SyncKind.ADDED), d.changes.map { it.title to it.kind })
    }

    @Test fun `dois desfazer ao mesmo tempo - as duas ficam marcadas como desfeitas`() {
        body = """{"songs":[{"title":"A","text":"a2"},{"title":"B","text":"b2"}]}"""
        val r = repo()
        r.saveSong(Song(title = "A", text = "a1", driveHash = lyricsHash("a1")))
        r.saveSong(Song(title = "B", text = "b1", driveHash = lyricsHash("b1")))
        Thread.sleep(400)
        val s = sync(r)
        s.onEnter().await()
        val ids = runBlocking { s.details() }!!.changes.map { it.key }
        assertEquals(2, ids.size)
        runBlocking {
            val a = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).async { s.revert(ids[0]) }
            val b = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).async { s.revert(ids[1]) }
            assertTrue(a.await() && b.await())
        }
        assertEquals(listOf(true, true), runBlocking { s.details() }!!.changes.map { it.reverted })
        assertEquals("também no arquivo (outra abertura do app)", listOf(true, true), runBlocking { sync(r).details() }!!.changes.map { it.reverted })
    }

    @Test fun `aceitar a atualizada registra a escolha e remover a nova tira da biblioteca`() {
        body = """{"songs":[{"title":"A","text":"a2"},{"title":"Nova","text":"n1"}]}"""
        val r = repo()
        r.saveSong(Song(title = "A", text = "a1", driveHash = lyricsHash("a1")))
        Thread.sleep(400)
        val s = sync(r)
        s.onEnter().await()
        val d = runBlocking { s.details() }!!
        val a = d.changes.single { it.kind == SyncKind.UPDATED }
        val nova = d.changes.single { it.kind == SyncKind.ADDED }
        assertTrue(runBlocking { s.keep(a.key) })
        assertEquals("a letra fica a atualizada", "a2", r.findSong("A", "")!!.text)
        assertFalse("depois de escolher, não dá para escolher de novo", runBlocking { s.revert(a.key) })
        assertTrue(runBlocking { s.removeNew(nova.key) })
        Thread.sleep(200)
        assertNull(r.findSong("Nova", ""))
        val again = runBlocking { sync(r).details() }!!.changes
        assertTrue(again.single { it.songId == a.songId }.kept)
        assertTrue(again.single { it.songId == nova.songId }.removed)
    }

    // ---- músicas em que o usuário tem a versão dele ----

    @Test fun `versão própria aparece na revisão e manter a atual não pergunta de novo`() {
        body = """{"songs":[{"title":"A","text":"versão do drive"}]}"""
        val r = repo()
        r.saveSong(Song(title = "A", text = "minha versão"))   // sem marca: conta como do usuário
        Thread.sleep(400)
        val s = sync(r)
        s.onEnter().await()
        assertEquals("a dele fica", "minha versão", r.findSong("A", "")!!.text)
        val c = runBlocking { s.details() }!!.changes.single()
        assertEquals(SyncKind.PROTECTED, c.kind)
        assertEquals("minha versão" to "versão do drive", c.before to c.after)
        assertTrue(runBlocking { s.revert(c.key) })   // "Manter a atual"
        Thread.sleep(200)
        val (_, again) = r.library.value.syncSongs(listOf(Song(title = "A", text = "versão do drive")), newId)
        assertEquals("a mesma versão recusada não volta", emptyList<SyncKind>(), again.changes.map { it.kind })
        val (_, other) = r.library.value.syncSongs(listOf(Song(title = "A", text = "outra versão do drive")), newId)
        assertEquals("uma versão nova de lá, sim", listOf(SyncKind.PROTECTED), other.changes.map { it.kind })
    }

    @Test fun `versão própria - aceitar a atualizada troca a letra e passa a acompanhar`() {
        body = """{"songs":[{"title":"A","text":"versão do drive"}]}"""
        val r = repo()
        r.saveSong(Song(title = "A", text = "minha versão", zoom = 1.2f))
        Thread.sleep(400)
        val s = sync(r)
        s.onEnter().await()
        val c = runBlocking { s.details() }!!.changes.single()
        assertTrue(runBlocking { s.keep(c.key) })   // "Aceitar a atualizada"
        Thread.sleep(200)
        val a = r.findSong("A", "")!!
        assertEquals("versão do drive", a.text)
        assertEquals(1.2f, a.zoom)
        val (next, _) = r.library.value.syncSongs(listOf(Song(title = "A", text = "versão do drive corrigida")), newId)
        assertEquals("agora acompanha as próximas", "versão do drive corrigida", next.songs.single().text)
    }

    @Test fun `manter a atual numa atualizada não vira pergunta na próxima atualização`() {
        body = """{"songs":[{"title":"A","text":"a2"}]}"""
        val r = repo()
        r.saveSong(Song(title = "A", text = "a1", driveHash = lyricsHash("a1")))
        Thread.sleep(400)
        val s = sync(r)
        s.onEnter().await()
        assertTrue(runBlocking { s.revert(runBlocking { s.details() }!!.changes.single().key) })
        Thread.sleep(200)
        val (_, again) = r.library.value.syncSongs(listOf(Song(title = "A", text = "a2")), newId)
        assertEquals(emptyList<SyncKind>(), again.changes.map { it.kind })
    }

    // ---- a biblioteca atualiza quando ela muda, não quando o app muda ----

    @Test fun `biblioteca nova publicada chega sem versão nova do app`() {
        body = """{"songs":[{"title":"A","text":"a1"}]}"""
        val r = repo()
        sync(r).onEnter().await()
        assertEquals(listOf("A"), r.library.value.songs.map { it.title })
        body = """{"songs":[{"title":"A","text":"a1"},{"title":"B","text":"b1"}]}"""   // publicou músicas
        sync(r).onEnter().await()   // mesma versão do app, outra abertura
        assertEquals(listOf("A", "B"), r.library.value.songs.map { it.title })
        sync(r).onEnter().await()
        assertEquals("a mesma biblioteca não é baixada de novo", 2, hits)
    }

    @Test fun `arquivo baixado diferente do anunciado - não aplica e tenta depois`() {
        body = """{"songs":[{"title":"A","text":"a1"}]}"""
        announced = "0".repeat(64)   // o arquivo de versão já anuncia outra biblioteca
        val r = repo()
        r.saveSong(Song(title = "Minha", text = "m"))
        sync(r).onEnter().await()
        assertEquals(listOf("Minha"), r.library.value.songs.map { it.title })
        announced = null   // o Drive terminou a troca
        sync(r).onEnter().await()
        assertEquals(listOf("Minha", "A"), r.library.value.songs.map { it.title })
    }

    // ---- muitas músicas (ex.: seções acrescentadas no aparelho) ----

    @Test fun `só seções a mais aqui e a de lá sem nenhuma - não entra na revisão`() {
        val lib = Library(songs = listOf(
            Song("x", "A", text = "[Verso 1]\nlinha um\n[Refrão]\nlinha dois"),   // seções acrescentadas aqui
            Song("y", "B", text = "[Verso 1]\nlinha um\nlinha dois"),
        ))
        val (out, r) = lib.syncSongs(listOf(
            Song(title = "A", text = "linha um\nlinha dois"),                       // de lá, sem seções: nada a decidir
            Song(title = "B", text = "[Refrão]\nlinha um\nlinha dois"),             // de lá com outras seções: decide
        ), newId)
        assertEquals("as daqui ficam", lib.songs.map { it.text }, out.songs.map { it.text })
        assertEquals(listOf("B" to SyncKind.PROTECTED), r.changes.map { it.title to it.kind })
    }

    @Test fun `aceitar todas e manter todas - decidem as pendentes de uma vez`() {
        body = """{"songs":[{"title":"A","text":"de lá A"},{"title":"B","text":"de lá B"},{"title":"C","text":"de lá C"}]}"""
        val r = repo()
        r.saveSong(Song(title = "A", text = "minha A"))
        r.saveSong(Song(title = "B", text = "minha B"))
        r.saveSong(Song(title = "C", text = "minha C"))
        Thread.sleep(400)
        val s = sync(r)
        s.onEnter().await()
        val ids = runBlocking { s.details() }!!.changes.associate { it.title to it.key }
        assertTrue(runBlocking { s.keep(ids.getValue("A")) })   // uma decidida antes, à mão
        assertEquals(0, runBlocking { s.decideAll(setOf(SyncKind.PROTECTED), accept = true) })
        Thread.sleep(200)
        assertEquals(listOf("de lá A", "de lá B", "de lá C"), listOf("A", "B", "C").map { r.findSong(it, "")!!.text })
        assertTrue(runBlocking { sync(r).details() }!!.changes.all { it.kept })
    }

    @Test fun `manter todas - nada muda nas letras e não pergunta de novo`() {
        body = """{"songs":[{"title":"A","text":"de lá A"},{"title":"B","text":"de lá B"}]}"""
        val r = repo()
        r.saveSong(Song(title = "A", text = "minha A"))
        r.saveSong(Song(title = "B", text = "minha B"))
        Thread.sleep(400)
        val s = sync(r)
        s.onEnter().await()
        assertEquals(0, runBlocking { s.decideAll(setOf(SyncKind.PROTECTED), accept = false) })
        Thread.sleep(200)
        assertEquals(listOf("minha A", "minha B"), listOf("A", "B").map { r.findSong(it, "")!!.text })
        val (_, again) = r.library.value.syncSongs(listOf(Song(title = "A", text = "de lá A"), Song(title = "B", text = "de lá B")), newId)
        assertEquals(emptyList<SyncKind>(), again.changes.map { it.kind })
    }

    // ---- versão nova do app primeiro ----

    @Test fun `versão nova do app anunciada - as músicas esperam e seguem depois do agora não`() {
        body = """{"songs":[{"title":"A","text":"a1"}]}"""
        appCode = 999_999
        val r = repo()
        r.saveSong(Song(title = "Minha", text = "m"))
        val s = sync(r)
        val job = s.onEnter()
        Thread.sleep(400)
        assertEquals("esperando a decisão sobre o app: nem baixou", 0, hits)
        order.defer(999_999, remember = true)   // "Agora não"
        job.await()
        assertEquals(listOf("Minha", "A"), r.library.value.songs.map { it.title })
        // reabriu no mesmo dia: já recusada, as músicas não esperam
        body = """{"songs":[{"title":"A","text":"a1"},{"title":"B","text":"b1"}]}"""
        sync(r).onEnter().await()
        assertEquals(listOf("Minha", "A", "B"), r.library.value.songs.map { it.title })
    }

    @Test fun `detalhes guardados antes da regra - as de só seções a mais aqui somem da revisão`() {
        val r = repo()
        File(context.filesDir, "sincronia").mkdirs()
        val old = com.juliano.stagebook.data.SyncDetails(1L, listOf(
            com.juliano.stagebook.data.SyncChange("x", "Só seções", SyncKind.PROTECTED, before = "[Verso 1]\nlinha um\nlinha dois", after = "linha um\nlinha dois"),
            com.juliano.stagebook.data.SyncChange("y", "Letra mudou lá", SyncKind.PROTECTED, before = "minha", after = "de lá"),
        ))
        File(context.filesDir, "sincronia/ultima.json").writeText(kotlinx.serialization.json.Json.encodeToString(com.juliano.stagebook.data.SyncDetails.serializer(), old))
        val d = runBlocking { sync(r).details() }!!
        assertEquals(listOf("Letra mudou lá"), d.changes.map { it.title })
    }

    // ---- título, artista e ano ----

    @Test fun `artista corrigido no Drive - aplica e manter a atual volta o anterior`() {
        val lib = Library(songs = listOf(Song("x", "A", author = "Artista Errado", text = "a1", driveHash = lyricsHash("a1"), driveMeta = metaHash("A", "Artista Errado", ""))))
        val (out, r) = lib.syncSongs(listOf(Song(title = "A", author = "Artista Certo", text = "a1")), newId)
        assertEquals("Artista Certo", out.songs.single().author)
        val c = r.changes.single()
        assertEquals(SyncKind.META, c.kind)
        assertEquals("Artista Errado" to "Artista Certo", c.authorFrom to c.authorTo)
    }

    @Test fun `artista mudado pelo usuário - o dele fica e aceitar troca`() {
        body = """{"songs":[{"title":"A","author":"Artista do Drive","text":"a1"}]}"""
        val r = repo()
        r.saveSong(Song(title = "A", author = "Meu Artista", text = "a1", driveHash = lyricsHash("a1"), driveMeta = metaHash("A", "Artista Antigo", "")))
        Thread.sleep(400)
        val s = sync(r)
        s.onEnter().await()
        assertEquals("o dele fica", "Meu Artista", r.findSong("A", "")!!.author)
        val c = runBlocking { s.details() }!!.changes.single()
        assertEquals(SyncKind.META_PROTECTED, c.kind)
        assertTrue(runBlocking { s.keep(c.key) })
        Thread.sleep(200)
        assertEquals("Artista do Drive", r.findSong("A", "")!!.author)
    }

    @Test fun `título trocado no Drive com a mesma letra - não duplica, é a mesma música`() {
        val lib = Library(
            songs = listOf(Song("x", "Titulo Errado", author = "Banda", text = "linha um\nlinha dois", zoom = 1.3f, driveHash = lyricsHash("linha um\nlinha dois"), driveMeta = metaHash("Titulo Errado", "Banda", ""))),
            sets = listOf(Setlist("s", "Show", listOf("x"))),
        )
        val (out, r) = lib.syncSongs(listOf(Song(title = "Título Certo", author = "Banda", text = "linha um\nlinha dois")), newId)
        assertEquals(1, out.songs.size)
        val song = out.songs.single()
        assertEquals("x" to "Título Certo", song.id to song.title)
        assertEquals(1.3f, song.zoom)
        assertEquals(lib.sets, out.sets)
        assertEquals(listOf(SyncKind.META), r.changes.map { it.kind })
        assertTrue("a conferência aceita a troca de título", syncIsSafe(lib, out, r))
    }

    @Test fun `ano vazio aqui é preenchido e aparece nos detalhes`() {
        val lib = Library(songs = listOf(Song("x", "A", author = "Banda", year = "", text = "a1")))
        val (out, r) = lib.syncSongs(listOf(Song(title = "A", author = "Banda", year = "1999", text = "a1")), newId)
        assertEquals("1999", out.songs.single().year)
        assertEquals(listOf(SyncKind.META), r.changes.map { it.kind })
    }

    @Test fun `toda conferência fica registrada, mesmo sem mudança`() {
        body = """{"songs":[{"title":"A","text":"a1"}]}"""
        val r = repo()
        r.saveSong(Song(title = "A", text = "a1"))   // igual à de lá: nada para revisar
        Thread.sleep(400)
        val s = sync(r)
        assertNull("nunca conferiu", s.lastCheck())
        s.onEnter().await()
        assertEquals(com.juliano.stagebook.data.LastCheck.Result.NOTHING_CHANGED, s.lastCheck()!!.result)
        sync(r).onEnter().await()   // reabriu, mesma biblioteca
        assertEquals(com.juliano.stagebook.data.LastCheck.Result.UP_TO_DATE, s.lastCheck()!!.result)
    }
}
