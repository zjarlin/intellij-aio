package site.addzero.smart.intentions.core

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement

object SmartInspectionSupport {
    fun registerProblem(
        holder: ProblemsHolder,
        element: PsiElement,
        description: String,
        vararg fixes: LocalQuickFix,
    ) {
        holder.registerProblem(element, description, *fixes)
    }
}
