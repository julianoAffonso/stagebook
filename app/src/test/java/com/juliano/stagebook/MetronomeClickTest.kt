package com.juliano.stagebook

import com.juliano.stagebook.domain.MetronomeClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Som do metrônomo (T33d): cada clique na amostra exata, sem escorregar, e o 1 do compasso mais agudo. */
class MetronomeClickTest {
    private val normal = ShortArray(10) { 100 }
    private val accent = ShortArray(10) { 200 }

    @Test fun `120 BPM a 48 mil amostras - um tempo a cada 24 mil amostras`() =
        assertEquals(24_000L, MetronomeClick.samplesPerBeat(120))

    @Test fun `os cliques caem na amostra certa mesmo atravessando pedaços`() {
        val spb = 50L
        val all = ShortArray(400)
        // em pedaços de 37 amostras (como o áudio escreve), juntando tudo
        var pos = 0L
        while (pos < all.size) {
            val chunk = ShortArray(minOf(37, all.size - pos.toInt()))
            MetronomeClick.render(chunk, pos, spb, normal, accent)
            chunk.copyInto(all, pos.toInt())
            pos += chunk.size
        }
        for (beat in 0 until 8) {
            val at = beat * 50
            assertEquals(if (beat % 4 == 0) 200.toShort() else 100.toShort(), all[at])
            assertEquals(0.toShort(), all[at + 10])   // o clique acabou: silêncio até o próximo
        }
    }

    @Test fun `o clique agudo e o normal são diferentes e curtos`() {
        val a = MetronomeClick.click(true)
        val n = MetronomeClick.click(false)
        assertTrue(a.size < MetronomeClick.RATE / 10)
        assertTrue(!a.contentEquals(n))
    }
}
