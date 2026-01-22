package site.addzero.gradle.catalog.fix

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import site.addzero.gradle.catalog.CatalogReferenceError

/**
 * 修复引用格式错误的策略
 * 适用场景：TOML 中有声明，但引用格式不对
 */
class WrongFormatFixStrategy : CatalogFixStrategy {

    override fun support(error: CatalogReferenceError): Boolean {
        return error is CatalogReferenceError.WrongFormat
    }

    override fun createFix(project: Project, error: CatalogReferenceError): LocalQuickFix? {
        if (error !is CatalogReferenceError.WrongFormat) return null

        return object : LocalQuickFix {
            override fun getFamilyName(): String = "修复版本目录引用格式"

            override fun getName(): String {
                return "替换为 '${error.catalogName}.${error.correctReference}'"
            }

            override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
                WriteCommandAction.runWriteCommandAction(project) {
                    replaceAllOccurrences(
                        project,
                        error.catalogName,
                        error.invalidReference,
                        error.correctReference
                    )
                }
            }
        }
    }

    override fun getFixDescription(error: CatalogReferenceError): String {
        if (error !is CatalogReferenceError.WrongFormat) return ""
        return "引用格式错误: '${error.invalidReference}' 应该是 '${error.correctReference}'"
    }

    /**
     * 在整个项目中替换所有相同的错误引用
     */
    private fun replaceAllOccurrences(
        project: Project,
        catalogName: String,
        oldReference: String,
        newReference: String
    ) {
        val oldFullReference = "$catalogName.$oldReference"
        val newFullReference = "$catalogName.$newReference"

        // 查找所有 .gradle.kts 文件
        val kotlinFiles = FileTypeIndex.getFiles(
            KotlinFileType.INSTANCE,
            GlobalSearchScope.projectScope(project)
        )

        val psiManager = com.intellij.psi.PsiManager.getInstance(project)

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
}
