package site.addzero.smart.intentions.kotlin.redundantexplicittype

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SmartRemoveProjectRedundantExplicitTypeIntentionTest : BasePlatformTestCase() {
    fun testRemovesRedundantExplicitTypesAcrossProject() {
        val currentFile = myFixture.configureByText(
            "Current.kt",
            """
            package demo

            val gree<caret>ting: String = "hi"
            val untouched = 42
            """.trimIndent(),
        )

        val otherFile = myFixture.addFileToProject(
            "Other.kt",
            """
            package demo

            class User(
                val name: String,
            )

            val answer: Int = 42
            val userName: String = User("Ada").name
            """.trimIndent(),
        )

        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        assertNotNull(element)

        val intention = SmartRemoveProjectRedundantExplicitTypeIntention()
        assertTrue(intention.isAvailable(project, myFixture.editor, element!!))
        intention.invoke(project, myFixture.editor, element)

        assertEquals(
            """
            package demo

            val greeting = "hi"
            val untouched = 42
            """.trimIndent(),
            currentFile.text,
        )

        assertEquals(
            """
            package demo

            class User(
                val name: String,
            )

            val answer = 42
            val userName = User("Ada").name
            """.trimIndent(),
            otherFile.text,
        )
    }

    fun testDoesNotOfferWhenPropertyIsNotApplicable() {
        myFixture.configureByText(
            "Current.kt",
            """
            package demo

            fun buildName(): String = "Ada"

            val nam<caret>e = buildName()
            """.trimIndent(),
        )

        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        assertNotNull(element)

        val intention = SmartRemoveProjectRedundantExplicitTypeIntention()
        assertFalse(intention.isAvailable(project, myFixture.editor, element!!))
    }
}
