package site.addzero.kcloud.idea

import com.intellij.codeInsight.daemon.GutterMark
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ControllerApiSourceLineMarkerProviderTest : BasePlatformTestCase() {
    fun testGeneratedApiShowsGutterToSourceFile() {
        myFixture.addFileToProject(
            "src/server/src/jvmMain/kotlin/site/addzero/demo/routes/DemoRoute.kt",
            """
            package site.addzero.demo.routes
            
            fun demoRoute() {
            }
            """.trimIndent(),
        )

        val generatedApi = myFixture.addFileToProject(
            "src/generated/commonMain/kotlin/site/addzero/demo/api/DemoApi.kt",
            """
            package site.addzero.demo.api
            
            /**
             * 原始文件: site.addzero.demo.routes.DemoRoute.kt
             */
            interface DemoApi {
            }
            """.trimIndent(),
        )
        myFixture.openFileInEditor(generatedApi.virtualFile)
        myFixture.doHighlighting()

        val gutters = myFixture.findAllGutters()
        assertTrue(hasTooltipContaining(gutters, "Jump to source file DemoRoute.kt"))
    }

    fun testSourceRouteShowsGutterToGeneratedApi() {
        myFixture.addFileToProject(
            "src/generated/commonMain/kotlin/site/addzero/demo/api/DemoApi.kt",
            """
            package site.addzero.demo.api
            
            /**
             * 原始文件: site.addzero.demo.routes.DemoRoute.kt
             */
            interface DemoApi {
            }
            """.trimIndent(),
        )

        val sourceFile = myFixture.addFileToProject(
            "src/server/src/jvmMain/kotlin/site/addzero/demo/routes/DemoRoute.kt",
            """
            package site.addzero.demo.routes
            
            fun demo<caret>Route() {
            }
            """.trimIndent(),
        )
        myFixture.openFileInEditor(sourceFile.virtualFile)
        myFixture.doHighlighting()

        val gutters = myFixture.findAllGutters()
        assertTrue(hasTooltipContaining(gutters, "Jump to generated Ktorfit API"))
    }

    fun testAnnotatedTopLevelRouteShowsGutterToGeneratedApi() {
        myFixture.addFileToProject(
            "src/generated/commonMain/kotlin/site/addzero/demo/api/DemoFlashApi.kt",
            """
            package site.addzero.demo.api
            
            /**
             * 原始文件: site.addzero.demo.routes.DemoFlash.kt
             */
            interface DemoFlashApi {
            }
            """.trimIndent(),
        )

        val sourceFile = myFixture.addFileToProject(
            "src/server/src/jvmMain/kotlin/site/addzero/demo/routes/DemoFlash.kt",
            """
            package site.addzero.demo.routes
            
            import org.springframework.web.bind.annotation.GetMapping
            
            @GetMapping("/api/demo/flash/status")
            fun getDemoFlashStatus(): String {
                return "ok"
            }
            """.trimIndent(),
        )
        myFixture.openFileInEditor(sourceFile.virtualFile)
        myFixture.doHighlighting()

        val gutters = myFixture.findAllGutters()
        assertTrue(hasTooltipContaining(gutters, "Jump to generated Ktorfit API"))
    }

    private fun hasTooltipContaining(
        gutters: List<GutterMark>,
        expectedText: String,
    ): Boolean {
        return gutters.any { gutter ->
            gutter.tooltipText?.contains(expectedText) == true
        }
    }
}
