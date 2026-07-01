package site.addzero.smart.intentions.kotlin.expressiontoblock

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SmartConvertExpressionToBlockIntentionTest : BasePlatformTestCase() {
    fun testConvertsAllExpressionBodiesInCurrentFile() {
        myFixture.configureByText(
            "Test.kt",
            """
            package demo

            @GetMapping("/deviceTop")
            @Operation(summary = "获得IoT 告警设备排名")
            fun get<caret>AlarmDeviceTop(@RequestParam("type") type: Int): JSONObject =
                deviceAlarmService.getAlarmDeviceTop(type)

            fun greet(name: String): String = "hello, ${'$'}name"

            fun alreadyBlock(): String {
                return "kept"
            }
            """.trimIndent(),
        )

        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        assertNotNull(element)

        val intention = SmartConvertExpressionToBlockIntention()
        assertTrue(intention.isAvailable(project, myFixture.editor, element!!))
        intention.invoke(project, myFixture.editor, element)

        assertEquals(
            """
            package demo

            @GetMapping("/deviceTop")
            @Operation(summary = "获得IoT 告警设备排名")
            fun getAlarmDeviceTop(@RequestParam("type") type: Int): JSONObject {
                return deviceAlarmService.getAlarmDeviceTop(type)
            }

            fun greet(name: String): String {
                return "hello, ${'$'}name"
            }

            fun alreadyBlock(): String {
                return "kept"
            }
            """.trimIndent(),
            myFixture.file.text,
        )
    }

    fun testAvailableWhenCaretOnEqualsBecauseItAppliesToCurrentFile() {
        myFixture.configureByText(
            "Test.kt",
            """
            package demo

            fun greet(): String <caret>= "hello"
            """.trimIndent(),
        )

        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        assertNotNull(element)

        val intention = SmartConvertExpressionToBlockIntention()
        assertTrue(intention.isAvailable(project, myFixture.editor, element!!))
        intention.invoke(project, myFixture.editor, element)

        assertEquals(
            """
            package demo

            fun greet(): String {
                return "hello"
            }
            """.trimIndent(),
            myFixture.file.text,
        )
    }

    fun testDoesNotOfferWhenNoConvertibleFunctionExists() {
        myFixture.configureByText(
            "Test.kt",
            """
            package demo

            fun gree<caret>t(): String {
                return "hello"
            }
            """.trimIndent(),
        )

        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        assertNotNull(element)

        val intention = SmartConvertExpressionToBlockIntention()
        assertFalse(intention.isAvailable(project, myFixture.editor, element!!))
    }

    fun testSkipsFunctionsWithoutExplicitReturnType() {
        myFixture.configureByText(
            "Test.kt",
            """
            package demo

            fun gree<caret>t() = "hello"
            """.trimIndent(),
        )

        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        assertNotNull(element)

        val intention = SmartConvertExpressionToBlockIntention()
        assertFalse(intention.isAvailable(project, myFixture.editor, element!!))
    }

    fun testConvertsExpressionBodyWithReturnToBlock() {
        myFixture.configureByText(
            "Test.kt",
            """
            package demo

            fun <caret>greet(): String = return "hello"
            """.trimIndent(),
        )

        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        assertNotNull(element)

        val intention = SmartConvertExpressionToBlockIntention()
        assertTrue(intention.isAvailable(project, myFixture.editor, element!!))
        intention.invoke(project, myFixture.editor, element)

        assertEquals(
            """
            package demo

            fun greet(): String {
                return "hello"
            }
            """.trimIndent(),
            myFixture.file.text,
        )
    }
}
