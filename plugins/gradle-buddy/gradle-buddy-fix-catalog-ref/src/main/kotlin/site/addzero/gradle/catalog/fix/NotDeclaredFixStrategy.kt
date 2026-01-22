package site.addzero.gradle.catalog.fix

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import site.addzero.gradle.catalog.CatalogReferenceError

/**
 * 未声明错误的修复策略
 * 当 TOML 中完全没有对应的声明时，提供两种选择：
 * 1. 如果找到相似的别名，弹出对话框让用户选择
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
            // 有候选项，创建选择对话框的修复
            SelectFromCandidatesFix(error)
        } else {
            // 没有候选项，提示添加到 TOML
            AddToTomlFix(error)
        }
    }

    /**
     * 从候选列表中选择的修复
     */
    private class SelectFromCandidatesFix(
        private val error: CatalogReferenceError.NotDeclared
    ) : LocalQuickFix {

        override fun getFamilyName(): String = "选择正确的版本目录引用"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val element = descriptor.psiElement ?: return

            // 准备候选项列表
            val candidates = error.suggestedAliases.map { result ->
                "${result.alias} (匹配度: ${String.format("%.0f", result.score * 100)}%, 匹配词: ${result.matchedTokens.joinToString(", ")})"
            }.toTypedArray()

            // 显示选择对话框
            val selectedIndex = Messages.showChooseDialog(
                project,
                "在 TOML 中找到以下相似的别名，请选择正确的引用：",
                "选择版本目录引用",
                Messages.getQuestionIcon(),
                candidates,
                candidates.firstOrNull()
            )

            if (selectedIndex >= 0 && selectedIndex < error.suggestedAliases.size) {
                val selectedAlias = error.suggestedAliases[selectedIndex].alias

                // 在 write action 中执行替换
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
            // 查找包含此元素的点限定表达式，并找到最顶层的表达式
            var current = element.parent
            while (current != null && current !is KtDotQualifiedExpression) {
                current = current.parent
            }

            var dotExpression = current as? KtDotQualifiedExpression ?: return

            // 向上查找，直到找到最顶层的点限定表达式
            // 例如：libs.kotlin.gradle.plugin 是一个嵌套的点表达式
            // 我们需要找到最外层的那个
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

    /**
     * 提示添加到 TOML 的修复
     */
    private class AddToTomlFix(
        private val error: CatalogReferenceError.NotDeclared
    ) : LocalQuickFix {

        override fun getFamilyName(): String = "添加到版本目录"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            // 显示提示信息
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
}
