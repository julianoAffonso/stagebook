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
import br.app.stagebook.domain.BreakTimer
import br.app.stagebook.domain.SetlistBreaks
import kotlinx.coroutines.delay

/**
 * How long a break has lasted, as the stage and the band's strip show it ("02:05"), counted from [since] so it goes on
 * after the device is turned. It ticks at the turn of each second, and only while the screen is in front: with the app
 * in the background nothing runs.
 */
@Composable
fun breakClock(since: Long): String = breakClock(BreakTimer.Timer(startedAt = since))

/**
 * The same for a break's [timer] (by wall clock, see [BreakTimer]): a paused one shows what it had counted and ticks
 * nothing. The time is worked out when it is drawn, so coming back to the screen shows the right time at once.
 */
@Composable
fun breakClock(timer: BreakTimer.Timer): String {
    var now by remember(timer) { mutableLongStateOf(System.currentTimeMillis()) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(timer, owner) {
        if (!timer.running) return@LaunchedEffect
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = System.currentTimeMillis()
                delay(1_000L - timer.elapsed(now) % 1_000L)
            }
        }
    }
    return SetlistBreaks.clock(timer.elapsed(now))
}
