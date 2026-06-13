package site.addzero.composebuddy

import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import site.addzero.composebuddy.features.effectkeys.EffectKeysAnalysis
import site.addzero.composebuddy.support.ComposeFunctionTypeSupport
import site.addzero.composebuddy.support.MoveComposeComponentWithDependenciesSupport
import site.addzero.composebuddy.support.MoveToSharedSourceSetSupport
import java.nio.file.Paths

class ComposeBuddyFeaturesTest : BasePlatformTestCase() {
    fun testComposeFunctionTypeSupportExtractsReceiverFromComposableAnnotationCallSyntax() {
        val receiverType = ComposeFunctionTypeSupport.extractReceiverTypeText(
            "@androidx.compose.runtime.Composable() (androidx.compose.foundation.layout.RowScope).() -> kotlin.Unit",
        )

        assertEquals("androidx.compose.foundation.layout.RowScope", receiverType)
    }

    fun testComposeFunctionTypeSupportExtractsReceiverFromSimpleComposableSyntax() {
        val receiverType = ComposeFunctionTypeSupport.extractReceiverTypeText(
            "@Composable demo.ColumnScope.() -> kotlin.Unit",
        )

        assertEquals("demo.ColumnScope", receiverType)
    }

    fun testComposeFunctionTypeSupportExtractsReceiverFromParenthesizedComposableSyntax() {
        val receiverType = ComposeFunctionTypeSupport.extractReceiverTypeText(
            "(@androidx.compose.runtime.Composable androidx.compose.foundation.layout.RowScope.() -> kotlin.Unit)",
        )

        assertEquals("androidx.compose.foundation.layout.RowScope", receiverType)
    }

    fun testModifierChainIntentionExtractsRepeatedChain() {
        myFixture.configureByText(
            "ModifierChains.kt",
            """
            import androidx.compose.foundation.layout.padding
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.unit.dp

            @Composable
            fun Sample<caret>() {
                First(modifier = Modifier.padding(8.dp).padding(4.dp))
                Second(modifier = Modifier.padding(8.dp).padding(4.dp))
            }
            """.trimIndent(),
        )

        invokeIntention("(KMP Buddy) Extract repeated Modifier chain")

        val text = myFixture.file.text
        assertTrue(text.contains("private fun androidx.compose.ui.Modifier.modifierChainStyle()"))
        assertTrue(Regex("Modifier\\.modifierChainStyle\\(\\)").findAll(text).count() >= 2)
    }

    fun testModifierChainStyleableIntentionConvertsProjectModifierStyleHelpers() {
        val currentFile = myFixture.configureByText(
            "ModifierStyle.kt",
            """
            package demo

            import androidx.compose.foundation.background
            import androidx.compose.foundation.layout.fillMaxSize
            import androidx.compose.foundation.layout.padding
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.graphics.Color
            import androidx.compose.ui.unit.dp

            private fun Modifier.root<caret>Style(background: Color): Modifier =
                fillMaxSize()
                    .background(background)
                    .padding(12.dp)
            """.trimIndent(),
        )
        val otherFile = myFixture.addFileToProject(
            "OtherModifierStyle.kt",
            """
            package demo

            import androidx.compose.foundation.layout.height
            import androidx.compose.foundation.layout.width
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.unit.dp

            internal fun Modifier.cardStyle(): Modifier {
                return this.width(120.dp)
                    .height(80.dp)
            }
            """.trimIndent(),
        )

        invokeIntention("(KMP Buddy) Convert Modifier chain styles to styleable")

        assertTrue(currentFile.text.contains("import androidx.compose.foundation.style.styleable"))
        assertTrue(currentFile.text.contains("import androidx.compose.foundation.style.ExperimentalFoundationStyleApi"))
        assertTrue(currentFile.text.contains("@OptIn(ExperimentalFoundationStyleApi::class)"))
        assertTrue(currentFile.text.contains("private fun Modifier.rootStyle(background: Color): Modifier"))
        assertTrue(currentFile.text.contains("return styleable {\n        fillMaxSize()\n        background(background)\n        padding(12.dp)\n    }"))
        assertFalse(currentFile.text.contains("= fillMaxSize()"))

        assertTrue(otherFile.text.contains("import androidx.compose.foundation.style.styleable"))
        assertTrue(otherFile.text.contains("import androidx.compose.foundation.style.ExperimentalFoundationStyleApi"))
        assertTrue(otherFile.text.contains("@OptIn(ExperimentalFoundationStyleApi::class)"))
        assertTrue(otherFile.text.contains("internal fun Modifier.cardStyle(): Modifier"))
        assertTrue(otherFile.text.contains("return styleable {\n        width(120.dp)\n        height(80.dp)\n    }"))
        assertFalse(otherFile.text.contains("return this.width(120.dp)"))
    }

    fun testModifierChainStyleableIntentionIgnoresOrdinaryModifierArguments() {
        myFixture.configureByText(
            "OrdinaryModifier.kt",
            """
            import androidx.compose.foundation.layout.padding
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.unit.dp

            @Composable
            fun Sa<caret>mple() {
                Box(modifier = Modifier.padding(8.dp).padding(4.dp))
            }
            """.trimIndent(),
        )

        val actions = myFixture.filterAvailableIntentions("(KMP Buddy) Convert Modifier chain styles to styleable")
        assertEmpty(actions)
    }

    fun testModifierChainStyleableIntentionHoistsComposableThemeReads() {
        val file = myFixture.configureByText(
            "ThemeModifierStyle.kt",
            """
            package demo

            import androidx.compose.foundation.background
            import androidx.compose.foundation.border
            import androidx.compose.foundation.layout.fillMaxSize
            import androidx.compose.material3.MaterialTheme
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.unit.dp

            private fun Modifier.root<caret>Style(): Modifier =
                fillMaxSize()
                    .background(MaterialTheme.appColors.workbenchStageBackground)
                    .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            """.trimIndent(),
        )

        invokeIntention("(KMP Buddy) Convert Modifier chain styles to styleable")

        assertTrue(file.text.contains("import androidx.compose.runtime.Composable"))
        assertTrue(file.text.contains("@Composable\nprivate fun Modifier.rootStyle(): Modifier"))
        assertTrue(file.text.contains("val appColors = MaterialTheme.appColors"))
        assertTrue(file.text.contains("val colorScheme = MaterialTheme.colorScheme"))
        assertTrue(file.text.contains("background(appColors.workbenchStageBackground)"))
        assertTrue(file.text.contains("border(width = 1.dp, color = colorScheme.outlineVariant)"))
        assertFalse(file.text.contains("background(MaterialTheme.appColors.workbenchStageBackground)"))
        assertFalse(file.text.contains("color = MaterialTheme.colorScheme.outlineVariant"))
    }

    fun testModifierArgumentStyleIntentionExtractsLocalStyleableHelper() {
        myFixture.configureByText(
            "Item.kt",
            """
            import androidx.compose.foundation.background
            import androidx.compose.foundation.layout.Box
            import androidx.compose.foundation.layout.height
            import androidx.compose.material3.MaterialTheme
            import androidx.compose.material3.Text
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.Alignment
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.unit.dp

            @Composable
            fun Item(modifier: Modifier, item: String) {
                Box(
                    modifier = modifier.height(48.dp).back<caret>ground(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium,
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(item)
                }
            }
            """.trimIndent(),
        )

        invokeIntention("(KMP Buddy) Extract Modifier argument as styleable helper")

        val text = myFixture.file.text
        assertTrue(text.contains("import androidx.compose.foundation.style.styleable"))
        assertTrue(text.contains("import androidx.compose.foundation.style.ExperimentalFoundationStyleApi"))
        assertTrue(text.contains("modifier = modifier.itemStyle()"))
        assertTrue(text.contains("@OptIn(ExperimentalFoundationStyleApi::class)"))
        assertTrue(text.contains("@Composable\nprivate fun Modifier.itemStyle(): Modifier"))
        assertTrue(text.contains("val colorScheme = MaterialTheme.colorScheme"))
        assertTrue(text.contains("val shapes = MaterialTheme.shapes"))
        assertTrue(
            text.contains(
                """
                return styleable {
                        height(48.dp)
                        background(
                            color = colorScheme.primaryContainer,
                            shape = shapes.medium,
                        )
                    }
                """.trimIndent(),
            ),
        )
    }

    fun testUiStateIntentionCreatesMinimalUiState() {
        myFixture.configureByText(
            "UiState.kt",
            """
            import androidx.compose.runtime.Composable

            data class ScreenState(val title: String, val subtitle: String, val count: Int)

            @Composable
            fun Sc<caret>reen(state: ScreenState) {
                Text(state.title)
                Text(state.subtitle)
            }

            @Composable
            fun Host(state: ScreenState) {
                Screen(state = state)
            }
            """.trimIndent(),
        )

        invokeIntention("(KMP Buddy) Extract minimal UiState")

        val text = myFixture.file.text
        assertTrue(text.contains("data class"))
        assertTrue(text.contains("val title: String"))
        assertTrue(text.contains("val subtitle: String"))
    }

    fun testFlattenUsedObjectParametersIntentionIsAvailableForPropsChains() {
        myFixture.configureByText(
            "ShellStatusBar.kt",
            """
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.Modifier

            data class ShellStatusBarProps(
                val currentScene: String,
                val currentTitle: String,
                val pageCount: Int,
                val modifier: Modifier = Modifier,
            )

            @Composable
            private fun ShellStatusBar(shellStatusBarProps<caret>: ShellStatusBarProps) {
                Row(
                    modifier = shellStatusBarProps.modifier.fillMaxWidth(),
                ) {
                    Text(text = "${'$'}{shellStatusBarProps.currentScene} / ${'$'}{shellStatusBarProps.currentTitle}")
                    Text(text = "${'$'}{shellStatusBarProps.pageCount} pages in this scene")
                }
            }
            """.trimIndent(),
        )

        val actions = myFixture.filterAvailableIntentions("(KMP Buddy) Flatten used object params into signature")
        assertTrue(actions.isNotEmpty())
    }

    fun testEffectKeysIntentionAddsCapturedParameterKeys() {
        myFixture.configureByText(
            "Effects.kt",
            """
            import androidx.compose.runtime.Composable
            import androidx.compose.runtime.LaunchedEffect

            @Composable
            fun Screen<caret>(query: String) {
                LaunchedEffect {
                    println(query)
                }
            }
            """.trimIndent(),
        )

        invokeIntention(effectKeysIntentionText())

        myFixture.checkResult(
            """
            import androidx.compose.runtime.Composable
            import androidx.compose.runtime.LaunchedEffect

            @Composable
            fun Screen(query: String) {
                LaunchedEffect(query) {
                    println(query)
                }
            }
            """.trimIndent(),
        )
    }

    fun testEffectKeysIntentionIgnoresUnkeyedRememberInitialization() {
        myFixture.configureByText(
            "RememberInitialization.kt",
            """
            import androidx.compose.runtime.Composable
            import androidx.compose.runtime.remember

            @Composable
            fun <T> Demo<caret>(calculation: () -> T): T {
                return remember {
                    calculation()
                }
            }
            """.trimIndent(),
        )

        val actions = myFixture.filterAvailableIntentions(effectKeysIntentionText())
        assertEmpty(actions)
    }

    fun testEffectKeysIntentionIgnoresNamedKeyArgument() {
        myFixture.configureByText(
            "NamedEffectKey.kt",
            """
            import androidx.compose.runtime.Composable
            import androidx.compose.runtime.LaunchedEffect

            @Composable
            fun Screen<caret>(query: String) {
                LaunchedEffect(key1 = query) {
                    println(query)
                }
            }
            """.trimIndent(),
        )

        assertEmpty(EffectKeysAnalysis.analyze(findFunction("Screen")))
        val actions = myFixture.filterAvailableIntentions(effectKeysIntentionText())
        assertEmpty(actions)
    }

    fun testEffectKeysIntentionIgnoresRememberedStateHolderCapturedByEffect() {
        myFixture.configureByText(
            "NavigationScaffold.kt",
            """
            import androidx.compose.runtime.Composable
            import androidx.compose.runtime.LaunchedEffect

            class NavigationGroup(val id: String) {
                fun containsRoute(path: String): Boolean = true
            }

            class NavigationScaffoldState {
                fun ensureExpanded(id: String) = Unit
            }

            @Composable
            fun rememberNavigationScaffoldState(): NavigationScaffoldState = NavigationScaffoldState()

            @Composable
            fun NavigationScaffold<caret>(
                navigationGroups: List<NavigationGroup>,
                selectedRoutePath: String,
                state: NavigationScaffoldState = rememberNavigationScaffoldState(),
            ) {
                LaunchedEffect(navigationGroups) {
                    navigationGroups.forEach { group ->
                        state.ensureExpanded(group.id)
                    }
                }
                LaunchedEffect(navigationGroups, selectedRoutePath) {
                    navigationGroups.forEach { group ->
                        if (group.containsRoute(selectedRoutePath)) {
                            state.ensureExpanded(group.id)
                        }
                    }
                }
            }
            """.trimIndent(),
        )

        assertEmpty(EffectKeysAnalysis.analyze(findFunction("NavigationScaffold")))
        val actions = myFixture.filterAvailableIntentions(effectKeysIntentionText())
        assertEmpty(actions)
    }

    fun testEffectKeysIntentionIgnoresViewModelCapturedByEffect() {
        myFixture.configureByText(
            "NavigationScaffold.kt",
            """
            import androidx.compose.runtime.Composable
            import androidx.compose.runtime.LaunchedEffect

            class TreeViewModel<T> {
                fun selectNode(path: String) = Unit
            }

            @Composable
            fun NavigationScaffold<caret>(
                viewModel: TreeViewModel<*>,
                selectedRoutePath: String,
            ) {
                LaunchedEffect(selectedRoutePath) {
                    viewModel.selectNode(selectedRoutePath)
                }
            }
            """.trimIndent(),
        )

        assertEmpty(EffectKeysAnalysis.analyze(findFunction("NavigationScaffold")))
        val actions = myFixture.filterAvailableIntentions(effectKeysIntentionText())
        assertEmpty(actions)
    }

    fun testPreviewSampleIntentionIsHidden() {
        myFixture.configureByText(
            "Previewable.kt",
            """
            import androidx.compose.runtime.Composable

            @Composable
            fun Ca<caret>rd(title: String, loading: Boolean) {
                Text(title)
            }
            """.trimIndent(),
        )

        val actions = myFixture.filterAvailableIntentions("(KMP Buddy) Generate preview samples")
        assertEmpty(actions)
    }

    fun testPreviewPlaygroundIntentionGeneratesMinimalPreviewHarness() {
        myFixture.configureByText(
            "QuickPreview.kt",
            """
            import androidx.compose.runtime.Composable

            enum class Tone {
                Neutral,
                Positive,
            }

            data class CardMeta(
                val subtitle: String,
                val priority: Int,
            )

            @Composable
            fun Ca<caret>rd(
                title: String,
                enabled: Boolean,
                tone: Tone,
                meta: CardMeta,
                onClick: () -> Unit,
                count: Int = 0,
            ) {
                Text(title)
            }
            """.trimIndent(),
        )

        invokeIntention("(KMP Buddy) Generate quick preview playground")

        val text = myFixture.file.text
        assertTrue(text.contains("@androidx.compose.ui.tooling.preview.Preview("))
        assertTrue(text.contains("private fun CardQuickPreview()"))
        assertTrue(text.contains("androidx.compose.foundation.text.BasicText(text = \"Quick preview: Card\")"))
        assertTrue(text.contains("title = \""))
        assertTrue(text.contains("enabled = "))
        assertTrue(text.contains("tone = Tone.Neutral"))
        assertTrue(text.contains("meta = CardMeta(subtitle = \""))
        assertTrue(text.contains("onClick = { }"))
        assertFalse(text.contains("count = "))
    }

    fun testPreviewPlaygroundIntentionIsHiddenForUnsupportedRequiredTypes() {
        myFixture.configureByText(
            "UnsupportedPreview.kt",
            """
            import androidx.compose.runtime.Composable
            
            class ExternalPayload(
                private val raw: java.io.File,
            )

            @Composable
            fun Sc<caret>reen(payload: ExternalPayload) {
                Text(payload.toString())
            }
            """.trimIndent(),
        )

        val actions = myFixture.filterAvailableIntentions("(KMP Buddy) Generate quick preview playground")
        assertEmpty(actions)
    }

    fun testPreviewPlaygroundIntentionIsHiddenOutsideComposableFunctionName() {
        myFixture.configureByText(
            "QuickPreviewHost.kt",
            """
            import androidx.compose.runtime.Composable

            @Composable
            fun Card(title: String) {
                Text(title)
            }

            @Composable
            fun Host() {
                Car<caret>d(title = "demo")
            }
            """.trimIndent(),
        )

        val actions = myFixture.filterAvailableIntentions("(KMP Buddy) Generate quick preview playground")
        assertEmpty(actions)
    }

    fun testPreviewPlaygroundIntentionIsHiddenOnComposableParameterList() {
        myFixture.configureByText(
            "QuickPreviewSignature.kt",
            """
            import androidx.compose.runtime.Composable

            @Composable
            fun Card(title<caret>: String) {
                Text(title)
            }
            """.trimIndent(),
        )

        val actions = myFixture.filterAvailableIntentions("(KMP Buddy) Generate quick preview playground")
        assertEmpty(actions)
    }

    fun testPreviewPlaygroundIntentionIsAvailableForSelectedComposableFunction() {
        myFixture.configureByText(
            "SelectedQuickPreview.kt",
            """
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.Modifier

            @Composable
            fun HostConfigStatusStrip(
                text: String,
                modifier: Modifier = Modifier,
            ) {
                Box(
                    modifier = modifier,
                ) {
                    Text(text)
                }
            }
            """.trimIndent(),
        )

        val originalText = myFixture.file.text
        val selectionStart = originalText.indexOf("@Composable")
        val selectionEnd = originalText.lastIndexOf("}") + 1
        myFixture.editor.selectionModel.setSelection(selectionStart, selectionEnd)
        myFixture.editor.caretModel.moveToOffset(selectionStart + 1)

        invokeIntention("(KMP Buddy) Generate quick preview playground")

        val text = myFixture.file.text
        assertTrue(text.contains("private fun HostConfigStatusStripQuickPreview()"))
        assertTrue(text.contains("HostConfigStatusStrip("))
    }

    fun testPreviewPlaygroundIntentionIsHiddenForSelectionInsideComposableBody() {
        myFixture.configureByText(
            "SelectedQuickPreviewBody.kt",
            """
            import androidx.compose.runtime.Composable

            @Composable
            fun HostConfigStatusStrip(
                text: String,
            ) {
                Box {
                    Te<caret>xt(text)
                }
            }
            """.trimIndent(),
        )

        val originalText = myFixture.file.text
        val selectionStart = originalText.indexOf("Text(text)")
        val selectionEnd = selectionStart + "Text(text)".length
        myFixture.editor.selectionModel.setSelection(selectionStart, selectionEnd)
        myFixture.editor.caretModel.moveToOffset(selectionStart + 2)

        val actions = myFixture.filterAvailableIntentions("(KMP Buddy) Generate quick preview playground")
        assertEmpty(actions)
    }

    fun testPreviewPlaygroundIntentionBuildsImportedComplexModels() {
        myFixture.addFileToProject(
            "preview/model/RemoteModels.kt",
            """
            package preview.model

            enum class Accent {
                Primary,
                Secondary,
            }

            data class Detail(
                val caption: String,
                val weight: Int,
            )

            data class RemoteCardMeta(
                val accent: Accent,
                val detail: Detail,
            )

            data class Boxed<T>(
                val value: T,
                val active: Boolean,
            )
            """.trimIndent(),
        )

        myFixture.configureByText(
            "ImportedPreview.kt",
            """
            import androidx.compose.runtime.Composable
            import preview.model.Boxed
            import preview.model.RemoteCardMeta

            @Composable
            fun Ca<caret>rd(meta: Boxed<RemoteCardMeta>) {
                Text(meta.toString())
            }
            """.trimIndent(),
        )

        invokeIntention("(KMP Buddy) Generate quick preview playground")

        val text = myFixture.file.text
        assertTrue(text.contains("meta = preview.model.Boxed("))
        assertTrue(text.contains("value = preview.model.RemoteCardMeta("))
        assertTrue(text.contains("accent = preview.model.Accent.Primary"))
        assertTrue(text.contains("detail = preview.model.Detail("))
        assertTrue(text.contains("active = "))
    }

    fun testPreviewPlaygroundSupportsLiquidBackdropComponents() {
        myFixture.configureByText(
            "LiquidButton.kt",
            """
            package site.addzero.component.液态玻璃

            import androidx.compose.foundation.layout.RowScope
            import androidx.compose.runtime.Composable
            import com.kyant.backdrop.Backdrop

            @Composable
            fun LiquidGlassSceneRoot(content: @Composable () -> Unit) {
                content()
            }

            object LocalLiquidBackdrop {
                val current: Backdrop
                    get() = TODO("preview")
            }

            @Composable
            fun Liquid<caret>Button(
                onClick: () -> Unit,
                backdrop: Backdrop,
                content: @Composable RowScope.() -> Unit,
            ) {
                content
            }
            """.trimIndent(),
        )

        invokeIntention("(KMP Buddy) Generate quick preview playground")

        val text = myFixture.file.text
        assertTrue(text.contains("private fun LiquidButtonQuickPreview()"))
        assertTrue(text.contains("LiquidGlassSceneRoot {"))
        assertTrue(text.contains("onClick = { }"))
        assertTrue(text.contains("backdrop = LocalLiquidBackdrop.current"))
        assertTrue(text.contains("content = { androidx.compose.foundation.text.BasicText(text = \"Preview content\") }"))
    }

    fun testMoveFileToSharedSourceSetIntentionIsAvailableOnModelDeclaration() {
        configureProjectFile(
            "feature/src/jvmMain/kotlin/demo/DeviceState.kt",
            """
            package demo

            data class Device<caret>State(
                val online: Boolean,
                val name: String,
            )
            """.trimIndent(),
        )

        val actions = myFixture.filterAvailableIntentions("(KMP Buddy) Move file to shared source set")
        assertTrue(actions.isNotEmpty())
    }

    fun testMoveFileToSharedSourceSetIntentionIsHiddenInsideComposableBody() {
        configureProjectFile(
            "feature/src/jvmMain/kotlin/demo/DeviceScreen.kt",
            """
            package demo

            import androidx.compose.runtime.Composable

            data class DeviceState(
                val online: Boolean,
            )

            @Composable
            fun DeviceScreen(state: DeviceState) {
                Tex<caret>t(state.toString())
            }
            """.trimIndent(),
        )

        val actions = myFixture.filterAvailableIntentions("(KMP Buddy) Move file to shared source set")
        assertEmpty(actions)
    }

    fun testMoveFileToSharedSourceSetPlanRequiresRememberedModuleSourceSet() {
        val virtualFile = myFixture.tempDirFixture.createFile(
            "feature/src/jvmMain/kotlin/demo/DeviceState.kt",
            """
            package demo

            data class DeviceState(val online: Boolean)
            """.trimIndent(),
        )

        assertNull(MoveToSharedSourceSetSupport.buildPlan(virtualFile))
    }

    fun testMoveFileToSharedSourceSetPlanUsesRememberedShareLogicForModule() {
        val virtualFile = myFixture.tempDirFixture.createFile(
            "feature/src/jvmMain/kotlin/demo/DeviceState.kt",
            """
            package demo

            data class DeviceState(val online: Boolean)
            """.trimIndent(),
        )
        val moduleRootPath = Paths.get(myFixture.tempDirFixture.findOrCreateDir("feature").path)
        MoveToSharedSourceSetSupport.rememberSharedSourceSet(
            moduleRootPath,
            MoveToSharedSourceSetSupport.SHARE_LOGIC_SOURCE_SET,
        )

        val plan = MoveToSharedSourceSetSupport.buildPlan(virtualFile)

        assertNotNull(plan)
        assertEquals(
            moduleRootPath
                .resolve("src/share_logic/kotlin/demo/DeviceState.kt")
                .normalize()
                .toString(),
            plan!!.targetFilePath.normalize().toString(),
        )
    }

    fun testMoveFileToSharedSourceSetPlanDoesNotMoveInsideTargetSourceSet() {
        val virtualFile = myFixture.tempDirFixture.createFile(
            "feature/src/share_ui/kotlin/demo/DeviceState.kt",
            """
            package demo

            data class DeviceState(val online: Boolean)
            """.trimIndent(),
        )
        val moduleRootPath = Paths.get(myFixture.tempDirFixture.findOrCreateDir("feature").path)
        MoveToSharedSourceSetSupport.rememberSharedSourceSet(
            moduleRootPath,
            MoveToSharedSourceSetSupport.SHARE_UI_SOURCE_SET,
        )

        assertNull(MoveToSharedSourceSetSupport.buildPlan(virtualFile))
    }

    fun testMoveComposeComponentWithDependenciesIntentionIsAvailableOnTopLevelComposable() {
        configureProjectFile(
            "feature/src/commonMain/kotlin/demo/Badge.kt",
            """
            package demo

            import androidx.compose.runtime.Composable

            @Composable
            fun Bad<caret>ge() {
                Text(Tokens.Label)
            }
            """.trimIndent(),
        )

        val actions = myFixture.filterAvailableIntentions(
            "(KMP Buddy) Extract minimal Compose dependency set",
        )

        assertTrue(actions.isNotEmpty())
    }

    fun testMoveComposeComponentWithDependenciesCollectsSamePackageDependencyClosure() {
        val badgeFile = configureProjectFile(
            "feature/src/commonMain/kotlin/demo/unavailable/UnavailableBadge.kt",
            """
            package demo.unavailable

            import androidx.compose.runtime.Composable

            @Composable
            fun Unavailable<caret>Badge() {
                Text(UnavailableRouteVisualTokens.TextMuted)
            }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "feature/src/commonMain/kotlin/demo/unavailable/UnavailableRouteVisualTokens.kt",
            """
            package demo.unavailable

            internal object UnavailableRouteVisualTokens {
                val TextMuted = Color(0xFF71757A)
            }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "feature/src/commonMain/kotlin/demo/unavailable/UnavailableRouteScreen.kt",
            """
            package demo.unavailable

            import androidx.compose.runtime.Composable

            @Composable
            fun UnavailableRouteScreen() {
                UnavailableBadge()
                Text(UnavailableRouteVisualTokens.TextMuted.toString())
            }
            """.trimIndent(),
        )

        val plan = MoveComposeComponentWithDependenciesSupport.buildPlan(
            project = project,
            componentFile = badgeFile,
        )

        assertNotNull(plan)
        val sourceNames = plan!!.dependencyFiles.map { file -> file.name }.toSet()
        assertEquals(setOf("UnavailableBadge.kt", "UnavailableRouteVisualTokens.kt"), sourceNames)
        val targetPaths = plan.minimalUseMovePlans.map { movePlan ->
            movePlan.targetFilePath.normalize().toString()
        }.toSet()
        val parentDirectoryPath = Paths.get(badgeFile.virtualFile!!.parent.path).normalize()
        assertEquals(
            setOf(
                parentDirectoryPath
                    .resolve("minimal_use/UnavailableBadge.kt")
                    .normalize()
                    .toString(),
            ),
            targetPaths,
        )
        val commonTargetPaths = plan.commonMovePlans.map { movePlan ->
            movePlan.targetFilePath.normalize().toString()
        }.toSet()
        assertEquals(
            setOf(
                parentDirectoryPath
                    .resolve("common/UnavailableRouteVisualTokens.kt")
                    .normalize()
                    .toString(),
            ),
            commonTargetPaths,
        )
        assertEquals(1, plan.couplingPoints.size)
        val readmeContent = plan.couplingReadme!!.content
        assertTrue(readmeContent.contains("demo.unavailable.common.UnavailableRouteVisualTokens"))
        assertTrue(readmeContent.contains("demo.unavailable.UnavailableRouteScreen"))

        assertTrue(
            MoveComposeComponentWithDependenciesSupport.writeCouplingReadme(
                project = project,
                plan = plan,
                commandName = "Test README",
            ),
        )
        val readmeFile = plan.componentParentDirectory.findChild("common")!!.findChild("README.md")!!
        val readmeText = String(readmeFile.contentsToByteArray(), Charsets.UTF_8)
        assertTrue(readmeText.contains("demo.unavailable.common.UnavailableRouteVisualTokens"))
        assertTrue(readmeText.contains("demo.unavailable.UnavailableRouteScreen"))
    }

    private fun configureProjectFile(path: String, textWithCaret: String): KtFile {
        val caretMarker = "<caret>"
        val caretOffset = textWithCaret.indexOf(caretMarker)
        assertTrue(caretOffset >= 0)

        val cleanedText = textWithCaret.replace(caretMarker, "")
        val kotlinRootMarker = "/kotlin/"
        val kotlinRootIndex = path.indexOf(kotlinRootMarker)
        assertTrue(kotlinRootIndex >= 0)
        val sourceRootPath = path.substring(0, kotlinRootIndex + kotlinRootMarker.length - 1)
        val sourceRoot = myFixture.tempDirFixture.findOrCreateDir(sourceRootPath)
        PsiTestUtil.addSourceRoot(module, sourceRoot)

        val virtualFile = myFixture.tempDirFixture.createFile(path, cleanedText)
        myFixture.openFileInEditor(virtualFile)
        myFixture.editor.caretModel.moveToOffset(caretOffset)
        return myFixture.file as KtFile
    }

    fun testContainerWrapIntentionRequiresSelectionAndWrapsSelectedContainer() {
        myFixture.configureByText(
            "Wrap.kt",
            """
            import androidx.compose.foundation.layout.Column
            import androidx.compose.foundation.layout.Row
            import androidx.compose.material.Text
            import androidx.compose.runtime.Composable

            @Composable
            fun Screen() {
                Column {
                    Row {
                        Text("A")
                    }
                }
            }
            """.trimIndent(),
        )
        val text = myFixture.file.text
        val selectionStart = text.indexOf("Row {")
        val selectionEnd = text.indexOf("}", selectionStart) + 1
        myFixture.editor.selectionModel.setSelection(selectionStart, selectionEnd)
        myFixture.editor.caretModel.moveToOffset(selectionStart + 2)

        invokeIntention("(KMP Buddy) Wrap selected layout container as higher-order container")

        val updatedText = myFixture.file.text
        assertTrue(updatedText.contains("private fun RowContainer(content: @androidx.compose.runtime.Composable () -> Unit)"))
        assertTrue(updatedText.contains("RowContainer {"))
    }

    fun testContainerWrapIntentionIsHiddenWithoutSelection() {
        myFixture.configureByText(
            "NoSelection.kt",
            """
            import androidx.compose.runtime.Composable

            @Composable
            fun Screen() {
                Row<caret> {
                    Text("A")
                }
            }
            """.trimIndent(),
        )

        val actions = myFixture.filterAvailableIntentions("(KMP Buddy) Wrap selected layout container as higher-order container")
        assertEmpty(actions)
    }

    fun testViewModelInlineIntentionExtractsUsedStateAndEvents() {
        myFixture.configureByText(
            "AddTree.kt",
            """
            import androidx.compose.runtime.Composable

            interface TreeViewModel<T> {
                val expanded: Set<T>
                fun onToggle(node: T)
                fun onRefresh()
            }

            @Composable
            fun <T> AddTree(viewModel<caret>: TreeViewModel<T>, node: T) {
                Text(viewModel.expanded.toString())
                Clickable { viewModel.onToggle(node) }
                Button(onClick = { viewModel.onRefresh() }) { }
            }

            @Composable
            fun Host(vm: TreeViewModel<String>) {
                AddTree(viewModel = vm, node = "A")
            }
            """.trimIndent(),
        )

        invokeIntention("(KMP Buddy) Inline used viewModel state and events into parameters")

        val text = myFixture.file.text
        assertTrue(text.contains("expanded: Set<T>"))
        assertTrue(text.contains("onToggle: (T) -> Unit"))
        assertTrue(text.contains("onRefresh: () -> Unit"))
        assertTrue(text.contains("expanded = vm.expanded"))
        assertTrue(text.contains("onToggle = { arg0 -> vm.onToggle(arg0) }"))
        assertTrue(text.contains("onRefresh = {  -> vm.onRefresh() }") || text.contains("onRefresh = { -> vm.onRefresh() }") || text.contains("onRefresh = { vm.onRefresh() }"))
    }

    fun testViewModelInlineIntentionExtractsNestedStateAndEvents() {
        myFixture.configureByText(
            "NestedAddTree.kt",
            """
            import androidx.compose.runtime.Composable

            data class TreeUiState<T>(
                val expanded: Set<T>,
                val selected: T?,
            )

            interface TreeViewModel<T> {
                val state: TreeUiState<T>
                fun onToggle(node: T)
            }

            @Composable
            fun <T> AddTree(viewModel<caret>: TreeViewModel<T>, node: T) {
                Text(viewModel.state.expanded.toString())
                Text(viewModel.state.selected.toString())
                Clickable { viewModel.onToggle(node) }
            }

            @Composable
            fun Host(vm: TreeViewModel<String>) {
                AddTree(viewModel = vm, node = "A")
            }
            """.trimIndent(),
        )

        invokeIntention("(KMP Buddy) Inline used viewModel state and events into parameters")

        val text = myFixture.file.text
        assertTrue(text.contains("stateExpanded: Set<T>"))
        assertTrue(text.contains("stateSelected: T?"))
        assertTrue(text.contains("Text(stateExpanded.toString())"))
        assertTrue(text.contains("Text(stateSelected.toString())"))
        assertTrue(text.contains("stateExpanded = vm.state.expanded"))
        assertTrue(text.contains("stateSelected = vm.state.selected"))
        assertTrue(text.contains("onToggle = { arg0 -> vm.onToggle(arg0) }"))
    }

    fun testViewModelInlineIntentionKeepsComputedPropertyInBody() {
        myFixture.configureByText(
            "ComputedViewModel.kt",
            """
            import androidx.compose.runtime.Composable

            data class TreeUiState<T>(
                val expanded: Set<T>,
                val selected: T?,
            )

            class TreeViewModel<T>(
                val state: TreeUiState<T>,
            ) {
                val expandedCount: Int
                    get() = state.expanded.size

                fun onToggle(node: T) {}
            }

            @Composable
            fun <T> AddTree(viewModel<caret>: TreeViewModel<T>, node: T) {
                Text(viewModel.expandedCount.toString())
                Text(viewModel.state.selected.toString())
                Clickable { viewModel.onToggle(node) }
            }

            @Composable
            fun Host(vm: TreeViewModel<String>) {
                AddTree(viewModel = vm, node = "A")
            }
            """.trimIndent(),
        )

        invokeIntention("(KMP Buddy) Inline used viewModel state and events into parameters")

        val text = myFixture.file.text
        assertTrue(text.contains("viewModel: TreeViewModel<T>"))
        assertTrue(text.contains("stateSelected: T?"))
        assertFalse(text.contains("fun <T> AddTree(expandedCount: Int"))
        assertFalse(text.contains("viewModel: TreeViewModel<T>, expandedCount: Int"))
        assertTrue(text.contains("Text(viewModel.expandedCount.toString())"))
        assertTrue(text.contains("Text(stateSelected.toString())"))
        assertTrue(text.contains("AddTree(viewModel = vm"))
    }

    fun testViewModelInlineIntentionExtractsDelegatedStatePropertiesFromStateParameter() {
        myFixture.configureByText(
            "StateMessage.kt",
            """
            import androidx.compose.runtime.Composable
            import androidx.compose.runtime.getValue
            import androidx.compose.runtime.mutableStateOf
            import androidx.compose.runtime.setValue

            class NoteScreenViewModel {
                var message by mutableStateOf<String?>(null)
                    private set

                var errorMessage by mutableStateOf<String?>(null)
                    private set
            }

            @Composable
            fun Host(viewModel: NoteScreenViewModel) {
                StateMessage(viewModel)
            }

            @Composable
            private fun State<caret>Message(state: NoteScreenViewModel) {
                val text = state.errorMessage ?: state.message ?: return
                Text(
                    text = text,
                    color = if (state.errorMessage == null) "primary" else "error",
                )
            }

            @Composable
            private fun Text(text: String, color: String) {
                text.hashCode()
                color.hashCode()
            }
            """.trimIndent(),
        )

        invokeIntention("(KMP Buddy) Inline used viewModel state and events into parameters")

        val text = myFixture.file.text
        assertTrue(text.contains("private fun StateMessage(errorMessage: String?, message: String?)"))
        assertTrue(text.contains("val text = errorMessage ?: message ?: return"))
        assertTrue(text.contains("color = if (errorMessage == null) \"primary\" else \"error\""))
        assertTrue(text.contains("StateMessage(errorMessage = viewModel.errorMessage, message = viewModel.message)"))
    }

    fun testParameterSortIntentionGroupsPropsStateEventsAndLambdas() {
        myFixture.configureByText(
            "SortedParameters.kt",
            """
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.Modifier

            @Composable
            fun SearchScreen<caret>(
                onRetry: () -> Unit,
                footer: @Composable () -> Unit,
                modifier: Modifier = Modifier,
                query: String,
                title: String,
                onQueryChange: (String) -> Unit,
                onSubmit: () -> Unit,
            ) {
                Text(title)
            }
            """.trimIndent(),
        )

        invokeIntention("(KMP Buddy) Sort parameters by props, state, events, and lambdas")

        myFixture.checkResult(
            """
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.Modifier

            @Composable
            fun SearchScreen(
                modifier: Modifier = Modifier,
                title: String,
                query: String,
                onQueryChange: (String) -> Unit,
                onRetry: () -> Unit,
                onSubmit: () -> Unit,
                footer: @Composable () -> Unit
            ) {
                Text(title)
            }
            """.trimIndent(),
        )
    }

    fun testParameterSortIntentionKeepsNamedCallSitesSafe() {
        myFixture.configureByText(
            "NamedCallSites.kt",
            """
            import androidx.compose.runtime.Composable

            @Composable
            fun SearchScreen<caret>(
                onRetry: () -> Unit,
                query: String,
                onQueryChange: (String) -> Unit,
                title: String,
            ) {
                Text(title)
            }

            @Composable
            fun Host(query: String, onQueryChange: (String) -> Unit) {
                SearchScreen(
                    onRetry = {},
                    query = query,
                    onQueryChange = onQueryChange,
                    title = "Search",
                )
            }
            """.trimIndent(),
        )

        invokeIntention("(KMP Buddy) Sort parameters by props, state, events, and lambdas")

        val text = myFixture.file.text
        assertTrue(text.contains("fun SearchScreen(\n    title: String,\n    query: String,\n    onQueryChange: (String) -> Unit,\n    onRetry: () -> Unit\n)"))
        assertTrue(text.contains("SearchScreen(\n        onRetry = {},\n        query = query,\n        onQueryChange = onQueryChange,\n        title = \"Search\",\n    )"))
    }

    fun testParameterSortIntentionIsHiddenForPositionalCallSites() {
        myFixture.configureByText(
            "UnsafeCallSites.kt",
            """
            import androidx.compose.runtime.Composable

            @Composable
            fun SearchScreen<caret>(
                onRetry: () -> Unit,
                query: String,
                onQueryChange: (String) -> Unit,
                title: String,
            ) {
                Text(title)
            }

            @Composable
            fun Host(query: String, onQueryChange: (String) -> Unit) {
                SearchScreen({}, query, onQueryChange, "Search")
            }
            """.trimIndent(),
        )

        val actions = myFixture.filterAvailableIntentions("(KMP Buddy) Sort parameters by props, state, events, and lambdas")
        assertEmpty(actions)
    }

    fun testCallArgFillIntentionFillsPlaceholderArgumentsFromParameters() {
        myFixture.configureByText(
            "FillCallArgs.kt",
            """
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.graphics.Color

            @Composable
            fun Screen(
                text: String,
                color: Color,
                modifier: Modifier,
            ) {
                Text<caret>(
                    text = TODO(),
                    color = null,
                    modifier = "",
                )
            }
            """.trimIndent(),
        )

        invokeIntention("(KMP Buddy) Fill call arguments from same-named parameters")

        val text = myFixture.file.text
        assertTrue(text.contains("text = text"))
        assertTrue(text.contains("color = color"))
        assertTrue(text.contains("modifier = modifier"))
    }

    fun testCallArgFillIntentionFillsMissingArgumentExpression() {
        myFixture.configureByText(
            "FillMissingCallArgs.kt",
            """
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.graphics.Color

            @Composable
            fun Screen(
                text: String,
                color: Color,
            ) {
                Text<caret>(
                    text = ,
                    color = ,
                )
            }
            """.trimIndent(),
        )

        invokeIntention("(KMP Buddy) Fill call arguments from same-named parameters")

        val text = myFixture.file.text
        assertTrue(text.contains("text = text"))
        assertTrue(text.contains("color = color"))
    }

    fun testCallArgFillIntentionIsHiddenForRealExpressions() {
        myFixture.configureByText(
            "NoFillCallArgs.kt",
            """
            import androidx.compose.runtime.Composable

            @Composable
            fun Screen(
                text: String,
            ) {
                Text<caret>(
                    text = text.uppercase(),
                )
            }
            """.trimIndent(),
        )

        val actions = myFixture.filterAvailableIntentions("(KMP Buddy) Fill call arguments from same-named parameters")
        assertEmpty(actions)
    }

    fun testCallArgExtractSingleIntentionAddsComposableParameter() {
        myFixture.configureByText(
            "ExtractSingleCallArg.kt",
            """
            import androidx.compose.runtime.Composable

            @Composable
            fun Text(
                text: String,
                lineHeight: Int,
                style: String,
            ) {}

            @Composable
            fun Screen(
                text: String,
                modifier: String,
            ) {
                Text(
                    text = text,
                    lineHeight<caret> = TODO(),
                    style = "body",
                )
            }
            """.trimIndent(),
        )

        invokeIntention("(KMP Buddy) Extract current call argument to composable parameter")

        val text = myFixture.file.text
        assertTrue(text.contains("lineHeight: kotlin.Int = TODO()") || text.contains("lineHeight: Int = TODO()"))
        assertTrue(text.contains("lineHeight = lineHeight"))
    }

    fun testCallArgExtractBatchIntentionAddsAllPlaceholderParameters() {
        myFixture.configureByText(
            "ExtractBatchCallArgs.kt",
            """
            import androidx.compose.runtime.Composable

            @Composable
            fun Text(
                text: String,
                color: String,
                lineHeight: Int,
                overflow: String?,
                style: String,
            ) {}

            @Composable
            fun Screen(
                text: String,
            ) {
                Text<caret>(
                    text = text,
                    color = TODO(),
                    lineHeight = TODO(),
                    overflow = null,
                    style = "body",
                )
            }
            """.trimIndent(),
        )

        invokeIntention("(KMP Buddy) Extract placeholder call arguments to composable parameters")

        val text = myFixture.file.text
        assertTrue(text.contains("color: kotlin.String = TODO()") || text.contains("color: String = TODO()"))
        assertTrue(text.contains("lineHeight: kotlin.Int = TODO()") || text.contains("lineHeight: Int = TODO()"))
        assertTrue(text.contains("overflow: kotlin.String? = null") || text.contains("overflow: String? = null"))
        assertTrue(text.contains("color = color"))
        assertTrue(text.contains("lineHeight = lineHeight"))
        assertTrue(text.contains("overflow = overflow"))
    }

    fun testSlotSpiIntentionExtractsCurrentNamedSlot() {
        myFixture.configureByText(
            "AlertSlots.kt",
            """
            import androidx.compose.runtime.Composable

            interface DialogButtonsScope

            @Composable
            fun Text(text: String) {}

            fun DialogButtonsScope.cancel(onClick: () -> Unit, content: @Composable () -> Unit) {}

            fun DialogButtonsScope.default(onClick: () -> Unit, content: @Composable () -> Unit) {}

            @Composable
            fun AlertDialog(
                title: @Composable () -> Unit,
                buttons: DialogButtonsScope.() -> Unit,
            ) {}

            class State {
                fun dismissAlert() {}
            }

            @Composable
            fun App() {
                val state: State = State()
                AlertDialog(
                    title = {
                        Text("title")
                    },
                    buttons = {
                        can<caret>cel(onClick = state::dismissAlert) {
                            Text("关闭")
                        }
                        default(onClick = state::dismissAlert) {
                            Text("继续")
                        }
                    },
                )
            }
            """.trimIndent(),
        )

        invokeIntention("(KMP Buddy) Extract named slot as SPI + default implementation")

        val text = myFixture.file.text
        assertTrue(text.contains("interface AppAlertDialogButtonsSpi"))
        assertTrue(text.contains("class DefaultAppAlertDialogButtonsSpi : AppAlertDialogButtonsSpi"))
        assertTrue(text.contains("scope: DialogButtonsScope"))
        assertTrue(text.contains("state: State"))
        assertTrue(text.contains("buttons = { org.koin.compose.koinInject<AppAlertDialogButtonsSpi>().Render(this, state = state) }"))
        assertTrue(text.contains("with(scope)"))
        assertTrue(text.contains("cancel(onClick = state::dismissAlert)"))
    }

    fun testSlotSpiIntentionExtractsAllSlotsFromComposableCall() {
        myFixture.configureByText(
            "AlertAllSlots.kt",
            """
            import androidx.compose.runtime.Composable

            interface DialogButtonsScope

            @Composable
            fun Text(text: String) {}

            fun DialogButtonsScope.cancel(onClick: () -> Unit, content: @Composable () -> Unit) {}

            @Composable
            fun AlertDialog(
                title: @Composable () -> Unit,
                buttons: DialogButtonsScope.() -> Unit,
            ) {}

            class State {
                fun dismissAlert() {}
            }

            @Composable
            fun App() {
                val state: State = State()
                Al<caret>ertDialog(
                    title = {
                        Text("title")
                    },
                    buttons = {
                        cancel(onClick = state::dismissAlert) {
                            Text("关闭")
                        }
                    },
                )
            }
            """.trimIndent(),
        )

        invokeIntention("(KMP Buddy) Extract named slot as SPI + default implementation")

        val text = myFixture.file.text
        assertTrue(text.contains("interface AppAlertDialogTitleSpi"))
        assertTrue(text.contains("class DefaultAppAlertDialogTitleSpi : AppAlertDialogTitleSpi"))
        assertTrue(text.contains("interface AppAlertDialogButtonsSpi"))
        assertTrue(text.contains("class DefaultAppAlertDialogButtonsSpi : AppAlertDialogButtonsSpi"))
        assertTrue(text.contains("title = { org.koin.compose.koinInject<AppAlertDialogTitleSpi>().Render() }"))
        assertTrue(text.contains("buttons = { org.koin.compose.koinInject<AppAlertDialogButtonsSpi>().Render(this, state = state) }"))
    }

    fun testSlotSpiIntentionExtractsSelectedSingleSlot() {
        myFixture.configureByText(
            "AlertSelectedSlot.kt",
            """
            import androidx.compose.runtime.Composable

            interface DialogButtonsScope

            @Composable
            fun Text(text: String) {}

            fun DialogButtonsScope.cancel(onClick: () -> Unit, content: @Composable () -> Unit) {}

            @Composable
            fun AlertDialog(
                title: @Composable () -> Unit,
                buttons: DialogButtonsScope.() -> Unit,
            ) {}

            class State {
                fun dismissAlert() {}
            }

            @Composable
            fun App() {
                val state: State = State()
                AlertDialog(
                    title = {
                        Text("title")
                    },
                    buttons = {
                        cancel(onClick = state::dismissAlert) {
                            Text("关闭")
                        }
                    },
                )
            }
            """.trimIndent(),
        )

        val originalText = myFixture.file.text
        val selectedText = "Text(\"title\")"
        val selectionStart = originalText.indexOf(selectedText)
        val selectionEnd = selectionStart + selectedText.length
        myFixture.editor.selectionModel.setSelection(selectionStart, selectionEnd)
        myFixture.editor.caretModel.moveToOffset(selectionStart + 1)

        invokeIntention("(KMP Buddy) Extract named slot as SPI + default implementation")

        val text = myFixture.file.text
        assertTrue(text.contains("interface AppAlertDialogTitleSpi"))
        assertTrue(text.contains("class DefaultAppAlertDialogTitleSpi : AppAlertDialogTitleSpi"))
        assertTrue(text.contains("title = { org.koin.compose.koinInject<AppAlertDialogTitleSpi>().Render() }"))
        assertFalse(text.contains("interface AppAlertDialogButtonsSpi"))
    }

    fun testSlotSpiIntentionExtractsOnlySelectedContinuousSlots() {
        myFixture.configureByText(
            "AlertSelectedSlots.kt",
            """
            import androidx.compose.runtime.Composable

            interface DialogButtonsScope

            @Composable
            fun Text(text: String) {}

            fun DialogButtonsScope.cancel(onClick: () -> Unit, content: @Composable () -> Unit) {}

            @Composable
            fun AlertDialog(
                title: @Composable () -> Unit,
                message: @Composable () -> Unit,
                buttons: DialogButtonsScope.() -> Unit,
            ) {}

            class State {
                fun dismissAlert() {}
            }

            @Composable
            fun App() {
                val state: State = State()
                AlertDialog(
                    title = {
                        Text("title")
                    },
                    message = {
                        Text("message")
                    },
                    buttons = {
                        cancel(onClick = state::dismissAlert) {
                            Text("关闭")
                        }
                    },
                )
            }
            """.trimIndent(),
        )

        val originalText = myFixture.file.text
        val selectionStart = originalText.indexOf("title = {")
        val selectionEnd = originalText.indexOf("buttons = {")
        myFixture.editor.selectionModel.setSelection(selectionStart, selectionEnd)
        myFixture.editor.caretModel.moveToOffset(selectionStart + 1)

        invokeIntention("(KMP Buddy) Extract named slot as SPI + default implementation")

        val text = myFixture.file.text
        assertTrue(text.contains("interface AppAlertDialogTitleSpi"))
        assertTrue(text.contains("interface AppAlertDialogMessageSpi"))
        assertTrue(text.contains("title = { org.koin.compose.koinInject<AppAlertDialogTitleSpi>().Render() }"))
        assertTrue(text.contains("message = { org.koin.compose.koinInject<AppAlertDialogMessageSpi>().Render() }"))
        assertFalse(text.contains("interface AppAlertDialogButtonsSpi"))
    }

    fun testSelectedRegionSpiIntentionExtractsSelectedComposeStatementWithReceiverAndState() {
        myFixture.configureByText(
            "SelectedRegionSpi.kt",
            """
            import androidx.compose.runtime.Composable

            interface ColumnScope

            object Modifier

            class State(
                val projectName: String,
            ) {
                fun updateProjectName(value: String) {}
            }

            @Composable
            fun CupertinoText(text: String) {}

            @Composable
            fun CupertinoBorderedTextField(
                value: String,
                onValueChange: (String) -> Unit,
                modifier: Modifier,
                singleLine: Boolean,
                placeholder: @Composable () -> Unit,
            ) {}

            @Composable
            fun Row(content: @Composable () -> Unit = {}) {}

            @Composable
            fun WorkbenchCard(
                content: @Composable ColumnScope.() -> Unit,
            ) {}

            @Composable
            fun FormsPage(state: State) {
                WorkbenchCard {
                    CupertinoBorderedTextField(
                        value = state.projectName,
                        onValueChange = state::updateProjectName,
                        modifier = Modifier,
                        singleLine = true,
                        placeholder = {
                            CupertinoText("输入项目名称")
                        },
                    )
                    Row {}
                }
            }
            """.trimIndent(),
        )

        val originalText = myFixture.file.text
        val selectionStart = originalText.lastIndexOf("CupertinoBorderedTextField(")
        val selectionEnd = originalText.indexOf("Row {}", selectionStart)
        myFixture.editor.selectionModel.setSelection(selectionStart, selectionEnd)
        myFixture.editor.caretModel.moveToOffset(selectionStart + 1)

        invokeIntention("(KMP Buddy) Extract selected Compose region as SPI + default implementation")

        val text = myFixture.file.text
        assertTrue(text.contains("interface FormsPageCupertinoBorderedTextFieldSpi"))
        assertTrue(text.contains("class DefaultFormsPageCupertinoBorderedTextFieldSpi : FormsPageCupertinoBorderedTextFieldSpi"))
        assertTrue(text.contains("state: State"))
        assertFalse(text.contains("scope: ColumnScope"))
        assertTrue(text.contains("org.koin.compose.koinInject<FormsPageCupertinoBorderedTextFieldSpi>().Render(state = state)"))
        assertFalse(text.contains("with(scope)"))
        assertTrue(text.contains("value = state.projectName"))
        assertTrue(text.contains("onValueChange = state::updateProjectName"))
    }

    fun testSelectedRegionSpiIntentionIsHiddenWithoutSelection() {
        myFixture.configureByText(
            "SelectedRegionNoSelection.kt",
            """
            import androidx.compose.runtime.Composable

            @Composable
            fun Text(text: String) {}

            @Composable
            fun Screen() {
                Te<caret>xt("demo")
            }
            """.trimIndent(),
        )

        val actions = myFixture.filterAvailableIntentions("(KMP Buddy) Extract selected Compose region as SPI + default implementation")
        assertEmpty(actions)
    }

    fun testSelectedRegionSpiIntentionExtractsWholeStatementFromPartialSelection() {
        myFixture.configureByText(
            "SelectedRegionPartialSelection.kt",
            """
            import androidx.compose.runtime.Composable

            interface ColumnScope

            object Modifier

            class State(
                val projectName: String,
            ) {
                fun updateProjectName(value: String) {}
            }

            @Composable
            fun CupertinoText(text: String) {}

            @Composable
            fun CupertinoBorderedTextField(
                value: String,
                onValueChange: (String) -> Unit,
                modifier: Modifier,
                singleLine: Boolean,
                placeholder: @Composable () -> Unit,
            ) {}

            @Composable
            fun WorkbenchCard(
                content: @Composable ColumnScope.() -> Unit,
            ) {}

            @Composable
            fun FormsPage(state: State) {
                WorkbenchCard {
                    CupertinoBorderedTextField(
                        value = state.projectName,
                        onValueChange = state::updateProjectName,
                        modifier = Modifier,
                        singleLine = true,
                        placeholder = {
                            CupertinoText("输入项目名称")
                        },
                    )
                }
            }
            """.trimIndent(),
        )

        val originalText = myFixture.file.text
        val selectionStart = originalText.indexOf("value = state.projectName")
        val selectionEnd = originalText.indexOf("CupertinoText(\"输入项目名称\")") + "CupertinoText(\"输入项目名称\")".length
        myFixture.editor.selectionModel.setSelection(selectionStart, selectionEnd)
        myFixture.editor.caretModel.moveToOffset(selectionStart + 1)

        invokeIntention("(KMP Buddy) Extract selected Compose region as SPI + default implementation")

        val text = myFixture.file.text
        assertTrue(text.contains("interface FormsPageCupertinoBorderedTextFieldSpi"))
        assertTrue(text.contains("org.koin.compose.koinInject<FormsPageCupertinoBorderedTextFieldSpi>().Render(state = state)"))
        assertTrue(text.contains("onValueChange = state::updateProjectName"))
    }

    fun testSelectedRegionSpiIntentionKeepsInnerComposeCallWhenSelectionIncludesIndentAndTrailingWhitespace() {
        myFixture.configureByText(
            "SelectedRegionWhitespaceSelection.kt",
            """
            import androidx.compose.runtime.Composable

            interface ColumnScope

            object Modifier

            class State(
                val projectName: String,
            ) {
                fun updateProjectName(value: String) {}
                fun showAlert() {}
            }

            @Composable
            fun CupertinoText(text: String) {}

            @Composable
            fun CupertinoBorderedTextField(
                value: String,
                onValueChange: (String) -> Unit,
                modifier: Modifier,
                singleLine: Boolean,
                placeholder: @Composable () -> Unit,
            ) {}

            @Composable
            fun Row(content: @Composable () -> Unit = {}) {}

            @Composable
            fun WorkbenchCard(
                content: @Composable ColumnScope.() -> Unit,
            ) {}

            @Composable
            fun FormsPage(state: State) {
                WorkbenchCard {
                    CupertinoBorderedTextField(
                        value = state.projectName,
                        onValueChange = state::updateProjectName,
                        modifier = Modifier,
                        singleLine = true,
                        placeholder = {
                            CupertinoText("输入项目名称")
                        },
                    )
                    Row { CupertinoText("next") }
                }
            }
            """.trimIndent(),
        )

        val originalText = myFixture.file.text
        val statementStart = originalText.lastIndexOf("CupertinoBorderedTextField(")
        val selectionStart = originalText.lastIndexOf("\n", statementStart) + 1
        val rowStart = originalText.indexOf("Row { CupertinoText(\"next\") }", statementStart)
        val selectionEnd = rowStart
        myFixture.editor.selectionModel.setSelection(selectionStart, selectionEnd)
        myFixture.editor.caretModel.moveToOffset(statementStart + 1)

        invokeIntention("(KMP Buddy) Extract selected Compose region as SPI + default implementation")

        val text = myFixture.file.text
        assertTrue(text.contains("interface FormsPageCupertinoBorderedTextFieldSpi"))
        assertFalse(text.contains("interface FormsPageWorkbenchCardSpi"))
        assertTrue(text.contains("org.koin.compose.koinInject<FormsPageCupertinoBorderedTextFieldSpi>().Render(state = state)"))
        assertTrue(text.contains("Row { CupertinoText(\"next\") }"))
    }

    fun testSelectedRegionSpiIntentionOmitsUnusedReceiverParameterFromAnnotatedFunctionType() {
        myFixture.configureByText(
            "SelectedRegionAnnotatedReceiver.kt",
            """
            import androidx.compose.runtime.Composable

            interface RowScope

            class CupertinoDemoState {
                var adaptiveCheckboxEnabled: Boolean = false
                fun updateAdaptiveCheckboxEnabled(value: Boolean) {}
            }

            @Composable
            fun AdaptiveCheckbox(
                checked: Boolean,
                onCheckedChange: (Boolean) -> Unit,
            ) {}

            @Composable
            fun HostRow(
                content: @androidx.compose.runtime.Composable() (RowScope).() -> kotlin.Unit,
            ) {}

            @Composable
            fun AdaptivePage(state: CupertinoDemoState) {
                HostRow {
                    AdaptiveCheckbox(
                        checked = state.adaptiveCheckboxEnabled,
                        onCheckedChange = state::updateAdaptiveCheckboxEnabled,
                    )
                }
            }
            """.trimIndent(),
        )

        val originalText = myFixture.file.text
        val selectionStart = originalText.lastIndexOf("AdaptiveCheckbox(")
        val selectionEnd = originalText.indexOf(")", selectionStart) + 1
        myFixture.editor.selectionModel.setSelection(selectionStart, selectionEnd)
        myFixture.editor.caretModel.moveToOffset(selectionStart + 1)

        invokeIntention("(KMP Buddy) Extract selected Compose region as SPI + default implementation")

        val text = myFixture.file.text
        assertTrue(text.contains("interface AdaptivePageAdaptiveCheckboxSpi"))
        assertFalse(text.contains("scope: RowScope"))
        assertFalse(text.contains("scope: ("))
        assertTrue(text.contains("state: CupertinoDemoState"))
        assertTrue(text.contains("org.koin.compose.koinInject<AdaptivePageAdaptiveCheckboxSpi>().Render(state = state)"))
        assertFalse(text.contains("with(scope)"))
        assertTrue(text.contains("override fun Render("))
    }

    fun testSelectedRegionSpiIntentionOmitsUnusedReceiverParameterFromParenthesizedFunctionType() {
        myFixture.configureByText(
            "SelectedRegionParenthesizedReceiver.kt",
            """
            import androidx.compose.runtime.Composable

            interface RowScope

            class CupertinoDemoState {
                fun showAlert() {}
            }

            @Composable
            fun CupertinoIcon(
                imageVector: String,
                contentDescription: String?,
            ) {}

            @Composable
            fun AdaptiveIconButton(
                onClick: () -> Unit,
                content: @Composable () -> Unit,
            ) {}

            @Composable
            fun HostRow(
                content: (@androidx.compose.runtime.Composable RowScope.() -> kotlin.Unit),
            ) {}

            object AdaptiveIcons {
                object Outlined {
                    const val Search: String = "search"
                }
            }

            @Composable
            fun AdaptivePage(state: CupertinoDemoState) {
                HostRow {
                    AdaptiveIconButton(onClick = state::showAlert) {
                        CupertinoIcon(
                            imageVector = AdaptiveIcons.Outlined.Search,
                            contentDescription = "Search",
                        )
                    }
                }
            }
            """.trimIndent(),
        )

        val originalText = myFixture.file.text
        val selectionStart = originalText.lastIndexOf("AdaptiveIconButton(")
        val selectionEnd = originalText.indexOf("contentDescription = \"Search\"", selectionStart) + "contentDescription = \"Search\"".length
        myFixture.editor.selectionModel.setSelection(selectionStart, selectionEnd)
        myFixture.editor.caretModel.moveToOffset(selectionStart + 1)

        invokeIntention("(KMP Buddy) Extract selected Compose region as SPI + default implementation")

        val text = myFixture.file.text
        assertTrue(text.contains("interface AdaptivePageAdaptiveIconButtonSpi"))
        assertFalse(text.contains("scope: RowScope"))
        assertFalse(text.contains("scope: ("))
        assertTrue(text.contains("state: CupertinoDemoState"))
        assertTrue(text.contains("org.koin.compose.koinInject<AdaptivePageAdaptiveIconButtonSpi>().Render(state = state)"))
        assertTrue(text.contains("AdaptiveIconButton(onClick = state::showAlert)"))
    }

    fun testSelectedRegionSpiIntentionKeepsReceiverParameterWhenImplicitReceiverIsUsed() {
        myFixture.configureByText(
            "SelectedRegionNeedsReceiver.kt",
            """
            import androidx.compose.runtime.Composable

            interface ColumnScope {
                fun Modifier.weight(value: Float): Modifier
            }

            object Modifier

            @Composable
            fun WeightedBox(
                modifier: Modifier,
            ) {}

            @Composable
            fun WorkbenchCard(
                content: @Composable ColumnScope.() -> Unit,
            ) {}

            @Composable
            fun Screen() {
                WorkbenchCard {
                    WeightedBox(
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            """.trimIndent(),
        )

        val originalText = myFixture.file.text
        val selectionStart = originalText.lastIndexOf("WeightedBox(")
        val selectionEnd = originalText.indexOf(")", selectionStart) + 1
        myFixture.editor.selectionModel.setSelection(selectionStart, selectionEnd)
        myFixture.editor.caretModel.moveToOffset(selectionStart + 1)

        invokeIntention("(KMP Buddy) Extract selected Compose region as SPI + default implementation")

        val text = myFixture.file.text
        assertTrue(text.contains("interface ScreenWeightedBoxSpi"))
        assertTrue(text.contains("scope: ColumnScope"))
        assertTrue(text.contains("org.koin.compose.koinInject<ScreenWeightedBoxSpi>().Render(this)"))
        assertTrue(text.contains("with(scope)"))
        assertTrue(text.contains("modifier = Modifier.weight(1f)"))
    }

    fun testDeleteComposeCallAndOrphansRemovesSameFilePrivateDependencyGraph() {
        myFixture.configureByText(
            "ConfigurationCenterScreen.kt",
            """
            import androidx.compose.runtime.Composable

            class ConfigurationCenterState(
                val alias: String,
            )

            @Composable
            fun ConfigurationCenterScreen(state: ConfigurationCenterState) {
                Row {
                    ConfigurationSpecRail(state)
                    ConfigurationDetailPanel(state)
                    ConfigurationAbi<caret>Panel(state)
                }
            }

            @Composable
            private fun ConfigurationSpecRail(state: ConfigurationCenterState) {
                SharedBadge()
                Text(state.alias)
            }

            @Composable
            private fun ConfigurationDetailPanel(state: ConfigurationCenterState) {
                SharedBadge()
                Text(state.alias)
            }

            @Composable
            private fun ConfigurationAbiPanel(state: ConfigurationCenterState) {
                AbiRuleRow("1")
                AliasRow(state.alias)
                OrphanNested()
                SharedBadge()
            }

            @Composable
            private fun AbiRuleRow(index: String) {
                Text(index)
            }

            @Composable
            private fun AliasRow(alias: String) {
                Text(alias)
            }

            @Composable
            private fun OrphanNested() {
                DeepOrphan()
            }

            @Composable
            private fun DeepOrphan() {
                Text("deep")
            }

            @Composable
            private fun SharedBadge() {
                Text("shared")
            }

            @Composable
            private fun Row(content: @Composable () -> Unit) {
                content()
            }

            @Composable
            private fun Text(value: String) {
                value.hashCode()
            }
            """.trimIndent(),
        )

        invokeIntention("(KMP Buddy) Delete Compose call and orphan local components")

        val text = myFixture.file.text
        assertFalse(text.contains("ConfigurationAbiPanel(state)"))
        assertFalse(text.contains("private fun ConfigurationAbiPanel"))
        assertFalse(text.contains("private fun AbiRuleRow"))
        assertFalse(text.contains("private fun AliasRow"))
        assertFalse(text.contains("private fun OrphanNested"))
        assertFalse(text.contains("private fun DeepOrphan"))
        assertTrue(text.contains("private fun ConfigurationSpecRail"))
        assertTrue(text.contains("private fun ConfigurationDetailPanel"))
        assertTrue(text.contains("private fun SharedBadge"))
        assertTrue(text.contains("private fun Text"))
    }

    fun testDeleteComposeCallAndOrphansRemovesSelectedSiblingCallsTogether() {
        myFixture.configureByText(
            "NoteScreen.kt",
            """
            import androidx.compose.runtime.Composable

            class Stats(
                val totalNotes: Int,
                val blinkoNotes: Int,
                val accountRows: Int,
            )

            class Snapshot(
                val stats: Stats,
            )

            class NoteScreenViewModel(
                val snapshot: Snapshot,
            )

            object Icons {
                object Outlined {
                    const val Description: String = "description"
                    const val AutoAwesome: String = "auto"
                    const val Key: String = "key"
                }
            }

            @Composable
            fun NoteScreen(state: NoteScreenViewModel) {
                NoteHeader(state)
            }

            @Composable
            private fun NoteHeader(state: NoteScreenViewModel) {
                Row {
                    Text("Note 工作台")
                    HeaderMetric("笔记", state.snapshot.stats.totalNotes.toString(), Icons.Outlined.Description)
                    HeaderMetric("Blinko", state.snapshot.stats.blinkoNotes.toString(), Icons.Outlined.AutoAwesome)
                    HeaderMetric("账号", state.snapshot.stats.accountRows.toString(), Icons.Outlined.Key)
                    Text("tail")
                }
            }

            @Composable
            private fun HeaderMetric(
                label: String,
                value: String,
                icon: String,
            ) {
                Text("${'$'}label/${'$'}value/${'$'}icon")
            }

            @Composable
            private fun Row(content: @Composable () -> Unit) {
                content()
            }

            @Composable
            private fun Text(value: String) {
                value.hashCode()
            }
            """.trimIndent(),
        )

        val originalText = myFixture.file.text
        val selectionStart = originalText.indexOf("HeaderMetric(\"笔记\"")
        val selectionEnd = originalText.indexOf("Text(\"tail\")", selectionStart)
        myFixture.editor.selectionModel.setSelection(selectionStart, selectionEnd)
        myFixture.editor.caretModel.moveToOffset(selectionStart + 1)

        invokeIntention("(KMP Buddy) Delete Compose call and orphan local components")

        val text = myFixture.file.text
        assertFalse(text.contains("HeaderMetric(\"笔记\""))
        assertFalse(text.contains("HeaderMetric(\"Blinko\""))
        assertFalse(text.contains("HeaderMetric(\"账号\""))
        assertFalse(text.contains("private fun HeaderMetric"))
        assertTrue(text.contains("Text(\"Note 工作台\")"))
        assertTrue(text.contains("Text(\"tail\")"))
        assertTrue(text.contains("private fun Row"))
        assertTrue(text.contains("private fun Text"))
    }

    fun testDeleteComposeCallAndOrphansIsHiddenForPublicComponentCall() {
        myFixture.configureByText(
            "PublicPanel.kt",
            """
            import androidx.compose.runtime.Composable

            @Composable
            fun Host() {
                Public<caret>Panel()
            }

            @Composable
            fun PublicPanel() {
                Text("public")
            }

            @Composable
            private fun Text(value: String) {
                value.hashCode()
            }
            """.trimIndent(),
        )

        val actions = myFixture.filterAvailableIntentions("(KMP Buddy) Delete Compose call and orphan local components")
        assertEmpty(actions)
    }

    private fun invokeIntention(actionText: String) {
        val action = myFixture.findSingleIntention(actionText)
        action.invoke(project, myFixture.editor, myFixture.file)
    }

    private fun findFunction(name: String): KtNamedFunction {
        return (myFixture.file as KtFile)
            .declarations
            .filterIsInstance<KtNamedFunction>()
            .first { function -> function.name == name }
    }

    private fun effectKeysIntentionText(): String {
        return ComposeBuddyBundle.message("intention.normalize.effect.keys")
    }
}
