package site.addzero.idfixer

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.SimpleListCellRenderer
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

    override fun getText(): String = "(Gradle Buddy) Fix build script reference"
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
        val pluginInfo = findBestPluginInfo(project, pluginId) ?: return IntentionPreviewInfo.EMPTY
        if (pluginInfo.fullyQualifiedId == pluginId) return IntentionPreviewInfo.EMPTY

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
        if (!element.containingFile.name.endsWith(".gradle.kts")) return false

        // Find the string template expression at the cursor
        val stringExpr = findPluginIdStringExpression(element) ?: return false

        // Check if this is inside an id() call
        if (!isPluginIdReference(stringExpr)) return false

        // Check if this is inside a plugins {} block
        if (!isInPluginsBlock(stringExpr)) return false

        // Extract the plugin ID
        val pluginId = extractPluginId(stringExpr) ?: return false

        return pluginId.isNotBlank()
    }

    /**
     * Performs the intention action: replaces all occurrences across the entire project.
     */
    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        // Find the string template expression at the cursor
        val stringExpr = findPluginIdStringExpression(element) ?: return

        // Extract the plugin ID
        val pluginId = extractPluginId(stringExpr) ?: return

        val pluginInfos = findPluginInfos(project, pluginId)
        if (pluginInfos.isEmpty()) {
            showNotification(project, "No matching build script candidates found", NotificationType.INFORMATION)
            return
        }

        val chosen = if (pluginInfos.size == 1) {
            pluginInfos.first()
        } else {
            val editorRef = editor
            val candidates = pluginInfos.map { info ->
                val relativePath = project.basePath?.let { base ->
                    info.file.path.removePrefix(base.trimEnd('/', '\\') + "/")
                } ?: info.file.path
                PluginCandidate(info, relativePath)
            }

            val popup = JBPopupFactory.getInstance()
                .createPopupChooserBuilder(candidates)
                .setTitle("Select build script reference")
                .setRenderer(SimpleListCellRenderer.create("") { candidate ->
                    "${candidate.info.fullyQualifiedId}  (${candidate.relativePath})"
                })
                .setItemChosenCallback { candidate ->
                    applyFix(project, pluginId, candidate.info)
                }
                .createPopup()
            if (editorRef != null) {
                popup.showInBestPositionFor(editorRef)
            } else {
                popup.showInFocusCenter()
            }
            return
        }

        applyFix(project, pluginId, chosen)
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

        val callExpression = PsiTreeUtil.getParentOfType(element, KtCallExpression::class.java)
        if (callExpression?.calleeExpression?.text == "id") {
            val argument = callExpression.valueArguments.firstOrNull()?.getArgumentExpression()
            if (argument is KtStringTemplateExpression) {
                return argument
            }
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
    private fun findBestPluginInfo(project: Project, pluginId: String): PluginIdInfo? {
        return findPluginInfos(project, pluginId).firstOrNull()
    }

    /**
     * Finds candidate PluginIdInfo entries for a given plugin ID by keyword similarity.
     */
    private fun findPluginInfos(project: Project, pluginId: String): List<PluginIdInfo> {
        val scanner = PluginIdScanner(project)
        val coreId = extractCoreId(pluginId)
        val keywords = extractKeywords(coreId)
        val allPlugins = scanner.scanProjectGradleScripts()

        val scored = allPlugins.mapNotNull { info ->
            if (info.fullyQualifiedId == pluginId) return@mapNotNull null
            val score = calculateMatchScore(coreId, keywords, info)
            if (score <= 0) return@mapNotNull null
            PluginCandidate(info, "", score)
        }

        return scored.sortedWith(compareByDescending<PluginCandidate> { it.score }
            .thenBy { it.info.fullyQualifiedId })
            .map { it.info }
    }

    private fun extractCoreId(pluginId: String): String {
        val trimmed = pluginId.trim()
        return if (trimmed.contains('.')) trimmed.substringAfterLast('.') else trimmed
    }

    private fun extractKeywords(coreId: String): List<String> {
        return coreId.split('-', '_', '.')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun calculateMatchScore(coreId: String, keywords: List<String>, info: PluginIdInfo): Int {
        val coreLower = coreId.lowercase()
        val shortLower = info.shortId.lowercase()
        val fullLower = info.fullyQualifiedId.lowercase()
        val nameLower = info.file.name.lowercase()
        val pathLower = info.file.path.lowercase()

        val normalizedKeywords = keywords.map { it.lowercase() }.distinct()
        val distinctiveKeywords = normalizedKeywords.filterNot { it in WEAK_KEYWORDS }

        var score = 0

        if (shortLower == coreLower) score += 120
        if (shortLower.contains(coreLower) && coreLower.isNotBlank()) score += 60
        if (fullLower.contains(coreLower) && coreLower.isNotBlank()) score += 40
        if (nameLower.contains(coreLower) && coreLower.isNotBlank()) score += 30
        if (pathLower.contains(coreLower) && coreLower.isNotBlank()) score += 20

        if (normalizedKeywords.isEmpty()) {
            return score
        }

        if (distinctiveKeywords.isNotEmpty()) {
            val hasDistinctiveMatch = distinctiveKeywords.any { kw ->
                shortLower.contains(kw) || fullLower.contains(kw) || nameLower.contains(kw)
            }
            if (!hasDistinctiveMatch) {
                return 0
            }
        }

        var matched = 0
        for (kw in normalizedKeywords) {
            val kwScore = when {
                shortLower.contains(kw) -> 30
                nameLower.contains(kw) -> 25
                fullLower.contains(kw) -> 20
                pathLower.contains(kw) -> 10
                else -> 0
            }
            if (kwScore > 0) {
                matched++
                score += kwScore
            }
        }

        if (matched > 0) {
            val coverage = matched.toDouble() / normalizedKeywords.size
            score += (coverage * 40.0).toInt()
            if (matched == normalizedKeywords.size) {
                score += 30
            }
        }

        return score
    }

    private companion object {
        val WEAK_KEYWORDS = setOf(
            "convention",
            "conventions",
            "plugin",
            "plugins",
            "gradle",
            "build",
            "logic",
            "module",
            "modules"
        )
    }

    private fun applyFix(project: Project, pluginId: String, pluginInfo: PluginIdInfo) {
        val engine = IdReplacementEngine(project, mapOf(pluginId to pluginInfo))
        val allCandidates = engine.findReplacementCandidates(GlobalSearchScope.projectScope(project))
        val candidates = allCandidates.filter { it.currentId == pluginId }

        if (candidates.isEmpty()) {
            showNotification(
                project,
                "No occurrences of '$pluginId' found to fix",
                NotificationType.INFORMATION
            )
            return
        }

        val result = com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction<ReplacementResult>(project) {
            engine.applyReplacements(candidates)
        }

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

    private data class PluginCandidate(
        val info: PluginIdInfo,
        val relativePath: String,
        val score: Int = 0
    )

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
