package br.app.stagebook

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
import br.app.stagebook.ui.stage.isChordLine
import br.app.stagebook.ui.stage.joinLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import br.app.stagebook.data.model.Look
import br.app.stagebook.ui.stage.StageScreen
import br.app.stagebook.ui.stage.StageSong
import br.app.stagebook.ui.stage.StageUiState
import br.app.stagebook.ui.theme.StagebookTheme
import android.view.KeyEvent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import br.app.stagebook.ui.LocalPedalInput
import br.app.stagebook.ui.PedalInput
import br.app.stagebook.ui.stage.widths
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
@Config(sdk = [35], qualifiers = "pt-rBR-w1280dp-h800dp-land-hdpi", application = android.app.Application::class)
class StageFlowTest {
    @get:Rule val compose = createComposeRule()

    private fun stanzas(prefix: String, n: Int) = (1..n).joinToString("\n\n") { s -> (1..4).joinToString("\n") { "$prefix estrofe $s linha $it" } }

    @Test
    fun `from the last page of a long song to a short song`() {
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
    fun `going back from the first page lands on the last page of the previous song without breaking`() {
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
    fun `two backs in a row right after changing songs do not skip the middle song`() {
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
    fun `zoom saved during fast presses does not lose a pedal press`() {
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
    fun `pinch changes the zoom of this song only and saves`() {
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
    fun `second and third pinch keep working after saving`() {
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

    @Test fun `the back arrow asks and leaves the stage`() {
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

    @Test fun `dragging left goes to the next page`() {
        assertEquals(1, swipeStage(0) { swipeLeft(startX = centerX + width / 4f, endX = centerX - width / 4f) })
    }

    @Test fun `dragging right goes to the previous page`() {
        assertEquals(0, swipeStage(1) { swipeRight(startX = centerX - width / 4f, endX = centerX + width / 4f) })
    }

    @Test fun `a short drag does not turn the page`() {
        assertEquals(1, swipeStage(1) { swipeLeft(startX = centerX + 20f, endX = centerX - 20f) })
    }

    @Test fun `a vertical drag does not turn the page`() {
        assertEquals(1, swipeStage(1) { swipeUp(startY = centerY + height / 4f, endY = centerY - height / 4f) })
    }

    // pedal Play/pause: só dá play com o modo player ligado e a música com backing track; nunca vira a página
    private fun pressPlayPause(look: Look, track: String): Pair<Int, Int> {
        val state = StageUiState(false, listOf(StageSong("Longa", "", stanzas("longa", 12), false, id = "1", track = track)), look = look)
        var page by mutableIntStateOf(0)
        var toggles = 0
        val pedal = PedalInput()
        compose.setContent {
            StagebookTheme {
                CompositionLocalProvider(LocalPedalInput provides pedal) {
                    StageScreen(state, 0, page, { _, p -> page = p }, onTogglePlay = { toggles++ })
                }
            }
        }
        compose.waitForIdle()
        compose.runOnUiThread {
            pedal.dispatch(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0))
            pedal.dispatch(KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0))
        }
        compose.waitForIdle()
        return toggles to page
    }

    @Test fun `Play-pause pedal plays the backing track in player mode`() {
        assertEquals(1 to 0, pressPlayPause(Look(fontSp = 34f, playerMode = true), "1.ogg"))
    }

    @Test fun `Play-pause pedal does nothing without player mode`() {
        assertEquals(0 to 0, pressPlayPause(Look(fontSp = 34f, playerMode = false), "1.ogg"))
    }

    @Test fun `Play-pause pedal does nothing on a song without a backing track`() {
        assertEquals(0 to 0, pressPlayPause(Look(fontSp = 34f, playerMode = true), ""))
    }

    @Test fun `media play-pause key becomes Play-pause and can be changed in settings`() {
        assertEquals(br.app.stagebook.domain.PedalAction.PLAY_PAUSE, br.app.stagebook.domain.PedalAction.of(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        val custom = br.app.stagebook.domain.PedalAction.assign(emptyMap(), KeyEvent.KEYCODE_B, br.app.stagebook.domain.PedalAction.PLAY_PAUSE)
        assertEquals(br.app.stagebook.domain.PedalAction.PLAY_PAUSE, br.app.stagebook.domain.PedalAction.of(KeyEvent.KEYCODE_B, custom))
    }

    @Test
    fun `pedal held down (repeated key) turns only one page`() {
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
    fun `pinch and the pedal changes song before saving - the zoom is saved anyway`() {
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
    fun `widths measured at once are the same as measured one by one`() {
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
    fun `heights measured in one pass are the same as measured line by line`() {
        // letra inventada com títulos de seção (cor e tamanho próprios), linhas vazias e uma linha longa
        val text = "[Verso 1]\nprimeira linha curta\numa linha bem mais comprida que as outras para ocupar a largura toda da coluna e quebrar\n\n" +
            "[Refrão]\n[G]refrão [D]aqui com acordes em [Em]cima\n[C] [G]\n\n\nfim"
        val chords = br.app.stagebook.ui.stage.ChordLook(true, 0.8f, androidx.compose.ui.graphics.Color.Yellow)
        val lines = br.app.stagebook.ui.stage.stageLines(text, br.app.stagebook.ui.stage.SectionNames(null), androidx.compose.ui.graphics.Color.Cyan, true, chords).map { it.text }
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
                val batch = br.app.stagebook.ui.stage.lineHeights(measurer, lines, style, width)
                assertEquals("largura $width", one, batch.map { it.height })
                assertEquals(lines.map { it.isEmpty() }, batch.map { it.blank })
            }
        }
        compose.waitForIdle()
    }

    // o Robolectric comum não quebra linha de texto: este precisa do desenho nativo (como as capturas)
    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun `a wrapping line with chords becomes pieces and only the ones with a chord keep the space`() {
        val chords = br.app.stagebook.ui.stage.ChordLook(true, 0.8f, androidx.compose.ui.graphics.Color.Yellow)
        val sourced = br.app.stagebook.ui.stage.stageLines(
            "[G]uma linha comprida com acorde só no começo e que vai quebrar",
            br.app.stagebook.ui.stage.SectionNames(null), androidx.compose.ui.graphics.Color.Cyan, true, chords,
        )
        compose.setContent {
            val measurer = rememberTextMeasurer()
            val style = TextStyle(fontSize = 34.sp, lineHeight = 42.5.sp)
            val wide = br.app.stagebook.ui.stage.splitWrappedChordLines(measurer, sourced, style, 5000)
            assertTrue("sem quebra, a mesma lista", wide === sourced)
            val narrow = br.app.stagebook.ui.stage.splitWrappedChordLines(measurer, sourced, style, 400)
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
    fun `holding a line offers editing and opens the editor on it`() {
        val edits = mutableListOf<Pair<String, Int>>()
        stageWithEdit({ id, c -> edits += id to c })
        holdSecondLine()
        compose.onNodeWithText("Editar esta linha").assertIsDisplayed().performClick()
        // "[Refrão]\n" tem 9 caracteres: a 2ª linha mostrada ("primeira linha") começa ali
        assertEquals(listOf("s1" to 9), edits)
    }

    @Test
    fun `with the edit button open, tapping outside only closes it (no page turn, no editor)`() {
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
    fun `top pencil opens the editor at the start of the page on screen`() {
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
