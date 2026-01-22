package site.addzero.gradle.catalog.fix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import site.addzero.gradle.catalog.CatalogReferenceError

/**
 * 未声明错误的修复策略
 * 当 TOML 中完全没有对应的声明时，提供两种选择：
 * 1. 如果找到相似的别名，创建多个子意图供用户选择（带预览）
 * 2. 如果没有找到任何相似的别名，提示用户添加到 TOML
 */
class NotDeclaredFixStrategy : CatalogFixStrategy {

    override fun support(error: CatalogReferenceError): Boolean {
        return error is CatalogReferenceError.NotDeclared
    }

    override fun getFixDescription(error: CatalogReferenceError): String {
        if (error !is CatalogReferenceError.NotDeclared) {
            return "修复版本目录引用"
        }

        return if (error.suggestedAliases.isNotEmpty()) {
            "选择正确的版本目录引用（找到 ${error.suggestedAliases.size} 个相似项）"
        } else {
            "版本目录中未声明 '${error.invalidReference}'，需要添加到 TOML"
        }
    }

    override fun createFix(project: Project, error: CatalogReferenceError): LocalQuickFix? {
        if (error !is CatalogReferenceError.NotDeclared) {
            return null
        }

        return if (error.suggestedAliases.isNotEmpty()) {
            // 有候选项，创建主修复（会生成子意图）
            SelectFromCandidatesFixWrapper(error)
        } else {
            // 没有候选项，提示添加到 TOML
            AddToTomlFix(error)
        }
    }

    /**
     * 包装器：用于在 Inspection 中显示
     */
    private class SelectFromCandidatesFixWrapper(
        private val error: CatalogReferenceError.NotDeclared
    ) : LocalQuickFix {

        override fun getFamilyName(): String = "选择正确的版本目录引用"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            // 这个方法不会被直接调用，因为我们会通过 Intention 来处理
            // 但为了兼容性，提供一个简单的实现
            val element = descriptor.psiElement ?: return

            if (error.suggestedAliases.isNotEmpty()) {
                val selectedAlias = error.suggestedAliases.first().alias
                com.intellij.openapi.application.ApplicationManager.getApplication().runWriteAction {
                    replaceReference(project, element, error.catalogName, error.invalidReference, selectedAlias)
                }
            }
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
    }

    /**
     * 提示添加到 TOML 的修复
     */
    private class AddToTomlFix(
        private val error: CatalogReferenceError.NotDeclared
    ) : LocalQuickFix {

        override fun getFamilyName(): String = "添加到版本目录"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val message = buildString {
                appendLine("版本目录 '${error.catalogName}' 中未找到 '${error.invalidReference}' 的声明。")
                appendLine()
                appendLine("也没有找到相似的别名。")
                appendLine()
                appendLine("请在 TOML 文件中添加对应的声明，例如：")
                appendLine()
                appendLine("[libraries]")
                appendLine("${error.invalidReference.replace('.', '-')} = { group = \"...\", name = \"...\", version = \"...\" }")
                appendLine()
                appendLine("或")
                appendLine()
                appendLine("[plugins]")
                appendLine("${error.invalidReference.replace('.', '-')} = { id = \"...\", version = \"...\" }")
            }

            Messages.showInfoMessage(
                project,
                message,
                "需要添加到版本目录"
            )
        }
    }

    companion object {
        /**
         * 创建候选项的子意图列表
         * 这些意图会显示在 Alt+Enter 菜单中
         */
        fun createCandidateIntentions(error: CatalogReferenceError.NotDeclared): List<IntentionAction> {
            return error.suggestedAliases.map { result ->
                SelectCandidateIntention(
                    error.catalogName,
                    error.invalidReference,
                    result.alias,
                    result.score,
                    result.matchedTokens
                )
            }
        }
    }

    /**
     * 单个候选项的意图操作
     * 每个候选项都是一个独立的意图，可以显示预览
     */
    private class SelectCandidateIntention(
        private val catalogName: String,
        private val oldReference: String,
        private val newReference: String,
        private val score: Double,
        private val matchedTokens: List<String>
    ) : IntentionAction, PriorityAction {

        override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.NORMAL

        override fun getFamilyName(): String = "选择版本目录引用"

        override fun getText(): String {
            val percentage = String.format("%.0f", score * 100)
            val tokens = if (matchedTokens.isNotEmpty()) {
                " (匹配: ${matchedTokens.joinToString(", ")})"
            } else {
                ""
            }
            return "替换为 '$catalogName.$newReference' [$percentage%]$tokens"
        }

        override fun startInWriteAction(): Boolean = true

        override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
            return file is KtFile && file.name.endsWith(".gradle.kts")
        }

        override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
            if (file !is KtFile) return
            if (editor == null) return

            val offset = editor.caretModel.offset
            val element = file.findElementAt(offset) ?: return

            replaceReference(project, element, catalogName, oldReference, newReference)
        }

        private fun replaceReference(
            project: Project,
            element: PsiElement,
            catalogName: String,
            oldReference: String,
            newReference: String
        ) {
            // 查找包含此元素的点限定表达式，并找到最顶层的表达式
            var current = element.parent
            while (current != null && current !is KtDotQualifiedExpression) {
                current = current.parent
            }

            var dotExpression = current as? KtDotQualifiedExpression ?: return

            // 向上查找，直到找到最顶层的点限定表达式
            while (dotExpression.parent is KtDotQualifiedExpression) {
                dotExpression = dotExpression.parent as KtDotQualifiedExpression
            }

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
}
