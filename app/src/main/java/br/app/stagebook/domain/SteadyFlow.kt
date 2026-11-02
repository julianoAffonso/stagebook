package br.app.stagebook.domain

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest

/**
 * A "ready" signal that ignores drops shorter than [ms]: true passes at once, false only if it lasts. The USB interface
 * reconnecting for its clock (a second or so, common on the first connection) restarted the metronome twice (audit
 * 02/11 #3).
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
fun Flow<Boolean>.ignoringShortDrops(ms: Long): Flow<Boolean> = transformLatest { ready ->
    if (!ready) delay(ms)
    emit(ready)
}.distinctUntilChanged()
