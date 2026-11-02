package br.app.stagebook.domain

import java.util.concurrent.atomic.AtomicLong

/**
 * Overlapping sends of the same thing (the current song to the band): each one takes a ticket, and only the newest may
 * write its result back. An older send that finishes late is ignored, so it can't bring back a song already left
 * behind (deep audit, 30/10).
 */
class Newest {
    private val last = AtomicLong()

    fun ticket(): Long = last.incrementAndGet()

    fun isNewest(ticket: Long): Boolean = last.get() == ticket
}
