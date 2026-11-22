package br.app.stagebook

import br.app.stagebook.ui.importer.ImportViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What the importer's address bar opens (user, 22/11: the phone's keyboard put spaces and capitals in an address). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ImportAddressTest {
    @Test fun `spaces after periods and capitals from the keyboard still open the address`() {
        assertEquals("https://clara.imslp.org/work/4215", ImportViewModel.urlFor("HTTPS://clara. Imslp. Org/work/4215"))
        assertEquals("https://www.mutopiaproject.org/cgibin/piece-info.cgi?id=1", ImportViewModel.urlFor("HTTPS://www. mutopiaproject. org/cgibin/piece-info. cgi?id=1"))
        assertEquals("https://imslp.org/", ImportViewModel.urlFor("Imslp. Org/"))
    }

    @Test fun `the path keeps its case`() {
        assertEquals("https://imslp.org/wiki/Main_Page", ImportViewModel.urlFor("HTTPS://IMSLP.org/wiki/Main_Page"))
    }

    @Test fun `words are still a search`() {
        assertTrue(ImportViewModel.urlFor("sweet child o mine").startsWith("https://www.google.com/search?q="))
        assertTrue(ImportViewModel.urlFor("satie gymnopedies", "partitura pdf").contains("partitura"))
    }

    // the bar is a search too (user, 22/11): a period with a space in a song's or artist's name stays words
    @Test fun `names with a period and a space stay a search`() {
        listOf("Sr. Brasil", "ac. dc back in black", "Dr. Feelgood", "St. Elmo's Fire", "Mr. Jones").forEach {
            assertTrue(it, ImportViewModel.urlFor(it).startsWith("https://www.google.com/search?q="))
        }
    }
}
