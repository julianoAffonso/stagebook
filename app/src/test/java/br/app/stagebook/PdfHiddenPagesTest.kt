package br.app.stagebook

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import br.app.stagebook.ui.stage.PdfOpen
import br.app.stagebook.ui.stage.PdfStageView
import br.app.stagebook.data.PdfPages
import br.app.stagebook.data.PdfSource
import br.app.stagebook.data.model.PdfCrop
import br.app.stagebook.data.model.PdfMark
import br.app.stagebook.domain.PdfHidden
import br.app.stagebook.ui.editor.EditorPdf
import br.app.stagebook.ui.editor.PDF_PAGES_COUNT_TAG
import br.app.stagebook.ui.editor.PdfPagesDialog
import br.app.stagebook.ui.editor.PdfSongCard
import br.app.stagebook.ui.editor.pdfPageTag
import br.app.stagebook.ui.theme.StagebookTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Pages of a PDF left off the stage (user, 22/11): the file keeps them, the stage turns past them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class PdfHiddenPagesTest {
    @get:Rule val compose = createComposeRule()

    private class FakeSource(override val pageCount: Int) : PdfSource {
        val rendered = mutableListOf<Int>()
        override fun pageSize(page: Int) = 400 to 600
        override fun render(page: Int, width: Int): Bitmap { rendered += page; return Bitmap.createBitmap(width, width, Bitmap.Config.ARGB_8888) }
        override fun close() {}
    }

    @Test fun `the pages shown skip the hidden ones, and one always shows`() {
        assertEquals(listOf(0, 2, 3), PdfHidden.visible(4, listOf(1)))
        assertEquals(listOf(0, 1), PdfHidden.visible(2, listOf(0, 1)))
        assertEquals(listOf(1), PdfHidden.toggle(emptyList(), 1, 3))
        assertEquals(emptyList<Int>(), PdfHidden.toggle(listOf(1), 1, 3))
        assertEquals("the last page shown stays", listOf(0, 1), PdfHidden.toggle(listOf(0, 1), 2, 3))
        assertEquals(listOf(0), PdfHidden.toggle(listOf(0), 7, 3))
    }

    @Test fun `marks and trims follow their file pages, numbered as the stage counts`() {
        val visible = listOf(0, 2, 3)
        val marks = PdfHidden.marks(listOf(PdfMark(1), PdfMark(2), PdfMark(3)), visible)
        assertEquals(listOf(1, 2), marks.map { it.page })
        val crops = PdfHidden.crops(listOf(PdfCrop(PdfCrop.ALL), PdfCrop(1), PdfCrop(3)), visible)
        assertEquals(listOf(PdfCrop.ALL, 2), crops.map { it.page })
        assertEquals(2, PdfHidden.fileOf(1, visible))
    }

    @Test fun `the stage's pages draw the file pages shown, through the same PDF`() = runBlocking {
        val source = FakeSource(4)
        val all = PdfPages(source)
        val shown = all.only(listOf(0, 2, 3))
        assertEquals(3, shown.pageCount)
        shown.bitmap(1, 100)
        assertEquals(listOf(2), source.rendered)
        assertEquals("every page shown: the same pages", all, all.only(listOf(0, 1, 2, 3)))
    }

    @Test fun `the stage counts only the pages shown and draws the file page behind each`() {
        val source = FakeSource(4)
        var count = 0
        compose.setContent { StagebookTheme { PdfStageView(PdfOpen.Ready(PdfPages(source).only(PdfHidden.visible(4, listOf(1)))), 1, invert = false, onPageCount = { count = it }) } }
        compose.waitUntil(5_000) { count == 3 }
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Página 2 de 3 do PDF").fetchSemanticsNodes().isNotEmpty() }
        assertEquals("stage page 2 is file page 3 (index 2); page 2 of the file is never drawn", true, 2 in source.rendered && 1 !in source.rendered)
    }

    @Test fun `the editor counts the pages on stage and a tap hides or brings one back`() {
        val toggled = mutableListOf<Int>()
        compose.setContent { StagebookTheme { PdfPagesDialog(EditorPdf("a.pdf", pages = 3, hidden = listOf(1)), { toggled += it }, {}) } }
        compose.onNodeWithTag(PDF_PAGES_COUNT_TAG).assertTextEquals("2 de 3 páginas no palco")
        compose.onNodeWithTag(pdfPageTag(1)).performClick()
        compose.onNodeWithTag(pdfPageTag(2)).performClick()
        assertEquals(listOf(1, 2), toggled)
    }

    @Test fun `the PDF card offers the pages on stage with how many show`() {
        var asked = false
        compose.setContent { StagebookTheme { PdfSongCard(EditorPdf("a.pdf", pages = 5, bytes = 1000, hidden = listOf(0, 3)), {}, {}, onPages = { asked = true }) } }
        compose.onNodeWithText("Páginas no palco · 3 de 5").performClick()
        assertEquals(true, asked)
    }
}
