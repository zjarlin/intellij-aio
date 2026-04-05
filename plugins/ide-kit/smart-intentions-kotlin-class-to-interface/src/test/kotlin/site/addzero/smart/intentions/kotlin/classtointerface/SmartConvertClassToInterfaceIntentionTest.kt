package site.addzero.smart.intentions.kotlin.classtointerface

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SmartConvertClassToInterfaceIntentionTest : BasePlatformTestCase() {
    fun testConvertsDataClassToInterface() {
        myFixture.configureByText(
            "S3Config.kt",
            """
            package demo

            data class S3Con<caret>fig(
                val endpoint: String,
                val region: String,
                val bucket: String,
                val accessKey: String,
                val secretKey: String,
            )
            """.trimIndent(),
        )

        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        assertNotNull(element)

        val intention = SmartConvertClassToInterfaceIntention()
        assertTrue(intention.isAvailable(project, myFixture.editor, element!!))
        intention.invoke(project, myFixture.editor, element)

        myFixture.checkResult(
            """
            package demo

            interface S3Config {
                val endpoint: String
                val region: String
                val bucket: String
                val accessKey: String
                val secretKey: String
            }
            """.trimIndent(),
        )
    }

    fun testConvertsPlainClassToInterfaceAndKeepsFunctions() {
        myFixture.configureByText(
            "Greeter.kt",
            """
            package demo

            class Gree<caret>ter(
                val name: String,
            ) {
                fun greet(): String {
                    return "hi, ${'$'}name"
                }
            }
            """.trimIndent(),
        )

        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        assertNotNull(element)

        val intention = SmartConvertClassToInterfaceIntention()
        assertTrue(intention.isAvailable(project, myFixture.editor, element!!))
        intention.invoke(project, myFixture.editor, element)

        myFixture.checkResult(
            """
            package demo

            interface Greeter {
                val name: String
                fun greet(): String {
                    return "hi, ${'$'}name"
                }
            }
            """.trimIndent(),
        )
    }

    fun testDoesNotOfferForInitBlock() {
        myFixture.configureByText(
            "Bad.kt",
            """
            package demo

            class Use<caret>r(
                val name: String,
            ) {
                init {
                    println(name)
                }
            }
            """.trimIndent(),
        )

        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        assertNotNull(element)

        val intention = SmartConvertClassToInterfaceIntention()
        assertFalse(intention.isAvailable(project, myFixture.editor, element!!))
    }

    fun testDoesNotOfferForSecondaryConstructor() {
        myFixture.configureByText(
            "Bad.kt",
            """
            package demo

            class Use<caret>r(
                val name: String,
            ) {
                constructor() : this("guest")
            }
            """.trimIndent(),
        )

        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        assertNotNull(element)

        val intention = SmartConvertClassToInterfaceIntention()
        assertFalse(intention.isAvailable(project, myFixture.editor, element!!))
    }

    fun testDoesNotOfferForPlainConstructorParameter() {
        myFixture.configureByText(
            "Bad.kt",
            """
            package demo

            class Use<caret>r(
                name: String,
            )
            """.trimIndent(),
        )

        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        assertNotNull(element)

        val intention = SmartConvertClassToInterfaceIntention()
        assertFalse(intention.isAvailable(project, myFixture.editor, element!!))
    }

    fun testDoesNotOfferForSuperclassConstructorCall() {
        myFixture.configureByText(
            "Bad.kt",
            """
            package demo

            open class Base

            class Use<caret>r(
                val name: String,
            ) : Base()
            """.trimIndent(),
        )

        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        assertNotNull(element)

        val intention = SmartConvertClassToInterfaceIntention()
        assertFalse(intention.isAvailable(project, myFixture.editor, element!!))
    }
}
