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

    fun testReachabilityFindsWholeFileLiveCodeAndLeavesMixedFilesInPlace() {
        val moduleRoot = addModuleBuildFile()
        addKt(
            "lib/compose/az-compose/src/commonMain/kotlin/demo/Live.kt",
            """
            package demo

            fun LiveComponent() = Unit
            """.trimIndent(),
        )
        addKt(
            "lib/compose/az-compose/src/commonMain/kotlin/demo/Mixed.kt",
            """
            package demo

            fun LiveFromMixed() = Unit

            fun DeadFromMixed() = Unit
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

            import demo.LiveComponent
            import demo.LiveFromMixed

            fun App() {
                LiveComponent()
                LiveFromMixed()
            }
            """.trimIndent(),
        )

        val analysis = DeadCodeReachabilityAnalyzer(project).analyze(
            entryFile = entryFile,
            entryFunction = entryFile.declarations.filterIsInstance<org.jetbrains.kotlin.psi.KtNamedFunction>()
                .single { it.name == "App" },
            sourceModuleRoot = moduleRoot,
        )

        assertTrue(analysis.movableLiveFiles.any { it.relativePath.endsWith("Live.kt") })
        assertFalse(analysis.movableLiveFiles.any { it.relativePath.endsWith("Mixed.kt") })
        assertTrue(analysis.movableDeadFiles.any { it.relativePath.endsWith("Dead.kt") })
        assertTrue(analysis.mixedFiles.any { it.relativePath.endsWith("Mixed.kt") })
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

    fun testLiveCodeMirrorWriterUsesSeparateRootAndPreservesFileName() {
        val projectRoot = Files.createTempDirectory("kmp-buddy-live-code")
        val sourceModule = projectRoot.resolve("lib/compose/az-compose")
        val liveFile = sourceModule.resolve("src/commonMain/kotlin/demo/nested/Live.kt")
        Files.createDirectories(liveFile.parent)
        Files.write(
            liveFile,
            """
            package demo.nested

            fun LiveComponent() = Unit
            """.trimIndent().toByteArray(Charsets.UTF_8),
        )
        Files.write(
            sourceModule.resolve("build.gradle.kts"),
            "plugins { id(\"site.addzero.buildlogic.kmp.cmp-lib\") }\n".toByteArray(Charsets.UTF_8),
        )
        val analysis = fileAnalysis(
            sourceModule = sourceModule,
            file = liveFile,
            relativePath = "src/commonMain/kotlin/demo/nested/Live.kt",
            declarationName = "LiveComponent",
            isLive = true,
        )

        val result = DeadCodeMirrorWriter(project).writeToMirror(
            projectRoot = projectRoot,
            mirrorRoot = projectRoot.resolve(".kmp-buddy/live-code-modules/az-compose"),
            analysis = analysis,
            mode = DeadCodeTransferMode.LIVE_CODE,
        )

        val expectedMirrorFile = result.mirrorRoot
            .resolve("src/commonMain/kotlin/demo/nested/Live.kt")
        assertTrue(expectedMirrorFile.exists())
        assertFalse(liveFile.exists())
        val manifestText = String(Files.readAllBytes(result.manifestPath), Charsets.UTF_8)
        assertTrue(manifestText.contains("\"transferMode\": \"live-code\""))
        assertTrue(String(Files.readAllBytes(result.reportPath), Charsets.UTF_8).contains("KMP Buddy Live Code Transfer Report"))
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
        val analysis = fileAnalysis(
            sourceModule = sourceModule,
            file = deadFile,
            relativePath = "src/commonMain/kotlin/demo/Dead.kt",
            declarationName = "DeadComponent",
            isLive = false,
        )
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
        return fileAnalysis(
            sourceModule = sourceModule,
            file = file,
            relativePath = relativePath,
            declarationName = "DeadComponent",
            isLive = false,
        )
    }

    private fun fileAnalysis(
        sourceModule: java.nio.file.Path,
        file: java.nio.file.Path,
        relativePath: String,
        declarationName: String,
        isLive: Boolean,
    ): DeadCodeAnalysisResult {
        val declaration = DeadCodeDeclarationKey(
            filePath = file.toString(),
            name = declarationName,
            offset = 0,
        )
        return DeadCodeAnalysisResult(
            sourceModulePath = sourceModule,
            files = listOf(
                DeadCodeFileAnalysis(
                    sourcePath = file,
                    relativePath = relativePath,
                    declarations = listOf(declaration),
                    liveDeclarations = if (isLive) listOf(declaration) else emptyList(),
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
