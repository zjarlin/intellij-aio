package site.addzero.gradle.sleep

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectViewFocusSupportTest {

    @Test
    fun `manual input keeps project view focused on manual modules only`() {
        val focusedModules = resolveProjectViewFocusSeedModules(
            manualInputActive = true,
            manualModules = setOf(":plugins:gradle-module-sleep"),
            loadedModules = setOf(":", ":plugins:gradle-module-sleep", ":plugins:gradle-buddy"),
            selectedModules = emptySet()
        )

        assertEquals(setOf(":plugins:gradle-module-sleep"), focusedModules)
    }

    @Test
    fun `manual input with no matches keeps project view empty`() {
        val focusedModules = resolveProjectViewFocusSeedModules(
            manualInputActive = true,
            manualModules = emptySet(),
            loadedModules = setOf(":", ":plugins:gradle-module-sleep"),
            selectedModules = emptySet()
        )

        assertEquals(emptySet<String>(), focusedModules)
    }

    @Test
    fun `root module does not disable project view filtering`() {
        val focusedModules = resolveProjectViewFocusSeedModules(
            manualInputActive = false,
            manualModules = emptySet(),
            loadedModules = setOf(":", ":plugins:gradle-module-sleep", ":plugins:gradle-buddy"),
            selectedModules = emptySet()
        )

        assertEquals(
            setOf(":plugins:gradle-module-sleep", ":plugins:gradle-buddy"),
            focusedModules
        )
    }

    @Test
    fun `manual input still reveals selected file module`() {
        val focusedModules = resolveProjectViewFocusSeedModules(
            manualInputActive = true,
            manualModules = setOf(":plugins:gradle-module-sleep"),
            loadedModules = setOf(":plugins:gradle-module-sleep"),
            selectedModules = setOf(":plugins:gradle-buddy")
        )

        assertEquals(
            setOf(":plugins:gradle-module-sleep", ":plugins:gradle-buddy"),
            focusedModules
        )
    }
}
