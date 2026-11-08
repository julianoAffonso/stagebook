package br.app.stagebook

import android.content.pm.PackageInstaller
import br.app.stagebook.domain.InstallOutcome
import br.app.stagebook.domain.InstallPlan
import org.junit.Assert.assertEquals
import org.junit.Test

// The installer session itself (PackageInstaller) can't run in a JVM test; what's decided around it can.
class InstallPlanTest {
    @Test fun `without permission to install, the source screen comes first on any Android`() {
        assertEquals(InstallPlan.NEEDS_SOURCE_PERMISSION, InstallPlan.choose(28, canInstall = false))
        assertEquals(InstallPlan.NEEDS_SOURCE_PERMISSION, InstallPlan.choose(34, canInstall = false))
    }

    @Test fun `Android 12 and newer use the installer session`() {
        assertEquals(InstallPlan.SESSION, InstallPlan.choose(31, canInstall = true))
        assertEquals(InstallPlan.SESSION, InstallPlan.choose(36, canInstall = true))
    }

    @Test fun `after a failed session, trying again uses the system installer screen`() {
        assertEquals(InstallPlan.SCREEN, InstallPlan.choose(34, canInstall = true, sessionFailed = true))
        // the source permission still comes first
        assertEquals(InstallPlan.NEEDS_SOURCE_PERMISSION, InstallPlan.choose(34, canInstall = false, sessionFailed = true))
    }

    @Test fun `Android 11 and older keep the system installer screen`() {
        assertEquals(InstallPlan.SCREEN, InstallPlan.choose(28, canInstall = true))
        assertEquals(InstallPlan.SCREEN, InstallPlan.choose(30, canInstall = true))
    }

    @Test fun `the system asking for a tap opens its screen`() {
        assertEquals(InstallOutcome.ASK, InstallOutcome.of(PackageInstaller.STATUS_PENDING_USER_ACTION))
    }

    @Test fun `success and cancel are not failures`() {
        assertEquals(InstallOutcome.DONE, InstallOutcome.of(PackageInstaller.STATUS_SUCCESS))
        assertEquals(InstallOutcome.CANCELLED, InstallOutcome.of(PackageInstaller.STATUS_FAILURE_ABORTED))
    }

    @Test fun `every other status is a failure`() {
        listOf(
            PackageInstaller.STATUS_FAILURE, PackageInstaller.STATUS_FAILURE_BLOCKED, PackageInstaller.STATUS_FAILURE_INVALID,
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_STORAGE,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE, 99,
        ).forEach { assertEquals("status $it", InstallOutcome.FAILED, InstallOutcome.of(it)) }
    }
}
