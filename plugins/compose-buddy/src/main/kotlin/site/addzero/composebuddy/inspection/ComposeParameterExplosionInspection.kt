package site.addzero.composebuddy.inspection

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtVisitorVoid
import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.composebuddy.analysis.ComposeFunctionSupport
import site.addzero.composebuddy.quickfix.NormalizeComposeSignatureQuickFix
import site.addzero.smart.intentions.core.SmartInspectionSupport

class ComposeParameterExplosionInspection : LocalInspectionTool() {
    override fun getDefaultLevel(): HighlightDisplayLevel = HighlightDisplayLevel.WEAK_WARNING

    override fun getGroupDisplayName(): String = ComposeBuddyBundle.message("plugin.name")

    override fun getDisplayName(): String = ComposeBuddyBundle.message("inspection.explosion.display")

    override fun getShortName(): String = "ComposeParameterExplosionInspection"

    override fun getStaticDescription(): String = ComposeBuddyBundle.message("inspection.explosion.description")

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : KtVisitorVoid() {
            override fun visitNamedFunction(function: KtNamedFunction) {
                super.visitNamedFunction(function)
                val analysis = ComposeFunctionSupport.analyzeSignature(function) ?: return
                if (!ComposeFunctionSupport.hasNormalizationOpportunity(function.project, analysis)) return
                SmartInspectionSupport.registerProblem(
                    holder,
                    function.nameIdentifier ?: function,
                    ComposeBuddyBundle.message("inspection.explosion.description"),
                    NormalizeComposeSignatureQuickFix(),
                )
            }
        }
    }
}
