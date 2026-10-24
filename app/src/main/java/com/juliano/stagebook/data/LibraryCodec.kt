package com.juliano.stagebook.data

import com.juliano.stagebook.data.model.Library
import com.juliano.stagebook.data.model.Song
import com.juliano.stagebook.domain.LyricsCompare
import com.juliano.stagebook.domain.SongMatch
import com.juliano.stagebook.domain.TextKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/** Leitura e gravação do letras.json e do arquivo de biblioteca ({"songs":[...]}) usado para importar/exportar. */
object LibraryCodec {
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true    // valor desconhecido (ex.: idioma de seção novo) cai no padrão
        encodeDefaults = true
        prettyPrint = true
        prettyPrintIndent = " "
    }

    @Serializable
    private data class SongsFile(val version: Int = 1, val songs: List<Song> = emptyList())

    fun decode(text: String, newId: () -> String): Library = withIds(json.decodeFromString<Library>(text), newId)

    private fun withIds(lib: Library, newId: () -> String): Library {
        return lib.copy(
            songs = lib.songs.map { if (it.id.isEmpty()) it.copy(id = newId()) else it },
            sets = lib.sets.map { if (it.id.isEmpty()) it.copy(id = newId()) else it },
        )
    }

    fun encode(library: Library): String = json.encodeToString(Library.serializer(), library)

    /** Grava direto no arquivo, sem montar o texto de ~1 MB na memória (o Fire tem 2 GB). */
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    fun encodeTo(library: Library, out: OutputStream) = json.encodeToStream(Library.serializer(), library, out)

    /** Lê direto do arquivo (abrir o app sem segurar o texto inteiro antes de decodificar). */
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    fun decode(input: InputStream, newId: () -> String): Library = withIds(json.decodeFromStream(Library.serializer(), input), newId)

    fun decodeSongs(text: String): List<Song> = json.decodeFromString<SongsFile>(text).songs

    fun encodeSongs(songs: List<Song>): String =
        json.encodeToString(SongsFile.serializer(), SongsFile(songs = songs.sortedBy { TextKey.of(it.title) }))
}

/** [lyricsChanged]: das atualizadas, quantas tiveram a letra trocada pela do arquivo. */
data class ImportResult(val added: Int, val replaced: Int, val lyricsChanged: Int = 0)

/**
 * Importar uma biblioteca sem duplicar: a mesma música (título + artista, ver SongMatch) é atualizada,
 * as outras entram novas. O zoom do palco que a música já tinha é mantido.
 */
fun Library.mergeSongs(incoming: List<Song>, newId: () -> String): Pair<Library, ImportResult> {
    val songs = songs.toMutableList()
    var added = 0
    var replaced = 0
    var lyricsChanged = 0
    for (song in incoming) {
        if (song.title.isBlank()) continue
        val i = songs.indexOfFirst { SongMatch.same(it.title, it.author, song.title, song.author) }
        if (i >= 0) {
            if (songs[i].text != song.text) lyricsChanged++
            // driveHash fica o de antes: letra trocada à mão pelo arquivo não volta a ser "a do Drive"
            songs[i] = song.copy(id = songs[i].id, zoom = songs[i].zoom, driveHash = songs[i].driveHash, author = song.author.ifBlank { songs[i].author }, year = song.year.ifBlank { songs[i].year })
            replaced++
        } else {
            songs += song.copy(id = newId(), driveHash = "")
            added++
        }
    }
    return copy(songs = songs) to ImportResult(added, replaced, lyricsChanged)
}

/**
 * O que a sincronia fez. [updated]: letra trocada pela do Drive (não editada aqui); [sectionsAdded]:
 * a daqui não tinha seções e a do Drive, com as mesmas palavras, tem; [keptSections]: as seções foram
 * mexidas aqui e ficaram; [keptEdited]: as palavras foram editadas aqui e ficaram.
 */
data class SyncResult(
    val added: Int = 0,
    val updated: Int = 0,
    val sectionsAdded: Int = 0,
    val keptSections: Int = 0,
    val keptEdited: Int = 0,
    val changes: List<SyncChange> = emptyList(),   // o que mudou, música a música (detalhes e desfazer)
) {
    /** Músicas em que o usuário tem a versão dele e a atualização trouxe outra (para ele decidir). */
    val protectedCount: Int get() = changes.count { it.kind == SyncKind.PROTECTED || it.kind == SyncKind.META_PROTECTED }
    val metaCount: Int get() = changes.count { it.kind == SyncKind.META }
}

/**
 * PROTECTED: a letra daqui foi mudada no aparelho e ficou; a da atualização é outra (o usuário escolhe).
 * META: título, artista ou ano atualizados; META_PROTECTED: os dados daqui foram mudados e ficaram.
 */
/**
 * O que o aviso das músicas conta (somado entre atualizações ainda não vistas): novas, com a letra
 * atualizada, que receberam seções, em que o usuário tem a versão dele, e com dados atualizados.
 */
data class SyncNotice(val added: Int = 0, val updated: Int = 0, val sections: Int = 0, val mine: Int = 0, val meta: Int = 0) {
    val any: Boolean get() = added + updated + sections + mine + meta > 0

    operator fun plus(o: SyncNotice) = SyncNotice(added + o.added, updated + o.updated, sections + o.sections, mine + o.mine, meta + o.meta)

    fun encode() = "$added,$updated,$sections,$mine,$meta"

    companion object {
        fun decode(s: String?): SyncNotice? {
            val n = s?.split(',')?.mapNotNull { it.toIntOrNull() } ?: return null
            return if (n.size < 3) null else SyncNotice(n[0], n[1], n[2], n.getOrElse(3) { 0 }, n.getOrElse(4) { 0 })
        }
    }
}

@Serializable
enum class SyncKind { ADDED, UPDATED, SECTIONS, PROTECTED, META, META_PROTECTED }

/**
 * Uma música que a sincronia mudou. [before]/[after]: letra antes e depois (só as que já existiam);
 * desfazer volta para [before] se a letra ainda for [after] (não passa por cima de uma edição posterior).
 */
@Serializable
data class SyncChange(
    val songId: String,
    val title: String,
    val kind: SyncKind,
    val before: String = "",
    val after: String = "",
    val reverted: Boolean = false,   // o usuário manteve a versão dele (a letra voltou a [before])
    val kept: Boolean = false,       // o usuário ficou com a atualização
    val removed: Boolean = false,    // música nova que o usuário não quis
    // título, artista e ano antes → depois (vazio nos dois = não mudou) — só em META/META_PROTECTED
    val titleFrom: String = "",
    val titleTo: String = "",
    val authorFrom: String = "",
    val authorTo: String = "",
    val yearFrom: String = "",
    val yearTo: String = "",
) {
    /** Identifica o cartão (a mesma música pode ter um da letra e outro dos dados). */
    val key: String get() = "$songId:$kind"
    val decided: Boolean get() = reverted || kept || removed
}

/** Detalhes da última sincronia. [rebuilt]: refeitos da cópia de antes (sincronia anterior a este registro). */
@Serializable
data class SyncDetails(val at: Long = 0, val changes: List<SyncChange> = emptyList(), val rebuilt: Boolean = false)

/** Impressão digital da letra, sem contar formatação (ver [LyricsCompare.canonical]). */
fun lyricsHash(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(LyricsCompare.canonical(text).toByteArray()).take(12).joinToString("") { "%02x".format(it) }

/** Marca de título, artista e ano (para saber se os daqui foram mudados desde que vieram do Drive). */
fun metaHash(title: String, author: String, year: String): String =
    MessageDigest.getInstance("SHA-256").digest("${title.trim()}\u0000${author.trim()}\u0000${year.trim()}".toByteArray())
        .take(12).joinToString("") { "%02x".format(it) }

private fun Song.meta() = metaHash(title, author, year)

/**
 * Sincronia automática com a biblioteca do Drive, decidida pelo conteúdo de cada música; nunca perde
 * o que foi feito no aparelho:
 * - nova no Drive: entra; com o título trocado no Drive e a mesma letra de uma daqui que sumiu de lá: é
 *   a mesma música com outro título (não duplica);
 * - letra: só formatação diferente fica a daqui; mesmas palavras, seções diferentes: a daqui sem nenhuma
 *   seção recebe as do Drive, com seções ficam as daqui; palavras diferentes: troca só se a daqui não foi
 *   mexida desde que veio do Drive ([Song.driveHash]), senão o usuário decide (PROTECTED);
 * - título, artista e ano: vazio aqui é preenchido; diferente, troca se os daqui não foram mexidos desde
 *   que vieram do Drive ([Song.driveMeta]), senão o usuário decide (META_PROTECTED).
 * Zoom, id, setlists, aparência e músicas que só estão aqui ficam.
 */
fun Library.syncSongs(incoming: List<Song>, newId: () -> String): Pair<Library, SyncResult> {
    val songs = songs.toMutableList()
    var r = SyncResult()
    val changes = ArrayList<SyncChange>()
    // chaves das músicas daqui montadas uma vez (título → posições), em vez de normalizar tudo de novo
    // para cada música do Drive
    val byTitle = HashMap<String, MutableList<Int>>()
    val artistKeys = ArrayList<String>()
    fun index(i: Int) {
        byTitle.getOrPut(TextKey.of(songs[i].title)) { mutableListOf() } += i
        artistKeys += TextKey.of(songs[i].author)
    }
    songs.indices.forEach(::index)
    fun find(song: Song): Int {
        val artist = TextKey.of(song.author)
        return byTitle[TextKey.of(song.title)]?.firstOrNull { SongMatch.sameArtistKey(artistKeys[it], artist) } ?: -1
    }
    // título trocado no Drive: a de lá sem par aqui e uma daqui que sumiu de lá, com a mesma letra
    val found = incoming.map { if (it.title.isBlank()) -1 else find(it) }
    val gone = songs.indices.toMutableSet().apply { removeAll(found.toSet()) }
    val renamed = HashMap<Int, Int>()   // posição na lista do Drive → posição aqui
    incoming.forEachIndexed { k, song ->
        if (found[k] >= 0 || song.title.isBlank() || gone.isEmpty()) return@forEachIndexed
        val words = LyricsCompare.words(song.text).takeIf { it.isNotEmpty() } ?: return@forEachIndexed
        gone.firstOrNull { LyricsCompare.words(songs[it].text) == words }?.let { renamed[k] = it; gone -= it }
    }
    incoming.forEachIndexed { k, song ->
        if (song.title.isBlank()) return@forEachIndexed
        val hash = lyricsHash(song.text)
        val i = renamed[k] ?: find(song)
        if (i < 0) {
            songs += song.copy(id = newId(), zoom = 1f, driveHash = hash, driveMeta = song.meta())
            changes += SyncChange(songs.last().id, song.title, SyncKind.ADDED)
            index(songs.lastIndex)
            r = r.copy(added = r.added + 1)
            return@forEachIndexed
        }
        val local = songs[i]
        // só precisa da marca da letra daqui quando as duas diferem de verdade
        val untouched by lazy { local.driveHash.isNotEmpty() && lyricsHash(local.text) == local.driveHash }
        // null = fica a letra daqui, sem passar a acompanhar o Drive
        val diff = LyricsCompare.diff(local.text, song.text)
        val take: String? = when (diff) {
            LyricsCompare.Diff.SAME, LyricsCompare.Diff.FORMAT -> local.text
            LyricsCompare.Diff.SECTIONS -> when {
                untouched || !LyricsCompare.hasSections(local.text) -> song.text.also { r = r.copy(sectionsAdded = r.sectionsAdded + 1) }
                else -> null.also { r = r.copy(keptSections = r.keptSections + 1) }
            }
            LyricsCompare.Diff.WORDS ->
                if (untouched) song.text.also { r = r.copy(updated = r.updated + 1) }
                else null.also { r = r.copy(keptEdited = r.keptEdited + 1) }
        }
        // dados: o título só muda numa troca de título detectada (fora isso ele é a chave e já é igual)
        val toTitle = if (i == renamed[k]) song.title else local.title
        val toAuthor = song.author.ifBlank { local.author }
        val toYear = song.year.ifBlank { local.year }
        val metaDiffers = toTitle != local.title || toAuthor != local.author || toYear != local.year
        val onlyFilling = (toTitle == local.title) && (toAuthor == local.author || local.author.isBlank()) && (toYear == local.year || local.year.isBlank())
        val metaUntouched = local.driveMeta.isNotEmpty() && local.meta() == local.driveMeta
        val takeMeta = metaDiffers && (metaUntouched || onlyFilling)
        val driveMeta = metaHash(toTitle, toAuthor, toYear)
        val title = if (takeMeta) toTitle else local.title
        val author = if (takeMeta) toAuthor else local.author
        val year = if (takeMeta) toYear else local.year
        songs[i] = local.copy(
            text = take ?: local.text,
            // mantida por edição: guarda a marca antiga, assim continua valendo como editada
            driveHash = if (take != null) hash else local.driveHash,
            title = title,
            author = author,
            year = year,
            driveMeta = if (takeMeta || !metaDiffers) driveMeta else local.driveMeta,
        )
        if (songs[i].author != local.author) artistKeys[i] = TextKey.of(songs[i].author)
        if (title != local.title) byTitle.getOrPut(TextKey.of(title)) { mutableListOf() } += i
        if (take != null && take != local.text) {
            val kind = if (LyricsCompare.words(local.text) == LyricsCompare.words(take)) SyncKind.SECTIONS else SyncKind.UPDATED
            changes += SyncChange(local.id, title, kind, before = local.text, after = take)
        } else if (take == null && local.declinedHash != hash && !onlyLocalSections(local.text, song.text)) {
            // a daqui fica, mas o usuário fica sabendo e pode aceitar a atualizada (a não ser que já recusou esta)
            changes += SyncChange(local.id, local.title, SyncKind.PROTECTED, before = local.text, after = song.text)
        }
        if (metaDiffers && (takeMeta || local.declinedMeta != driveMeta)) {
            changes += SyncChange(
                local.id, title, if (takeMeta) SyncKind.META else SyncKind.META_PROTECTED,
                titleFrom = local.title, titleTo = toTitle,
                authorFrom = local.author, authorTo = toAuthor,
                yearFrom = local.year, yearTo = toYear,
            )
        }
    }
    return copy(songs = songs) to r.copy(changes = changes)
}

/**
 * Conferência antes de gravar a sincronia: toda música daqui continua, com o mesmo zoom e letra não
 * vazia, e o mesmo título (a não ser numa troca de título da junção); só entram as novas contadas;
 * setlists e aparência intactos; letras trocadas não passam das que a junção decidiu trocar.
 */
fun syncIsSafe(old: Library, new: Library, r: SyncResult): Boolean {
    val byId = new.songs.associateBy { it.id }
    val retitled = r.changes.filter { it.kind == SyncKind.META && it.titleFrom != it.titleTo }.map { it.songId }.toSet()
    val kept = old.songs.all { o ->
        val n = byId[o.id] ?: return@all false
        (n.title == o.title || o.id in retitled) && n.zoom == o.zoom && (n.text == o.text || n.text.isNotBlank())
    }
    val changed = old.songs.count { byId[it.id]?.text != it.text }
    return kept && new.sets == old.sets && new.look == old.look &&
        new.songs.size == old.songs.size + r.added && changed <= r.updated + r.sectionsAdded
}

/**
 * Só seções a mais no aparelho e a versão de lá sem nenhuma (mesmas palavras): a daqui é melhor e não há o
 * que decidir. Vale na junção e também nos detalhes guardados de uma atualização anterior a esta regra.
 */
internal fun onlyLocalSections(local: String, drive: String): Boolean =
    LyricsCompare.diff(local, drive) == LyricsCompare.Diff.SECTIONS && !LyricsCompare.hasSections(drive)
