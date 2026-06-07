package site.addzero.composebuddy.deadcode

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.exists

class DeadCodeCullingTest : BasePlatformTestCase() {
    fun testReachabilityKeepsUsedFilesAndFindsWholeFileDeadCode() {
        val moduleRoot = addModuleBuildFile()
        addKt(
            "lib/compose/az-compose/src/commonMain/kotlin/demo/Used.kt",
            """
            package demo

            fun UsedComponent() {
                UsedHelper()
            }

            fun UsedHelper() = Unit
            """.trimIndent(),
        )
        addKt(
            "lib/compose/az-compose/src/commonMain/kotlin/demo/Dead.kt",
            """
            package demo

            fun DeadComponent() = Unit
            """.trimIndent(),
        )
        val entryFile = addKt(
            "apps/cmp-aio/app/shared/src/commonMain/kotlin/site/addzero/App.kt",
            """
            package site.addzero

            import demo.UsedComponent

            fun App() {
                UsedComponent()
            }
            """.trimIndent(),
        )

        val analysis = DeadCodeReachabilityAnalyzer(project).analyze(
            entryFile = entryFile,
            entryFunction = entryFile.declarations.filterIsInstance<org.jetbrains.kotlin.psi.KtNamedFunction>()
                .single { it.name == "App" },
            sourceModuleRoot = moduleRoot,
        )

        assertTrue(analysis.movableDeadFiles.any { it.relativePath.endsWith("Dead.kt") })
        assertFalse(analysis.movableDeadFiles.any { it.relativePath.endsWith("Used.kt") })
    }

    fun testGeneratedRouteTableReferenceKeepsRoutePageLive() {
        addModuleBuildFile()
        addKt(
            "lib/compose/az-compose/src/commonMain/kotlin/demo/Unused.kt",
            """
            package demo

            fun UnusedComponent() = Unit
            """.trimIndent(),
        )
        addKt(
            "apps/cmp-aio/app/shared/src/commonMain/kotlin/site/addzero/app/admin/agent_screen/AgentManagementScreen.kt",
            """
            package site.addzero.app.admin.agent_screen

            fun AgentManagementRoute() {
                println("agent")
            }
            """.trimIndent(),
        )
        addKt(
            "apps/cmp-aio/app/shared/generated/commonMain/kotlin/site/addzero/app/admin/generated/RouteTable.kt",
            """
            package site.addzero.app.admin.generated

            object RouteTable {
                val allRoutes = mapOf(
                    "/agents" to { site.addzero.app.admin.agent_screen.AgentManagementRoute() },
                )
            }
            """.trimIndent(),
        )
        addKt(
            "apps/cmp-aio/app/shared/src/commonMain/kotlin/site/addzero/app/admin/RouteScaffoldScreen.kt",
            """
            package site.addzero.app.admin

            import site.addzero.app.admin.generated.RouteTable

            fun RouteScaffoldScreen() {
                RouteTable.allRoutes["/agents"]?.invoke()
            }
            """.trimIndent(),
        )
        val entryFile = addKt(
            "apps/cmp-aio/app/shared/src/commonMain/kotlin/site/addzero/App.kt",
            """
            package site.addzero

            import site.addzero.app.admin.RouteScaffoldScreen

            fun App() {
                RouteScaffoldScreen()
            }
            """.trimIndent(),
        )

        val appRoot = entryFile.virtualFile.findAncestor("shared") ?: error("app root missing")
        val analysis = DeadCodeReachabilityAnalyzer(project).analyze(
            entryFile = entryFile,
            entryFunction = entryFile.declarations.filterIsInstance<org.jetbrains.kotlin.psi.KtNamedFunction>()
                .single { it.name == "App" },
            sourceModuleRoot = appRoot,
        )

        assertFalse(analysis.movableDeadFiles.any { it.relativePath.endsWith("AgentManagementScreen.kt") })
    }

    fun testMirrorWriterPreservesRelativePathAndFileName() {
        val projectRoot = Files.createTempDirectory("kmp-buddy-dead-code")
        val sourceModule = projectRoot.resolve("lib/compose/az-compose")
        val deadFile = sourceModule.resolve("src/commonMain/kotlin/demo/nested/Dead.kt")
        Files.createDirectories(deadFile.parent)
        Files.write(
            deadFile,
            """
            package demo.nested

            fun DeadComponent() = Unit
            """.trimIndent().toByteArray(Charsets.UTF_8),
        )
        Files.write(
            sourceModule.resolve("build.gradle.kts"),
            "plugins { id(\"site.addzero.buildlogic.kmp.cmp-lib\") }\n".toByteArray(Charsets.UTF_8),
        )
        val analysis = deadFileAnalysis(sourceModule, deadFile, "src/commonMain/kotlin/demo/nested/Dead.kt")

        val result = DeadCodeMirrorWriter(project).writeToMirror(
            projectRoot = projectRoot,
            mirrorRoot = projectRoot.resolve(".kmp-buddy/dead-code-modules/az-compose"),
            analysis = analysis,
        )

        val expectedMirrorFile = result.mirrorRoot
            .resolve("src/commonMain/kotlin/demo/nested/Dead.kt")
        assertTrue(expectedMirrorFile.exists())
        assertFalse(deadFile.exists())
        assertTrue(result.manifestPath.exists())
        assertTrue(result.reportPath.exists())
        assertTrue(String(Files.readAllBytes(result.manifestPath), Charsets.UTF_8).contains("Dead.kt"))
    }

    fun testRestoreSkipsExistingChangedSourceAndWritesConflictReport() {
        val projectRoot = Files.createTempDirectory("kmp-buddy-dead-code")
        val sourceModule = projectRoot.resolve("lib/compose/az-compose")
        val deadFile = sourceModule.resolve("src/commonMain/kotlin/demo/Dead.kt")
        Files.createDirectories(deadFile.parent)
        Files.write(
            deadFile,
            """
            package demo

            fun DeadComponent() = Unit
            """.trimIndent().toByteArray(Charsets.UTF_8),
        )
        Files.write(
            sourceModule.resolve("build.gradle.kts"),
            "plugins { id(\"site.addzero.buildlogic.kmp.cmp-lib\") }\n".toByteArray(Charsets.UTF_8),
        )
        val analysis = deadFileAnalysis(sourceModule, deadFile, "src/commonMain/kotlin/demo/Dead.kt")
        val cullResult = DeadCodeMirrorWriter(project).writeToMirror(
            projectRoot = projectRoot,
            mirrorRoot = projectRoot.resolve(".kmp-buddy/dead-code-modules/az-compose"),
            analysis = analysis,
        )

        val sourcePath = deadFile
        sourcePath.parent.toFile().mkdirs()
        Files.write(sourcePath, "package demo\n\nfun DeadComponent() = println(\"changed\")\n".toByteArray(Charsets.UTF_8))

        val restoreResult = DeadCodeRestorer(project).restore(cullResult.manifestPath)

        assertEquals(0, restoreResult.restoredFileCount)
        assertEquals(1, restoreResult.conflictCount)
        assertNotNull(restoreResult.conflictReportPath)
        assertTrue(restoreResult.conflictReportPath!!.exists())
    }

    private fun addModuleBuildFile(): com.intellij.openapi.vfs.VirtualFile {
        val buildFile = myFixture.addFileToProject(
            "lib/compose/az-compose/build.gradle.kts",
            """
            plugins {
                id("site.addzero.buildlogic.kmp.cmp-lib")
            }
            """.trimIndent(),
        )
        return buildFile.virtualFile.parent ?: error("module root missing")
    }

    private fun addKt(path: String, text: String): KtFile {
        val psiFile = myFixture.addFileToProject(path, text)
        val virtualFile = psiFile.virtualFile
        runWriteAction {
            virtualFile.refresh(false, false)
        }
        return PsiManager.getInstance(project).findFile(virtualFile) as KtFile
    }

    private fun deadFileAnalysis(
        sourceModule: java.nio.file.Path,
        file: java.nio.file.Path,
        relativePath: String,
    ): DeadCodeAnalysisResult {
        return DeadCodeAnalysisResult(
            sourceModulePath = sourceModule,
            files = listOf(
                DeadCodeFileAnalysis(
                    sourcePath = file,
                    relativePath = relativePath,
                    declarations = listOf(
                        DeadCodeDeclarationKey(
                            filePath = file.toString(),
                            name = "DeadComponent",
                            offset = 0,
                        ),
                    ),
                    liveDeclarations = emptyList(),
                ),
            ),
            reachableDeclarationCount = 1,
        )
    }

    private fun VirtualFile.findAncestor(name: String): VirtualFile? {
        return generateSequence(this) { file -> file.parent }
            .firstOrNull { file -> file.name == name }
    }
}
