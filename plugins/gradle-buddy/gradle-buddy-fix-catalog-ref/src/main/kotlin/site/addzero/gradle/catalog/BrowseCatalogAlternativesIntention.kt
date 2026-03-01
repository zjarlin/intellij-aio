package site.addzero.gradle.catalog

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtFile

/**
 * 浏览版本目录的其他候选项
 * 即使当前引用有效，也可以查看其他可能的选项
 */
class BrowseCatalogAlternativesIntention : IntentionAction, PriorityAction {

    private var cachedCandidates: List<CandidateItem>? = null
    private var cachedCatalogName: String? = null
    private var cachedCurrentReference: String? = null

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.NORMAL

    override fun getFamilyName(): String = "Gradle buddy"

    override fun getText(): String {
        val count = cachedCandidates?.size ?: 0
        return if (count > 0) {
            "(Gradle buddy) Browse catalog alternatives ($count candidates)"
        } else {
            "(Gradle buddy) Browse catalog alternatives"
        }
    }

    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        if (file !is KtFile) return false
        if (!file.name.endsWith(".gradle.kts")) return false
        if (editor == null) return false

        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset) ?: return false

        val dotExpression = CatalogExpressionUtils.findTopDotExpression(element) ?: return false

        if (!CatalogExpressionUtils.isCatalogReference(dotExpression)) {
            return false
        }

        if (CatalogExpressionUtils.containsMethodCall(dotExpression)) {
            return false
        }

        val (catalogName, reference) = CatalogExpressionUtils.extractCatalogReference(dotExpression) ?: return false

        val scanner = CatalogReferenceScanner(project)
        val catalogs = scanner.scanAllCatalogs()

        val availableAliases = catalogs[catalogName] ?: return false

        val matcher = AliasSimilarityMatcher()
        val suggestedAliases = CatalogExpressionUtils.filterCandidatesForReference(
            reference,
            matcher.findSimilarAliases(reference, availableAliases)
        )

        if (suggestedAliases.isEmpty()) return false

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

        val dotExpression = CatalogExpressionUtils.findTargetDotExpression(element, catalogName, currentReference) ?: return

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

    private data class CandidateItem(
        val alias: String,
        val score: Double,
        val matchedTokens: List<String>,
        val catalogName: String,
        val oldReference: String,
        val isCurrent: Boolean
    )
}
