package site.addzero.gradle.catalog.fix

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import site.addzero.gradle.catalog.CatalogReferenceError

/**
 * 未声明错误的修复策略
 * 当 TOML 中完全没有对应的声明时：
 * - 如果找到相似的别名，由 SelectCatalogReferenceIntentionGroup 显示上下文菜单
 * - 如果没有找到任何相似的别名，提示用户添加到 TOML
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
            "选择正确的版本目录引用（找到 ${error.suggestedAliases.size} 个候选项）"
        } else {
            "版本目录中未声明 '${error.invalidReference}'，需要添加到 TOML"
        }
    }

    override fun createFix(project: Project, error: CatalogReferenceError): LocalQuickFix? {
        if (error !is CatalogReferenceError.NotDeclared) {
            return null
        }

        // 如果有候选项，由 SelectCatalogReferenceIntentionGroup 处理（上下文菜单）
        // 这里只处理没有候选项的情况
        return if (error.suggestedAliases.isEmpty()) {
            AddToTomlFix(error)
        } else {
            // 返回 null，让 Intention 处理
            null
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
}
