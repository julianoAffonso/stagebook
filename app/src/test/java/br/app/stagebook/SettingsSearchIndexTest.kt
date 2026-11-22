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

    /** The search as it was before the index: every item read and normalized on every call. */
    private fun oldSearch(query: String, ctx: IndexCtx, r: Resources): List<String> {
        val words = TextKey.of(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val scored = allSettingsItems().mapNotNull { item ->
            val raw = if (item.kind == br.app.stagebook.ui.settings.ItemKind.SLIDER) r.getString(item.title, -7777) else r.getString(item.title)
            val title = raw.substringBefore("-7777").trim().trimEnd(':', ' ').ifEmpty { raw }
            val name = TextKey.of(title)
            val kw = TextKey.of(item.keywords?.let(r::getString).orEmpty())
            val where = TextKey.of(r.getString(item.card) + " " + r.getString(item.group.title))
            val help = TextKey.of(item.detail?.let(r::getString).orEmpty())
            val nameWords = name.split(' ')
            var score = 0
            for (w in words) {
                val rank = when {
                    nameWords.any { it.startsWith(w) } -> 0
                    w in name -> 0
                    w in kw -> 1
                    w in where -> 2
                    w in help -> 3
                    else -> return@mapNotNull null
                }
                score = maxOf(score, rank)
            }
            Triple(item.id, score, item.hide(ctx) != null)
        }
        return scored.sortedWith(compareBy({ it.second }, { it.third })).map { it.first + ":" + it.second + ":" + it.third }
    }

    private val queries = listOf(
        "midi", "tela", "pedal", "METRONOMO", "rolag", "screen", "ligar o editor", "saida midi", "fonte tamanho", "banda",
        "zz nada", "  ", "cor tema escuro", "acorde", "backup", "android bateria", "a",
    )

    private val ctxs = listOf(
        IndexCtx(),
        IndexCtx(look = Look(bandOn = false, internetOn = false, extrasOn = false, playerMode = false), midiSupported = true),
        IndexCtx(look = Look(stageMode = StageMode.SCROLL), midiSupported = true, advanced = AdvancedUi(), nationalTags = true),
    )

    @Test fun `the index returns the same hits in the same order as the old search`() {
        val r = context.resources
        val index = SettingsSearchIndex(r)
        for (ctx in ctxs) for (q in queries) {
            val now = index.search(q, ctx).map { it.item.id + ":" + it.score + ":" + (it.hide != null) }
            assertEquals("query '$q'", oldSearch(q, ctx, r), now)
            assertEquals("one-off '$q'", now, searchSettings(q, ctx, r).map { it.item.id + ":" + it.score + ":" + (it.hide != null) })
        }
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
