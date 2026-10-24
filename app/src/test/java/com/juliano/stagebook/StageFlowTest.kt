package com.juliano.stagebook

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import com.juliano.stagebook.ui.stage.isChordLine
import com.juliano.stagebook.ui.stage.joinLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import com.juliano.stagebook.data.model.Look
import com.juliano.stagebook.ui.stage.StageScreen
import com.juliano.stagebook.ui.stage.StageSong
import com.juliano.stagebook.ui.stage.StageUiState
import com.juliano.stagebook.ui.theme.StagebookTheme
import android.view.KeyEvent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.juliano.stagebook.ui.LocalPedalInput
import com.juliano.stagebook.ui.PedalInput
import com.juliano.stagebook.ui.stage.widths
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Passar páginas e músicas no palco como o pedal faz, inclusive no meio da animação. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-hdpi", application = android.app.Application::class)
class StageFlowTest {
    @get:Rule val compose = createComposeRule()

    private fun stanzas(prefix: String, n: Int) = (1..n).joinToString("\n\n") { s -> (1..4).joinToString("\n") { "$prefix estrofe $s linha $it" } }

    @Test
    fun `da última página de uma música longa para uma música curta`() {
        val state = StageUiState(
            loading = false,
            songs = listOf(StageSong("Longa", "", stanzas("longa", 12), false), StageSong("Curta", "", stanzas("curta", 2), false)),
            look = Look(fontSp = 34f),
        )
        var song by mutableIntStateOf(0)
        var page by mutableIntStateOf(-1)   // última página da longa
        compose.setContent {
            StagebookTheme { StageScreen(state, song, page, { s, p -> song = s; page = p }) }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        song = 1; page = 0                  // pedal: próxima música
        repeat(30) { compose.mainClock.advanceTimeByFrame() }   // atravessa a animação de troca
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("curta estrofe 1 linha 1", substring = true).assertIsDisplayed()
    }

    @Test
    fun `voltar da primeira página cai na última da música anterior sem quebrar`() {
        val state = StageUiState(
            loading = false,
            songs = listOf(StageSong("Curta", "", stanzas("curta", 2), false), StageSong("Longa", "", stanzas("longa", 12), false)),
            look = Look(fontSp = 34f),
        )
        var song by mutableIntStateOf(1)
        var page by mutableIntStateOf(0)
        compose.setContent {
            StagebookTheme { StageScreen(state, song, page, { s, p -> song = s; page = p }) }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        song = 0; page = -1
        repeat(30) { compose.mainClock.advanceTimeByFrame() }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("curta estrofe 2 linha 4", substring = true).assertIsDisplayed()
    }


    @Test
    fun `dois voltar seguidos logo depois de trocar de música não pulam a música do meio`() {
        val state = StageUiState(
            loading = false,
            songs = listOf(
                StageSong("Primeira", "", stanzas("primeira", 2), false),
                StageSong("Longa", "", stanzas("longa", 12), false),
                StageSong("Curta", "", stanzas("curta", 2), false),
            ),
            look = Look(fontSp = 34f),
        )
        var song by mutableIntStateOf(2)
        var page by mutableIntStateOf(0)
        val pedal = PedalInput()
        compose.setContent {
            StagebookTheme {
                CompositionLocalProvider(LocalPedalInput provides pedal) { StageScreen(state, song, page, { s, p -> song = s; page = p }) }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        fun back() = compose.runOnUiThread {
            pedal.dispatch(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PAGE_UP, 0))
            pedal.dispatch(KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_PAGE_UP, 0))
        }
        back()                                   // da Curta para a última página da Longa
        compose.mainClock.advanceTimeByFrame()   // um quadro: a Longa entrou, a contagem de páginas ainda não
        back()                                   // pé rápido: volta uma página da Longa
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals("continua na Longa", 1, song)
        assertTrue("o segundo aperto voltou uma página (não se perdeu): page=$page", page >= 0)
    }

    @Test
    fun `zoom gravado no meio de apertos rápidos não perde aperto do pedal`() {
        val songs = listOf(
            StageSong("Primeira", "", stanzas("primeira", 2), false, id = "p"),
            StageSong("Longa", "", stanzas("longa", 12), false, id = "l"),
            StageSong("Curta", "", stanzas("curta", 2), false, id = "c", zoom = 1f),
        )
        var state by mutableStateOf(StageUiState(loading = false, songs = songs, look = Look(fontSp = 34f)))
        var song by mutableIntStateOf(2)
        var page by mutableIntStateOf(0)
        val pedal = PedalInput()
        compose.setContent {
            StagebookTheme {
                CompositionLocalProvider(LocalPedalInput provides pedal) { StageScreen(state, song, page, { s, p -> song = s; page = p }) }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        fun back() = compose.runOnUiThread {
            pedal.dispatch(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PAGE_UP, 0))
            pedal.dispatch(KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_PAGE_UP, 0))
        }
        back()                                   // da Curta para a última página da Longa
        // o zoom da Curta (pinça antes do pedal) é gravado e a lista de músicas chega de novo
        state = state.copy(songs = songs.map { if (it.id == "c") it.copy(zoom = 1.4f) else it })
        compose.mainClock.advanceTimeByFrame()
        back()                                   // pé rápido: volta uma página da Longa
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals("continua na Longa", 1, song)
        assertTrue("o segundo aperto voltou uma página: page=$page", page >= 0)
    }

    @Test
    fun `pinça muda o zoom só desta música e grava`() {
        val state = StageUiState(
            loading = false,
            songs = listOf(StageSong("Longa", "", stanzas("longa", 12), false, id = "a", zoom = 1f)),
            look = Look(fontSp = 34f),
        )
        var saved: Pair<String, Float>? = null
        compose.setContent {
            StagebookTheme { StageScreen(state, 0, 0, { _, _ -> }, onZoom = { id, z -> saved = id to z }) }
        }
        compose.onRoot().performTouchInput {
            pinch(center - Offset(50f, 0f), center - Offset(250f, 0f), center + Offset(50f, 0f), center + Offset(250f, 0f), durationMillis = 400)
        }
        compose.waitForIdle()   // deixa o fim do gesto ser processado antes de contar o tempo da gravação
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        val (id, zoom) = saved!!
        assertEquals("a", id)
        assertTrue("zoom $zoom deveria ter aumentado", zoom > 1.5f)
        compose.onNodeWithText("Zoom desta música", substring = true).assertIsDisplayed()
    }

    @Test
    fun `segunda e terceira pinça continuam funcionando depois de gravar`() {
        var songs by mutableStateOf(listOf(StageSong("Longa", "", stanzas("longa", 12), false, id = "a", zoom = 1f)))
        val saved = mutableListOf<Float>()
        compose.setContent {
            StagebookTheme {
                // como no app: gravar o zoom volta para a tela pela música atualizada
                StageScreen(StageUiState(false, songs, look = Look(fontSp = 34f)), 0, 0, { _, _ -> }, onZoom = { _, z ->
                    saved += z
                    songs = songs.map { it.copy(zoom = z) }
                })
            }
        }
        fun pinch(out: Boolean) {
            compose.onRoot().performTouchInput {
                val near = 60f
                val far = 160f
                val (a, b) = if (out) near to far else far to near
                pinch(center - Offset(a, 0f), center - Offset(b, 0f), center + Offset(a, 0f), center + Offset(b, 0f), durationMillis = 300)
            }
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(1_000)
            compose.waitForIdle()
        }
        pinch(out = true)
        pinch(out = true)
        pinch(out = false)
        assertEquals("três pinças, três gravações: " + saved, 3, saved.size)
        assertTrue("segunda pinça aumenta: " + saved, saved[1] > saved[0])
        assertTrue("terceira pinça diminui: " + saved, saved[2] < saved[1])
    }

    private fun swipeStage(page0: Int, swipe: androidx.compose.ui.test.TouchInjectionScope.() -> Unit): Int {
        val state = StageUiState(false, listOf(StageSong("Longa", "", stanzas("longa", 12), false)), look = Look(fontSp = 34f))
        var page by mutableIntStateOf(page0)
        compose.setContent { StagebookTheme { StageScreen(state, 0, page, { _, p -> page = p }) } }
        compose.waitForIdle()
        compose.onRoot().performTouchInput(swipe)
        compose.waitForIdle()
        return page
    }

    @Test fun `a seta de voltar pergunta e sai do palco`() {
        val state = StageUiState(false, listOf(StageSong("Longa", "", stanzas("longa", 12), false)), look = Look(fontSp = 34f))
        var back = 0
        compose.setContent { StagebookTheme { StageScreen(state, 0, 0, { _, _ -> }, onBack = { back++ }) } }
        compose.onNodeWithContentDescription("Voltar").performClick()
        compose.waitForIdle()
        assertEquals("pergunta antes de sair", 0, back)
        compose.onNodeWithText("Cancelar").performClick()
        compose.waitForIdle()
        assertEquals("cancelar fica no palco", 0, back)
        compose.onNodeWithContentDescription("Voltar").performClick()
        compose.onNodeWithText("Sair").performClick()
        compose.waitForIdle()
        assertEquals(1, back)
    }

    @Test fun `arrastar para a esquerda avança a página`() {
        assertEquals(1, swipeStage(0) { swipeLeft(startX = centerX + width / 4f, endX = centerX - width / 4f) })
    }

    @Test fun `arrastar para a direita volta a página`() {
        assertEquals(0, swipeStage(1) { swipeRight(startX = centerX - width / 4f, endX = centerX + width / 4f) })
    }

    @Test fun `arrastar pouco não vira a página`() {
        assertEquals(1, swipeStage(1) { swipeLeft(startX = centerX + 20f, endX = centerX - 20f) })
    }

    @Test fun `arrastar na vertical não vira a página`() {
        assertEquals(1, swipeStage(1) { swipeUp(startY = centerY + height / 4f, endY = centerY - height / 4f) })
    }

    @Test
    fun `pedal com o pé apoiado (tecla repetida) vira só uma página`() {
        val state = StageUiState(false, listOf(StageSong("Longa", "", stanzas("longa", 12), false)), look = Look(fontSp = 34f))
        var page by mutableIntStateOf(0)
        val pedal = PedalInput()
        compose.setContent {
            StagebookTheme {
                CompositionLocalProvider(LocalPedalInput provides pedal) { StageScreen(state, 0, page, { _, p -> page = p }) }
            }
        }
        compose.waitForIdle()
        compose.runOnUiThread {
            repeat(6) { r -> pedal.dispatch(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PAGE_DOWN, r)) }
            pedal.dispatch(KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_PAGE_DOWN, 0))
        }
        compose.waitForIdle()
        assertEquals(1, page)
    }

    @Test
    fun `pinça e o pedal troca de música antes de gravar - o zoom é gravado mesmo assim`() {
        val state = StageUiState(
            false,
            listOf(StageSong("Longa", "", stanzas("longa", 12), false, id = "a", zoom = 1f), StageSong("Curta", "", stanzas("curta", 2), false, id = "b")),
            look = Look(fontSp = 34f),
        )
        var song by mutableIntStateOf(0)
        var saved: Pair<String, Float>? = null
        compose.setContent {
            StagebookTheme { StageScreen(state, song, 0, { s, _ -> song = s }, onZoom = { id, z -> saved = id to z }) }
        }
        compose.onRoot().performTouchInput {
            pinch(center - Offset(50f, 0f), center - Offset(250f, 0f), center + Offset(50f, 0f), center + Offset(250f, 0f), durationMillis = 300)
        }
        compose.waitForIdle()
        song = 1                                   // pedal antes dos 700 ms da gravação
        compose.waitForIdle()
        val (id, zoom) = saved!!
        assertEquals("a", id)
        assertTrue("zoom $zoom deveria ter aumentado", zoom > 1.2f)
    }

    @Test
    fun `larguras medidas de uma vez são as mesmas medidas uma a uma`() {
        val texts = listOf("Hazard", "linha bem mais comprida da letra", "É", "palavra-composta", "x").map(::AnnotatedString)
        compose.setContent {
            val measurer = rememberTextMeasurer()
            val style = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold)
            val batch = widths(measurer, texts, style)
            val one = texts.map { measurer.measure(it, style, softWrap = false, maxLines = 1).size.width }
            assertEquals(one, batch)
        }
        compose.waitForIdle()
    }

    @Test
    fun `alturas medidas numa passada são as mesmas medidas linha a linha`() {
        // letra inventada com títulos de seção (cor e tamanho próprios), linhas vazias e uma linha longa
        val text = "[Verso 1]\nprimeira linha curta\numa linha bem mais comprida que as outras para ocupar a largura toda da coluna e quebrar\n\n" +
            "[Refrão]\n[G]refrão [D]aqui com acordes em [Em]cima\n[C] [G]\n\n\nfim"
        val chords = com.juliano.stagebook.ui.stage.ChordLook(true, 0.8f, androidx.compose.ui.graphics.Color.Yellow)
        val lines = com.juliano.stagebook.ui.stage.stageLines(text, com.juliano.stagebook.ui.stage.SectionNames(null), androidx.compose.ui.graphics.Color.Cyan, true, chords).map { it.text }
        compose.setContent {
            val measurer = rememberTextMeasurer()
            val style = TextStyle(
                fontSize = 34.sp,
                lineHeight = 42.5.sp,
                lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                    androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                    androidx.compose.ui.text.style.LineHeightStyle.Trim.None,
                ),
            )
            assertTrue("a linha com acordes tem de estar no teste", lines.any { it.getStringAnnotations(0, it.length).isNotEmpty() })
            for (width in listOf(1800, 600)) {
                val one = lines.map { l ->
                    // sozinha, do mesmo jeito que o palco monta a linha (linha com acordes tem altura maior)
                    val alone = joinLines(listOf(l))
                    measurer.measure(alone, style, constraints = androidx.compose.ui.unit.Constraints(maxWidth = width)).size.height
                }
                val batch = com.juliano.stagebook.ui.stage.lineHeights(measurer, lines, style, width)
                assertEquals("largura $width", one, batch.map { it.height })
                assertEquals(lines.map { it.isEmpty() }, batch.map { it.blank })
            }
        }
        compose.waitForIdle()
    }

    // o Robolectric comum não quebra linha de texto: este precisa do desenho nativo (como as capturas)
    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun `linha com acordes que quebra vira pedaços e só os com acorde guardam o espaço`() {
        val chords = com.juliano.stagebook.ui.stage.ChordLook(true, 0.8f, androidx.compose.ui.graphics.Color.Yellow)
        val sourced = com.juliano.stagebook.ui.stage.stageLines(
            "[G]uma linha comprida com acorde só no começo e que vai quebrar",
            com.juliano.stagebook.ui.stage.SectionNames(null), androidx.compose.ui.graphics.Color.Cyan, true, chords,
        )
        compose.setContent {
            val measurer = rememberTextMeasurer()
            val style = TextStyle(fontSize = 34.sp, lineHeight = 42.5.sp)
            val wide = com.juliano.stagebook.ui.stage.splitWrappedChordLines(measurer, sourced, style, 5000)
            assertTrue("sem quebra, a mesma lista", wide === sourced)
            val narrow = com.juliano.stagebook.ui.stage.splitWrappedChordLines(measurer, sourced, style, 400)
            assertTrue("quebrou em pedaços: ${narrow.size}", narrow.size > 1)
            assertTrue(narrow.first().text.isChordLine())
            assertTrue(narrow.drop(1).none { it.text.isChordLine() })
            assertEquals(setOf(0), narrow.map { it.source }.toSet())   // todos apontam para a mesma linha da letra
            assertEquals(sourced.single().text.text.replace(" ", ""), narrow.joinToString("") { it.text.text }.replace(" ", ""))
        }
        compose.waitForIdle()
    }

    // ---- editar a partir do palco ----
    private val lyric = "[Refrão]\nprimeira linha\nsegunda linha\n\nterceira linha"

    private fun stageWithEdit(onEdit: (String, Int) -> Unit, onPosition: (Int, Int) -> Unit = { _, _ -> }) = compose.setContent {
        StagebookTheme {
            StageScreen(
                StageUiState(loading = false, songs = listOf(StageSong("Hazard", "", lyric, false, id = "s1")), look = Look(fontSp = 34f)),
                0, 0, onPosition, onEdit = onEdit,
            )
        }
    }

    /** Segura no meio da 2ª linha mostrada, pela posição dela no texto desenhado. */
    private fun holdSecondLine() {
        val node = compose.onNodeWithText("primeira linha", substring = true)
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        node.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action!!(layouts)
        val l = layouts.first()
        node.performTouchInput { longClick(Offset(24f, (l.getLineTop(1) + l.getLineBottom(1)) / 2)) }
    }

    @Test
    fun `segurar uma linha oferece editar e abre o editor nela`() {
        val edits = mutableListOf<Pair<String, Int>>()
        stageWithEdit({ id, c -> edits += id to c })
        holdSecondLine()
        compose.onNodeWithText("Editar esta linha").assertIsDisplayed().performClick()
        // "[Refrão]\n" tem 9 caracteres: a 2ª linha mostrada ("primeira linha") começa ali
        assertEquals(listOf("s1" to 9), edits)
    }

    @Test
    fun `com o botão de editar aberto, tocar fora só fecha (não vira a página nem abre o editor)`() {
        val edits = mutableListOf<Int>()
        val moves = mutableListOf<Int>()
        stageWithEdit({ _, c -> edits += c }, { s, _ -> moves += s })
        holdSecondLine()
        compose.onNodeWithText("Editar esta linha").assertIsDisplayed()
        compose.onNodeWithText("primeira linha", substring = true).performTouchInput { click(Offset(width * 0.9f, 10f)) }
        compose.onNodeWithText("Editar esta linha").assertDoesNotExist()
        assertTrue(edits.isEmpty())
        assertTrue(moves.isEmpty())
    }

    @Test
    fun `lápis do topo abre o editor no começo da página que está na tela`() {
        val text = stanzas("longa", 12)
        var cursor = -2
        compose.setContent {
            StagebookTheme {
                StageScreen(
                    StageUiState(loading = false, songs = listOf(StageSong("Longa", "", text, false, id = "s1")), look = Look(fontSp = 34f)),
                    0, 1, { _, _ -> }, onEdit = { _, c -> cursor = c },
                )
            }
        }
        compose.onNodeWithContentDescription("Editar a letra nesta página").performClick()
        assertTrue(cursor > 0)
        val firstLine = text.substring(cursor).substringBefore('\n')
        compose.onNodeWithText(firstLine, substring = true).assertIsDisplayed()
    }
}
