package br.app.stagebook.domain

/** Where the metronome sounds: [out] -1 = the device; otherwise a USB output, with the next one too when [pair]. */
data class MetronomeOut(val out: Int, val pair: Boolean) {
    val device: Boolean get() = out < 0

    companion object {
        val DEVICE = MetronomeOut(-1, false)

        /**
         * The choices: the device, then the USB interface's outputs (pairs first, like 3-4 into the desk's 7/8, then
         * single ones). Only the device when the interface last used isn't a USB one.
         */
        fun options(output: OutputInfo): List<MetronomeOut> {
            if (output.key == OutputInfo.DEVICE || output.count <= 0) return listOf(DEVICE)
            val pairs = (0 until output.usable - 1 step 2).map { MetronomeOut(it, true) }
            val singles = (0 until output.usable).map { MetronomeOut(it, false) }
            return listOf(DEVICE) + pairs + singles
        }
    }
}
