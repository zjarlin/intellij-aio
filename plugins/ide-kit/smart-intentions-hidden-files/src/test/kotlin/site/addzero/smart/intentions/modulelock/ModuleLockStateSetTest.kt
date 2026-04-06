package site.addzero.smart.intentions.modulelock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleLockStateSetTest {
    @Test
    fun `locked module roots hide descendants`() {
        val entries = mutableListOf<LockedModuleState>()

        ModuleLockStateSet.add(entries, "feature-cart", listOf("/repo/apps/cart"))

        assertTrue(ModuleLockStateSet.contains(entries, "feature-cart"))
        assertTrue(ModuleLockStateSet.containsPath(entries, "/repo/apps/cart/src/App.kt"))
        assertFalse(ModuleLockStateSet.containsPath(entries, "/repo/apps/order/src/App.kt"))
    }

    @Test
    fun `updating same module replaces stored roots`() {
        val entries = mutableListOf<LockedModuleState>()

        ModuleLockStateSet.add(entries, "feature-cart", listOf("/repo/apps/cart"))
        ModuleLockStateSet.add(entries, "feature-cart", listOf("/repo/apps/cart", "/repo/apps/cart-shared"))

        assertEquals(1, entries.size)
        assertEquals(
            listOf("/repo/apps/cart", "/repo/apps/cart-shared"),
            entries.single().rootPaths,
        )
    }

    @Test
    fun `remove unlocks module by name`() {
        val entries = mutableListOf<LockedModuleState>()

        ModuleLockStateSet.add(entries, "feature-cart", listOf("/repo/apps/cart"))

        assertTrue(ModuleLockStateSet.remove(entries, "feature-cart"))
        assertTrue(entries.isEmpty())
    }
}
