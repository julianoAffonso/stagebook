package com.juliano.stagebook

import com.juliano.stagebook.data.model.Song
import com.juliano.stagebook.domain.SongSearch
import org.junit.Assert.assertEquals
import org.junit.Test

class SongSearchTest {
    private val songs = listOf(
        Song(id = "1", title = "Será", author = "Legião Urbana"),
        Song(id = "2", title = "africa", author = "Toto"),
        Song(id = "3", title = "Billie Jean", author = "Michael Jackson"),
        Song(id = "4", title = "It's My Life", author = "Talk Talk"),
    )

    @Test
    fun `ordem de título ignorando acento e caixa, busca por título ou artista`() {
        val s = SongSearch.of(songs)
        assertEquals(listOf("africa", "Billie Jean", "It's My Life", "Será"), s.find("").map { it.title })
        assertEquals(listOf("Será"), s.find("sera").map { it.title })
        assertEquals(listOf("Será"), s.find("LEGIAO").map { it.title })
        assertEquals(listOf("It's My Life"), s.find("talk").map { it.title })
        assertEquals(emptyList<Song>(), s.find("nada disso"))
    }
}
