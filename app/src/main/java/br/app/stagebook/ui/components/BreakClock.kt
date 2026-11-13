package br.app.stagebook.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import br.app.stagebook.domain.SetlistBreaks
import kotlinx.coroutines.delay

/**
 * How long a break has lasted, as the stage and the band's strip show it ("02:05"), counted from [since] so it goes on
 * after the device is turned. It ticks at the turn of each second, and only while the screen is in front: with the app
 * in the background nothing runs.
 */
@Composable
fun breakClock(since: Long): String {
    var now by remember(since) { mutableLongStateOf(System.currentTimeMillis()) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(since, owner) {
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = System.currentTimeMillis()
                delay(1_000L - (now - since).coerceAtLeast(0L) % 1_000L)
            }
        }
    }
    return SetlistBreaks.clock(now - since)
}
