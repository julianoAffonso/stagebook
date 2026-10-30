package br.app.stagebook

import br.app.stagebook.domain.ImportSiteRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImportSiteRulesTest {
    @Test fun `typed address becomes https and the name comes from the domain`() {
        assertEquals("https://www.cifras.com.br/", ImportSiteRules.normalize("www.cifras.com.br"))
        assertEquals("https://tabs.ultimate-guitar.com/tab/x", ImportSiteRules.normalize("https://tabs.ultimate-guitar.com/tab/x"))
        assertEquals("Cifras", ImportSiteRules.nameOf("https://www.cifras.com.br/"))
        assertEquals("Lacuerda", ImportSiteRules.nameOf("https://acordes.lacuerda.net/"))
    }

    @Test fun `what is not a site does not come in`() {
        assertNull(ImportSiteRules.normalize("cifra club"))
        assertNull(ImportSiteRules.normalize(""))
    }
}
