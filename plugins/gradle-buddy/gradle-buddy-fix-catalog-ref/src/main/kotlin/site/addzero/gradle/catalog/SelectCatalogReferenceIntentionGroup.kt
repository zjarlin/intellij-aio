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
import site.addzero.gradle.catalog.fix.CatalogFixStrategyFactory

/**
 * 选择版本目录引用的意图操作组
 * 显示一个智能弹出菜单，列出所有候选项
 */
class SelectCatalogReferenceIntentionGroup : IntentionAction, PriorityAction {

    private var cachedError: CatalogReferenceError.NotDeclared? = null

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.HIGH

    override fun getFamilyName(): String = "Gradle Buddy"

    override fun getText(): String {
        val error = cachedError
        return if (error != null && error.suggestedAliases.isNotEmpty()) {
            "(Gradle Buddy) Select correct catalog reference (${error.suggestedAliases.size} candidates)"
        } else {
            "(Gradle Buddy) Select correct catalog reference"
        }
    }

    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        if (file !is KtFile) return false
        if (!file.name.endsWith(".gradle.kts")) return false
        if (editor == null) return false

        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset) ?: return false

        // 检测错误
        val error = detectCatalogReferenceError(project, element) ?: return false

        // 只处理 NotDeclared 类型的错误
        if (error !is CatalogReferenceError.NotDeclared) return false

        // 只有当有候选项时才显示
        if (error.suggestedAliases.isEmpty()) return false

        cachedError = error
        return true
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        if (file !is KtFile) return
        if (editor == null) return

        val error = cachedError ?: return
        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset) ?: return

        // 创建候选项列表
        val candidates = error.suggestedAliases.map { result ->
            CandidateItem(
                alias = result.alias,
                score = result.score,
                matchedTokens = result.matchedTokens,
                catalogName = error.catalogName,
                oldReference = error.invalidReference
            )
        }

        // 显示弹出菜单
        val popup = JBPopupFactory.getInstance().createListPopup(
            object : BaseListPopupStep<CandidateItem>("选择版本目录引用", candidates) {
                override fun getTextFor(value: CandidateItem): String {
                    val percentage = String.format("%.0f", value.score * 100)
                    val tokens = if (value.matchedTokens.isNotEmpty()) {
                        " (匹配: ${value.matchedTokens.take(3).joinToString(", ")}${if (value.matchedTokens.size > 3) "..." else ""})"
                    } else {
                        ""
                    }
                    return "${value.catalogName}.${value.alias} [$percentage%]$tokens"
                }

                override fun onChosen(selectedValue: CandidateItem?, finalChoice: Boolean): PopupStep<*>? {
                    if (selectedValue != null && finalChoice) {
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

    private fun detectCatalogReferenceError(project: Project, element: PsiElement): CatalogReferenceError? {
        // 找到最顶层的 KtDotQualifiedExpression
        var dotExpression = PsiTreeUtil.getParentOfType(element, KtDotQualifiedExpression::class.java)
            ?: return null

        // 向上查找，直到找到最顶层的 KtDotQualifiedExpression
        while (dotExpression.parent is KtDotQualifiedExpression) {
            dotExpression = dotExpression.parent as KtDotQualifiedExpression
        }

        if (!isCatalogReference(dotExpression)) {
            return null
        }

        val (catalogName, reference) = extractCatalogReference(dotExpression) ?: return null

        val scanner = CatalogReferenceScanner(project)
        val catalogs = scanner.scanAllCatalogs()

        val availableAliases = catalogs[catalogName] ?: return null

        if (availableAliases.contains(reference)) {
            return null
        }

        return detectErrorType(catalogName, reference, availableAliases)
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
        val topExpression = getTopExpression(expression)
        val fullText = topExpression.text
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

    private fun detectErrorType(
        catalogName: String,
        invalidReference: String,
        availableAliases: Set<String>
    ): CatalogReferenceError {
        val correctReference = findCorrectFormat(invalidReference, availableAliases)

        return if (correctReference != null) {
            CatalogReferenceError.WrongFormat(
                catalogName = catalogName,
                invalidReference = invalidReference,
                correctReference = correctReference,
                availableAliases = availableAliases
            )
        } else {
            val matcher = AliasSimilarityMatcher()
            // 移除 topN 限制，返回所有有 token 匹配的候选项
            val suggestedAliases = matcher.findSimilarAliases(invalidReference, availableAliases)

            CatalogReferenceError.NotDeclared(
                catalogName = catalogName,
                invalidReference = invalidReference,
                availableAliases = availableAliases,
                suggestedAliases = suggestedAliases
            )
        }
    }

    private fun findCorrectFormat(invalidReference: String, availableAliases: Set<String>): String? {
        val candidates = mutableSetOf<String>()

        val camelCaseConverted = invalidReference.replace(Regex("([a-z])([A-Z])")) { matchResult ->
            "${matchResult.groupValues[1]}.${matchResult.groupValues[2].lowercase()}"
        }
        candidates.add(camelCaseConverted)

        candidates.add(invalidReference.replace('_', '.'))
        candidates.add(invalidReference.replace('-', '.'))
        candidates.add(invalidReference.lowercase())

        return candidates.firstOrNull { it in availableAliases }
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
        val oldReference: String
    )
}
