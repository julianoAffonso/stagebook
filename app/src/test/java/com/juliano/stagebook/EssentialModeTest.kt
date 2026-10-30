package com.juliano.stagebook

import androidx.test.core.app.ApplicationProvider
import com.juliano.stagebook.data.AppUpdater
import com.juliano.stagebook.data.LibraryRepository
import com.juliano.stagebook.data.model.Look
import com.juliano.stagebook.data.model.essential
import com.juliano.stagebook.ui.settings.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test fun `essencial so com as quatro desligadas`() {
        assertFalse(Look().essential)   // app novo: tudo ligado menos o player
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
}
