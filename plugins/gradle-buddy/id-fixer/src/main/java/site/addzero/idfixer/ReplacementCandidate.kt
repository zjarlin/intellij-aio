package site.addzero.idfixer

import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Represents a candidate for plugin ID replacement in a build file.
 *
 * This data class tracks a specific location where a plugin ID needs to be replaced
 * with its fully qualified version. It contains all information needed to perform
 * the replacement and report the change to the user.
 *
 * @property file The PSI file containing the plugin ID reference
 * @property element The string template expression containing the plugin ID (e.g., the "kmp-core" in id("kmp-core"))
 * @property currentId The current (short) plugin ID being used
 * @property suggestedId The fully qualified plugin ID that should be used
 * @property lineNumber The line number where the plugin ID appears (1-based)
 *
 * @see PluginIdInfo
 * @see ReplacementResult
 * @see IdReplacementEngine
 */
data class ReplacementCandidate(
    val file: PsiFile,
    val element: KtStringTemplateExpression,
    val currentId: String,
    val suggestedId: String,
    val lineNumber: Int
) {
    /**
     * Gets the file path relative to the project root for display purposes.
     */
    fun getDisplayPath(): String = file.virtualFile?.path ?: file.name

    /**
     * Gets a human-readable description of this replacement.
     */
    fun getDescription(): String =
        "${file.name}:$lineNumber - Replace \"$currentId\" with \"$suggestedId\""

    /**
     * Checks if this replacement is valid (element is still valid in the PSI tree).
     */
    fun isValid(): Boolean = element.isValid && file.isValid
}
