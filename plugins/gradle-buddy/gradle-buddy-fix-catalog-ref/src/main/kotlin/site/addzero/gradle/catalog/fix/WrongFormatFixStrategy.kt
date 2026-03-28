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
import site.addzero.gradle.buddy.i18n.GradleBuddyBundle
import site.addzero.gradle.catalog.CatalogReferenceError
import site.addzero.gradle.catalog.DynamicCatalogReferenceSupport

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
            override fun getFamilyName(): String = GradleBuddyBundle.message("fix.wrong.format")

            override fun getName(): String {
                return "替换为 '${error.correctReference}'"
            }

            override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
                WriteCommandAction.runWriteCommandAction(project) {
                    val dynamicCallInfo = DynamicCatalogReferenceSupport.resolveDynamicCatalogCall(descriptor.psiElement)
                    if (dynamicCallInfo != null) {
                        replaceAllDynamicOccurrences(
                            project = project,
                            catalogName = dynamicCallInfo.catalogName,
                            tableName = dynamicCallInfo.tableName,
                            oldReference = error.invalidReference,
                            newReference = error.correctReference
                        )
                    } else {
                        replaceAllAccessorOccurrences(
                            project = project,
                            catalogName = error.catalogName,
                            oldReference = error.invalidReference,
                            newReference = error.correctReference
                        )
                    }
                }
            }
        }
    }

    override fun getFixDescription(error: CatalogReferenceError): String {
        if (error !is CatalogReferenceError.WrongFormat) return ""
        return "引用格式错误: '${error.invalidReference}' 应该是 '${error.correctReference}'"
    }

    private fun replaceAllAccessorOccurrences(
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

    private fun replaceAllDynamicOccurrences(
        project: Project,
        catalogName: String,
        tableName: String,
        oldReference: String,
        newReference: String
    ) {
        val kotlinFiles = FileTypeIndex.getFiles(
            KotlinFileType.INSTANCE,
            GlobalSearchScope.projectScope(project)
        )

        val psiManager = com.intellij.psi.PsiManager.getInstance(project)
        for (virtualFile in kotlinFiles) {
            if (!virtualFile.name.endsWith(".gradle.kts")) continue

            val psiFile = psiManager.findFile(virtualFile) as? KtFile ?: continue
            val stringExpressions = psiFile.collectDescendantsOfType<org.jetbrains.kotlin.psi.KtStringTemplateExpression> { expression ->
                val callInfo = DynamicCatalogReferenceSupport.resolveDynamicCatalogCall(expression) ?: return@collectDescendantsOfType false
                callInfo.catalogName == catalogName &&
                    callInfo.tableName == tableName &&
                    callInfo.alias == oldReference
            }

            for (expression in stringExpressions) {
                DynamicCatalogReferenceSupport.replaceStringExpression(project, expression, newReference)
            }
        }
    }
}
