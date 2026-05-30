package site.addzero.smart.intentions.kotlin.shortenqualifiedname

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SmartShortenProjectQualifiedNamesIntentionTest : BasePlatformTestCase() {
    fun testShortensQualifiedNamesAcrossProject() {
        myFixture.addFileToProject(
            "site/addzero/workbench/immersivedesktop/Api.kt",
            """
            package site.addzero.workbench.immersivedesktop

            class ImmersiveDesktopWindowConfig

            fun immersiveDesktopWindowDecoration(config: ImmersiveDesktopWindowConfig): String {
                return config.toString()
            }
            """.trimIndent(),
        )

        val currentFile = myFixture.configureByText(
            "Current.kt",
            """
            package demo

            fun call(
                config: site.addzero.workbench.immersivedesktop.ImmersiveDesktopWindowConfig,
            ): String {
                return site.addzero.workbench.immersivedesktop.immersiveDesktopWindowDecoration(config)
            }
            """.trimIndent(),
        )

        val otherFile = myFixture.addFileToProject(
            "Other.kt",
            """
            package sample

            fun otherCall(
                config: site.addzero.workbench.immersivedesktop.ImmersiveDesktopWindowConfig,
            ): String {
                return site.addzero.workbench.immersivedesktop.immersiveDesktopWindowDecoration(config)
            }
            """.trimIndent(),
        )

        val element = myFixture.file.findElementAt(myFixture.caretOffset)
            ?: myFixture.file.firstChild

        val intention = SmartShortenProjectQualifiedNamesIntention()
        assertTrue(intention.isAvailable(project, myFixture.editor, element))
        intention.invoke(project, myFixture.editor, element)

        assertEquals(
            """
            package demo

            import site.addzero.workbench.immersivedesktop.ImmersiveDesktopWindowConfig
            import site.addzero.workbench.immersivedesktop.immersiveDesktopWindowDecoration

            fun call(
                config: ImmersiveDesktopWindowConfig,
            ): String {
                return immersiveDesktopWindowDecoration(config)
            }
            """.trimIndent(),
            currentFile.text,
        )

        assertEquals(
            """
            package sample

            import site.addzero.workbench.immersivedesktop.ImmersiveDesktopWindowConfig
            import site.addzero.workbench.immersivedesktop.immersiveDesktopWindowDecoration

            fun otherCall(
                config: ImmersiveDesktopWindowConfig,
            ): String {
                return immersiveDesktopWindowDecoration(config)
            }
            """.trimIndent(),
            otherFile.text,
        )
    }

    fun testDoesNotOfferOutsideKotlinFile() {
        myFixture.configureByText("notes.txt", "plain<caret> text")

        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        assertNotNull(element)

        val intention = SmartShortenProjectQualifiedNamesIntention()
        assertFalse(intention.isAvailable(project, myFixture.editor, element!!))
    }
}
