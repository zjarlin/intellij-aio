package site.addzero.smart.intentions.kotlin.inheritbase

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

class InheritFromBaseSupportTest : BasePlatformTestCase() {
    fun testStartsEditorInputWithColonBeforeClassBody() {
        myFixture.configureByText(
            "Controller.kt",
            """
            package demo

            class DemoCon<caret>troller {
            }
            """.trimIndent(),
        )

        val klass = currentClass()

        InheritFromBaseSupport.startEditorInput(project, myFixture.editor, klass)

        myFixture.checkResult(
            """
            package demo

            class DemoController : <caret> {
            }
            """.trimIndent(),
        )
    }

    fun testStartsEditorInputWithCommaAfterExistingSuperType() {
        myFixture.configureByText(
            "Controller.kt",
            """
            package demo

            interface Existing

            class DemoCon<caret>troller : Existing {
            }
            """.trimIndent(),
        )

        val klass = currentClass("DemoController")

        InheritFromBaseSupport.startEditorInput(project, myFixture.editor, klass)

        myFixture.checkResult(
            """
            package demo

            interface Existing

            class DemoController : Existing, <caret> {
            }
            """.trimIndent(),
        )
    }

    private fun currentClass(name: String? = null): KtClass {
        val classes = myFixture.file.collectDescendantsOfType<KtClass>()
        return if (name == null) {
            classes.last()
        } else {
            classes.first { klass -> klass.name == name }
        }
    }
}
