package br.app.stagebook

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.model.ImportSites
import br.app.stagebook.data.model.Look
import br.app.stagebook.data.model.SiteKind
import br.app.stagebook.data.model.SongProfile
import br.app.stagebook.data.model.profile
import br.app.stagebook.data.model.siteKind
import br.app.stagebook.data.model.sites
import br.app.stagebook.data.model.withProfile
import br.app.stagebook.data.MusicBrainz
import br.app.stagebook.data.ImportDraft
import br.app.stagebook.ui.importer.ImportKindDialog
import br.app.stagebook.ui.importer.ImportViewModel
import br.app.stagebook.ui.settings.SettingsActions
import br.app.stagebook.ui.settings.SettingsScreen
import br.app.stagebook.ui.settings.SettingsSection
import br.app.stagebook.ui.settings.SettingsUiState
import br.app.stagebook.ui.theme.StagebookTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The automatic profile (user, 13/11): the stage of lyrics and chords, and an importer that asks what is coming in. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "pt-rBR-w1280dp-h800dp-land-hdpi")
class AutoProfileTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private fun str(id: Int) = context.getString(id)

    @Test fun `automatic is the chords profile that asks - each kind keeps its own sites`() {
        val auto = Look(chords = false).withProfile(SongProfile.AUTO)
        assertEquals(true, auto.chords); assertEquals(true, auto.autoProfile); assertEquals(SongProfile.AUTO, auto.profile)
        // what can't ask (a setlist's missing songs) takes the chord sites
        assertEquals(SiteKind.CHORDS, auto.siteKind)
        assertEquals(ImportSites.CHORDS, auto.sites(SiteKind.CHORDS)); assertEquals(ImportSites.LYRICS, auto.sites(SiteKind.LYRICS))
        // leaving it leaves nothing behind
        assertEquals(SongProfile.LYRICS, auto.withProfile(SongProfile.LYRICS).profile)
        assertEquals(false, auto.withProfile(SongProfile.CHORDS).autoProfile)
        assertEquals(SongProfile.CHORDS, Look().profile)
    }

    @Test fun `the question offers lyrics and chords, and tells which was picked`() {
        var picked: SiteKind? = null
        compose.setContent { StagebookTheme { ImportKindDialog({ picked = it }, {}) } }
        compose.onNodeWithText(str(R.string.import_kind_title)).assertExists()
        compose.onNodeWithText(str(R.string.import_kind_chords)).assertExists()
        compose.onNodeWithText(str(R.string.import_kind_lyrics)).performClick()
        assertEquals(SiteKind.LYRICS, picked)
    }

    private fun importer(look: Look, kind: String): ImportViewModel = runBlocking {
        val repo = LibraryRepository(context, CoroutineScope(Dispatchers.IO))
        withTimeout(5_000) { repo.loaded.first { it } }
        repo.updateLook { look }
        ImportViewModel(
            repo, br.app.stagebook.data.PageImporter(repo, MusicBrainz(), br.app.stagebook.data.DeezerBpm(), br.app.stagebook.data.Connectivity(context)),
            ImportDraft(), SavedStateHandle(mapOf("query" to "", "kind" to kind)), br.app.stagebook.data.ImportQueue(repo),
        )
    }

    @Test fun `the importer opens on the site of what was asked for`() {
        val auto = Look().withProfile(SongProfile.AUTO)
        assertEquals(ImportSites.LYRICS.first().url, importer(auto, "LYRICS").home)
        assertEquals(ImportSites.CHORDS.first().url, importer(auto, "CHORDS").home)
        // no answer (the other profiles): the profile's own kind, as before
        assertEquals(ImportSites.CHORDS.first().url, importer(Look(), "").home)
        assertEquals(ImportSites.LYRICS.first().url, importer(Look(chords = false), "").home)
    }

    // a tall screen: the card is far down a list that only composes what shows
    @Config(qualifiers = "pt-rBR-w1280dp-h4000dp-port-hdpi")
    @Test fun `Settings offers automatic beside the other two profiles`() {
        var chosen: SongProfile? = null
        compose.setContent { StagebookTheme { SettingsScreen(SettingsUiState(loading = false), SettingsActions(onProfile = { chosen = it }), section = SettingsSection.LYRICS) } }
        compose.onNodeWithText(str(R.string.settings_lyrics_only)).assertExists()
        // "Automático" is also an answer of other cards further down (the columns): the profile's comes first
        compose.onAllNodesWithText(str(R.string.settings_profile_auto))[0].performClick()
        assertEquals(SongProfile.AUTO, chosen)
    }

    @Test fun `automatic lists the chord sites and the lyrics sites`() {
        val auto = SettingsUiState(loading = false, look = Look().withProfile(SongProfile.AUTO))
        compose.setContent { StagebookTheme { SettingsScreen(auto, SettingsActions(), section = SettingsSection.IMPORT) } }
        assertEquals(1, compose.onAllNodesWithText(str(R.string.settings_chord_sites)).fetchSemanticsNodes().size)
        assertEquals(1, compose.onAllNodesWithText(str(R.string.settings_lyric_sites)).fetchSemanticsNodes().size)
        compose.onAllNodesWithText(str(R.string.settings_import_sites)).fetchSemanticsNodes().let { assertEquals(true, it.isNotEmpty()) }
    }

    @Test fun `the chords profile lists only its own sites`() {
        compose.setContent { StagebookTheme { SettingsScreen(SettingsUiState(loading = false), SettingsActions(), section = SettingsSection.IMPORT) } }
        assertEquals(true, compose.onAllNodesWithText(str(R.string.settings_chord_sites)).fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithText(str(R.string.settings_lyric_sites)).assertDoesNotExist()
    }
}
