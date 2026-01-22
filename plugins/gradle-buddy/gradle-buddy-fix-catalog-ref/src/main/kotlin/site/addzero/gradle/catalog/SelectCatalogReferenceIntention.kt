package site.addzero.gradle.catalog

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * 选择版本目录引用的意图操作
 * 为每个候选项创建一个独立的意图，显示在 Alt+Enter 菜单中
 */
class SelectCatalogReferenceIntention(
    private val catalogName: String,
    private val oldReference: String,
    private val newReference: String,
    private val score: Double,
    private val matchedTokens: List<String>
) : IntentionAction, PriorityAction {

    override fun getPriority(): PriorityAction.Priority {
        // 根据匹配度设置优先级
        return when {
            score >= 0.7 -> PriorityAction.Priority.HIGH
            score >= 0.4 -> PriorityAction.Priority.NORMAL
            else -> PriorityAction.Priority.LOW
        }
    }

    override fun getFamilyName(): String = "选择版本目录引用"

    override fun getText(): String {
        val percentage = String.format("%.0f", score * 100)
        val tokens = if (matchedTokens.isNotEmpty()) {
            " (匹配: ${matchedTokens.take(3).joinToString(", ")}${if (matchedTokens.size > 3) "..." else ""})"
        } else {
            ""
        }
        return "替换为 '$catalogName.$newReference' [$percentage%]$tokens"
    }

    override fun startInWriteAction(): Boolean = true

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        if (file !is KtFile) return false
        if (!file.name.endsWith(".gradle.kts")) return false
        if (editor == null) return false

        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset) ?: return false

        // 检查是否在版本目录引用上
        val dotExpression = findTopLevelDotExpression(element) ?: return false
        val fullText = dotExpression.text

        // 检查是否匹配当前的错误引用
        return fullText == "$catalogName.$oldReference" ||
               fullText.startsWith("$catalogName.$oldReference.")
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        if (file !is KtFile) return
        if (editor == null) return

        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset) ?: return

        replaceReference(project, element)
    }

    private fun findTopLevelDotExpression(element: PsiElement): KtDotQualifiedExpression? {
        var current = element.parent
        while (current != null && current !is KtDotQualifiedExpression) {
            current = current.parent
        }

        var dotExpression = current as? KtDotQualifiedExpression ?: return null

        // 向上查找最顶层的点限定表达式
        while (dotExpression.parent is KtDotQualifiedExpression) {
            dotExpression = dotExpression.parent as KtDotQualifiedExpression
        }

        return dotExpression
    }

    private fun replaceReference(project: Project, element: PsiElement) {
        val dotExpression = findTopLevelDotExpression(element) ?: return

        // 检查这个表达式是否以 catalogName 开头
        val fullText = dotExpression.text
        if (!fullText.startsWith("$catalogName.")) {
            return
        }

        // 构建新的引用文本
        val newFullReference = "$catalogName.$newReference"

        // 创建新的表达式
        val factory = KtPsiFactory(project)
        val newExpression = factory.createExpression(newFullReference)

        // 替换整个表达式
        dotExpression.replace(newExpression)
    }
}
