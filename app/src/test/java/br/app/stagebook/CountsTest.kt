package br.app.stagebook

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import br.app.stagebook.ui.components.setCountText
import br.app.stagebook.ui.components.songCountText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Song and setlist counts (device test B-08): zero is "Nenhuma música", never "0 música". */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class CountsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `zero songs and setlists read as none, one and many keep the number`() {
        compose.setContent {
            Text(listOf(songCountText(0), songCountText(1), songCountText(12), setCountText(0), setCountText(3)).joinToString("|"))
        }
        compose.onNodeWithText("Nenhuma música|1 música|12 músicas|Nenhum setlist|3 setlists").assertExists()
    }
}
