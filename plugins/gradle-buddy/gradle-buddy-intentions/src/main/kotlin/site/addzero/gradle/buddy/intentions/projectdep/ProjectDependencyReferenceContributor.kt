package site.addzero.gradle.buddy.intentions.projectdep

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.ResolveResult
import com.intellij.psi.util.parentOfType
import com.intellij.util.ProcessingContext
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

class ProjectDependencyReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(KtStringTemplateExpression::class.java),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
                    val stringExpression = element as? KtStringTemplateExpression ?: return PsiReference.EMPTY_ARRAY
                    val modulePath = extractProjectPath(stringExpression) ?: return PsiReference.EMPTY_ARRAY
                    return arrayOf(ProjectDependencyStringReference(stringExpression, modulePath))
                }
            }
        )
    }

    private fun extractProjectPath(expression: KtStringTemplateExpression): String? {
        if (expression.entries.isEmpty() || expression.entries.any { it !is KtLiteralStringTemplateEntry }) {
            return null
        }

        val callExpression = expression.parentOfType<KtCallExpression>(true) ?: return null
        if (callExpression.calleeExpression?.text != "project") {
            return null
        }
        if (callExpression.valueArguments.singleOrNull()?.getArgumentExpression() != expression) {
            return null
        }

        val modulePath = expression.entries
            .filterIsInstance<KtLiteralStringTemplateEntry>()
            .joinToString(separator = "") { it.text }
            .trim()

        return modulePath.takeIf { it.startsWith(":") }
    }

    private class ProjectDependencyStringReference(
        element: KtStringTemplateExpression,
        private val modulePath: String
    ) : PsiPolyVariantReferenceBase<KtStringTemplateExpression>(
        element,
        TextRange(1, element.textLength - 1),
        true
    ) {

        override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
            val module = ProjectModuleResolver.findByProjectPath(element.project, modulePath) ?: return emptyArray()
            val psiFile = PsiManager.getInstance(element.project).findFile(module.buildFile) ?: return emptyArray()
            return arrayOf(PsiElementResolveResult(psiFile))
        }

        override fun getVariants(): Array<Any> = emptyArray()
    }
}
