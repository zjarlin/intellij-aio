package site.addzero.idfixer

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.*

/**
 * Intention action that fixes plugin ID references to use fully qualified names.
 *
 * When triggered, performs PROJECT-WIDE replacement of all occurrences.
 */
class FixPluginIdIntention : PsiElementBaseIntentionAction(), IntentionAction {

    override fun getText(): String = "(Gradle Buddy) Fix build-logic qualified name"
    override fun getFamilyName(): String = "Gradle Plugin ID"

    /**
     * Generates a preview for the intention action without performing write actions.
     */
    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        // Find the element at the cursor
        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset) ?: return IntentionPreviewInfo.EMPTY

        // Find the string template expression at the cursor
        val stringExpr = findPluginIdStringExpression(element) ?: return IntentionPreviewInfo.EMPTY

        // Extract the plugin ID
        val pluginId = extractPluginId(stringExpr) ?: return IntentionPreviewInfo.EMPTY

        // Find the plugin info with fully qualified ID
        val pluginInfo = findPluginInfo(project, pluginId) ?: return IntentionPreviewInfo.EMPTY

        // Create a preview by replacing just the current element
        val factory = KtPsiFactory(project)
        val newString = factory.createStringTemplate(pluginInfo.fullyQualifiedId)
        stringExpr.replace(newString)

        return IntentionPreviewInfo.DIFF
    }

    /**
     * Checks if this intention action is available at the current cursor position.
     */
    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        // Must be in a Kotlin file
        if (element.containingFile !is KtFile) return false

        // Find the string template expression at the cursor
        val stringExpr = findPluginIdStringExpression(element) ?: return false

        // Check if this is inside an id() call
        if (!isPluginIdReference(stringExpr)) return false

        // Check if this is inside a plugins {} block
        if (!isInPluginsBlock(stringExpr)) return false

        // Extract the plugin ID
        val pluginId = extractPluginId(stringExpr) ?: return false

        // Skip external plugins (they have dots in their IDs)
        if (isExternalPlugin(pluginId)) return false

        // Find the plugin info to check if it needs qualification
        val pluginInfo = findPluginInfo(project, pluginId) ?: return false

        // Only offer the fix if the plugin has a package and isn't already qualified
        return pluginInfo.hasPackage() && !pluginInfo.isFullyQualified(pluginId)
    }

    /**
     * Performs the intention action: replaces all occurrences across the entire project.
     */
    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        // Find the string template expression at the cursor
        val stringExpr = findPluginIdStringExpression(element) ?: return

        // Extract the plugin ID
        val pluginId = extractPluginId(stringExpr) ?: return

        // Find the plugin info with fully qualified ID
        val pluginInfo = findPluginInfo(project, pluginId) ?: return

        // Build the plugin ID mapping for the replacement engine
        val scanner = PluginIdScanner(project)
        val buildLogicDirs = scanner.findBuildLogicDirectories()
        val allPlugins = buildLogicDirs.flatMap { scanner.scanBuildLogic(it) }
        val pluginIdMapping = allPlugins.associateBy { it.shortId }

        // Create the replacement engine
        val engine = IdReplacementEngine(project, pluginIdMapping)

        // Find all replacement candidates in the project
        val allCandidates = engine.findReplacementCandidates(GlobalSearchScope.projectScope(project))

        // Filter to only the specific plugin ID we're fixing
        val candidates = allCandidates.filter { it.currentId == pluginId }

        if (candidates.isEmpty()) {
            showNotification(
                project,
                "No occurrences of '$pluginId' found to fix",
                NotificationType.INFORMATION
            )
            return
        }

        // Apply all replacements in a write action
        val result = com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction<ReplacementResult>(project) {
            engine.applyReplacements(candidates)
        }

        // Show notification with summary
        if (result.isSuccessful()) {
            val message = if (result.filesModified == 1) {
                "Fixed ${result.replacementsMade} occurrence(s) of '$pluginId' in 1 file"
            } else {
                "Fixed ${result.replacementsMade} occurrence(s) of '$pluginId' in ${result.filesModified} files"
            }
            showNotification(project, message, NotificationType.INFORMATION)
        } else {
            showNotification(
                project,
                "Failed to fix plugin ID: ${result.errors.firstOrNull() ?: "Unknown error"}",
                NotificationType.ERROR
            )
        }
    }

    /**
     * Finds the string template expression containing the plugin ID.
     */
    private fun findPluginIdStringExpression(element: PsiElement): KtStringTemplateExpression? {
        // Try to find the string template expression directly
        var stringExpr = PsiTreeUtil.getParentOfType(element, KtStringTemplateExpression::class.java)
        if (stringExpr != null) return stringExpr

        // If the cursor is on the quotes, the element might be the string itself
        if (element.parent is KtStringTemplateExpression) {
            return element.parent as KtStringTemplateExpression
        }

        return null
    }

    /**
     * Checks if the given string expression is a plugin ID reference.
     */
    private fun isPluginIdReference(stringExpr: KtStringTemplateExpression): Boolean {
        // Find the parent call expression
        val valueArgument = stringExpr.parent as? KtValueArgument ?: return false
        val argumentList = valueArgument.parent as? org.jetbrains.kotlin.psi.KtValueArgumentList ?: return false
        val callExpression = argumentList.parent as? KtCallExpression ?: return false

        // Check if the call is to a function named "id"
        val callName = callExpression.calleeExpression?.text ?: return false
        return callName == "id"
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
            if (entry is org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry) {
                return entry.text
            }
        }

        // For more complex templates, concatenate all literal parts
        return entries.filterIsInstance<org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry>()
            .joinToString("") { it.text }
            .takeIf { it.isNotEmpty() }
    }

    /**
     * Checks if a plugin ID appears to be an external library plugin.
     */
    private fun isExternalPlugin(pluginId: String): Boolean {
        return pluginId.contains(".")
    }

    /**
     * Finds the PluginIdInfo for a given plugin ID.
     */
    private fun findPluginInfo(project: Project, pluginId: String): PluginIdInfo? {
        val scanner = PluginIdScanner(project)
        val buildLogicDirs = scanner.findBuildLogicDirectories()

        for (buildLogicDir in buildLogicDirs) {
            val plugins = scanner.scanBuildLogic(buildLogicDir)
            val pluginInfo = plugins.find { it.matchesShortId(pluginId) }
            if (pluginInfo != null) {
                return pluginInfo
            }
        }

        return null
    }

    /**
     * Shows a notification to the user.
     */
    private fun showNotification(project: Project, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Gradle Plugin ID Fixer")
            .createNotification(message, type)
            .notify(project)
    }
}
