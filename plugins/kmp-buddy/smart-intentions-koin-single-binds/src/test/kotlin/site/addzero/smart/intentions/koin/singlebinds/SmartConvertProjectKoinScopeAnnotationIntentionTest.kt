package site.addzero.smart.intentions.koin.singlebinds

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SmartConvertProjectKoinScopeAnnotationIntentionTest : BasePlatformTestCase() {
    fun testConvertsProjectSingleToSingleton() {
        val currentFile = myFixture.configureByText(
            "Current.kt",
            """
            package demo

            import org.koin.core.annotation.Single

            @Sin<caret>gle(createdAtStart = true)
            class MainWindow
            """.trimIndent(),
        )

        val otherFile = myFixture.addFileToProject(
            "Other.kt",
            """
            package demo

            import org.koin.core.annotation.Single

            @Single
            class SearchPanel
            """.trimIndent(),
        )

        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        assertNotNull(element)

        val intention = SmartConvertProjectSingleToSingletonIntention()
        assertTrue(intention.isAvailable(project, myFixture.editor, element!!))
        intention.invoke(project, myFixture.editor, element)

        assertEquals(
            """
            package demo

            import org.koin.core.annotation.Singleton

            @Singleton(createdAtStart = true)
            class MainWindow
            """.trimIndent(),
            currentFile.text,
        )

        assertEquals(
            """
            package demo

            import org.koin.core.annotation.Singleton

            @Singleton
            class SearchPanel
            """.trimIndent(),
            otherFile.text,
        )
    }

    fun testConvertsProjectSingletonToSingle() {
        val currentFile = myFixture.configureByText(
            "Current.kt",
            """
            package demo

            import org.koin.core.annotation.Singleton

            @Single<caret>ton(createdAtStart = true)
            class MainWindow
            """.trimIndent(),
        )

        val otherFile = myFixture.addFileToProject(
            "Other.kt",
            """
            package demo

            @org.koin.core.annotation.Singleton
            class SearchPanel
            """.trimIndent(),
        )

        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        assertNotNull(element)

        val intention = SmartConvertProjectSingletonToSingleIntention()
        assertTrue(intention.isAvailable(project, myFixture.editor, element!!))
        intention.invoke(project, myFixture.editor, element)

        assertEquals(
            """
            package demo

            import org.koin.core.annotation.Single

            @Single(createdAtStart = true)
            class MainWindow
            """.trimIndent(),
            currentFile.text,
        )

        assertEquals(
            """
            package demo

            @org.koin.core.annotation.Single
            class SearchPanel
            """.trimIndent(),
            otherFile.text,
        )
    }

    fun testDoesNotOfferSingleToSingletonForCustomAnnotation() {
        myFixture.configureByText(
            "Current.kt",
            """
            package demo

            annotation class Single

            @Sin<caret>gle
            class MainWindow
            """.trimIndent(),
        )

        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        assertNotNull(element)

        val intention = SmartConvertProjectSingleToSingletonIntention()
        assertFalse(intention.isAvailable(project, myFixture.editor, element!!))
    }

    fun testDoesNotOfferSingletonToSingleForCustomAnnotation() {
        myFixture.configureByText(
            "Current.kt",
            """
            package demo

            annotation class Singleton

            @Single<caret>ton
            class MainWindow
            """.trimIndent(),
        )

        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        assertNotNull(element)

        val intention = SmartConvertProjectSingletonToSingleIntention()
        assertFalse(intention.isAvailable(project, myFixture.editor, element!!))
    }
}
