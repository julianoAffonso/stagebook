package br.app.stagebook

import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.model.Look
import br.app.stagebook.data.model.StageMode
import br.app.stagebook.ui.settings.AdvancedUi
import br.app.stagebook.ui.settings.IndexCtx
import br.app.stagebook.ui.settings.SettingsSearchIndex
import br.app.stagebook.ui.settings.allSettingsItems
import br.app.stagebook.ui.settings.searchSettings
import br.app.stagebook.domain.TextKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The search index is read once per language (perf, 14/11): same hits as the old per-keystroke version, no rereading. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class SettingsSearchIndexTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    /**
     * What the person finds, written down (audit 22/11: the oracle was a copy of the ranking itself, so a wrong ranking
     * passed as long as both copies agreed). Each query with its first hits, as "id:rank:hidden": rank 0 = in the name,
     * 1 = keyword, 2 = card or group, 3 = only in the explanation.
     */
    private val expected = mapOf(
        "midi" to listOf("pedal_connect:0:false", "pedal_midi:0:false", "midi_usb:0:false", "midi_out:1:false"),
        "pedal" to listOf("pedal_connect:0:false", "pedal_common:0:false", "midi_usb:0:false", "pedal_midi:1:false"),
        "METRONOMO" to listOf("metro_test:0:false", "essential_extras:0:false", "metro_shown:1:false", "metro_count:1:false"),
        "rolag" to listOf("auto_scroll:0:true", "stage_mode:1:false"),   // hidden in Turn pages mode, and still found
        "ligar o editor" to listOf("web_editor:0:false"),
        "saida midi" to listOf("midi_out:2:false", "pedal_common:3:false", "pedal_midi:3:false"),
        "banda" to listOf("band_members:0:false", "essential_band:0:false", "band_rename:1:false", "band_nearby:1:false"),
        "backup" to listOf("backup_library:1:false", "backup_tracks:1:false", "backup_settings:1:false", "backup_make:1:false"),
        "fade" to listOf("stop_fade:0:false", "trim_silence:1:false"),
        "tela externa" to listOf("external_show:0:false", "external_idle:1:false", "essential_extras:3:false"),
        "zz nada" to emptyList(),
        "  " to emptyList(),
    )

    private val queries = expected.keys.toList() + listOf("tela", "screen", "fonte tamanho", "cor tema escuro", "acorde", "android bateria", "a")

    private val ctxs = listOf(
        IndexCtx(),
        IndexCtx(look = Look(bandOn = false, internetOn = false, extrasOn = false, playerMode = false), midiSupported = true),
        IndexCtx(look = Look(stageMode = StageMode.SCROLL), midiSupported = true, advanced = AdvancedUi(), nationalTags = true),
    )

    private fun hits(found: List<br.app.stagebook.ui.settings.SettingsHit>) = found.map { it.item.id + ":" + it.score + ":" + (it.hide != null) }

    @Test fun `each query finds what the person expects, best first`() {
        val index = SettingsSearchIndex(context.resources)
        val ctx = IndexCtx(midiSupported = true)
        for ((q, want) in expected) {
            val got = hits(index.search(q, ctx))
            assertEquals("query '$q'", want, if (want.isEmpty()) got else got.take(want.size))
        }
    }

    @Test fun `the index kept between keystrokes answers as a search made from scratch`() {
        val r = context.resources
        val index = SettingsSearchIndex(r)
        for (ctx in ctxs) for (q in queries) assertEquals("'$q'", hits(searchSettings(q, ctx, r)), hits(index.search(q, ctx)))
        assertTrue(queries.any { q -> index.search(q, ctxs[1]).any { it.inHelp } })
        assertTrue(ctxs.any { ctx -> queries.any { q -> index.search(q, ctx).any { it.hide != null } } })
    }

    @Test fun `a keystroke does not read the strings again`() {
        var reads = 0
        // a Resources that counts lookups: subclassing has only this constructor, deprecated with no replacement
        @Suppress("DEPRECATION")
        val counting = object : Resources(context.resources.assets, context.resources.displayMetrics, context.resources.configuration) {
            override fun getString(id: Int): String { reads++; return context.resources.getString(id) }
            override fun getString(id: Int, vararg args: Any?): String { reads++; return context.resources.getString(id, *args) }
        }
        // the counter sees the old cost: a one-off search (index built per call) reads the texts on every query
        searchSettings("midi", ctxs[1], counting)
        assertTrue("a rebuilt index reads again", reads > 100)
        reads = 0
        val index = SettingsSearchIndex(counting)
        assertTrue("the index reads the texts", reads > 100)
        val built = reads
        val ctx = ctxs[1]
        repeat(3) { for (q in queries) index.search(q, ctx) }
        val afterWarm = reads
        repeat(5) { for (q in queries) index.search(q, ctx) }
        assertEquals("search after the first passes reads nothing more", afterWarm, reads)
        assertTrue("only the few hide reasons are read after the build (${afterWarm - built})", afterWarm - built < 20)
    }

    @Test fun `every word of the search example finds something, in both languages`() {
        for (tag in listOf("pt-BR", "en")) {
            val cfg = android.content.res.Configuration(context.resources.configuration).apply { setLocale(java.util.Locale.forLanguageTag(tag)) }
            val r = context.createConfigurationContext(cfg).resources
            val hint = r.getString(R.string.settings_search_hint)
            val words = hint.split(',').map { it.trim().substringAfterLast(' ') }.filter { it.isNotEmpty() }
            assertEquals("$tag: three examples in '$hint'", 3, words.size)
            val index = SettingsSearchIndex(r)
            for (w in words) assertTrue("$tag: '$w' (from '$hint') finds nothing", index.search(w, IndexCtx()).isNotEmpty())
        }
    }
}
