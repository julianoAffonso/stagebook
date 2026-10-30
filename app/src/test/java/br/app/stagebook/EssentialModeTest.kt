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

    @Test fun `essencial so com as quatro desligadas`() {
        assertFalse(Look().essential)   // app novo: tudo ligado, o player também
        assertFalse(Look().anyOff)        // e por isso nenhum aviso ao abrir
        assertTrue(Look(extrasOn = false).anyOff)
        assertTrue(Look(bandOn = false, internetOn = false, extrasOn = false, playerMode = false).essential)
        assertFalse(Look(bandOn = false, internetOn = false, extrasOn = false, playerMode = true).essential)
    }

    @Test fun `ligar desliga tudo - religar uma parte sai do modo - desligar religa banda internet e extras`() = runBlocking {
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

    @Test fun `sem os extras a janela do acorde fica com o desenho e as variacoes - sem som nem Detalhes`() {
        compose.setContent {
            br.app.stagebook.ui.theme.StagebookTheme {
                br.app.stagebook.ui.stage.ChordShapePopup("G", br.app.stagebook.data.model.Instrument.VIOLAO, "[G]linha", {}, {}, extras = false)
            }
        }
        compose.onNodeWithContentDescription("Tocar").assertDoesNotExist()
        compose.onNodeWithText("Detalhes").assertDoesNotExist()
        compose.onNodeWithContentDescription("Próxima variação").assertExists()
    }

    @Test fun `banda na rede desligada encerra o Quem conduz e nao religa`() = runBlocking {
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

    @Test fun `internet desligada - nada sai para a internet da banda`() = runBlocking {
        val repo = LibraryRepository(context, CoroutineScope(Dispatchers.IO))
        withTimeout(5_000) { repo.loaded.first { it } }
        repo.updateLook { it.copy(internetOn = false) }
        withTimeout(5_000) { repo.library.first { !it.look.internetOn } }
        val cloud = br.app.stagebook.data.SetlistCloud(repo, br.app.stagebook.data.DeviceIdentity(context))
        assertEquals(br.app.stagebook.data.SetlistCloud.Received.Offline, cloud.receive(br.app.stagebook.domain.ShareCode.generate()))
        assertTrue(cloud.acks(br.app.stagebook.domain.ShareCode.generate()).isEmpty())
    }

    @Test fun `internet desligada - compartilhar fica com o QR e sem o aviso de sem internet`() {
        compose.setContent {
            br.app.stagebook.ui.theme.StagebookTheme {
                br.app.stagebook.ui.share.ShareOptions(12, online = false, onQr = {}, onInternet = {}, internetOn = false)
            }
        }
        compose.onNodeWithText("QR na tela").assertExists()
        compose.onNodeWithText("Enviar pela internet").assertDoesNotExist()
        compose.onNodeWithText("Sem internet", substring = true).assertDoesNotExist()
    }

    @Test fun `aviso ao abrir - nao mostrar de novo vale ate o modo desligar`() = runBlocking {
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

    @Test fun `aviso com nao mostrar de novo - o ok devolve a caixa`() {
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

    @Test fun `aviso lista o que esta desligado e o link leva aos Ajustes`() {
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
