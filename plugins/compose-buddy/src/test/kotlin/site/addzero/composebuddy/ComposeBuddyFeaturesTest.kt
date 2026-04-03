package site.addzero.composebuddy

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

        invokeIntention("(Compose Buddy) Extract repeated Modifier chain")

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

        invokeIntention("(Compose Buddy) Extract minimal UiState")

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

        val actions = myFixture.filterAvailableIntentions("(Compose Buddy) Flatten used object params into signature")
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

        invokeIntention("(Compose Buddy) Normalize remember/effect keys")

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

        invokeIntention("(Compose Buddy) Generate preview samples")

        val text = myFixture.file.text
        assertTrue(text.contains("@androidx.compose.ui.tooling.preview.Preview"))
        assertTrue(text.contains("private fun CardDefaultPreview()"))
        assertTrue(text.contains("private fun CardLoadingPreview()"))
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

        invokeIntention("(Compose Buddy) Wrap selected layout container as higher-order container")

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

        val actions = myFixture.filterAvailableIntentions("(Compose Buddy) Wrap selected layout container as higher-order container")
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

        invokeIntention("(Compose Buddy) Inline used viewModel state and events into parameters")

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

        invokeIntention("(Compose Buddy) Inline used viewModel state and events into parameters")

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

        invokeIntention("(Compose Buddy) Inline used viewModel state and events into parameters")

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

        invokeIntention("(Compose Buddy) Fill call arguments from same-named parameters")

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

        invokeIntention("(Compose Buddy) Fill call arguments from same-named parameters")

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

        val actions = myFixture.filterAvailableIntentions("(Compose Buddy) Fill call arguments from same-named parameters")
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

        invokeIntention("(Compose Buddy) Extract current call argument to composable parameter")

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

        invokeIntention("(Compose Buddy) Extract placeholder call arguments to composable parameters")

        val text = myFixture.file.text
        assertTrue(text.contains("color: kotlin.String = TODO()") || text.contains("color: String = TODO()"))
        assertTrue(text.contains("lineHeight: kotlin.Int = TODO()") || text.contains("lineHeight: Int = TODO()"))
        assertTrue(text.contains("overflow: kotlin.String? = null") || text.contains("overflow: String? = null"))
        assertTrue(text.contains("color = color"))
        assertTrue(text.contains("lineHeight = lineHeight"))
        assertTrue(text.contains("overflow = overflow"))
    }

    private fun invokeIntention(actionText: String) {
        val action = myFixture.findSingleIntention(actionText)
        action.invoke(project, myFixture.editor, myFixture.file)
    }
}
