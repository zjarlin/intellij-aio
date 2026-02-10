package site.addzero.gradle.catalog

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * 浏览版本目录的其他候选项
 * 即使当前引用有效，也可以查看其他可能的选项
 */
class BrowseCatalogAlternativesIntention : IntentionAction, PriorityAction {

    private var cachedCandidates: List<CandidateItem>? = null
    private var cachedCatalogName: String? = null
    private var cachedCurrentReference: String? = null

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.NORMAL

    override fun getFamilyName(): String = "Gradle Buddy"

    override fun getText(): String {
        val count = cachedCandidates?.size ?: 0
        return if (count > 0) {
            "(Gradle Buddy) Browse catalog alternatives ($count candidates)"
        } else {
            "(Gradle Buddy) Browse catalog alternatives"
        }
    }

    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        if (file !is KtFile) return false
        if (!file.name.endsWith(".gradle.kts")) return false
        if (editor == null) return false

        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset) ?: return false

        // 找到最顶层的 KtDotQualifiedExpression
        var dotExpression = PsiTreeUtil.getParentOfType(element, KtDotQualifiedExpression::class.java)
            ?: return false

        while (dotExpression.parent is KtDotQualifiedExpression) {
            dotExpression = dotExpression.parent as KtDotQualifiedExpression
        }

        if (!isCatalogReference(dotExpression)) {
            return false
        }

        val (catalogName, reference) = extractCatalogReference(dotExpression) ?: return false

        val scanner = CatalogReferenceScanner(project)
        val catalogs = scanner.scanAllCatalogs()

        val availableAliases = catalogs[catalogName] ?: return false

        // 使用相似度匹配查找候选项
        val matcher = AliasSimilarityMatcher()
        var suggestedAliases = matcher.findSimilarAliases(reference, availableAliases)

        // 如果是 library 引用，过滤掉 versions.xxx 候选项
        val prefixes = listOf("versions.", "plugins.", "bundles.")
        if (prefixes.none { reference.startsWith(it) }) {
            suggestedAliases = suggestedAliases.filter { !it.alias.startsWith("versions.") }
        }

        if (suggestedAliases.isEmpty()) return false

        // 缓存结果
        cachedCandidates = suggestedAliases.map { result ->
            CandidateItem(
                alias = result.alias,
                score = result.score,
                matchedTokens = result.matchedTokens,
                catalogName = catalogName,
                oldReference = reference,
                isCurrent = result.alias == reference
            )
        }
        cachedCatalogName = catalogName
        cachedCurrentReference = reference

        return true
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        if (file !is KtFile) return
        if (editor == null) return

        val candidates = cachedCandidates ?: return
        val catalogName = cachedCatalogName ?: return
        val currentReference = cachedCurrentReference ?: return
        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset) ?: return

        // 找到目标 dotExpression 并严格校验
        val dotExpression = findTargetDotExpression(element, catalogName, currentReference) ?: return

        // 显示弹出菜单
        val popup = JBPopupFactory.getInstance().createListPopup(
            object : BaseListPopupStep<CandidateItem>("浏览版本目录引用", candidates) {
                override fun getTextFor(value: CandidateItem): String {
                    val percentage = String.format("%.0f", value.score * 100)
                    val tokens = if (value.matchedTokens.isNotEmpty()) {
                        " (匹配: ${value.matchedTokens.take(3).joinToString(", ")}${if (value.matchedTokens.size > 3) "..." else ""})"
                    } else {
                        ""
                    }
                    val current = if (value.isCurrent) " ✓ 当前" else ""
                    return "${value.catalogName}.${value.alias} [$percentage%]$tokens$current"
                }

                override fun onChosen(selectedValue: CandidateItem?, finalChoice: Boolean): PopupStep<*>? {
                    if (selectedValue != null && finalChoice && !selectedValue.isCurrent) {
                        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
                            replaceDotExpression(
                                project,
                                dotExpression,
                                selectedValue.catalogName,
                                selectedValue.alias
                            )
                        }
                    }
                    return PopupStep.FINAL_CHOICE
                }
            }
        )

        popup.showInBestPositionFor(editor)
    }

    /**
     * 从 element 向上找到目标 KtDotQualifiedExpression，
     * 并严格校验其文本与预期的 catalogName.oldReference 完全匹配
     */
    private fun findTargetDotExpression(
        element: PsiElement,
        catalogName: String,
        oldReference: String
    ): KtDotQualifiedExpression? {
        var dotExpression = PsiTreeUtil.getParentOfType(element, KtDotQualifiedExpression::class.java)
            ?: return null

        while (dotExpression.parent is KtDotQualifiedExpression) {
            dotExpression = dotExpression.parent as KtDotQualifiedExpression
        }

        val expectedText = "$catalogName.$oldReference"
        if (dotExpression.text != expectedText) {
            return null
        }

        return dotExpression
    }

    /**
     * 直接替换已定位的 dotExpression
     */
    private fun replaceDotExpression(
        project: Project,
        dotExpression: KtDotQualifiedExpression,
        catalogName: String,
        newReference: String
    ) {
        val cleanRef = if (newReference.startsWith("$catalogName.")) {
            newReference.removePrefix("$catalogName.")
        } else {
            newReference
        }

        val newFullReference = "$catalogName.$cleanRef"
        val factory = KtPsiFactory(project)
        val newExpression = factory.createExpression(newFullReference)
        dotExpression.replace(newExpression)
    }

    private fun isCatalogReference(expression: KtDotQualifiedExpression): Boolean {
        val topExpression = getTopExpression(expression)
        if (containsForbiddenSegment(topExpression)) return false

        var current = topExpression
        while (current.receiverExpression is KtDotQualifiedExpression) {
            current = current.receiverExpression as KtDotQualifiedExpression
        }

        val rootName = (current.receiverExpression as? org.jetbrains.kotlin.psi.KtNameReferenceExpression)?.getReferencedName()
        val catalogNames = setOf("libs", "zlibs", "klibs", "testLibs")
        return rootName in catalogNames
    }

    private fun extractCatalogReference(expression: KtDotQualifiedExpression): Pair<String, String>? {
        val fullText = getTopExpression(expression).text
        val parts = fullText.split(".")

        if (parts.size < 2) {
            return null
        }

        val catalogName = parts[0]
        val reference = parts.drop(1).joinToString(".")

        return catalogName to reference
    }

    private fun getTopExpression(expression: KtDotQualifiedExpression): KtDotQualifiedExpression {
        var topExpression = expression
        while (topExpression.parent is KtDotQualifiedExpression) {
            topExpression = topExpression.parent as KtDotQualifiedExpression
        }
        return topExpression
    }

    private fun containsForbiddenSegment(expression: KtDotQualifiedExpression): Boolean {
        val fullText = expression.text
        return fullText.contains(".javaClass") || fullText.endsWith(".javaClass")
    }

    private data class CandidateItem(
        val alias: String,
        val score: Double,
        val matchedTokens: List<String>,
        val catalogName: String,
        val oldReference: String,
        val isCurrent: Boolean
    )
}
