package br.app.stagebook.data

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * A long-lived scope for a service of its own (band, player, chord sound): an unexpected error in one of its jobs is
 * logged instead of crashing the app on stage, like the shared app scope (audit 30/10).
 */
fun backgroundScope(dispatcher: CoroutineDispatcher): CoroutineScope =
    CoroutineScope(SupervisorJob() + dispatcher + CoroutineExceptionHandler { _, e -> Log.e("stagebook", "background error", e) })
