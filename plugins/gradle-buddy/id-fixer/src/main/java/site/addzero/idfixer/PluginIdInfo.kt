package site.addzero.idfixer

import com.intellij.openapi.vfs.VirtualFile

/**
 * Represents metadata about a precompiled script plugin.
 */
data class PluginIdInfo(
    val shortId: String,
    val fullyQualifiedId: String,
    val packageName: String,
    val file: VirtualFile
) {
    fun hasPackage(): Boolean = packageName.isNotEmpty()
    fun matchesShortId(id: String): Boolean = shortId == id
    fun isFullyQualified(id: String): Boolean = fullyQualifiedId == id
}
