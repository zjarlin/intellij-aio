package site.addzero.composebuddy.previewsandbox

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import java.lang.reflect.InvocationTargetException
import java.nio.file.Files
import kotlin.io.path.exists

class ComposePreviewSandboxTest : BasePlatformTestCase() {
    fun testReachableAstSnapshotPrunesUnusedSameFileDeclarations() {
        myFixture.configureByText(
            "PreviewSandbox.kt",
            """
            package demo

            import androidx.compose.material3.Text
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.tooling.preview.Preview
            import demo.unused.UnusedImport

            @Preview
            @Composable
            fun CardPreview() {
                Card(title = formatTitle("hello"))
            }

            @Composable
            private fun Card(title: String) {
                Text(title)
            }

            private fun formatTitle(value: String): String {
                return value.uppercase()
            }

            private fun unusedPreviewHelper(): String {
                return "unused"
            }
            """.trimIndent(),
        )

        val snapshot = collectSnapshot("CardPreview")
        val generatedText = snapshot.files.joinToString("\n") { file ->
            file.imports.joinToString("\n") + "\n" + file.declarations.joinToString("\n")
        }

        assertTrue(generatedText.contains("fun CardPreview()"))
        assertTrue(generatedText.contains("private fun Card(title: String)"))
        assertTrue(generatedText.contains("private fun formatTitle(value: String): String"))
        assertTrue(generatedText.contains("import androidx.compose.material3.Text"))
        assertFalse(generatedText.contains("unusedPreviewHelper"))
        assertFalse(generatedText.contains("UnusedImport"))
    }

    fun testReachableAstSnapshotPrunesUnusedCrossFileDeclarations() {
        myFixture.addFileToProject(
            "src/main/kotlin/demo/PreviewModels.kt",
            """
            package demo

            data class CardState(
                val title: String,
            )

            fun usedTitle(): String {
                return "Used"
            }

            fun unusedTitle(): String {
                return "Unused"
            }
            """.trimIndent(),
        )

        myFixture.configureByText(
            "PreviewSandbox.kt",
            """
            package demo

            import androidx.compose.runtime.Composable
            import androidx.compose.ui.tooling.preview.Preview

            @Preview
            @Composable
            fun CardPreview() {
                Card(state = CardState(usedTitle()))
            }

            @Composable
            private fun Card(state: CardState) {
                println(state.title)
            }
            """.trimIndent(),
        )

        val snapshot = collectSnapshot("CardPreview")
        val generatedText = snapshot.files.joinToString("\n") { file ->
            file.declarations.joinToString("\n")
        }

        assertTrue(generatedText.contains("data class CardState"))
        assertTrue(generatedText.contains("fun usedTitle(): String"))
        assertFalse(generatedText.contains("fun unusedTitle(): String"))
    }

    fun testWriterCreatesGraphicalPreviewRunnerForPrivatePreview() {
        myFixture.configureByText(
            "PreviewSandbox.kt",
            """
            package demo

            import androidx.compose.runtime.Composable
            import androidx.compose.ui.tooling.preview.Preview

            @Preview
            @Composable
            private fun CardPreview() {
                Card()
            }

            @Composable
            private fun Card() {
                println("card")
            }
            """.trimIndent(),
        )

        val snapshot = collectSnapshot("CardPreview")
        val written = ComposePreviewSandboxWriter.write(project, snapshot)
            ?: error("Expected preview sandbox files to be written")
        val entryText = String(Files.readAllBytes(written.entryFile), Charsets.UTF_8)
        val buildText = String(Files.readAllBytes(written.buildFile), Charsets.UTF_8)
        val gradlePropertiesText = String(Files.readAllBytes(written.gradlePropertiesFile), Charsets.UTF_8)
        val runnerText = String(Files.readAllBytes(written.runnerFile), Charsets.UTF_8)

        assertTrue(written.buildFile.exists())
        assertTrue(written.gradlePropertiesFile.exists())
        assertTrue(written.runnerFile.exists())
        assertTrue(written.entryFile.toString().contains("src/jvmMain/kotlin"))
        assertTrue(written.gradlePropertiesFile.startsWith(written.rootDirectory))
        assertTrue(entryText.contains("internal fun ${written.entryComposableName}()"))
        assertTrue(entryText.contains("CardPreview()"))
        assertTrue(buildText.contains("id(\"org.jetbrains.compose\")"))
        assertTrue(buildText.contains("compose.desktop"))
        assertTrue(buildText.contains("JvmTarget.fromTarget(\"${snapshot.jvmTarget}\")"))
        assertTrue(buildText.contains("kmpBuddyPreviewDependencyClasspath"))
        assertTrue(buildText.contains("implementation(kmpBuddyPreviewDependencyClasspath)"))
        assertTrue(buildText.contains("writeKmpBuddyPreviewClasspath"))
        assertTrue(buildText.contains("mainClass = \"${written.runnerMainClass}\""))
        assertTrue(gradlePropertiesText.contains("${PreviewSandboxGradleJvm.GRADLE_JAVA_HOME_PROPERTY}="))
        if (snapshot.gradleJavaHome.isNotBlank()) {
            assertTrue(gradlePropertiesText.contains(snapshot.gradleJavaHome))
        }
        assertTrue(runnerText.contains("fun main() = application"))
        assertTrue(runnerText.contains("fun createKmpBuddyPreviewPanel(): JComponent"))
        assertTrue(runnerText.contains("${written.entryComposableName}()"))
        assertTrue(written.classpathFile.toString().endsWith("build/kmpBuddyPreview/classpath.txt"))
    }

    fun testReachableAstSnapshotKeepsDelegatedPropertyOperatorImports() {
        myFixture.configureByText(
            "PreviewSandbox.kt",
            """
            package demo

            import androidx.compose.material3.Text
            import androidx.compose.runtime.Composable
            import androidx.compose.runtime.getValue
            import androidx.compose.runtime.mutableStateOf
            import androidx.compose.runtime.remember
            import androidx.compose.runtime.setValue
            import androidx.compose.ui.tooling.preview.Preview

            @Preview
            @Composable
            fun CounterPreview() {
                var count by remember { mutableStateOf(0) }
                Text(count.toString())
            }
            """.trimIndent(),
        )

        val snapshot = collectSnapshot("CounterPreview")
        val imports = snapshot.files.flatMap(PreviewSandboxSourceFile::imports)

        assertTrue(imports.contains("import androidx.compose.runtime.getValue"))
        assertTrue(imports.contains("import androidx.compose.runtime.setValue"))
    }

    fun testExternalDependenciesAreInferredFromReachableAstImports() {
        val dependencies = PreviewSandboxExternalDependencies.infer(
            listOf(
                PreviewSandboxSourceFile(
                    key = "AppViewModel.kt",
                    packageName = "demo",
                    originalPath = "AppViewModel.kt",
                    outputFileName = "AppViewModel.kt",
                    imports = listOf(
                        "import androidx.lifecycle.ViewModel",
                        "import androidx.lifecycle.viewModelScope",
                        "import kotlinx.coroutines.launch",
                        "import kotlinx.serialization.Serializable",
                        "import org.koin.compose.koinInject",
                        "import org.koin.core.KoinApplication.Companion.init",
                        "import org.koin.core.annotation.KoinViewModel",
                    ),
                    declarations = listOf(
                        """
                        @KoinViewModel
                        @Serializable
                        class AppViewModel : ViewModel()
                        """.trimIndent(),
                    ),
                ),
            ),
        )

        assertTrue(dependencies.contains("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.10.0"))
        assertTrue(dependencies.contains("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0"))
        assertTrue(dependencies.contains("org.jetbrains.kotlinx:kotlinx-serialization-core:1.11.0"))
        assertTrue(dependencies.contains("io.insert-koin:koin-annotations:4.2.1"))
        assertTrue(dependencies.contains("io.insert-koin:koin-compose:4.2.1"))
        assertTrue(dependencies.contains("io.insert-koin:koin-core:4.2.1"))
    }

    fun testWriterAddsInferredExternalDependenciesToSandboxBuild() {
        myFixture.configureByText(
            "PreviewSandbox.kt",
            """
            package demo

            import androidx.compose.runtime.Composable
            import androidx.compose.ui.tooling.preview.Preview

            @Preview
            @Composable
            private fun CardPreview() {
                Card()
            }

            @Composable
            private fun Card() {
                println("card")
            }
            """.trimIndent(),
        )

        val snapshot = collectSnapshot("CardPreview").copy(
            externalMavenDependencies = listOf(
                "io.insert-koin:koin-compose:4.2.1",
                PreviewSandboxExternalDependencies.KOTLINX_SERIALIZATION_CORE,
            ),
        )
        val written = ComposePreviewSandboxWriter.write(project, snapshot)
            ?: error("Expected preview sandbox files to be written")
        val buildText = String(Files.readAllBytes(written.buildFile), Charsets.UTF_8)

        assertTrue(buildText.contains("id(\"org.jetbrains.kotlin.plugin.serialization\")"))
        assertTrue(buildText.contains("implementation(\"io.insert-koin:koin-compose:4.2.1\")"))
        assertTrue(buildText.contains("implementation(\"${PreviewSandboxExternalDependencies.KOTLINX_SERIALIZATION_CORE}\")"))
    }

    fun testErrorFormatterUnwrapsReflectionFailures() {
        val root = IllegalStateException("Preview factory failed")
        val formatted = ComposePreviewSandboxErrorFormatter.format(
            context = "Unable to render the graphical Compose preview in panel.",
            throwable = InvocationTargetException(root),
        )

        assertTrue(formatted.contains("Unable to render the graphical Compose preview in panel."))
        assertTrue(formatted.contains("java.lang.IllegalStateException"))
        assertTrue(formatted.contains("Preview factory failed"))
        assertTrue(formatted.contains("reflection wrapper:"))
    }

    private fun collectSnapshot(functionName: String): PreviewSandboxSnapshot {
        val file = myFixture.file as KtFile
        val function = file.declarations
            .filterIsInstance<KtNamedFunction>()
            .first { candidate -> candidate.name == functionName }
        return PreviewReachableAstCollector.collect(function)
            ?: error("Expected preview sandbox snapshot for $functionName")
    }
}
