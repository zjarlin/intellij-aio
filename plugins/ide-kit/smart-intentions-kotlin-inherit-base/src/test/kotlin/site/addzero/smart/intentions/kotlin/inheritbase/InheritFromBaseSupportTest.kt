package site.addzero.smart.intentions.kotlin.inheritbase

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

class InheritFromBaseSupportTest : BasePlatformTestCase() {
    fun testAddsGenericBaseInterfaceAndImport() {
        myFixture.addFileToProject(
            "site/addzero/crud/BaseController.kt",
            """
            package site.addzero.crud

            interface BaseController<E : Any>
            """.trimIndent(),
        )
        myFixture.configureByText(
            "RuleConditionsController.kt",
            """
            package demo.ruleconditions

            class IotRuleConditionsCon<caret>troller {
            }
            """.trimIndent(),
        )

        val klass = currentClass()
        val choice = BaseTypeChoice(
            displayName = "BaseController",
            typeText = "site.addzero.crud.BaseController",
            qualifiedName = "site.addzero.crud.BaseController",
            packageName = "site.addzero.crud",
            typeParameterNames = listOf("E"),
        )

        InheritFromBaseSupport.apply(project, myFixture.editor, klass, choice)

        myFixture.checkResult(
            """
            package demo.ruleconditions

            import site.addzero.crud.BaseController

            class IotRuleConditionsController : BaseController<EArg> {
            }
            """.trimIndent(),
        )
    }

    fun testAddsSecondSuperTypeWithComma() {
        myFixture.configureByText(
            "Controller.kt",
            """
            package demo

            interface Existing
            interface BaseApi<T>

            class DemoCon<caret>troller : Existing {
            }
            """.trimIndent(),
        )

        val klass = currentClass("DemoController")
        val choice = BaseTypeChoice(
            displayName = "BaseApi",
            typeText = "BaseApi",
            qualifiedName = null,
            packageName = null,
            typeParameterNames = listOf("T"),
        )

        InheritFromBaseSupport.apply(project, myFixture.editor, klass, choice)

        myFixture.checkResult(
            """
            package demo

            interface Existing
            interface BaseApi<T>

            class DemoController : Existing, BaseApi<TArg> {
            }
            """.trimIndent(),
        )
    }

    fun testKeepsExplicitGenericText() {
        myFixture.configureByText(
            "Controller.kt",
            """
            package demo

            class DemoCon<caret>troller
            """.trimIndent(),
        )

        val klass = currentClass()
        val choice = BaseTypeChoice(
            displayName = "BaseController",
            typeText = "BaseController<RuleConditionsDO>",
            qualifiedName = null,
            packageName = null,
            typeParameterNames = emptyList(),
        )

        InheritFromBaseSupport.apply(project, myFixture.editor, klass, choice)

        myFixture.checkResult(
            """
            package demo

            class DemoController : BaseController<RuleConditionsDO>
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
