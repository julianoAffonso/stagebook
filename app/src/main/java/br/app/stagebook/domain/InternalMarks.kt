package br.app.stagebook.domain

/**
 * Lines the app writes into a song for itself, which the person never typed: the chords taken off the stage's chord
 * list (`{x_hide_chords: …}`, and any other `x_` directive) and the chord shapes picked on stage (`{define…}`). The
 * editors hide them (user, 02/11: they showed up while editing) and put them back when saving, so nothing is lost.
 */
object InternalMarks {
    private val directive = Regex("""^\s*\{\s*([A-Za-z_][A-Za-z_-]*)""")

    fun isInternal(line: String): Boolean {
        val name = directive.find(line)?.groupValues?.get(1)?.lowercase() ?: return false
        return line.trimEnd().endsWith("}") && (name.startsWith("x_") || name.startsWith("x-") || name.startsWith("define"))
    }

    /** The text without the app's own lines, and those lines, in order. */
    fun split(text: String): Pair<String, List<String>> {
        val (internal, shown) = text.split('\n').partition(::isInternal)
        return shown.joinToString("\n") to internal
    }

    /**
     * [shown] (as edited) with the app's [internal] lines back at the start. A line the person typed for the same thing
     * wins (the same directive, and for a {define} the same chord): saved first, the hidden one used to be the one read
     * (audit 02/11).
     */
    fun join(shown: String, internal: List<String>): String {
        val typed = shown.split('\n').mapNotNull(::key).toSet()
        val back = internal.filter { key(it) !in typed }
        return if (back.isEmpty()) shown else (back + shown).joinToString("\n")
    }

    /** The edited text with the internal lines of the song as it was saved ([saved]) put back. */
    fun restore(edited: String, saved: String): String = join(edited, split(saved).second)

    // what a line is about: the directive name, plus the chord for a {define}
    private fun key(line: String): String? {
        if (!isInternal(line)) return null
        val name = directive.find(line)?.groupValues?.get(1)?.lowercase() ?: return null
        if (!name.startsWith("define")) return name
        val chord = line.substringAfter(':', "").trim().substringBefore(' ')
        return "$name $chord"
    }
}
