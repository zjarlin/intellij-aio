package site.addzero.smart.intentions.hiddenfiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HiddenPathStateSetTest {
    @Test
    fun `directory entries hide descendants`() {
        val entries = mutableListOf<HiddenPathState>()

        HiddenPathStateSet.add(entries, "/repo/build", directory = true)

        assertTrue(HiddenPathStateSet.contains(entries, "/repo/build"))
        assertTrue(HiddenPathStateSet.contains(entries, "/repo/build/generated/App.kt"))
        assertFalse(HiddenPathStateSet.contains(entries, "/repo/src/App.kt"))
    }

    @Test
    fun `ancestor hide entry deduplicates descendants`() {
        val entries = mutableListOf<HiddenPathState>()

        HiddenPathStateSet.add(entries, "/repo/build/generated/App.kt", directory = false)
        HiddenPathStateSet.add(entries, "/repo/build", directory = true)

        assertEquals(1, entries.size)
        assertEquals("/repo/build", entries.single().path)
        assertTrue(entries.single().directory)
    }

    @Test
    fun `unhide removes the entry that affects the selected file`() {
        val entries = mutableListOf<HiddenPathState>()

        HiddenPathStateSet.add(entries, "/repo/build", directory = true)

        assertTrue(HiddenPathStateSet.removeAffecting(entries, "/repo/build/generated/App.kt"))
        assertTrue(entries.isEmpty())
    }
}
