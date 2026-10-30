package com.juliano.stagebook

import androidx.test.core.app.ApplicationProvider
import com.juliano.stagebook.data.AppUpdater
import com.juliano.stagebook.data.LibraryRepository
import com.juliano.stagebook.data.model.Look
import com.juliano.stagebook.data.model.anyOff
import com.juliano.stagebook.data.model.essential
import com.juliano.stagebook.ui.settings.SettingsViewModel
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
            com.juliano.stagebook.data.MidiPedal(context, repo, CoroutineScope(Dispatchers.IO)),
            com.juliano.stagebook.data.Backup(context, repo, com.juliano.stagebook.data.TrackStore(context)),
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
            com.juliano.stagebook.ui.theme.StagebookTheme {
                com.juliano.stagebook.ui.stage.ChordShapePopup("G", com.juliano.stagebook.data.model.Instrument.VIOLAO, "[G]linha", {}, {}, extras = false)
            }
        }
        compose.onNodeWithContentDescription("Tocar").assertDoesNotExist()
        compose.onNodeWithText("Detalhes").assertDoesNotExist()
        compose.onNodeWithContentDescription("Próxima variação").assertExists()
    }

    @Test fun `banda na rede desligada encerra o Quem conduz e nao religa`() = runBlocking {
        val repo = LibraryRepository(context, CoroutineScope(Dispatchers.IO))
        withTimeout(5_000) { repo.loaded.first { it } }
        val link = com.juliano.stagebook.data.BandLink(
            context, com.juliano.stagebook.data.DeviceIdentity(context), com.juliano.stagebook.data.BandTrust(context),
            com.juliano.stagebook.data.StageGate(), com.juliano.stagebook.data.BandInbox(context), repo,
        )
        val conduct = com.juliano.stagebook.data.BandConduct(link, repo, com.juliano.stagebook.data.DeviceIdentity(context))
        conduct.start("s1", "Ensaio", listOf(com.juliano.stagebook.domain.Conduct.Item("Música")))
        assertTrue(conduct.role.value is com.juliano.stagebook.data.BandConduct.Role.Conducting)
        repo.updateLook { it.copy(bandOn = false) }
        withTimeout(5_000) { conduct.role.first { it == com.juliano.stagebook.data.BandConduct.Role.None } }
        kotlinx.coroutines.delay(300)   // o envio que já tinha saído não traz a condução de volta
        assertEquals(com.juliano.stagebook.data.BandConduct.Role.None, conduct.role.value)
        assertFalse(link.running.value)
    }

    @Test fun `internet desligada - nada sai para a internet da banda`() = runBlocking {
        val repo = LibraryRepository(context, CoroutineScope(Dispatchers.IO))
        withTimeout(5_000) { repo.loaded.first { it } }
        repo.updateLook { it.copy(internetOn = false) }
        withTimeout(5_000) { repo.library.first { !it.look.internetOn } }
        val cloud = com.juliano.stagebook.data.SetlistCloud(repo, com.juliano.stagebook.data.DeviceIdentity(context))
        assertEquals(com.juliano.stagebook.data.SetlistCloud.Received.Offline, cloud.receive(com.juliano.stagebook.domain.ShareCode.generate()))
        assertTrue(cloud.acks(com.juliano.stagebook.domain.ShareCode.generate()).isEmpty())
    }

    @Test fun `internet desligada - compartilhar fica com o QR e sem o aviso de sem internet`() {
        compose.setContent {
            com.juliano.stagebook.ui.theme.StagebookTheme {
                com.juliano.stagebook.ui.share.ShareOptions(12, online = false, onQr = {}, onInternet = {}, internetOn = false)
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
            com.juliano.stagebook.data.MidiPedal(context, repo, CoroutineScope(Dispatchers.IO)),
            com.juliano.stagebook.data.Backup(context, repo, com.juliano.stagebook.data.TrackStore(context)),
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
            com.juliano.stagebook.ui.theme.StagebookTheme {
                com.juliano.stagebook.ui.components.InfoDialog("Modo essencial ligado", "Texto", onDismiss = {}, neverAgain = { answer = it })
            }
        }
        compose.onNodeWithText("Não mostrar de novo").performClick()
        compose.onNodeWithText("OK").performClick()
        assertEquals(true, answer)
    }
}
