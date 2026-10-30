package br.app.stagebook.domain

/** Rules for a site typed in the import settings. */
object ImportSiteRules {
    private val host = Regex("""^(?:https?://)?([a-z0-9-]+(?:\.[a-z0-9-]+)+)(/.*)?$""", RegexOption.IGNORE_CASE)

    /** Full address (https://…/) or null if it doesn't look like a site. */
    fun normalize(input: String): String? {
        val m = host.matchEntire(input.trim()) ?: return null
        val path = m.groupValues[2].ifEmpty { "/" }
        return "https://" + m.groupValues[1].lowercase() + path
    }

    /** Site name from the domain: "www.cifras.com.br" → "Cifras". */
    fun nameOf(url: String): String {
        val h = host.matchEntire(url)?.groupValues?.get(1)?.lowercase() ?: return url
        // the name is what comes before the domain ending (.com, .com.br, .net…): acordes.lacuerda.net → Lacuerda
        val parts = h.split('.').filterNot { it == "www" || it == "m" }.toMutableList()
        while (parts.size > 1 && parts.last() in SUFFIXES) parts.removeAt(parts.lastIndex)
        return parts.last().replaceFirstChar { it.uppercase() }
    }

    private val SUFFIXES = setOf("com", "net", "org", "br", "it", "es", "pt", "co", "uk", "io", "ar", "mx", "info")
}
