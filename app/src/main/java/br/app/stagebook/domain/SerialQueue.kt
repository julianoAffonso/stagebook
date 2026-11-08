package br.app.stagebook.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Items handled one at a time, in the order they were offered, by a single consumer that only starts with the first
 * item. After [idleMs] without anything new it calls [onIdle] once (to let go of a resource the items opened) and then
 * waits without a clock: nothing runs while nothing is offered. Two separate launches on a thread pool can swap order;
 * this can't (the MIDI exit of the old song must go before the entry of the next, audit 07/11).
 */
class SerialQueue<T>(
    private val scope: CoroutineScope,
    private val idleMs: Long,
    private val process: suspend (T) -> Unit,
    private val onIdle: suspend () -> Unit,
) {
    private val channel = Channel<T>(Channel.UNLIMITED)
    private var started = false

    fun offer(item: T) {
        synchronized(this) {
            if (!started) {
                started = true
                scope.launch { consume() }
            }
        }
        channel.trySend(item)
    }

    private suspend fun consume() {
        var holding = false
        while (true) {
            val item = if (holding) withTimeoutOrNull(idleMs) { channel.receive() } else channel.receive()
            if (item == null) { onIdle(); holding = false } else { process(item); holding = true }
        }
    }
}
