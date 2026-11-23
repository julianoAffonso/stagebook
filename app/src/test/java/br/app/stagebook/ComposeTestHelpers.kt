package br.app.stagebook

import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.text.TextLayoutResult
import br.app.stagebook.data.web.HttpRequest
import br.app.stagebook.data.web.HttpResponse
import br.app.stagebook.data.web.WebEditorApi
import br.app.stagebook.data.web.WebEditorAuth
import br.app.stagebook.ui.PedalInput
import java.io.File

/** What the screen tests do the same way everywhere (audit 22/11: each was written again in several files). */

/** One press of a pedal button: down and up, then the screen settles. */
internal fun pressPedal(compose: ComposeTestRule, pedal: PedalInput, key: Int = KeyEvent.KEYCODE_PAGE_DOWN) {
    compose.runOnUiThread {
        pedal.dispatch(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, key, 0))
        pedal.dispatch(KeyEvent(0, 0, KeyEvent.ACTION_UP, key, 0))
    }
    compose.waitForIdle()
}

/** How this node's text was laid out (its lines, whether it was cut). */
internal fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
    val out = ArrayList<TextLayoutResult>()
    fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(out)
    return out.single()
}

/** The computer the web editor tests call from. */
internal val TEST_DEVICE = WebEditorAuth.Device("d", "Chrome · Windows", "h", 0)

/** One call to the editor on the computer's API, with the query read from [path] as the server does. */
internal fun WebEditorApi.send(method: String, path: String, body: String = "", file: File? = null, device: WebEditorAuth.Device = TEST_DEVICE): HttpResponse =
    handle(
        HttpRequest(
            method, path.substringBefore('?'),
            path.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.associate { it.substringBefore('=') to it.substringAfter('=') },
            body = body.toByteArray(), file = file,
        ),
        device,
    )!!
