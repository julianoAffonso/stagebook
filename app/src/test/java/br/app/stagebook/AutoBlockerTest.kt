package br.app.stagebook

import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.UpdateOffer
import br.app.stagebook.data.device.AutoBlocker
import br.app.stagebook.ui.AppUpdateDialog
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Samsung's Auto Blocker: its screen opens only where it exists, at the moment of installing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class AutoBlockerTest {
    @get:Rule val compose = createComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val ready = UpdateOffer.Ready(java.io.File("update.apk"))

    @Test fun `another maker doesn't have it`() = assertFalse(AutoBlocker.available(app))

    @Test fun `a Samsung has it`() {
        val screen = ResolveInfo().apply { activityInfo = ActivityInfo().apply { packageName = "com.samsung.android.rampart"; name = "Screen" } }
        shadowOf(app.packageManager).addResolveInfoForIntent(AutoBlocker.screen, screen)
        assertTrue(AutoBlocker.available(app))
    }

    @Test fun `only Samsung's app can answer`() = assertEquals("com.samsung.android.rampart", AutoBlocker.screen.`package`)

    @Test fun `the downloaded update offers the screen`() {
        var opened = false
        compose.setContent { StagebookTheme { AppUpdateDialog(ready, {}, {}, {}, {}, onAutoBlocker = { opened = true }) } }
        compose.onNodeWithText("Abrir o Bloqueador automático").performClick()
        assertTrue(opened)
    }

    @Test fun `without it the window stays the same`() {
        compose.setContent { StagebookTheme { AppUpdateDialog(ready, {}, {}, {}, {}) } }
        compose.onNodeWithText("Abrir o Bloqueador automático").assertDoesNotExist()
    }

    @Test fun `offering the update doesn't mention it yet`() {
        compose.setContent { StagebookTheme { AppUpdateDialog(UpdateOffer.Available("0.1.733"), {}, {}, {}, {}, onAutoBlocker = {}) } }
        compose.onNodeWithText("Abrir o Bloqueador automático").assertDoesNotExist()
    }
}
