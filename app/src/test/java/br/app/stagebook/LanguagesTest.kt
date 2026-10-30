package br.app.stagebook

import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.AppLanguage
import br.app.stagebook.data.Languages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class LanguagesTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test fun `a língua escolhida vale para os textos e a do aparelho não muda nada`() {
        Languages.set(context, AppLanguage.PT)
        assertEquals("pt", Languages.wrap(context).resources.configuration.locales[0].language)
        Languages.set(context, AppLanguage.SYSTEM)
        assertTrue(Languages.wrap(context) === context)
    }

    @Test fun `língua sem tradução não é oferecida nem aplicada`() {
        Languages.set(context, AppLanguage.ES)   // ainda fora de available
        assertEquals(AppLanguage.SYSTEM, Languages.get(context))
    }

    /** Toda língua oferecida tem a tradução completa: nenhum texto do português faltando. */
    @Test fun `traduções oferecidas estão completas`() {
        val names = Regex("""<(string|plurals) name="([^"]+)"(?![^>]*translatable="false")""")
        fun keys(f: File) = names.findAll(f.readText()).map { it.groupValues[2] }.toSet()
        val base = keys(File("src/main/res/values/strings.xml"))
        assertTrue(base.size > 100)
        Languages.available.filter { it != AppLanguage.SYSTEM && it != AppLanguage.PT }.forEach { l ->
            val f = File("src/main/res/values-${l.tag}/strings.xml")
            assertTrue("falta ${f.path}", f.exists())
            val missing = base - keys(f)
            assertTrue("${l.tag} sem tradução de: ${missing.take(10)}", missing.isEmpty())
        }
    }
}
