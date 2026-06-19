package site.addzero.smart.intentions.kotlin.movefunctiontocurrentfile

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import site.addzero.smart.intentions.core.SmartIntentionsMessages

class SmartMoveFunctionToCurrentFileQuickFixTest : BasePlatformTestCase() {
    fun testMovesPrivateTopLevelFunctionIntoCurrentFile() {
        myFixture.addFileToProject(
            "demo/shared/RenderLabel.kt",
            """
            package demo.shared

            fun renderLabel(value: String): String {
                return value
            }
            """.trimIndent(),
        )

        val sourceFile = myFixture.addFileToProject(
            "demo/DevEnvironmentScreen.kt",
            """
            package demo

            import demo.shared.renderLabel

            private fun DevEnvironmentCard(): String {
                return renderLabel("card")
            }
            """.trimIndent(),
        )

        val currentFile = myFixture.configureByText(
            "DevEnvironmentRoute.kt",
            """
            package demo

            fun render(): String {
                return DevEnviron<caret>mentCard()
            }
            """.trimIndent(),
        )

        myFixture.enableInspections(SmartMoveFunctionToCurrentFileInspection())
        val action = myFixture.findSingleIntention(SmartIntentionsMessages.MOVE_FUNCTION_TO_CURRENT_FILE)
        action.invoke(project, myFixture.editor, myFixture.file)

        myFixture.checkResult(
            """
            package demo

            import demo.shared.renderLabel

            fun render(): String {
                return DevEnvironmentCard()
            }

            private fun DevEnvironmentCard(): String {
                return renderLabel("card")
            }
            """.trimIndent(),
        )

        assertFalse(sourceFile.isValid)
    }

    fun testDoesNotOfferWhenMovedFunctionDependsOnPrivateHelper() {
        myFixture.addFileToProject(
            "demo/DevEnvironmentScreen.kt",
            """
            package demo

            private fun helper(): String {
                return "helper"
            }

            private fun DevEnvironmentCard(): String {
                return helper()
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "DevEnvironmentRoute.kt",
            """
            package demo

            fun render(): String {
                return DevEnviron<caret>mentCard()
            }
            """.trimIndent(),
        )

        myFixture.enableInspections(SmartMoveFunctionToCurrentFileInspection())
        val actions = myFixture.filterAvailableIntentions(SmartIntentionsMessages.MOVE_FUNCTION_TO_CURRENT_FILE)
        assertEmpty(actions)
    }
}
