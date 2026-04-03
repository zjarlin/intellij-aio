package site.addzero.composebuddy

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import site.addzero.composebuddy.analysis.ComposeFunctionSupport
import site.addzero.composebuddy.refactor.ComposeRefactorEngine
import site.addzero.composebuddy.refactor.ComposeRefactorRequest

class ComposeBuddyIntentionsTest : BasePlatformTestCase() {
    fun testExpandTextWrapperSignatureAddsCommonComposeParameters() {
        myFixture.configureByText(
            "Title.kt",
            """
            import androidx.compose.material.Text
            import androidx.compose.runtime.Composable
            
            @Composable
            fun Ti<caret>tle(text: String, highlight: Boolean) {
                Text(text = text, color = if (highlight) Color.Red else Color.Black)
            }
            """.trimIndent(),
        )

        val action = myFixture.findSingleIntention("(Compose Buddy) Expand Compose wrapper signature")
        action.invoke(project, myFixture.editor, myFixture.file)

        val text = myFixture.file.text
        assertTrue(text.contains("modifier: Modifier = Modifier"))
        assertTrue(text.contains("style: TextStyle = LocalTextStyle.current"))
        assertTrue(text.contains("modifier = modifier"))
        assertTrue(text.contains("style = style"))
    }

    fun testGenerateWrapperPropsMigratesCallSites() {
        myFixture.configureByText(
            "Title.kt",
            """
            import androidx.compose.material.Text
            import androidx.compose.runtime.Composable
            
            @Composable
            fun <caret>Title(text: String, highlight: Boolean) {
                Text(text = text, color = if (highlight) Color.Red else Color.Black)
            }
            
            @Composable
            fun Sample() {
                Title(text = "Hello", highlight = true)
            }
            """.trimIndent(),
        )

        val action = myFixture.findSingleIntention("(Compose Buddy) Generate Compose wrapper props")
        action.invoke(project, myFixture.editor, myFixture.file)

        val text = myFixture.file.text
        assertTrue(text.contains("data class TitleProps("))
        assertTrue(text.contains("fun Title(props: TitleProps, highlight: Boolean)"))
        assertTrue(text.contains("text = props.text"))
        assertTrue(text.contains("Title(props = TitleProps(text = \"Hello\"), highlight = true)"))
    }

    fun testNormalizeComposeSignatureCreatesPropsEventsStateAndUpdatesCalls() {
        myFixture.configureByText(
            "Search.kt",
            """
            import androidx.compose.runtime.Composable
            
            @Composable
            fun SearchS<caret>creen(
                query: String,
                onQueryChange: (String) -> Unit,
                loading: Boolean,
                onRetry: () -> Unit,
                modifier: Modifier = Modifier,
            ) {
                SearchBar(query = query, onQueryChange = onQueryChange, modifier = modifier)
                if (loading) {
                    RetryButton(onRetry)
                }
            }
            
            @Composable
            fun Host(query: String, onQueryChange: (String) -> Unit, loading: Boolean) {
                SearchScreen(
                    query = query,
                    onQueryChange = onQueryChange,
                    loading = loading,
                    onRetry = { println("retry") },
                    modifier = Modifier,
                )
            }
            """.trimIndent(),
        )

        val action = myFixture.findSingleIntention("(Compose Buddy) Normalize Compose signature")
        action.invoke(project, myFixture.editor, myFixture.file)

        val text = myFixture.file.text
        assertTrue(text.contains("data class SearchScreenProps("))
        assertTrue(text.contains("data class SearchScreenEvents("))
        assertTrue(text.contains("data class SearchScreenState("))
        assertTrue(text.contains("searchScreenProps: SearchScreenProps"))
        assertTrue(text.contains("searchScreenState: SearchScreenState"))
        assertTrue(text.contains("searchScreenEvents: SearchScreenEvents"))
        assertTrue(text.contains("query = searchScreenState.query"))
        assertTrue(text.contains("modifier = searchScreenProps.modifier"))
        assertTrue(text.contains("searchScreenProps = SearchScreenProps(loading = loading, modifier = Modifier)"))
        assertTrue(text.contains("searchScreenState = SearchScreenState(query = query, onQueryChange = onQueryChange)"))
        assertTrue(text.contains("searchScreenEvents = SearchScreenEvents(onRetry = { println(\"retry\") })"))
    }

    fun testNormalizeComposeSignatureCanKeepCompatibilityFunction() {
        myFixture.configureByText(
            "Compat.kt",
            """
            import androidx.compose.runtime.Composable
            
            @Composable
            fun Lo<caret>ginScreen(
                username: String,
                onUsernameChange: (String) -> Unit,
                onSubmit: () -> Unit,
                enabled: Boolean,
            ) {
                Form(username = username, onUsernameChange = onUsernameChange, enabled = enabled)
                SubmitButton(onSubmit)
            }
            """.trimIndent(),
        )

        val function = myFixture.file.findElementAt(myFixture.editor.caretModel.offset)
            ?.getStrictParentOfType<KtNamedFunction>()
            ?: error("function not found")
        val analysis = ComposeFunctionSupport.analyzeSignature(function) ?: error("analysis not found")
        ComposeRefactorEngine(project).normalizeSignature(
            analysis = analysis,
            request = ComposeRefactorRequest(
                extractProps = true,
                extractEvents = true,
                extractState = true,
                propsTypeName = "LoginScreenProps",
                eventsTypeName = "LoginScreenEvents",
                stateTypeName = "LoginScreenState",
                keepCompatibilityFunction = true,
            ),
        )

        val text = myFixture.file.text
        assertTrue(text.contains("@Deprecated(\"Use the normalized compose-buddy signature\")"))
        assertTrue(text.contains("fun LoginScreen(username: String, onUsernameChange: (String) -> Unit, onSubmit: () -> Unit, enabled: Boolean)"))
    }

    fun testDoesNotOfferNormalizeForNonComposableFunction() {
        myFixture.configureByText(
            "Plain.kt",
            """
            fun pl<caret>ain(query: String, onQueryChange: (String) -> Unit, loading: Boolean, onRetry: () -> Unit) = Unit
            """.trimIndent(),
        )

        val actions = myFixture.filterAvailableIntentions("(Compose Buddy) Normalize Compose signature")
        assertEmpty(actions)
    }
}
