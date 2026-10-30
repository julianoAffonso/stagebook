package br.app.stagebook

import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.AppLanguage
import br.app.stagebook.R
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

    @Test fun `the chosen language applies to the texts and the device language changes nothing`() {
        Languages.set(context, AppLanguage.PT)
        assertEquals("pt", Languages.wrap(context).resources.configuration.locales[0].language)
        assertEquals("BR", Languages.wrap(context).resources.configuration.locales[0].country)
        assertEquals("Ajustes", Languages.wrap(context).getString(R.string.settings_title))
        Languages.set(context, AppLanguage.EN)
        assertEquals("Settings", Languages.wrap(context).getString(R.string.settings_title))
        Languages.set(context, AppLanguage.SYSTEM)
        assertTrue(Languages.wrap(context) === context)
    }

    @Test fun `saved language that left the app falls back to the device language`() {
        context.getSharedPreferences("lingua", android.content.Context.MODE_PRIVATE).edit().putString("lingua", "es").commit()
        assertEquals(AppLanguage.SYSTEM, Languages.get(context))
    }

    /** Toda língua oferecida tem a tradução completa: nenhum texto da base (inglês) faltando. */
    @Test fun `offered translations are complete`() {
        val names = Regex("""<(string|plurals) name="([^"]+)"(?![^>]*translatable="false")""")
        fun keys(f: File) = names.findAll(f.readText()).map { it.groupValues[2] }.toSet()
        val base = keys(File("src/main/res/values/strings.xml"))
        assertTrue(base.size > 100)
        Languages.available.filter { it != AppLanguage.SYSTEM && it != AppLanguage.EN }.forEach { l ->
            val folder = l.tag.replace("-", "-r")   // pt-BR → values-pt-rBR
            val f = File("src/main/res/values-$folder/strings.xml")
            assertTrue("falta ${f.path}", f.exists())
            val missing = base - keys(f)
            assertTrue("${l.tag} sem tradução de: ${missing.take(10)}", missing.isEmpty())
        }
    }
}
