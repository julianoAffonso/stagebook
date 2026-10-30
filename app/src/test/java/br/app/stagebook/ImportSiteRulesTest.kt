package br.app.stagebook

import br.app.stagebook.domain.ImportSiteRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImportSiteRulesTest {
    @Test fun `endereço digitado vira https e nome pelo domínio`() {
        assertEquals("https://www.cifras.com.br/", ImportSiteRules.normalize("www.cifras.com.br"))
        assertEquals("https://tabs.ultimate-guitar.com/tab/x", ImportSiteRules.normalize("https://tabs.ultimate-guitar.com/tab/x"))
        assertEquals("Cifras", ImportSiteRules.nameOf("https://www.cifras.com.br/"))
        assertEquals("Lacuerda", ImportSiteRules.nameOf("https://acordes.lacuerda.net/"))
    }

    @Test fun `o que não é site não entra`() {
        assertNull(ImportSiteRules.normalize("cifra club"))
        assertNull(ImportSiteRules.normalize(""))
    }
}
