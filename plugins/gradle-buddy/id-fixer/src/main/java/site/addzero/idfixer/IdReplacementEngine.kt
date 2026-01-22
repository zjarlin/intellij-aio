package site.addzero.idfixer

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.*

/**
 * Represents a plugin ID that needs to be replaced.
 */
data class ReplacementCandidate(
    val file: PsiFile,
    val element: KtStringTemplateExpression,
    val currentId: String,
    val suggestedId: String,
    val lineNumber: Int
)

/**
 * Engine for finding and replacing plugin ID references in Gradle build files.
 */
class IdReplacementEngine(
    private val project: Project,
    private val pluginIdMapping: Map<String, PluginIdInfo>
) {

    /**
     * Finds all plugin ID references that need to be replaced.
     */
    fun findReplacementCandidates(scope: SearchScope): List<ReplacementCandidate> {
        val candidates = mutableListOf<ReplacementCandidate>()

        // Find all Kotlin files in the scope (includes .gradle.kts files)
        val kotlinFiles = FileTypeIndex.getFiles(KotlinFileType.INSTANCE, scope as GlobalSearchScope)

        val psiManager = PsiManager.getInstance(project)

        for (virtualFile in kotlinFiles) {
            // Only process .gradle.kts files
            if (!virtualFile.name.endsWith(".gradle.kts")) continue

            val psiFile = psiManager.findFile(virtualFile) as? KtFile ?: continue

            // Find all string template expressions in the file
            val stringExpressions = PsiTreeUtil.findChildrenOfType(psiFile, KtStringTemplateExpression::class.java)

            for (stringExpr in stringExpressions) {
                // Check if this is a plugin ID reference in a plugins block
                if (!isPluginIdReference(stringExpr)) continue
                if (!isInPluginsBlock(stringExpr)) continue

                // Extract the plugin ID string
                val pluginId = extractPluginId(stringExpr) ?: continue

                // Check if this is a local plugin that needs qualification
                val pluginInfo = pluginIdMapping[pluginId] ?: continue

                // Skip if the plugin has no package (doesn't need qualification)
                if (!pluginInfo.hasPackage()) continue

                // Skip if already using the fully qualified ID
                if (pluginInfo.isFullyQualified(pluginId)) continue

                // Skip external library plugins (they typically have dots in the ID)
                if (isExternalPlugin(pluginId)) continue

                // Create a replacement candidate
                candidates.add(
                    ReplacementCandidate(
                        file = psiFile,
                        element = stringExpr,
                        currentId = pluginId,
                        suggestedId = pluginInfo.fullyQualifiedId,
                        lineNumber = getLineNumber(stringExpr)
                    )
                )
            }
        }

        return candidates
    }

    /**
     * Applies the given replacements to the project files.
     */
    fun applyReplacements(candidates: List<ReplacementCandidate>): ReplacementResult {
        if (candidates.isEmpty()) {
            return ReplacementResult.noReplacements()
        }

        val errors = mutableListOf<String>()
        var replacementsMade = 0
        val filesModified = mutableSetOf<PsiFile>()

        // Perform all replacements in a single write action
        WriteCommandAction.runWriteCommandAction(project) {
            for (candidate in candidates) {
                try {
                    // Verify the element is still valid
                    if (!candidate.element.isValid || !candidate.file.isValid) {
                        errors.add("${candidate.file.name}:${candidate.lineNumber} - Element is no longer valid")
                        continue
                    }

                    // Perform the replacement
                    replacePluginId(candidate.element, candidate.suggestedId)

                    // Track success
                    replacementsMade++
                    filesModified.add(candidate.file)

                } catch (e: Exception) {
                    errors.add("${candidate.file.name}:${candidate.lineNumber} - ${e.message}")
                }
            }
        }

        return ReplacementResult(
            filesModified = filesModified.size,
            replacementsMade = replacementsMade,
            errors = errors
        )
    }

    /**
     * Checks if the given PSI element is a plugin ID reference.
     */
    private fun isPluginIdReference(element: PsiElement): Boolean {
        // The element should be a string template expression
        if (element !is KtStringTemplateExpression) return false

        // Find the parent call expression
        val valueArgument = element.parent as? KtValueArgument ?: return false
        val argumentList = valueArgument.parent as? KtValueArgumentList ?: return false
        val callExpression = argumentList.parent as? KtCallExpression ?: return false

        // Check if the call is to a function named "id"
        val callName = callExpression.calleeExpression?.text ?: return false
        if (callName != "id") return false

        // Check if this is the first (and typically only) argument
        val arguments = callExpression.valueArguments
        if (arguments.isEmpty() || arguments.first() != valueArgument) return false

        return true
    }

    /**
     * Checks if the given element is inside a `plugins {}` block.
     */
    private fun isInPluginsBlock(element: PsiElement): Boolean {
        var current: PsiElement? = element

        while (current != null) {
            // Look for a lambda expression
            if (current is KtLambdaExpression) {
                // Check if this lambda is an argument to a "plugins" call
                val lambdaArgument = current.parent as? KtLambdaArgument ?: run {
                    current = current.parent
                    continue
                }

                val callExpression = lambdaArgument.parent as? KtCallExpression ?: run {
                    current = current.parent
                    continue
                }

                val callName = callExpression.calleeExpression?.text
                if (callName == "plugins") {
                    return true
                }
            }

            current = current.parent
        }

        return false
    }

    /**
     * Extracts the plugin ID string from a string template expression.
     */
    private fun extractPluginId(stringExpr: KtStringTemplateExpression): String? {
        // Get all entries in the string template
        val entries = stringExpr.entries

        // For simple strings, there should be exactly one literal entry
        if (entries.size == 1) {
            val entry = entries.first()
            if (entry is KtLiteralStringTemplateEntry) {
                return entry.text
            }
        }

        // For more complex templates, concatenate all literal parts
        // (though plugin IDs should always be simple strings)
        return entries.filterIsInstance<KtLiteralStringTemplateEntry>()
            .joinToString("") { it.text }
            .takeIf { it.isNotEmpty() }
    }

    /**
     * Checks if a plugin ID appears to be an external library plugin.
     */
    private fun isExternalPlugin(pluginId: String): Boolean {
        // External plugins typically have dots in their IDs
        // Local plugins typically use kebab-case without dots
        return pluginId.contains(".")
    }

    /**
     * Gets the line number (1-based) of a PSI element.
     */
    private fun getLineNumber(element: PsiElement): Int {
        val document = element.containingFile?.viewProvider?.document ?: return 0
        val offset = element.textRange.startOffset
        return document.getLineNumber(offset) + 1 // Convert to 1-based
    }

    /**
     * Replaces a plugin ID string with a new ID.
     */
    private fun replacePluginId(stringElement: KtStringTemplateExpression, newId: String) {
        val factory = KtPsiFactory(project)

        // Create a new string template with the new ID
        val newString = factory.createStringTemplate(newId)

        // Replace the old string with the new one
        stringElement.replace(newString)
    }
}
