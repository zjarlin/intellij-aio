package site.addzero.smart.intentions.find.sourceonly

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceOnlyFileNameSuffixFilterTest {
    @Test
    fun `parse suffix text with multiple separators`() {
        val suffixes = SourceOnlyFileNameSuffixFilter.parseText(
            """
            Draft
            *Draft.kt, Mapper；Dto
            """.trimIndent(),
        )

        assertEquals(listOf("Draft", "Draft.kt", "Mapper", "Dto"), suffixes)
    }

    @Test
    fun `match file stem suffix without extension`() {
        assertTrue(SourceOnlyFileNameSuffixFilter.matches("SmsLogDraft.kt", listOf("Draft")))
        assertFalse(SourceOnlyFileNameSuffixFilter.matches("DraftSmsLog.kt", listOf("Draft")))
    }

    @Test
    fun `match full file name suffix`() {
        assertTrue(SourceOnlyFileNameSuffixFilter.matches("SmsLogDraft.kt", listOf("Draft.kt")))
        assertFalse(SourceOnlyFileNameSuffixFilter.matches("SmsLogDraft.java", listOf("Draft.kt")))
    }
}
