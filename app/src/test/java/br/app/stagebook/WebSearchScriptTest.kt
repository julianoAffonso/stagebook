package br.app.stagebook

import br.app.stagebook.data.web.WebPageReader
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The search on the computer reads the results with this script: the sites must go in as a real JS array (31/10). */
class WebSearchScriptTest {
    @Test fun `the import sites go into the script as an array`() {
        val js = WebPageReader.resultsScript(listOf("cifraclub.com.br", "cifras.com.br"))
        assertTrue(js.trimEnd().endsWith("""})(["cifraclub.com.br","cifras.com.br"])"""))
        assertFalse(js.contains("joinToString"))
        assertFalse(js.contains("${'$'}{"))
    }
}
