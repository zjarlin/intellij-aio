package site.addzero.gradle.sleep.loader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnDemandModuleLoaderTest {

    @Test
    fun `rewrite removes stale include statements outside managed block`() {
        val originalContent = """
            rootProject.name = "addzero-lib-jvm"
            
            // >>> Gradle Module Sleep: On-Demand Modules (DO NOT EDIT THIS BLOCK) >>>
            // Generated at: 2026-04-04T22:31:27.940807
            // Loaded: 2, Excluded: 0, Total: 2
            include(":lib:kcp:spread-pack:kcp-spread-pack-plugin")
            include(":lib:kcp:spread-pack:kcp-spread-pack-gradle-plugin")
            // <<< Gradle Module Sleep: End Of Block <<<
            
            include(":lib:kcp:spread-pack:kcp-spread-pack-plugin")
            include(":lib:kcp:spread-pack:kcp-spread-pack-gradle-plugin")
            include(":lib:kcp:spread-pack:kcp-spread-pack-ide-plugin")
        """.trimIndent()

        val rewrittenContent = rewriteSettingsWithActiveModules(
            originalContent,
            setOf(
                ":lib:kcp:spread-pack:kcp-spread-pack-plugin",
                ":lib:kcp:spread-pack:kcp-spread-pack-gradle-plugin",
            )
        )

        assertTrue(rewrittenContent.contains("include(\":lib:kcp:spread-pack:kcp-spread-pack-plugin\")"))
        assertTrue(rewrittenContent.contains("include(\":lib:kcp:spread-pack:kcp-spread-pack-gradle-plugin\")"))
        assertTrue(rewrittenContent.contains("//include(\":lib:kcp:spread-pack:kcp-spread-pack-ide-plugin\") // excluded by Gradle Buddy"))
        assertFalse(rewrittenContent.contains("\ninclude(\":lib:kcp:spread-pack:kcp-spread-pack-ide-plugin\")\n"))
    }

    private fun rewriteSettingsWithActiveModules(
        originalContent: String,
        activeModules: Set<String>
    ): String {
        val method = OnDemandModuleLoader::class.java.getDeclaredMethod(
            "rewriteSettingsWithActiveModules",
            String::class.java,
            Set::class.java
        )
        method.isAccessible = true
        return method.invoke(OnDemandModuleLoader, originalContent, activeModules) as String
    }
}
