package br.app.stagebook

import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.DeviceIdentity
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.web.WebEditorAuth
import br.app.stagebook.data.web.WebEditorServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Editor on the computer (T37): left on, the next app start begins with it off. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class WebEditorStartTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test fun `every app start begins with the editor off`() = runBlocking {
        val repo = LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        withTimeout(5_000) { repo.loaded.first { it } }
        repo.updateLook { it.copy(webEditorOn = true) }
        WebEditorServer(context, repo, DeviceIdentity(context), WebEditorAuth(context), { error("not used") }, { error("not used") }, { error("not used") })
        assertEquals(false, withTimeout(5_000) { repo.library.first { !it.look.webEditorOn } }.look.webEditorOn)
    }
}
