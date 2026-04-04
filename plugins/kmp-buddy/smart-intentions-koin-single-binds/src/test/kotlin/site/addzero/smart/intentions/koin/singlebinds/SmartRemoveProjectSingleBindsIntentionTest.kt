package site.addzero.smart.intentions.koin.singlebinds

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SmartRemoveProjectSingleBindsIntentionTest : BasePlatformTestCase() {
    fun testRemovesSingleBindsAcrossProject() {
        val currentFile = myFixture.configureByText(
            "Current.kt",
            """
            package demo

            import org.koin.core.annotation.Single

            interface MainWindowSpi
            interface ToolbarSpi

            @Sin<caret>gle(
                binds = [MainWindowSpi::class],
            )
            class MainWindow : MainWindowSpi
            """.trimIndent(),
        )

        val otherFile = myFixture.addFileToProject(
            "Other.kt",
            """
            package demo

            import org.koin.core.annotation.Single

            interface SearchSpi
            interface CommandSpi

            @Single(createdAtStart = true, binds = [SearchSpi::class])
            class SearchPanel : SearchSpi

            @Single(binds = [CommandSpi::class], createdAtStart = true)
            class CommandPalette : CommandSpi
            """.trimIndent(),
        )

        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        assertNotNull(element)

        val intention = SmartRemoveProjectSingleBindsIntention()
        assertTrue(intention.isAvailable(project, myFixture.editor, element!!))
        intention.invoke(project, myFixture.editor, element)

        assertEquals(
            """
            package demo

            import org.koin.core.annotation.Single

            interface MainWindowSpi
            interface ToolbarSpi

            @Single
            class MainWindow : MainWindowSpi
            """.trimIndent(),
            currentFile.text,
        )

        assertEquals(
            """
            package demo

            import org.koin.core.annotation.Single

            interface SearchSpi
            interface CommandSpi

            @Single(createdAtStart = true)
            class SearchPanel : SearchSpi

            @Single(createdAtStart = true)
            class CommandPalette : CommandSpi
            """.trimIndent(),
            otherFile.text,
        )
    }

    fun testDoesNotOfferWhenSingleHasNoBinds() {
        myFixture.configureByText(
            "Current.kt",
            """
            package demo

            import org.koin.core.annotation.Single

            @Sin<caret>gle
            class MainWindow
            """.trimIndent(),
        )

        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        assertNotNull(element)

        val intention = SmartRemoveProjectSingleBindsIntention()
        assertFalse(intention.isAvailable(project, myFixture.editor, element!!))
    }

    fun testDoesNotOfferForNonKoinSingleAnnotation() {
        myFixture.configureByText(
            "Current.kt",
            """
            package demo

            annotation class Single(val binds: Array<String>)

            @Sin<caret>gle(binds = ["MainWindowSpi"])
            class MainWindow
            """.trimIndent(),
        )

        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        assertNotNull(element)

        val intention = SmartRemoveProjectSingleBindsIntention()
        assertFalse(intention.isAvailable(project, myFixture.editor, element!!))
    }
}
