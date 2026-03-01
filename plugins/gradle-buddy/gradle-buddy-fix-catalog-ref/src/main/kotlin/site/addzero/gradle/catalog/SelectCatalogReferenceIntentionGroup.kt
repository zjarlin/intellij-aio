package site.addzero.gradle.catalog

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtFile

/**
 * 选择版本目录引用的意图操作组
 * 显示一个智能弹出菜单，列出所有候选项
 */
class SelectCatalogReferenceIntentionGroup : IntentionAction, PriorityAction {

    private var cachedError: CatalogReferenceError.NotDeclared? = null

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.HIGH

    override fun getFamilyName(): String = "Gradle buddy"

    override fun getText(): String {
        val error = cachedError
        return if (error != null && error.suggestedAliases.isNotEmpty()) {
            "(Gradle buddy) Select correct catalog reference (${error.suggestedAliases.size} candidates)"
        } else {
            "(Gradle buddy) Select correct catalog reference"
        }
    }

    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        if (file !is KtFile) return false
        if (!file.name.endsWith(".gradle.kts")) return false
        if (editor == null) return false

        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset) ?: return false

        val error = detectCatalogReferenceError(project, element) ?: return false

        if (error !is CatalogReferenceError.NotDeclared) return false
        if (error.suggestedAliases.isEmpty()) return false

        val filteredAliases = CatalogExpressionUtils.filterCandidatesForReference(error.invalidReference, error.suggestedAliases)
        if (filteredAliases.isEmpty()) return false

        cachedError = error.copy(suggestedAliases = filteredAliases)
        return true
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        if (file !is KtFile) return
        if (editor == null) return

        val error = cachedError ?: return
        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset) ?: return

        val dotExpression = CatalogExpressionUtils.findTargetDotExpression(element, error.catalogName, error.invalidReference) ?: return

        val candidates = error.suggestedAliases.map { result ->
            CandidateItem(
                alias = result.alias,
                score = result.score,
                matchedTokens = result.matchedTokens,
                catalogName = error.catalogName,
                oldReference = error.invalidReference
            )
        }

        // 只有 1 个候选项时，静默替换，不弹出菜单
        if (candidates.size == 1) {
            val c = candidates[0]
            WriteCommandAction.runWriteCommandAction(project) {
                CatalogExpressionUtils.replaceDotExpression(project, dotExpression, c.catalogName, c.alias)
            }
            return
        }

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
                        WriteCommandAction.runWriteCommandAction(project) {
                            CatalogExpressionUtils.replaceDotExpression(
                                project,
                                dotExpression,
                                selectedValue.catalogName,
                                selectedValue.alias
                            )
                        }
                    }
                    return FINAL_CHOICE
                }
            }
        )

        popup.showInBestPositionFor(editor)
    }

    private fun detectCatalogReferenceError(project: Project, element: PsiElement): CatalogReferenceError? {
        val dotExpression = CatalogExpressionUtils.findTopDotExpression(element) ?: return null

        if (!CatalogExpressionUtils.isCatalogReference(dotExpression)) {
            return null
        }

        if (CatalogExpressionUtils.containsMethodCall(dotExpression)) {
            return null
        }

        val (catalogName, reference) = CatalogExpressionUtils.extractCatalogReference(dotExpression) ?: return null

        val scanner = CatalogReferenceScanner(project)
        val catalogs = scanner.scanAllCatalogs()

        val availableAliases = catalogs[catalogName] ?: return null

        if (availableAliases.contains(reference)) {
            return null
        }

        return CatalogExpressionUtils.detectErrorType(catalogName, reference, availableAliases)
    }

    private data class CandidateItem(
        val alias: String,
        val score: Double,
        val matchedTokens: List<String>,
        val catalogName: String,
        val oldReference: String
    )
}
