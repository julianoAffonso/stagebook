package br.app.stagebook

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import br.app.stagebook.ui.components.AppIcons
import br.app.stagebook.ui.components.MenuAction
import br.app.stagebook.ui.components.OverflowMenu
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Menu ⋮ das listas e das barras de cima: abre fechado, a ação escolhida roda e o menu fecha. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w1280dp-h800dp-land-hdpi", application = android.app.Application::class)
class OverflowMenuTest {
    @get:Rule val compose = createComposeRule()
    private var picked = ""

    private fun menu() = compose.setContent {
        StagebookTheme {
            OverflowMenu(
                listOf(
                    MenuAction("Editar", AppIcons.Edit, { picked = "editar" }),
                    MenuAction("Excluir", AppIcons.Delete, { picked = "excluir" }, danger = true),
                ),
            )
        }
    }

    @Test fun `começa fechado e abre no toque`() {
        menu()
        compose.onAllNodesWithText("Excluir").assertCountEquals(0)
        compose.onNodeWithContentDescription("Mais opções").performClick()
        compose.onNodeWithText("Editar").assertExists()
        compose.onNodeWithText("Excluir").assertExists()
    }

    @Test fun `a ação escolhida roda e o menu fecha`() {
        menu()
        compose.onNodeWithContentDescription("Mais opções").performClick()
        compose.onNodeWithText("Excluir").performClick()
        assertEquals("excluir", picked)
        compose.onAllNodesWithText("Editar").assertCountEquals(0)
    }
}
