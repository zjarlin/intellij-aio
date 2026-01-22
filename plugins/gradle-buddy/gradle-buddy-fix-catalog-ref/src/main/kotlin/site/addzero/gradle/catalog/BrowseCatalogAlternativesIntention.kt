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
        val suggestedAliases = matcher.findSimilarAliases(reference, availableAliases)

        // 只要有候选项就显示（包括当前引用本身）
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
        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset) ?: return

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
                        // 执行替换
                        com.intellij.openapi.application.ApplicationManager.getApplication().runWriteAction {
                            replaceReference(
                                project,
                                element,
                                selectedValue.catalogName,
                                selectedValue.oldReference,
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

    private fun isCatalogReference(expression: KtDotQualifiedExpression): Boolean {
        var current = expression
        while (current.receiverExpression is KtDotQualifiedExpression) {
            current = current.receiverExpression as KtDotQualifiedExpression
        }

        val rootName = (current.receiverExpression as? org.jetbrains.kotlin.psi.KtNameReferenceExpression)?.getReferencedName()
        val catalogNames = setOf("libs", "zlibs", "klibs", "testLibs")
        return rootName in catalogNames
    }

    private fun extractCatalogReference(expression: KtDotQualifiedExpression): Pair<String, String>? {
        val fullText = expression.text
        val parts = fullText.split(".")

        if (parts.size < 2) {
            return null
        }

        val catalogName = parts[0]
        val reference = parts.drop(1).joinToString(".")

        return catalogName to reference
    }

    private fun replaceReference(
        project: Project,
        element: PsiElement,
        catalogName: String,
        oldReference: String,
        newReference: String
    ) {
        var current = element.parent
        while (current != null && current !is KtDotQualifiedExpression) {
            current = current.parent
        }

        var dotExpression = current as? KtDotQualifiedExpression ?: return

        while (dotExpression.parent is KtDotQualifiedExpression) {
            dotExpression = dotExpression.parent as KtDotQualifiedExpression
        }

        val fullText = dotExpression.text
        if (!fullText.startsWith("$catalogName.")) {
            return
        }

        val newFullReference = "$catalogName.$newReference"
        val factory = KtPsiFactory(project)
        val newExpression = factory.createExpression(newFullReference)
        dotExpression.replace(newExpression)
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
