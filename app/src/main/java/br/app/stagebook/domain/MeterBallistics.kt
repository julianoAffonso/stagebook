package br.app.stagebook.domain

import androidx.compose.runtime.Immutable
import kotlin.math.log10
import kotlin.math.max

/** What each bar shows, in dBFS: the level (falls slowly) and the mark of the highest recent peak. */
@Immutable
data class Meters(val levelDb: List<Float>, val holdDb: List<Float>) {
    companion object {
        const val FLOOR_DB = -48f
        const val TOP_DB = 3f
        fun silent(size: Int) = Meters(List(size) { FLOOR_DB }, List(size) { FLOOR_DB })

        fun toDb(linear: Float, floor: Float = FLOOR_DB): Float = if (linear <= 0f) floor else max(floor, 20f * log10(linear))

        /** Position on the bar, from 0 (FLOOR_DB) to 1 (TOP_DB). */
        fun fraction(db: Float): Float = ((db - FLOOR_DB) / (TOP_DB - FLOOR_DB)).coerceIn(0f, 1f)
    }
}

/**
 * Peak meter ballistics: the bar rises at once and falls 24 dB/s; the peak mark holds for 1 s and then falls too. That
 * way a short peak stays visible on a screen updated ~15 times per second.
 */
class MeterBallistics(private val size: Int, private val floor: Float = Meters.FLOOR_DB) {
    private val level = FloatArray(size) { floor }
    private val hold = FloatArray(size) { floor }
    private val holdLeftMs = LongArray(size)

    fun update(peaksDb: FloatArray, dtMs: Long): Meters {
        val fall = FALL_DB_PER_S * dtMs / 1000f
        for (i in 0 until size) {
            val p = peaksDb[i]
            level[i] = max(p, level[i] - fall).coerceAtLeast(floor)
            if (p >= hold[i]) {
                hold[i] = p
                holdLeftMs[i] = HOLD_MS
            } else if (holdLeftMs[i] > 0) {
                holdLeftMs[i] -= dtMs
            } else {
                hold[i] = max(level[i], hold[i] - fall)
            }
        }
        return Meters(level.toList(), hold.toList())
    }

    private companion object {
        const val FALL_DB_PER_S = 24f
        const val HOLD_MS = 1_000L
    }
}
