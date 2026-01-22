package site.addzero.idfixer

import com.intellij.openapi.vfs.VirtualFile

/**
 * Represents metadata about a precompiled script plugin.
 *
 * This data class encapsulates all information needed to identify and reference
 * a Gradle precompiled script plugin, including its short ID, fully qualified ID,
 * package name, and source file location.
 *
 * @property shortId The short plugin ID extracted from the filename (e.g., "kmp-core" from "kmp-core.gradle.kts")
 * @property fullyQualifiedId The fully qualified plugin ID including package (e.g., "site.addzero.buildlogic.kmp.platform.kmp-core")
 * @property packageName The package declaration from the plugin file (e.g., "site.addzero.buildlogic.kmp.platform")
 * @property file The virtual file reference to the .gradle.kts plugin file
 *
 * @see ReplacementCandidate
 * @see PluginIdScanner
 */
data class PluginIdInfo(
    val shortId: String,
    val fullyQualifiedId: String,
    val packageName: String,
    val file: VirtualFile
) {
    /**
     * Checks if this plugin has a package declaration.
     * Plugins without package declarations don't need fully qualified IDs.
     */
    fun hasPackage(): Boolean = packageName.isNotEmpty()

    /**
     * Checks if the given ID matches this plugin's short ID.
     */
    fun matchesShortId(id: String): Boolean = shortId == id

    /**
     * Checks if the given ID is already the fully qualified ID.
     */
    fun isFullyQualified(id: String): Boolean = fullyQualifiedId == id
}
