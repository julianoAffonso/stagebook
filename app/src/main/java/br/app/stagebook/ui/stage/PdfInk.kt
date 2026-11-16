package br.app.stagebook.ui.stage

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import br.app.stagebook.data.model.PdfCrop
import br.app.stagebook.data.model.PdfMark
import br.app.stagebook.data.model.PdfPoint
import br.app.stagebook.domain.PdfMarks

/**
 * The highlighter strokes of one page, ready to draw: [strokes] are fractions of the page and [crop], if any, is the part
 * shown. The path and the line of each kept stroke are made once per size of the page on screen, and the stroke under the
 * finger is laid on one path used again, so a frame of a zoom, a move or a drawing finger allocates nothing here (audit 16/11:
 * every frame made a path, a list and a line style for each stroke).
 */
internal class PdfInk(private val strokes: List<PdfMark>, private val crop: PdfCrop?) {
    private val paths = arrayOfNulls<Path>(strokes.size)       // null: a stroke of one point (a dot), or none
    private val styles = arrayOfNulls<Stroke>(strokes.size)
    private var madeW = Float.NaN
    private var madeH = Float.NaN
    private val livePath = Path()
    private var liveStyle: Stroke? = null

    /** How many times the kept strokes' paths were made (once per size; the tests count it). */
    var builds = 0
        private set

    private val cw get() = crop?.let { it.right - it.left } ?: 1f
    private val ch get() = crop?.let { it.bottom - it.top } ?: 1f

    /** Draws the kept strokes and, over them, [live]: the one under the finger (empty = none). */
    fun draw(scope: DrawScope, live: List<PdfPoint>, color: Color) = with(scope) {
        val w = size.width
        val h = size.height
        if (w != madeW || h != madeH) make(w, h)
        val paint = color.copy(alpha = 0.4f)
        for (i in strokes.indices) {
            val stroke = strokes[i]
            val path = paths[i]
            if (path != null) drawPath(path, paint, style = styles[i]!!)
            else if (stroke.points.size == 1) drawCircle(paint, stroke.width / cw * w / 2f, at(stroke.points[0], w, h))
        }
        if (live.size == 1) drawCircle(paint, PdfMarks.WIDTH / cw * w / 2f, at(live[0], w, h))
        else if (live.size > 1) {
            livePath.rewind()
            trace(livePath, live, w, h)
            drawPath(livePath, paint, style = liveStyle!!)
        }
    }

    private fun make(w: Float, h: Float) {
        for (i in strokes.indices) {
            val stroke = strokes[i]
            if (stroke.points.size < 2) { paths[i] = null; styles[i] = null; continue }
            paths[i] = Path().also { trace(it, stroke.points, w, h) }
            styles[i] = line(stroke.width, w)
        }
        liveStyle = line(PdfMarks.WIDTH, w)
        madeW = w
        madeH = h
        builds++
    }

    private fun line(width: Float, w: Float) = Stroke(width = width / cw * w, cap = StrokeCap.Round, join = StrokeJoin.Round)

    private fun at(p: PdfPoint, w: Float, h: Float) = Offset((p.x - (crop?.left ?: 0f)) / cw * w, (p.y - (crop?.top ?: 0f)) / ch * h)

    private fun trace(path: Path, points: List<PdfPoint>, w: Float, h: Float) {
        for (i in points.indices) {
            val q = at(points[i], w, h)
            if (i == 0) path.moveTo(q.x, q.y) else path.lineTo(q.x, q.y)
        }
    }
}
