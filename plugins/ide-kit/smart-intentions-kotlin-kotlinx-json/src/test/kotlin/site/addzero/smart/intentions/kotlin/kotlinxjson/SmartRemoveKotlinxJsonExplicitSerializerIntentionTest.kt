package site.addzero.smart.intentions.kotlin.kotlinxjson

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SmartRemoveKotlinxJsonExplicitSerializerIntentionTest : BasePlatformTestCase() {
    fun testRemovesEncodeSerializerArgument() {
        myFixture.configureByText(
            "Codec.kt",
            """
            package demo

            fun encode(json: Json, element: VibeElementSchema): String {
                return json.encodeTo<caret>String(VibeElementSchema.serializer(), element)
            }
            """.trimIndent(),
        )

        invokeIntention()

        myFixture.checkResult(
            """
            package demo

            fun encode(json: Json, element: VibeElementSchema): String {
                return json.encodeToString(element)
            }
            """.trimIndent(),
        )
    }

    fun testRemovesDecodeSerializerArgument() {
        myFixture.configureByText(
            "Codec.kt",
            """
            package demo

            fun decode(json: Json, source: String): VibeElementSchema {
                return json.decodeFrom<caret>String(VibeElementSchema.serializer(), source)
            }
            """.trimIndent(),
        )

        invokeIntention()

        myFixture.checkResult(
            """
            package demo

            fun decode(json: Json, source: String): VibeElementSchema {
                return json.decodeFromString(source)
            }
            """.trimIndent(),
        )
    }

    fun testRemovesQualifiedSerializerArgument() {
        myFixture.configureByText(
            "Codec.kt",
            """
            package demo

            fun decode(json: Json, source: String): schema.VibeElementSchema {
                return json.decodeFrom<caret>String(schema.VibeElementSchema.serializer(), source)
            }
            """.trimIndent(),
        )

        invokeIntention()

        myFixture.checkResult(
            """
            package demo

            fun decode(json: Json, source: String): schema.VibeElementSchema {
                return json.decodeFromString(source)
            }
            """.trimIndent(),
        )
    }

    fun testDoesNotOfferForNonSerializerArgument() {
        myFixture.configureByText(
            "Codec.kt",
            """
            package demo

            fun decode(json: Json, source: String, serializer: KSerializer<VibeElementSchema>): VibeElementSchema {
                return json.decodeFrom<caret>String(serializer, source)
            }
            """.trimIndent(),
        )

        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        assertNotNull(element)

        val intention = SmartRemoveKotlinxJsonExplicitSerializerIntention()
        assertFalse(intention.isAvailable(project, myFixture.editor, element!!))
    }

    private fun invokeIntention() {
        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        assertNotNull(element)

        val intention = SmartRemoveKotlinxJsonExplicitSerializerIntention()
        assertTrue(intention.isAvailable(project, myFixture.editor, element!!))
        intention.invoke(project, myFixture.editor, element)
    }
}
