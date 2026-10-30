package br.app.stagebook

import br.app.stagebook.domain.Pagination.Line
import br.app.stagebook.domain.ScrollSteps
import org.junit.Assert.assertEquals
import org.junit.Test

class ScrollStepsTest {
    private fun lines(vararg h: Int) = h.map { Line(it, blank = it == 10) }

    @Test fun `everything fits - a single step`() = assertEquals(1, ScrollSteps.steps(lines(50, 50), 300).size)

    @Test fun `scrolls to the first line that did not fit whole and lights it at the top`() {
        // lines 100 tall on a 250 screen: the 3rd (top 200) didn't fit whole
        val steps = ScrollSteps.steps(lines(100, 100, 100, 100, 100), 250)
        assertEquals(200, steps[1].offset)
        assertEquals(0, steps[1].markY)
        assertEquals(100, steps[1].markHeight)
    }

    @Test fun `the last step stops at the end of the song and the lit line sits lower`() {
        val steps = ScrollSteps.steps(lines(100, 100, 100, 100, 100), 250)
        val last = steps.last()
        assertEquals(250, last.offset)          // 500 of song - 250 of screen
        assertEquals(400 - 250, last.markY)     // the 5th line (top 400) that didn't fit in the previous step
    }

    @Test fun `blank line between verses does not get the highlight`() {
        val steps = ScrollSteps.steps(lines(100, 100, 10, 100, 100, 100), 205)
        assertEquals(210, steps[1].offset)      // skips the blank line (top 200) and goes to the text one (top 210)
    }

    @Test fun `line taller than the screen scrolls the whole screen`() {
        val steps = ScrollSteps.steps(lines(600, 100), 250)
        assertEquals(250, steps[1].offset)
    }

    @Test fun `pause in the middle - the position becomes a step and the next starts from it`() {
        // lines 100 tall, screen 250; paused at 130 (between steps 0 and 200)
        val steps = ScrollSteps.steps(lines(100, 100, 100, 100, 100, 100), 250, anchor = 130)
        assertEquals(listOf(0, 130, 300, 350), steps.map { it.offset })
        assertEquals(0, steps[1].markHeight)    // the pause point doesn't light up: the lyrics don't move
        assertEquals(300, steps[2].offset)      // the 4th line (top 300) didn't fit whole from 130 (it goes to 380)
    }

    @Test fun `pause at the end or on an exact step does not duplicate`() {
        val base = ScrollSteps.steps(lines(100, 100, 100, 100, 100), 250).map { it.offset }
        assertEquals(base, ScrollSteps.steps(lines(100, 100, 100, 100, 100), 250, anchor = 200).map { it.offset })
        assertEquals(base.last(), ScrollSteps.steps(lines(100, 100, 100, 100, 100), 250, anchor = 9999).last().offset)
    }
}
