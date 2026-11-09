package br.app.stagebook.domain

/**
 * Where the app was when it installed its own update, to open there again afterwards (user, 09/11: after updating it
 * stayed closed and started from the beginning). A mark older than [FRESH_MS] is ignored: the person moved on.
 */
object ResumePoint {
    const val FRESH_MS = 15 * 60_000L
    const val SETTINGS = "settings:"   // + a SettingsSection name

    fun fresh(savedAt: Long, now: Long): Boolean = savedAt > 0 && now >= savedAt && now - savedAt <= FRESH_MS

    /**
     * A navigation route pattern ("pkg.SetlistDetail/{setId}?x={x}") filled with its arguments, or null when one is
     * missing or null (nothing to go back to safely). [encode] escapes a value for a route.
     */
    fun fill(pattern: String, args: Map<String, String?>, encode: (String) -> String): String? {
        var missing = false
        val out = Regex("\\{([^}]+)\\}").replace(pattern) { m ->
            val v = args[m.groupValues[1]]
            if (v == null) { missing = true; "" } else encode(v)
        }
        return if (missing) null else out
    }
}
