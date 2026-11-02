package br.app.stagebook.domain

/** Rules for a site typed in the import settings. */
object ImportSiteRules {
    private val host = Regex("""^(?:https?://)?([a-z0-9-]+(?:\.[a-z0-9-]+)+)(/.*)?$""", RegexOption.IGNORE_CASE)

    /**
     * The site's address (https://domain/) or null if it doesn't look like a site. Only the domain is kept (user's
     * request of 30/10): a path or a page typed in is dropped, the list holds lyrics and chord sites, not links.
     */
    fun normalize(input: String): String? {
        val m = host.matchEntire(input.trim()) ?: return null
        return "https://" + m.groupValues[1].lowercase() + "/"
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
