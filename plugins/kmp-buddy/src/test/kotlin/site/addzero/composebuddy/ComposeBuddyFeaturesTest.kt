package site.addzero.composebuddy

import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ComposeBuddyFeaturesTest : BasePlatformTestCase() {
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

        invokeIntention("(KMP Buddy) Normalize remember/effect keys")

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

    fun testPreviewSampleIntentionGeneratesPreviewFunctions() {
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

        invokeIntention("(KMP Buddy) Generate preview samples")

        val text = myFixture.file.text
        assertTrue(text.contains("@androidx.compose.ui.tooling.preview.Preview"))
        assertTrue(text.contains("private fun CardDefaultPreview()"))
        assertTrue(text.contains("private fun CardLoadingPreview()"))
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

    private fun configureProjectFile(path: String, textWithCaret: String) {
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
        assertTrue(text.contains("scope: ColumnScope"))
        assertTrue(text.contains("state: State"))
        assertTrue(text.contains("org.koin.compose.koinInject<FormsPageCupertinoBorderedTextFieldSpi>().Render(this, state = state)"))
        assertTrue(text.contains("with(scope)"))
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

    private fun invokeIntention(actionText: String) {
        val action = myFixture.findSingleIntention(actionText)
        action.invoke(project, myFixture.editor, myFixture.file)
    }
}
