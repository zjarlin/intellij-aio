package site.addzero.composebuddy.run

import com.intellij.codeInsight.daemon.GutterMark
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class KmpCommonMainStartupLineMarkerProviderTest : BasePlatformTestCase() {
    fun testCommonMainAppShowsRunGutterForDesktopConsumer() {
        myFixture.addFileToProject(
            "apps/demo/app/shared/build.gradle.kts",
            """
            plugins {
                id("site.addzero.buildlogic.kmp.cmp-lib")
            }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "apps/demo/app/desktopApp/build.gradle.kts",
            """
            plugins {
                id("site.addzero.buildlogic.kmp.cmp-desktop")
            }
            
            kotlin {
                sourceSets {
                    commonMain.dependencies {
                        implementation(project(":apps:demo:app:shared"))
                    }
                }
            }
            
            compose.desktop {
                application {
                    mainClass = "site.addzero.MainKt"
                }
            }
            """.trimIndent(),
        )

        val appFile = myFixture.addFileToProject(
            "apps/demo/app/shared/src/commonMain/kotlin/site/addzero/App.kt",
            """
            package site.addzero
            
            import androidx.compose.runtime.Composable
            
            @Composable
            fun App() {
            }
            """.trimIndent(),
        )

        myFixture.openFileInEditor(appFile.virtualFile)
        val runTarget = KmpStartupTargetResolver.resolve(project, appFile.virtualFile)
        assertEquals(":apps:demo:app:desktopApp:run", runTarget?.fullTaskName)
        myFixture.doHighlighting()

        assertTrue(
            hasTooltipContaining(
                myFixture.findAllGutters(),
                "Run KMP app :apps:demo:app:desktopApp:run",
            ),
        )
    }

    fun testNonCommonMainAppDoesNotShowRunGutter() {
        myFixture.addFileToProject(
            "apps/demo/app/desktopApp/build.gradle.kts",
            """
            plugins {
                id("site.addzero.buildlogic.kmp.cmp-desktop")
            }
            """.trimIndent(),
        )

        val appFile = myFixture.addFileToProject(
            "apps/demo/app/desktopApp/src/jvmMain/kotlin/site/addzero/App.kt",
            """
            package site.addzero
            
            import androidx.compose.runtime.Composable
            
            @Composable
            fun App() {
            }
            """.trimIndent(),
        )

        myFixture.openFileInEditor(appFile.virtualFile)
        myFixture.doHighlighting()

        assertFalse(hasTooltipContaining(myFixture.findAllGutters(), "Run KMP app"))
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
