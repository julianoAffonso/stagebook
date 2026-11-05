package br.app.stagebook

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.AutoZoomSizes
import br.app.stagebook.domain.StageFit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The automatic zoom stays on this device, past closing the app. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class AutoZoomSizesTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test fun `a size found comes back after the app closes`() {
        AutoZoomSizes(context).put("a", StageFit.Found(0.72f, 2))
        assertEquals(StageFit.Found(0.72f, 2), AutoZoomSizes(context).get("a"))
    }

    @Test fun `an unknown song has no size`() = assertNull(AutoZoomSizes(context).get("nothing"))

    @Test fun `a damaged value is measured again`() {
        context.getSharedPreferences("auto_zoom", Context.MODE_PRIVATE).edit { putString("a", "abc") }
        assertNull(AutoZoomSizes(context).get("a"))
    }

    @Test fun `old sizes don't pile up`() {
        val sizes = AutoZoomSizes(context)
        repeat(2001) { sizes.put("song$it", StageFit.Found(1f, 1)) }
        assertEquals(1, context.getSharedPreferences("auto_zoom", Context.MODE_PRIVATE).all.size)
    }
}
