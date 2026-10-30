package br.app.stagebook

import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.AppUpdater
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.model.Look
import br.app.stagebook.data.model.anyOff
import br.app.stagebook.data.model.essential
import br.app.stagebook.ui.settings.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Modo essencial (T19): as quatro partes desligadas; mexer numa parte tira do modo; desligar religa o que é padrão. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class EssentialModeTest {
    @get:org.junit.Rule val compose = androidx.compose.ui.test.junit4.createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test fun `essential only with all four off`() {
        assertFalse(Look().essential)   // app novo: tudo ligado, o player também
        assertFalse(Look().anyOff)        // e por isso nenhum aviso ao abrir
        assertTrue(Look(extrasOn = false).anyOff)
        assertTrue(Look(bandOn = false, internetOn = false, extrasOn = false, playerMode = false).essential)
        assertFalse(Look(bandOn = false, internetOn = false, extrasOn = false, playerMode = true).essential)
    }

    @Test fun `turning on turns everything off - turning a part back on leaves the mode - turning off brings back band internet and extras`() = runBlocking {
        val repo = LibraryRepository(context, CoroutineScope(Dispatchers.IO))
        val vm = SettingsViewModel(
            repo, context, AppUpdater(context),
            br.app.stagebook.data.MidiPedal(context, repo, CoroutineScope(Dispatchers.IO)),
            br.app.stagebook.data.Backup(context, repo, br.app.stagebook.data.TrackStore(context)),
        )
        suspend fun until(ok: (Look) -> Boolean) = withTimeout(5_000) { repo.library.first { ok(it.look) } }.look

        vm.setPlayerMode(true)
        until { it.playerMode }
        vm.setEssential(true)
        until { it.essential }

        vm.setInternetOn(true)
        assertFalse(until { it.internetOn }.essential)

        vm.setEssential(true)
        until { it.essential }
        vm.setEssential(false)
        val back = until { it.bandOn }
        assertTrue(back.internetOn && back.extrasOn)
        assertFalse(back.playerMode)   // o player continua como estava no modo (desligado)
    }

    @Test fun `without extras the chord window keeps the diagram and shapes - no sound or Details`() {
        compose.setContent {
            br.app.stagebook.ui.theme.StagebookTheme {
                br.app.stagebook.ui.stage.ChordShapePopup("G", br.app.stagebook.data.model.Instrument.ACOUSTIC_GUITAR, "[G]linha", {}, {}, extras = false)
            }
        }
        compose.onNodeWithContentDescription("Tocar").assertDoesNotExist()
        compose.onNodeWithText("Detalhes").assertDoesNotExist()
        compose.onNodeWithContentDescription("Próxima variação").assertExists()
    }

    @Test fun `band on the network off ends Lead the band and does not come back`() = runBlocking {
        val repo = LibraryRepository(context, CoroutineScope(Dispatchers.IO))
        withTimeout(5_000) { repo.loaded.first { it } }
        val link = br.app.stagebook.data.BandLink(
            context, br.app.stagebook.data.DeviceIdentity(context), br.app.stagebook.data.BandTrust(context),
            br.app.stagebook.data.StageGate(), br.app.stagebook.data.BandInbox(context), repo,
        )
        val conduct = br.app.stagebook.data.BandConduct(link, repo, br.app.stagebook.data.DeviceIdentity(context))
        conduct.start("s1", "Ensaio", listOf(br.app.stagebook.domain.Conduct.Item("Música")))
        assertTrue(conduct.role.value is br.app.stagebook.data.BandConduct.Role.Conducting)
        repo.updateLook { it.copy(bandOn = false) }
        withTimeout(5_000) { conduct.role.first { it == br.app.stagebook.data.BandConduct.Role.None } }
        kotlinx.coroutines.delay(300)   // o envio que já tinha saído não traz a condução de volta
        assertEquals(br.app.stagebook.data.BandConduct.Role.None, conduct.role.value)
        assertFalse(link.running.value)
    }

    @Test fun `internet off - nothing goes out to the band internet`() = runBlocking {
        val repo = LibraryRepository(context, CoroutineScope(Dispatchers.IO))
        withTimeout(5_000) { repo.loaded.first { it } }
        repo.updateLook { it.copy(internetOn = false) }
        withTimeout(5_000) { repo.library.first { !it.look.internetOn } }
        val cloud = br.app.stagebook.data.SetlistCloud(repo, br.app.stagebook.data.DeviceIdentity(context))
        assertEquals(br.app.stagebook.data.SetlistCloud.Received.Offline, cloud.receive(br.app.stagebook.domain.ShareCode.generate()))
        assertTrue(cloud.acks(br.app.stagebook.domain.ShareCode.generate()).isEmpty())
    }

    @Test fun `internet off - share keeps the QR and no no-internet notice`() {
        compose.setContent {
            br.app.stagebook.ui.theme.StagebookTheme {
                br.app.stagebook.ui.share.ShareOptions(12, online = false, onQr = {}, onInternet = {}, internetOn = false)
            }
        }
        compose.onNodeWithText("QR na tela").assertExists()
        compose.onNodeWithText("Enviar pela internet").assertDoesNotExist()
        compose.onNodeWithText("Sem internet", substring = true).assertDoesNotExist()
    }

    @Test fun `alert on open - don't show again lasts until the mode is turned off`() = runBlocking {
        val repo = LibraryRepository(context, CoroutineScope(Dispatchers.IO))
        withTimeout(5_000) { repo.loaded.first { it } }
        val vm = SettingsViewModel(
            repo, context, AppUpdater(context),
            br.app.stagebook.data.MidiPedal(context, repo, CoroutineScope(Dispatchers.IO)),
            br.app.stagebook.data.Backup(context, repo, br.app.stagebook.data.TrackStore(context)),
        )
        suspend fun until(ok: (Look) -> Boolean) = withTimeout(5_000) { repo.library.first { ok(it.look) } }.look
        vm.setEssential(true)
        until { it.essential }
        repo.updateLook { it.copy(essentialAlertOff = true) }   // "Não mostrar de novo"
        until { it.essentialAlertOff }
        vm.setExtrasOn(true)   // religar uma parte também sai do modo
        assertFalse(until { it.extrasOn }.essentialAlertOff)
        vm.setEssential(true)
        assertFalse(until { it.essential }.essentialAlertOff)   // ligou de novo: o aviso volta
    }

    @Test fun `alert with don't show again - ok returns the checkbox`() {
        var answer: Boolean? = null
        compose.setContent {
            br.app.stagebook.ui.theme.StagebookTheme {
                br.app.stagebook.ui.components.InfoDialog("Modo essencial ligado", "Texto", onDismiss = {}, neverAgain = { answer = it })
            }
        }
        compose.onNodeWithText("Não mostrar de novo").performClick()
        compose.onNodeWithText("OK").performClick()
        assertEquals(true, answer)
    }

    @Test fun `alert lists what is off and the link goes to Settings`() {
        var opened = false
        compose.setContent {
            br.app.stagebook.ui.theme.StagebookTheme {
                br.app.stagebook.ui.home.EssentialAlert(Look(bandOn = false, extrasOn = false), {}, {}, onOpen = { opened = true })
            }
        }
        compose.onNodeWithText("•  Banda na rede").assertExists()
        compose.onNodeWithText("•  Extras do palco").assertExists()
        compose.onNodeWithText("•  Internet").assertDoesNotExist()
        compose.onNodeWithText("Ajustes → Modo essencial").performClick()
        assertTrue(opened)
    }
}
