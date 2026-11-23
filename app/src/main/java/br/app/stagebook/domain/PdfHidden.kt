package br.app.stagebook.domain

import br.app.stagebook.data.model.PdfCrop
import br.app.stagebook.data.model.PdfMark

/**
 * Pages of a song's PDF left off the stage (user, 22/11): the file keeps them, the stage turns and scrolls past them. The
 * hidden pages, the marks and the trims are kept by file page; the stage counts only the pages it shows.
 */
object PdfHidden {
    /** The file pages shown, in order. At least one always shows: hiding every page would leave an empty stage. */
    fun visible(count: Int, hidden: Collection<Int>): List<Int> {
        val h = hidden.toSet()
        return (0 until count).filter { it !in h }.ifEmpty { (0 until count).toList() }
    }

    /** [page] shown again if hidden, hidden if shown; the last page shown can't be hidden. */
    fun toggle(hidden: List<Int>, page: Int, count: Int): List<Int> = when {
        page in hidden -> hidden - page
        page !in 0 until count -> hidden
        (hidden + page).toSet().size >= count -> hidden
        else -> (hidden + page).distinct().sorted()
    }

    /** The marks of the pages shown, numbered as the stage counts them. */
    fun marks(marks: List<PdfMark>, visible: List<Int>): List<PdfMark> =
        marks.mapNotNull { m -> visible.indexOf(m.page).takeIf { it >= 0 }?.let { m.copy(page = it) } }

    /** The trims of the pages shown (and the one for all pages), numbered as the stage counts them. */
    fun crops(crops: List<PdfCrop>, visible: List<Int>): List<PdfCrop> =
        crops.mapNotNull { c -> if (c.page == PdfCrop.ALL) c else visible.indexOf(c.page).takeIf { it >= 0 }?.let { c.copy(page = it) } }

    /** The file page that stage page [shown] is. */
    fun fileOf(shown: Int, visible: List<Int>): Int = visible.getOrElse(shown) { shown }
}
