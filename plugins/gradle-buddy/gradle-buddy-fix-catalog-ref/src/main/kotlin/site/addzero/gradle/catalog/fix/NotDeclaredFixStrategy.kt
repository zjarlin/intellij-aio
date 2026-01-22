package site.addzero.gradle.catalog.fix

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import site.addzero.gradle.catalog.CatalogReferenceError

/**
 * 修复未声明依赖的策略
 * 适用场景：TOML 中根本没有这个声明
 */
class NotDeclaredFixStrategy : CatalogFixStrategy {

    override fun support(error: CatalogReferenceError): Boolean {
        return error is CatalogReferenceError.NotDeclared
    }

    override fun createFix(project: Project, error: CatalogReferenceError): LocalQuickFix? {
        if (error !is CatalogReferenceError.NotDeclared) return null

        // 尝试找到最相似的声明
        val suggestion = findBestMatch(error.invalidReference, error.availableAliases)

        return if (suggestion != null) {
            // 如果找到相似的，提供替换建议
            object : LocalQuickFix {
                override fun getFamilyName(): String = "修复版本目录引用"

                override fun getName(): String {
                    return "替换为相似的声明 '${error.catalogName}.$suggestion'"
                }

                override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
                    WriteCommandAction.runWriteCommandAction(project) {
                        replaceWithSuggestion(project, error, suggestion)
                    }
                }
            }
        } else {
            // 如果找不到相似的，提供添加到 TOML 的建议
            object : LocalQuickFix {
                override fun getFamilyName(): String = "添加到版本目录"

                override fun getName(): String {
                    return "在 TOML 中添加 '${error.invalidReference}' 的声明"
                }

                override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
                    showAddToTomlDialog(project, error)
                }
            }
        }
    }

    override fun getFixDescription(error: CatalogReferenceError): String {
        if (error !is CatalogReferenceError.NotDeclared) return ""
        return "依赖未声明: '${error.invalidReference}' 在 TOML 中不存在"
    }

    /**
     * 替换为建议的声明
     */
    private fun replaceWithSuggestion(
        project: Project,
        error: CatalogReferenceError.NotDeclared,
        suggestion: String
    ) {
        val oldFullReference = "${error.catalogName}.${error.invalidReference}"
        val newFullReference = "${error.catalogName}.$suggestion"

        // 查找所有 .gradle.kts 文件
        val kotlinFiles = FileTypeIndex.getFiles(
            KotlinFileType.INSTANCE,
            GlobalSearchScope.projectScope(project)
        )

        val psiManager = PsiManager.getInstance(project)

        for (virtualFile in kotlinFiles) {
            if (!virtualFile.name.endsWith(".gradle.kts")) continue

            val psiFile = psiManager.findFile(virtualFile) as? KtFile ?: continue

            // 查找所有匹配的表达式
            val expressions = psiFile.collectDescendantsOfType<KtDotQualifiedExpression> { expr ->
                expr.text == oldFullReference
            }

            // 替换所有匹配的表达式
            val factory = KtPsiFactory(project)
            for (expr in expressions) {
                val newExpr = factory.createExpression(newFullReference)
                expr.replace(newExpr)
            }
        }
    }

    /**
     * 显示添加到 TOML 的对话框
     */
    private fun showAddToTomlDialog(project: Project, error: CatalogReferenceError.NotDeclared) {
        val message = buildString {
            appendLine("依赖 '${error.invalidReference}' 在版本目录中不存在。")
            appendLine()
            appendLine("请手动在 gradle/${error.catalogName}.versions.toml 中添加声明。")
            appendLine()
            appendLine("示例格式：")
            appendLine("[libraries]")
            val tomlKey = error.invalidReference.replace('.', '-')
            appendLine("$tomlKey = { group = \"com.example\", name = \"library\", version = \"1.0.0\" }")
            appendLine()
            appendLine("或")
            appendLine()
            appendLine("[plugins]")
            appendLine("$tomlKey = { id = \"com.example.plugin\", version = \"1.0.0\" }")
        }

        Messages.showInfoMessage(
            project,
            message,
            "添加版本目录声明"
        )
    }

    /**
     * 查找最佳匹配的别名
     * 使用编辑距离算法
     */
    private fun findBestMatch(target: String, candidates: Set<String>): String? {
        if (candidates.isEmpty()) return null

        // 首先尝试精确匹配（忽略大小写）
        val exactMatch = candidates.find { it.equals(target, ignoreCase = true) }
        if (exactMatch != null) return exactMatch

        // 尝试部分匹配
        val partialMatches = candidates.filter { candidate ->
            val targetParts = target.split(".")
            val candidateParts = candidate.split(".")

            // 检查是否有相同的部分
            targetParts.any { targetPart ->
                candidateParts.any { candidatePart ->
                    candidatePart.equals(targetPart, ignoreCase = true)
                }
            }
        }

        if (partialMatches.isNotEmpty()) {
            // 返回编辑距离最小的
            val best = partialMatches.minByOrNull { levenshteinDistance(target, it) }
            // 只有当编辑距离小于目标长度的一半时才返回
            if (best != null && levenshteinDistance(target, best) < target.length / 2) {
                return best
            }
        }

        // 如果没有好的匹配，返回 null
        return null
    }

    /**
     * 计算两个字符串的编辑距离
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length

        val dp = Array(len1 + 1) { IntArray(len2 + 1) }

        for (i in 0..len1) {
            dp[i][0] = i
        }

        for (j in 0..len2) {
            dp[0][j] = j
        }

        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // 删除
                    dp[i][j - 1] + 1,      // 插入
                    dp[i - 1][j - 1] + cost // 替换
                )
            }
        }

        return dp[len1][len2]
    }
}
