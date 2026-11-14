package br.app.stagebook

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.BandInbox
import br.app.stagebook.data.BandLink
import br.app.stagebook.data.DeviceIdentity
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.SetlistCloud
import br.app.stagebook.data.model.Song
import br.app.stagebook.domain.BandProtocol
import br.app.stagebook.domain.QrPayload
import br.app.stagebook.ui.share.itemText
import br.app.stagebook.ui.theme.StagebookTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A package from the internet inbox (and the band's on the network) written by this app is read by the app published
 * with version 2 (v0.1.1090) as follows: its rule is `v > 2 -> "update the app"`, and a missing `v` is 2. So `v` goes in
 * as 3 only when the package carries breaks (what that app would drop), and a package without breaks has no `v` and
 * opens in any version.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class InboxVersionTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val identity = DeviceIdentity(context)
    @get:Rule val compose = createComposeRule()

    /** What the published app reads of a package: only the rule copied from it (BandProtocol.VERSION was 2 there). */
    @Serializable private data class OldPack(val list: String, val songs: List<JsonElement> = emptyList(), val v: Int = 2)
    private fun oldAppSaysUpdate(json: String): Boolean =
        Json { ignoreUnknownKeys = true }.decodeFromString<OldPack>(json).v > 2

    private fun repo() = LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        .also { r -> runBlocking { withTimeout(5_000) { r.loaded.first { it } }; r.restore(emptyList(), emptyList()) } }

    private fun LibraryRepository.show(withBreak: Boolean): String {
        val s = createSetlist("Show")
        addToSetlist(s, listOf("A", "B", "C").map { saveSong(Song(title = it, author = "x", text = it)) })
        if (withBreak) addBreak(s)
        return s
    }

    private fun link(repo: LibraryRepository) =
        BandLink(context, identity, br.app.stagebook.data.BandTrust(context), br.app.stagebook.data.StageGate(), BandInbox(context), repo)

    private fun inboxWire(withBreak: Boolean): String {
        val repo = repo(); val cloud = SetlistCloud(repo, identity)
        val (packed, songs) = cloud.setlistPackage(repo.show(withBreak), true)!!
        return SetlistCloud.unpack(byteArrayOf('P'.code.toByte(), '1'.code.toByte()), cloud.pairPackage(packed, songs), SetlistCloud.MAX_PACKAGE)!!
    }

    private fun pairWire(withBreak: Boolean): String {
        val repo = repo(); val cloud = SetlistCloud(repo, identity)
        val (packed, songs) = cloud.setlistPackage(repo.show(withBreak), true)!!
        return link(repo).packageBytes(packed, songs).decodeToString()
    }

    @Test fun `the internet inbox - with breaks v is 3 and the published app says update, without it has no v and opens`() {
        val with = inboxWire(true); val without = inboxWire(false)
        assertEquals("3", Json.parseToJsonElement(with).jsonObject["v"]?.jsonPrimitive?.content)
        assertNull(Json.parseToJsonElement(without).jsonObject["v"])
        assertTrue(oldAppSaysUpdate(with))
        assertFalse(oldAppSaysUpdate(without))
    }

    @Test fun `the band package - with breaks v is 3, without it has no v`() {
        val with = pairWire(true); val without = pairWire(false)
        assertEquals("3", Json.parseToJsonElement(with).jsonObject["v"]?.jsonPrimitive?.content)
        assertNull(Json.parseToJsonElement(without).jsonObject["v"])
        assertTrue(oldAppSaysUpdate(with))
        assertFalse(oldAppSaysUpdate(without))
    }

    @Test fun `loose songs never ask for an update`() {
        val repo = repo(); val cloud = SetlistCloud(repo, identity)
        val ids = listOf("A", "B").map { repo.saveSong(Song(title = it, author = "x", text = it)) }
        val (packed, songs) = cloud.songsPackage(ids)!!
        assertEquals(BandProtocol.PACKAGE_BASE, BandProtocol.packageVersion(packed))
        assertNull(Json.parseToJsonElement(cloud.pairPackage(packed, songs).let { SetlistCloud.unpack(byteArrayOf('P'.code.toByte(), '1'.code.toByte()), it, SetlistCloud.MAX_PACKAGE)!! }).jsonObject["v"])
    }

    @Test fun `this app opens both, packages from the published app without v, and says newer for a version above its own`() {
        val repo = repo(); val cloud = SetlistCloud(repo, identity); val link = link(repo)
        val sets = listOf(true, false).map { cloud.setlistPackage(repo.show(it), true)!! }
        for ((packed, songs) in sets) {
            assertNotNull(cloud.openPairPackage(cloud.pairPackage(packed, songs)))
            assertNotNull(link.openPackage(link.packageBytes(packed, songs)))
        }
        // what the published app wrote (no v at all): same reading as before
        val (plain, songs) = cloud.setlistPackage(repo.show(false), true)!!
        val legacy = """{"list":"${java.util.Base64.getEncoder().encodeToString(plain)}","songs":[]}"""
        assertNotNull(link.openPackage(legacy.toByteArray()))
        assertEquals(QrPayload.unpack(plain), cloud.openPairPackage(SetlistCloud.pack(byteArrayOf('P'.code.toByte(), '1'.code.toByte()), legacy))!!.text)
        // a version above this app's: the inbox says "update", the network does not open it
        val future = """{"list":"${java.util.Base64.getEncoder().encodeToString(plain)}","songs":[],"v":${BandProtocol.VERSION + 1}}"""
        assertEquals(SetlistCloud.NEWER, cloud.openPairPackage(SetlistCloud.pack(byteArrayOf('P'.code.toByte(), '1'.code.toByte()), future))!!.text)
        assertNull(link.openPackage(future.toByteArray()))
        assertTrue(songs.isNotEmpty())
    }

    private fun newerItem() = BandInbox.Item("1", "ana", "Ana", SetlistCloud.NEWER)

    @Test @Config(qualifiers = "en")
    fun `the inbox line for a newer app, in English, says to update the app`() {
        compose.setContent { StagebookTheme { Text(itemText(newerItem())) } }
        compose.onNodeWithText("Ana sent something from a newer Stagebook. Update the app to check it.").assertIsDisplayed()
    }

    @Test @Config(qualifiers = "pt-rBR")
    fun `the inbox line for a newer app, in Portuguese, says to update the app`() {
        compose.setContent { StagebookTheme { Text(itemText(newerItem())) } }
        compose.onNodeWithText("Ana mandou algo de um Stagebook mais novo. Atualize o app para conferir.").assertIsDisplayed()
    }
}
