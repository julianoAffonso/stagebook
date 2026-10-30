package br.app.stagebook

import br.app.stagebook.data.LibraryCodec
import br.app.stagebook.domain.QrPayload
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Compatibilidade de importação: o arquivo de biblioteca exportado no formato v1 e o QR de setlist no formato atual
 * abrem no Stagebook. Mudar o formato da biblioteca ou do QR não pode quebrar estes testes.
 */
class ImportCompatTest {
    @Test fun `arquivo de biblioteca no formato v1 importa`() {
        // formato exato do arquivo v1 (LibraryCodec.encodeSongs, encodeDefaults + prettyPrint), letra fictícia
        val exported = """
            {
                "version": 1,
                "songs": [
                    {
                        "id": "k3x9",
                        "title": "Canção de Teste",
                        "author": "Banda Fictícia",
                        "year": "1999",
                        "text": "[Refrão]\nlinha um\nlinha dois",
                        "zoom": 1.2,
                        "driveHash": "ab12",
                        "declinedHash": "",
                        "driveMeta": "",
                        "declinedMeta": ""
                    }
                ]
            }
        """.trimIndent()
        val song = LibraryCodec.decodeSongs(exported).single()
        assertEquals("Canção de Teste", song.title)
        assertEquals("Banda Fictícia", song.author)
        assertEquals("1999", song.year)
        assertEquals("[Refrão]\nlinha um\nlinha dois", song.text)
        assertEquals(1.2f, song.zoom)
    }

    @Test fun `QR de setlist no formato atual é lido`() {
        val text = "LETRAS3\n2\nShow de sábado\nCanção A\nCanção B\tArtista B"
        // bytes do QR: "L3" + deflate sem cabeçalho zlib
        val bytes = QrPayload.pack(text)
        assertEquals("L3", String(bytes, 0, 2, Charsets.ISO_8859_1))
        val inflater = java.util.zip.Inflater(true).apply { setInput(bytes, 2, bytes.size - 2) }
        val out = ByteArray(4096)
        val n = inflater.inflate(out)
        val read = QrPayload.parse(String(out, 0, n, Charsets.UTF_8))
        assertEquals("Show de sábado", read?.name)
        assertEquals(listOf("Canção A", "Canção B"), read?.songs?.map { it.title })
    }

    @Test fun `a faixa do aparelho não vai na exportação`() {
        val lib = br.app.stagebook.data.model.Library(songs = listOf(br.app.stagebook.data.model.Song(id = "a", title = "T", text = "x", track = "a-1.ogg")))
        val out = LibraryCodec.encodeSongs(lib.songs.map { it.copy(track = "") })
        assertEquals("", LibraryCodec.decodeSongs(out).single().track)
    }
}
