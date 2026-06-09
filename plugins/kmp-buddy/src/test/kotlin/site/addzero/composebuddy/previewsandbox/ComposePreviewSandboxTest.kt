package site.addzero.composebuddy.previewsandbox

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import java.lang.reflect.InvocationTargetException
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
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

    fun testReachableAstSnapshotKeepsImportsUsedByCopiedParameterAndBodyExpressions() {
        myFixture.configureByText(
            "GridHeaderCell.kt",
            """
            package site.addzero.component.layout

            import androidx.compose.foundation.BorderStroke
            import androidx.compose.material3.MaterialTheme
            import androidx.compose.material3.Surface
            import androidx.compose.material3.contentColorFor
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.graphics.Color
            import androidx.compose.ui.graphics.RectangleShape
            import androidx.compose.ui.tooling.preview.Preview
            import androidx.compose.ui.unit.Dp
            import androidx.compose.ui.unit.dp
            import demo.unused.UnusedImport

            @Preview
            @Composable
            fun HeaderPreview() {
                HeaderCell(label = "A", width = 48.dp)
            }

            @Composable
            fun HeaderCell(
                label: String,
                width: Dp,
                modifier: Modifier = Modifier,
                borderWidth: Dp = 1.dp,
                color: Color = MaterialTheme.colorScheme.surface,
            ) {
                val border = BorderStroke(borderWidth, color)
                Surface(
                    modifier = modifier,
                    shape = RectangleShape,
                    color = color,
                    contentColor = contentColorFor(color),
                    border = border,
                ) {
                    println(label + ":" + width)
                }
            }
            """.trimIndent(),
        )

        val snapshot = collectSnapshot("HeaderPreview")
        val imports = snapshot.files.flatMap(PreviewSandboxSourceFile::imports)

        assertTrue(imports.contains("import androidx.compose.foundation.BorderStroke"))
        assertTrue(imports.contains("import androidx.compose.ui.graphics.RectangleShape"))
        assertTrue(imports.contains("import androidx.compose.material3.contentColorFor"))
        assertTrue(imports.contains("import androidx.compose.ui.unit.dp"))
        assertFalse(imports.contains("import demo.unused.UnusedImport"))
    }

    fun testReachableAstSnapshotDropsImportsOnlyUsedByKdocLinks() {
        myFixture.configureByText(
            "Dialog.kt",
            """
            package demo

            import androidx.compose.runtime.Composable
            import androidx.compose.ui.tooling.preview.Preview
            import demo.modal.ModalDescription
            import demo.modal.ModalTitle

            @Preview
            @Composable
            fun DialogPreview() {
                Dialog(header = { println("header") })
            }

            /**
             * Header usually contains [ModalTitle] and [ModalDescription].
             */
            @Composable
            fun Dialog(header: @Composable () -> Unit) {
                header()
            }
            """.trimIndent(),
        )

        val snapshot = collectSnapshot("DialogPreview")
        val imports = snapshot.files.flatMap(PreviewSandboxSourceFile::imports)
        val generatedText = snapshot.files.joinToString("\n") { sourceFile ->
            sourceFile.declarations.joinToString("\n")
        }

        assertFalse(imports.contains("import demo.modal.ModalDescription"))
        assertFalse(imports.contains("import demo.modal.ModalTitle"))
        assertTrue(generatedText.contains("[ModalTitle] and [ModalDescription]"))
    }

    fun testWriterRendersKmpActualDeclarationAsPlainJvmDeclaration() {
        myFixture.configureByText(
            "SidebarPreview.kt",
            """
            package site.addzero.util

            import androidx.compose.runtime.Composable
            import androidx.compose.ui.tooling.preview.Preview

            @Preview
            @Composable
            fun SidebarPreview() {
                println("sidebar")
            }
            """.trimIndent(),
        )

        val snapshot = collectSnapshot("SidebarPreview")
        val snapshotWithActual = snapshot.copy(
            files = snapshot.files + PreviewSandboxSourceFile(
                key = "PlatformScreen.jvm.kt",
                packageName = "site.addzero.util",
                originalPath = "src/jvmMain/kotlin/site/addzero/util/PlatformScreen.jvm.kt",
                outputFileName = "PlatformScreen.jvm.kt",
                imports = emptyList(),
                declarations = listOf("actual fun isMobile(): Boolean = false"),
            ),
        )
        val written = ComposePreviewSandboxWriter.write(project, snapshotWithActual)
            ?: error("Expected preview sandbox files to be written")
        val generatedText = written.generatedFiles.joinToString("\n") { generatedFile ->
            String(Files.readAllBytes(generatedFile.path), Charsets.UTF_8)
        }

        assertFalse(generatedText.contains("expect fun isMobile"))
        assertFalse(generatedText.contains("actual fun isMobile"))
        assertTrue(generatedText.contains("fun isMobile(): Boolean = false"))
    }

    fun testReachableAstSnapshotIncludesJvmActualForCommonMainExpectDeclaration() {
        myFixture.addFileToProject(
            "src/commonMain/kotlin/site/addzero/component/zh/fonts/ChineseFonts.kt",
            """
            package site.addzero.component.zh.fonts

            import androidx.compose.runtime.Composable

            class ChineseUiFontProfile(
                val family: String,
            )

            expect fun preferredChineseUiFontProfile(): ChineseUiFontProfile

            @Composable
            expect fun rememberChineseUiFontFamilyOrNull(): String?

            @Composable
            fun rememberChineseTypography(): String {
                return rememberChineseUiFontFamilyOrNull() ?: preferredChineseUiFontProfile().family
            }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "src/jvmMain/kotlin/site/addzero/component/zh/fonts/ChineseFonts.jvm.kt",
            """
            package site.addzero.component.zh.fonts

            import androidx.compose.runtime.Composable

            @Composable
            actual fun rememberChineseUiFontFamilyOrNull(): String? = rememberPreferredJvmChineseUiFontFamily()

            actual fun preferredChineseUiFontProfile(): ChineseUiFontProfile = ChineseUiFontProfile("system")

            private fun rememberPreferredJvmChineseUiFontFamily(): String {
                return preferredChineseUiFontProfile().family
            }
            """.trimIndent(),
        )
        val previewFile = myFixture.addFileToProject(
            "src/commonMain/kotlin/demo/InputPreview.kt",
            """
            package demo

            import androidx.compose.runtime.Composable
            import androidx.compose.ui.tooling.preview.Preview
            import site.addzero.component.zh.fonts.rememberChineseTypography

            @Preview
            @Composable
            fun InputPreview() {
                rememberChineseTypography()
            }
            """.trimIndent(),
        ) as KtFile
        myFixture.configureFromExistingVirtualFile(previewFile.virtualFile)

        val snapshot = collectSnapshot("InputPreview")
        val snapshotText = snapshot.files.joinToString("\n") { sourceFile ->
            sourceFile.declarations.joinToString("\n")
        }
        val written = ComposePreviewSandboxWriter.write(project, snapshot)
            ?: error("Expected preview sandbox files to be written")
        val generatedText = written.generatedFiles.joinToString("\n") { generatedFile ->
            String(Files.readAllBytes(generatedFile.path), Charsets.UTF_8)
        }

        assertTrue(snapshotText.contains("actual fun rememberChineseUiFontFamilyOrNull"))
        assertTrue(snapshotText.contains("actual fun preferredChineseUiFontProfile"))
        assertFalse(generatedText.contains("expect fun rememberChineseUiFontFamilyOrNull"))
        assertFalse(generatedText.contains("expect fun preferredChineseUiFontProfile"))
        assertFalse(generatedText.contains("actual fun rememberChineseUiFontFamilyOrNull"))
        assertFalse(generatedText.contains("actual fun preferredChineseUiFontProfile"))
        assertTrue(generatedText.contains("fun rememberChineseUiFontFamilyOrNull(): String?"))
        assertTrue(generatedText.contains("fun preferredChineseUiFontProfile(): ChineseUiFontProfile"))
    }

    fun testReachableAstSnapshotInfersExternalDependenciesFromThemeChain() {
        myFixture.addFileToProject(
            "src/main/kotlin/site/addzero/component/ComponentPreviewTheme.kt",
            """
            package site.addzero.component

            import androidx.compose.runtime.Composable
            import site.addzero.themes.AppTheme

            @Composable
            fun ComponentPreviewTheme(content: @Composable () -> Unit) {
                AppTheme(content)
            }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "src/main/kotlin/site/addzero/themes/AppTheme.kt",
            """
            package site.addzero.themes

            import androidx.compose.runtime.Composable
            import org.koin.compose.koinInject
            import site.addzero.context.viewmode.AppViewModel

            @Composable
            fun AppTheme(content: @Composable () -> Unit) {
                val viewModel: AppViewModel = koinInject()
                viewModel.launchPreviewJob()
                content()
            }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "src/main/kotlin/site/addzero/context/viewmode/AppViewModel.kt",
            """
            package site.addzero.context.viewmode

            import androidx.lifecycle.ViewModel
            import androidx.lifecycle.viewModelScope
            import kotlinx.coroutines.launch
            import org.koin.core.KoinApplication
            import org.koin.core.annotation.KoinViewModel

            @KoinViewModel
            class AppViewModel : ViewModel() {
                fun launchPreviewJob() {
                    viewModelScope.launch {
                        println(KoinApplication::class.simpleName)
                    }
                }
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "DropdownMenu.kt",
            """
            package site.addzero.component.dropdown

            import androidx.compose.runtime.Composable
            import androidx.compose.ui.tooling.preview.Preview
            import site.addzero.component.ComponentPreviewTheme

            @Preview
            @Composable
            fun DropdownMenuPreview() {
                ComponentPreviewTheme {
                    println("preview")
                }
            }
            """.trimIndent(),
        )

        val snapshot = collectSnapshot("DropdownMenuPreview")
        val dependencies = snapshot.externalMavenDependencies

        assertTrue(dependencies.contains("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.10.0"))
        assertTrue(dependencies.contains("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0"))
        assertTrue(dependencies.contains("io.insert-koin:koin-annotations:4.2.1"))
        assertTrue(dependencies.contains("io.insert-koin:koin-compose:4.2.1"))
        assertTrue(dependencies.contains("io.insert-koin:koin-core:4.2.1"))
    }

    fun testWriterWrapsKoinPreviewAndRegistersReachableAnnotatedBindings() {
        myFixture.addFileToProject(
            "src/main/kotlin/site/addzero/context/spi/AppStateRepository.kt",
            """
            package site.addzero.context.spi

            import org.koin.core.annotation.Single

            interface AppStateRepository {
                fun load(): String?
            }

            @Single
            class InMemoryAppStateRepository : AppStateRepository {
                override fun load(): String? = null
            }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "src/main/kotlin/site/addzero/context/viewmode/AppViewModel.kt",
            """
            package site.addzero.context.viewmode

            import org.koin.core.annotation.KoinViewModel
            import site.addzero.context.spi.AppStateRepository

            @KoinViewModel
            class AppViewModel(
                private val repository: AppStateRepository,
            ) {
                val label: String = repository.load() ?: "preview"
            }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "src/main/kotlin/site/addzero/themes/AppTheme.kt",
            """
            package site.addzero.themes

            import androidx.compose.runtime.Composable
            import org.koin.compose.koinInject
            import site.addzero.context.viewmode.AppViewModel

            @Composable
            fun AppTheme(content: @Composable () -> Unit) {
                val viewModel: AppViewModel = koinInject()
                println(viewModel.label)
                content()
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "CardPreview.kt",
            """
            package site.addzero.component.card

            import androidx.compose.runtime.Composable
            import androidx.compose.ui.tooling.preview.Preview
            import site.addzero.themes.AppTheme

            @Preview
            @Composable
            fun CardPreview() {
                AppTheme {
                    println("card")
                }
            }
            """.trimIndent(),
        )

        val snapshot = collectSnapshot("CardPreview")
        val generatedText = snapshot.files.joinToString("\n") { sourceFile ->
            sourceFile.declarations.joinToString("\n")
        }
        val written = ComposePreviewSandboxWriter.write(project, snapshot)
            ?: error("Expected preview sandbox files to be written")
        val runnerText = String(Files.readAllBytes(written.runnerFile), Charsets.UTF_8)

        assertTrue(generatedText.contains("class InMemoryAppStateRepository"))
        assertTrue(snapshot.externalMavenDependencies.contains(PreviewSandboxExternalDependencies.KOIN_COMPOSE))
        assertTrue(snapshot.externalMavenDependencies.contains(PreviewSandboxExternalDependencies.KOIN_CORE))
        assertTrue(runnerText.contains("org.koin.compose.KoinApplication"))
        assertTrue(runnerText.contains("configuration = org.koin.dsl.koinConfiguration"))
        assertTrue(
            runnerText.contains(
                "single<site.addzero.context.spi.AppStateRepository> { site.addzero.context.spi.InMemoryAppStateRepository() }",
            ),
        )
        assertTrue(
            runnerText.contains(
                "single { site.addzero.context.viewmode.AppViewModel(get()) }",
            ),
        )
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

    fun testExternalDependenciesIncludeKyantBackdropAndShapes() {
        val dependencies = PreviewSandboxExternalDependencies.infer(
            listOf(
                PreviewSandboxSourceFile(
                    key = "LiquidButton.kt",
                    packageName = "site.addzero.component.liquid",
                    originalPath = "LiquidButton.kt",
                    outputFileName = "LiquidButton.kt",
                    imports = listOf(
                        "import com.kyant.backdrop.Backdrop",
                        "import com.kyant.backdrop.drawBackdrop",
                        "import com.kyant.shapes.Capsule",
                    ),
                    declarations = listOf(
                        """
                        fun useLiquid(backdrop: Backdrop) {
                            println(Capsule::class.simpleName)
                        }
                        """.trimIndent(),
                    ),
                ),
            ),
        )

        assertTrue(dependencies.contains(PreviewSandboxExternalDependencies.KYANT_BACKDROP))
        assertTrue(dependencies.contains(PreviewSandboxExternalDependencies.KYANT_SHAPES))
    }

    fun testExternalDependenciesIncludeCoilComposeForAvatarPreviews() {
        val dependencies = PreviewSandboxExternalDependencies.infer(
            listOf(
                PreviewSandboxSourceFile(
                    key = "Avatar.kt",
                    packageName = "site.addzero.component.avatar",
                    originalPath = "Avatar.kt",
                    outputFileName = "Avatar.kt",
                    imports = listOf(
                        "import coil3.compose.AsyncImagePainter",
                        "import coil3.compose.rememberAsyncImagePainter",
                    ),
                    declarations = listOf(
                        """
                        fun avatar() {
                            val imageUrl = "https://picsum.photos/1200/800"
                            println(imageUrl)
                            println(AsyncImagePainter::class.simpleName)
                            println(::rememberAsyncImagePainter.name)
                        }
                        """.trimIndent(),
                    ),
                ),
            ),
        )

        assertTrue(dependencies.contains(PreviewSandboxExternalDependencies.COIL_COMPOSE))
        assertTrue(dependencies.contains(PreviewSandboxExternalDependencies.COIL_NETWORK_KTOR3))
    }

    fun testExternalDependenciesResolveUnknownImportsFromDependencyClasspath() {
        val cacheRoot = Files.createTempDirectory("kmp-buddy-preview-dependencies")
        val dependencyJar = createGradleCacheJar(
            root = cacheRoot,
            group = "dev.example",
            variantModule = "unknown-ui-jvm",
            rootModule = "unknown-ui",
            version = "1.2.3",
            classEntry = "dev/example/unknown/Widget.class",
        )
        val dependencies = PreviewSandboxExternalDependencies.infer(
            files = listOf(
                PreviewSandboxSourceFile(
                    key = "UnknownWidget.kt",
                    packageName = "demo",
                    originalPath = "UnknownWidget.kt",
                    outputFileName = "UnknownWidget.kt",
                    imports = listOf("import dev.example.unknown.Widget"),
                    declarations = listOf(
                        """
                        fun render(widget: Widget) {
                            println(widget)
                        }
                        """.trimIndent(),
                    ),
                ),
            ),
            dependencyClassPath = listOf(dependencyJar.toString()),
        )

        assertTrue(dependencies.contains("dev.example:unknown-ui:1.2.3"))
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

    fun testReachableAstSnapshotAddsThemeSupportForBareComponentPreview() {
        myFixture.addFileToProject(
            "src/main/kotlin/site/addzero/themes/AppTheme.kt",
            """
            package site.addzero.themes

            import androidx.compose.material3.MaterialTheme
            import androidx.compose.runtime.Composable
            import androidx.compose.runtime.ReadOnlyComposable
            import androidx.compose.runtime.staticCompositionLocalOf

            val LocalAppColorScheme = staticCompositionLocalOf<AppColorScheme> { error("missing") }
            val LocalAppRadius = staticCompositionLocalOf<AppRadius> { Radius }
            val LocalAppShadows = staticCompositionLocalOf<AppShadows> { Shadows }

            val MaterialTheme.appColors
                @Composable
                @ReadOnlyComposable
                get() = LocalAppColorScheme.current
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "src/main/kotlin/site/addzero/themes/AppColorScheme.kt",
            """
            package site.addzero.themes

            import androidx.compose.material3.ColorScheme
            import androidx.compose.runtime.Immutable
            import androidx.compose.ui.graphics.Color

            @Immutable
            interface AppColorScheme {
                val material: ColorScheme
                val ring: Color
            }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "src/main/kotlin/site/addzero/themes/DefaultLightAppColorScheme.kt",
            """
            package site.addzero.themes

            import androidx.compose.ui.graphics.Color

            internal object DefaultLightAppColorScheme : AppColorScheme {
                override val material = DefaultMaterialLightColorScheme
                override val ring = Color.Black
            }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "src/main/kotlin/site/addzero/themes/MaterialDefaultTheme.kt",
            """
            package site.addzero.themes

            import androidx.compose.material3.lightColorScheme

            internal val DefaultMaterialLightColorScheme = lightColorScheme()
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "src/main/kotlin/site/addzero/themes/Radius.kt",
            """
            package site.addzero.themes

            import androidx.compose.ui.unit.Dp
            import androidx.compose.ui.unit.dp

            interface AppRadius {
                val sm: Dp
            }

            object Radius : AppRadius {
                override val sm = 4.dp
            }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "src/main/kotlin/site/addzero/themes/Shadows.kt",
            """
            package site.addzero.themes

            import androidx.compose.ui.graphics.Color

            interface AppShadows {
                val ambient: Color
            }

            object Shadows : AppShadows {
                override val ambient = Color.Transparent
            }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "src/main/kotlin/site/addzero/component/checkbox/CheckboxDefaults.kt",
            """
            package site.addzero.component.checkbox

            import androidx.compose.material3.MaterialTheme
            import site.addzero.themes.appColors

            object CheckboxDefaults {
                fun ring() = MaterialTheme.appColors.ring
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Checkbox.kt",
            """
            package site.addzero.component.checkbox

            import androidx.compose.runtime.Composable
            import androidx.compose.ui.tooling.preview.Preview

            @Preview
            @Composable
            fun CheckboxQuickPreview() {
                println(CheckboxDefaults.ring())
            }
            """.trimIndent(),
        )

        val snapshot = collectSnapshot("CheckboxQuickPreview")
        val generatedText = snapshot.files.joinToString("\n") { sourceFile ->
            sourceFile.declarations.joinToString("\n")
        }
        val written = ComposePreviewSandboxWriter.write(project, snapshot)
            ?: error("Expected preview sandbox files to be written")
        val runnerText = String(Files.readAllBytes(written.runnerFile), Charsets.UTF_8)

        assertTrue(generatedText.contains("val LocalAppColorScheme"))
        assertTrue(generatedText.contains("interface AppColorScheme"))
        assertTrue(generatedText.contains("internal object DefaultLightAppColorScheme"))
        assertTrue(generatedText.contains("internal val DefaultMaterialLightColorScheme"))
        assertTrue(generatedText.contains("object Radius"))
        assertTrue(generatedText.contains("object Shadows"))
        assertTrue(runnerText.contains("site.addzero.themes.LocalAppColorScheme provides site.addzero.themes.DefaultLightAppColorScheme"))
        assertTrue(runnerText.contains("site.addzero.themes.LocalAppRadius provides site.addzero.themes.Radius"))
        assertTrue(runnerText.contains("site.addzero.themes.LocalAppShadows provides site.addzero.themes.Shadows"))
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

    private fun createGradleCacheJar(
        root: Path,
        group: String,
        variantModule: String,
        rootModule: String,
        version: String,
        classEntry: String,
    ): Path {
        val filesRoot = root
            .resolve("caches")
            .resolve("modules-2")
            .resolve("files-2.1")
            .resolve(group)
        val variantVersionDirectory = filesRoot
            .resolve(variantModule)
            .resolve(version)
        val jarDirectory = variantVersionDirectory.resolve("jarhash")
        Files.createDirectories(jarDirectory)
        val jarFile = jarDirectory.resolve("$variantModule.jar")
        JarOutputStream(Files.newOutputStream(jarFile)).use { jar ->
            jar.putNextEntry(JarEntry(classEntry))
            jar.write(byteArrayOf(0))
            jar.closeEntry()
        }

        val rootModuleDirectory = filesRoot
            .resolve(rootModule)
            .resolve(version)
            .resolve("modulehash")
        Files.createDirectories(rootModuleDirectory)
        Files.write(rootModuleDirectory.resolve("$rootModule-$version.module"), "{}".toByteArray(Charsets.UTF_8))
        return jarFile
    }
}
