package site.addzero.composebuddy.features.effectkeys

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtVisitorVoid
import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.smart.intentions.core.SmartInspectionSupport

class EffectKeysInspection : LocalInspectionTool() {
    override fun getDefaultLevel(): HighlightDisplayLevel = HighlightDisplayLevel.WEAK_WARNING

    override fun getGroupDisplayName(): String = ComposeBuddyBundle.message("plugin.name")

    override fun getDisplayName(): String = ComposeBuddyBundle.message("inspection.effect.keys.display")

    override fun getShortName(): String = "ComposeEffectKeysInspection"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : KtVisitorVoid() {
            override fun visitNamedFunction(function: KtNamedFunction) {
                EffectKeysAnalysis.analyze(function).forEach { issue ->
                    SmartInspectionSupport.registerProblem(
                        holder,
                        issue.call.calleeExpression ?: issue.call,
                        ComposeBuddyBundle.message("inspection.effect.keys.description"),
                    )
                }
            }
        }
    }
}
