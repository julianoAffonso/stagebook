package com.juliano.stagebook

import com.juliano.stagebook.domain.Chords
import com.juliano.stagebook.domain.Tablature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TablatureTest {
    private val tab = "e|---0---3---|\nB|---1---0---|"

    @Test fun `reconhece linha de tab e não confunde com letra`() {
        assertTrue(Tablature.isTabLine("e|---0---3---|"))
        assertTrue(Tablature.isTabLine("|--5h7--5--|"))
        assertFalse(Tablature.isTabLine("Saio cedo pela rua"))
        assertFalse(Tablature.isTabLine("[G]linha"))
    }

    @Test fun `sobe e desce as casas sem tirar as colunas do lugar`() {
        assertEquals("e|---2---5---|", Tablature.shiftLine("e|---0---3---|", 2))
        assertEquals("e|---10--12--|", Tablature.shiftLine("e|---8---10--|", 2))   // 8 vira 10: come um traço
        assertEquals("e|---9---10--|", Tablature.shiftLine("e|---10--11--|", -1))  // 10 vira 9: devolve um traço
        assertEquals("e|---11--|", Tablature.shiftLine("e|---0---|", -1))          // negativa sobe uma oitava (come um traço)
        assertEquals("B|--5h7--|", Tablature.shiftLine("B|--3h5--|", 2))           // a corda B não é casa
    }

    @Test fun `transpor o tom leva a tab junto`() {
        val text = "{key: G}\n[G]letra\n${Tablature.START}\n$tab\n${Tablature.END}"
        val up = Chords.transpose(text, 2)
        assertTrue(up.contains("e|---2---5---|"))
        assertTrue(up.contains("[A]letra"))
    }

    @Test fun `importar - embrulha a tab solta, ou tira`() {
        val raw = "letra\n$tab\nmais letra"
        assertEquals("letra\n${Tablature.START}\n$tab\n${Tablature.END}\nmais letra", Tablature.wrap(raw))
        assertEquals("letra\nmais letra", Tablature.strip(Tablature.wrap(raw)))
        assertTrue(Tablature.hasTab(raw))
    }
}
