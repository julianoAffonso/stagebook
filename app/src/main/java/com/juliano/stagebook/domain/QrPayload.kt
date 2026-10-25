package com.juliano.stagebook.domain

import com.juliano.stagebook.data.model.Library
import com.juliano.stagebook.data.model.Setlist
import java.io.ByteArrayOutputStream
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Conteúdo do QR do setlist: os bytes `L3` + deflate (sem cabeçalho zlib) do texto UTF-8:
 * ```
 * <cabeçalho do formato>
 * <quantidade de músicas>
 * <nome do setlist>
 * <título>[\t<artista>]
 * …
 * ```
 * Só nome, títulos e, quando o título se repete na biblioteca, o artista: cada aparelho tem as próprias músicas; a chave é
 * o título, e o artista só desempata títulos iguais ("It's My Life" do Bon Jovi e do Talk Talk). Medido em 194 músicas:
 * 28 bytes por música com o artista sempre, 16 sem o desnecessário, e o deflate tira mais ~30%. A quantidade deixa quem
 * lê conferir que recebeu tudo; quem lê é obrigado a avisar, antes de criar o setlist, cada título que não achou.
 */
object QrPayload {
    const val HEADER = "LETRAS3"   // cabeçalho do formato: fixo (QRs já gerados com ele continuam valendo)
    private val PACKED_MARK = byteArrayOf('L'.code.toByte(), '3'.code.toByte())

    /**
     * Teto do QR, em bytes (já compactado). O QR aguenta até 2.331 bytes nesta correção (M), mas denso
     * assim a câmera do TCL (análise em 640x480) não lê com folga; ~1.000 bytes (versão 25, 117 módulos)
     * dão ~80 músicas de título médio. Subir só depois de testar a leitura no TCL.
     */
    const val MAX_BYTES = 1000

    data class Entry(val title: String, val artist: String)

    data class Parsed(val name: String, val songs: List<Entry>)

    private fun clean(s: String) = s.replace('\n', ' ').replace('\t', ' ').trim()

    fun entries(set: Setlist, library: Library): List<Entry> =
        set.items.mapNotNull { id -> library.song(id)?.let { Entry(clean(it.title), clean(it.author)) } }

    /** Títulos (pela chave) que aparecem mais de uma vez na biblioteca: só esses levam o artista. */
    private fun repeatedTitles(library: Library): Set<String> =
        library.songs.groupingBy { TextKey.of(it.title) }.eachCount().filterValues { it > 1 }.keys

    /** O texto do QR (antes de compactar). */
    fun text(set: Setlist, library: Library, name: String = set.name, extra: List<Entry> = emptyList()): String {
        val repeated = repeatedTitles(library)
        val songs = entries(set, library) + extra
        val lines = songs.map { if (it.artist.isEmpty() || TextKey.of(it.title) !in repeated) it.title else it.title + "\t" + it.artist }
        return (listOf(HEADER, songs.size.toString(), clean(name)) + lines).joinToString("\n")
    }

    /** Os bytes que vão no QR: `L3` + deflate do texto. */
    fun pack(text: String): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        return try {
            deflater.setInput(text.toByteArray(Charsets.UTF_8))
            deflater.finish()
            val buf = ByteArray(text.length * 3 + 64)
            val n = deflater.deflate(buf)
            PACKED_MARK + buf.copyOf(n)
        } finally {
            deflater.end()
        }
    }

    fun build(set: Setlist, library: Library): ByteArray = pack(text(set, library))

    /** O setlist como está, com outro nome e/ou mais músicas (ids da biblioteca), ainda cabe no QR. */
    fun fits(set: Setlist, library: Library, name: String = set.name, extraIds: List<String> = emptyList()): Boolean {
        val extra = extraIds.mapNotNull { id -> library.song(id)?.let { Entry(clean(it.title), clean(it.author)) } }
        return pack(text(set, library, name, extra)).size <= MAX_BYTES
    }

    private const val MAX_TEXT = 64 * 1024   // um setlist nunca chega perto; protege de dado estranho

    /** Texto do QR compactado (`L3` + deflate), ou null se os bytes não forem desse formato. */
    fun unpack(bytes: ByteArray?): String? {
        if (bytes == null || bytes.size <= PACKED_MARK.size || bytes[0] != PACKED_MARK[0] || bytes[1] != PACKED_MARK[1]) return null
        val inflater = Inflater(true)
        return try {
            inflater.setInput(bytes, PACKED_MARK.size, bytes.size - PACKED_MARK.size)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) return null   // cortado
                out.write(buf, 0, n)
                if (out.size() > MAX_TEXT) return null
            }
            out.toString(Charsets.UTF_8.name())
        } catch (_: DataFormatException) {
            null
        } finally {
            inflater.end()
        }
    }

    /** QR de setlist de um formato mais novo que este app (o número do cabeçalho maior): pede para atualizar. */
    fun isNewer(text: String): Boolean =
        Regex("^LETRAS(\\d+)\n").find(text)?.groupValues?.get(1)?.toIntOrNull()?.let { it > HEADER.removePrefix("LETRAS").toInt() } == true

    /** Leitura do texto já descompactado (referência para o leitor): null se não for um QR de setlist ou se faltar título. */
    fun parse(payload: String): Parsed? {
        val lines = payload.replace("\r", "").split('\n')
        if (lines.size < 3 || lines[0] != HEADER) return null
        val count = lines[1].trim().toIntOrNull() ?: return null
        val songs = lines.drop(3).filter { it.isNotBlank() }.map { l ->
            val parts = l.split('\t', limit = 2)
            Entry(parts[0], parts.getOrElse(1) { "" })
        }
        if (songs.size != count) return null
        return Parsed(lines[2], songs)
    }
}
