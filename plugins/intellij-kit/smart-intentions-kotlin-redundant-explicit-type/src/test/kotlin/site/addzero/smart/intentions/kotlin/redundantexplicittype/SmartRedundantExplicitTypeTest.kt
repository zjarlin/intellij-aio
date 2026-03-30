package site.addzero.smart.intentions.kotlin.redundantexplicittype

import com.intellij.openapi.application.PathManager
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.psi.PsiElement
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import site.addzero.smart.intentions.core.SmartIntentionsMessages
import java.nio.file.Paths

class SmartRedundantExplicitTypeTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        addKotlinStdlib()
    }

    override fun getTestDataPath(): String {
        return ""
    }

    fun testRemovesExplicitTypeFromTopLevelPropertyIntention() {
        myFixture.configureByText(
            "RouteRegistry.kt",
            """
            package sample

            data class KCloudRouteEntry(val routePath: String)
            data class KCloudRouteScene(val id: String, val routes: List<KCloudRouteEntry>)

            private val scenes = listOf(
                KCloudRouteScene(
                    id = "home",
                    routes = listOf(KCloudRouteEntry("/home")),
                ),
            )

            val routeEntries: List<KCloudRouteEntry> = scenes.flatMap { scene -> scene.routes }
            private val scenesById: Map<String, KCloudRouteScene> = scenes.associateBy { scene -> scene.id }
            private val routesByPath: Map<String, KCloudRouteEntry> = routeEntries.associateBy { route -> route.routePath<caret> }
            """.trimIndent(),
        )

        invokeDirectIntention()

        myFixture.checkResult(
            """
            package sample

            data class KCloudRouteEntry(val routePath: String)
            data class KCloudRouteScene(val id: String, val routes: List<KCloudRouteEntry>)

            private val scenes = listOf(
                KCloudRouteScene(
                    id = "home",
                    routes = listOf(KCloudRouteEntry("/home")),
                ),
            )

            val routeEntries: List<KCloudRouteEntry> = scenes.flatMap { scene -> scene.routes }
            private val scenesById: Map<String, KCloudRouteScene> = scenes.associateBy { scene -> scene.id }
            private val routesByPath = routeEntries.associateBy { route -> route.routePath }
            """.trimIndent(),
        )
    }

    fun testRemovesExplicitTypeFromLocalPropertyIntention() {
        myFixture.configureByText(
            "LocalSample.kt",
            """
            package sample

            fun loadRoutes(names: List<String>): Int {
                val count: Int = names.count()<caret>
                return count
            }
            """.trimIndent(),
        )

        invokeDirectIntention()

        myFixture.checkResult(
            """
            package sample

            fun loadRoutes(names: List<String>): Int {
                val count = names.count()
                return count
            }
            """.trimIndent(),
        )
    }

    fun testRemovesExplicitTypeFromVarPropertyIntention() {
        myFixture.configureByText(
            "VarSample.kt",
            """
            package sample

            fun sample() {
                var count: Int = 1<caret>
                count += 1
            }
            """.trimIndent(),
        )

        invokeDirectIntention()

        myFixture.checkResult(
            """
            package sample

            fun sample() {
                var count = 1
                count += 1
            }
            """.trimIndent(),
        )
    }

    fun testRemovesExplicitTypeFromGetterPropertyIntention() {
        myFixture.configureByText(
            "GetterSample.kt",
            """
            package sample

            class RouteRegistry(private val items: List<String>) {
                val size: Int<caret>
                    get() = items.size
            }
            """.trimIndent(),
        )

        invokeDirectIntention()

        myFixture.checkResult(
            """
            package sample

            class RouteRegistry(private val items: List<String>) {
                val size
                    get() = items.size
            }
            """.trimIndent(),
        )
    }

    fun testInspectionQuickFixMatchesIntentionResult() {
        myFixture.enableInspections(SmartRedundantExplicitTypeInspection())
        myFixture.configureByText(
            "InspectionSample.kt",
            """
            package sample

            private val routePath: String = "/home"<caret>
            """.trimIndent(),
        )

        myFixture.doHighlighting()
        val action = findInspectionQuickFix()
        myFixture.launchAction(action)

        myFixture.checkResult(
            """
            package sample

            private val routePath = "/home"
            """.trimIndent(),
        )
    }

    fun testInspectionOnlyReportsSafePropertiesInFile() {
        myFixture.enableInspections(SmartRedundantExplicitTypeInspection())
        myFixture.configureByText(
            "BatchSample.kt",
            """
            package sample

            private val title: String = "Routes"
            private val names: List<String> = mutableListOf("a")

            fun sample() {
                val count: Int = 1
                val items: List<String> = mutableListOf("x")
            }
            """.trimIndent(),
        )

        myFixture.doHighlighting()
        val quickFixes = myFixture.getAllQuickFixes().filter {
            it.text == SmartIntentionsMessages.REMOVE_REDUNDANT_EXPLICIT_TYPE
        }

        assertSize(2, quickFixes)
    }

    fun testDoesNotOfferWhenTypeWouldNarrow() {
        myFixture.configureByText(
            "NarrowingSample.kt",
            """
            package sample

            val names: List<String> = mutableListOf("a")<caret>
            """.trimIndent(),
        )

        assertDirectIntentionUnavailable()
    }

    fun testDoesNotOfferWithoutInitializerOrGetter() {
        myFixture.configureByText(
            "NoInitializer.kt",
            """
            package sample

            class RouteRegistry {
                lateinit var routePath: String<caret>
            }
            """.trimIndent(),
        )

        assertDirectIntentionUnavailable()
    }

    fun testDoesNotOfferForFunctionReturnType() {
        myFixture.configureByText(
            "FunctionSample.kt",
            """
            package sample

            fun routeCount(): Int<caret> = 1
            """.trimIndent(),
        )

        assertDirectIntentionUnavailable()
    }

    private fun invokeDirectIntention() {
        val action = SmartRemoveRedundantExplicitTypeIntention()
        val property = requirePropertyAtCaret()
        assertTrue(action.isAvailable(project, myFixture.editor, property))
        action.invoke(project, myFixture.editor, property)
    }

    private fun assertDirectIntentionUnavailable() {
        val action = SmartRemoveRedundantExplicitTypeIntention()
        val element = elementNearCaret()
        assertFalse(action.isAvailable(project, myFixture.editor, element))
    }

    private fun findInspectionQuickFix(): IntentionAction {
        return myFixture.getAllQuickFixes().single {
            it.text == SmartIntentionsMessages.REMOVE_REDUNDANT_EXPLICIT_TYPE
        }
    }

    private fun requirePropertyAtCaret(): KtProperty {
        return candidatePropertyAtCaret()
            .firstOrNull()
            ?: error("No property found at caret")
    }

    private fun elementNearCaret(): PsiElement {
        return candidatePropertyAtCaret().firstOrNull()
            ?: myFixture.file
    }

    private fun candidatePropertyAtCaret(): Sequence<KtProperty> {
        val offset = myFixture.editor.caretModel.offset
        val candidateOffsets = buildList {
            if (offset > 0) {
                add(offset - 1)
            }
            add(offset)
        }.distinct()
        return candidateOffsets.asSequence()
            .mapNotNull { candidateOffset ->
                myFixture.file.findElementAt(candidateOffset)?.getNonStrictParentOfType<KtProperty>()
            }
    }

    private fun addKotlinStdlib() {
        val kotlinLibDir = Paths.get(
            PathManager.getHomePath(),
            "plugins",
            "Kotlin",
            "kotlinc",
            "lib",
        ).toString()
        PsiTestUtil.addLibrary(
            module,
            "kotlin-stdlib",
            kotlinLibDir,
            "kotlin-stdlib.jar",
            "kotlin-stdlib-jdk8.jar",
        )
    }
}
