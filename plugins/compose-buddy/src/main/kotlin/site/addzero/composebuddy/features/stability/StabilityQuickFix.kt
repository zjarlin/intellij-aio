package site.addzero.composebuddy.features.stability

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import site.addzero.composebuddy.ComposeBuddyBundle

class StabilityQuickFix : LocalQuickFix {
    override fun getFamilyName(): String = ComposeBuddyBundle.message("quickfix.stabilize.parameters")

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val parameter = descriptor.psiElement as? KtParameter ?: return
        val issue = StabilityAnalysis.analyze(parameter.getStrictParentOfType<KtNamedFunction>() ?: return)
            .firstOrNull { it.parameter == parameter } ?: return
        val factory = KtPsiFactory(project)
        val name = parameter.name ?: return
        parameter.replace(factory.createParameter("$name: ${issue.replacementType}"))
    }
}
