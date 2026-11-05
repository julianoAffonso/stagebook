package br.app.stagebook

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.provider.Settings
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.UpdateOffer
import br.app.stagebook.data.device.AutoBlocker
import br.app.stagebook.data.device.AutoBlockerBackOn
import br.app.stagebook.ui.AppUpdateDialog
import br.app.stagebook.ui.settings.AdvancedDetail
import br.app.stagebook.ui.settings.AdvancedUi
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

    private val secure get() = app.contentResolver
    private fun state() = Settings.Secure.getString(secure, "rampart_main_switch_enabled")
    private fun grant() = shadowOf(app).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
    private fun on() = Settings.Secure.putString(secure, "rampart_main_switch_enabled", "1")

    @Test fun `without the switch the app never touches it`() {
        grant(); on()
        AutoBlocker.beforeInstall(app)
        assertEquals("1", state())
    }

    @Test fun `without the computer's permission the switch means nothing`() {
        on(); AutoBlocker.setBypass(app, true)
        assertFalse(AutoBlocker.bypass(app))
        AutoBlocker.beforeInstall(app)
        assertEquals("1", state())
    }

    @Test fun `it's off for the install and back on after`() {
        grant(); on(); AutoBlocker.setBypass(app, true)
        AutoBlocker.beforeInstall(app)
        assertEquals("0", state())
        AutoBlocker.afterInstall(app)
        assertEquals("1", state())
    }

    @Test fun `the update replacing the app puts it back`() {
        grant(); on(); AutoBlocker.setBypass(app, true)
        AutoBlocker.beforeInstall(app)
        AutoBlockerBackOn().onReceive(app, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
        assertEquals("1", state())
    }

    @Test fun `the choice stays for the next update`() {
        grant(); on(); AutoBlocker.setBypass(app, true)
        AutoBlocker.beforeInstall(app); AutoBlocker.afterInstall(app)
        AutoBlocker.beforeInstall(app)
        assertEquals("0", state())
    }

    @Test fun `someone who keeps it off stays off`() {
        grant(); Settings.Secure.putString(secure, "rampart_main_switch_enabled", "0"); AutoBlocker.setBypass(app, true)
        AutoBlocker.beforeInstall(app); AutoBlocker.afterInstall(app)
        assertEquals("0", state())
    }

    @Test fun `with nothing pending the app doesn't turn it on`() {
        grant(); Settings.Secure.putString(secure, "rampart_main_switch_enabled", "0")
        AutoBlocker.afterInstall(app)
        assertEquals("0", state())
    }

    @Test fun `without the computer's permission the switch shows locked`() {
        compose.setContent { StagebookTheme { AdvancedDetail(AdvancedUi(canWrite = false, autoBlocker = true), { _, _ -> }, {}) } }
        compose.onNode(isToggleable() and hasAnyDescendantOrSelfText()).assertIsNotEnabled()
    }

    @Test fun `with the permission the switch works`() {
        compose.setContent { StagebookTheme { AdvancedDetail(AdvancedUi(canWrite = true, autoBlocker = true), { _, _ -> }, {}) } }
        compose.onNode(isToggleable() and hasAnyDescendantOrSelfText()).assertIsEnabled()
    }

    private fun hasAnyDescendantOrSelfText() = hasText("Desligar só para atualizar o Stagebook", substring = true)

    @Test fun `offering the update doesn't mention it yet`() {
        compose.setContent { StagebookTheme { AppUpdateDialog(UpdateOffer.Available("0.1.733"), {}, {}, {}, {}, onAutoBlocker = {}) } }
        compose.onNodeWithText("Abrir o Bloqueador automático").assertDoesNotExist()
    }
}
