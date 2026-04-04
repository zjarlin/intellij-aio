package site.addzero.composebuddy.inspection

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtVisitorVoid
import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.composebuddy.analysis.ComposeFunctionSupport
import site.addzero.composebuddy.quickfix.ExpandWrapperSignatureQuickFix
import site.addzero.composebuddy.quickfix.GenerateWrapperPropsQuickFix
import site.addzero.smart.intentions.core.SmartInspectionSupport

class ComposeWrapperInspection : LocalInspectionTool() {
    override fun getDefaultLevel(): HighlightDisplayLevel = HighlightDisplayLevel.WEAK_WARNING

    override fun getGroupDisplayName(): String = ComposeBuddyBundle.message("plugin.name")

    override fun getDisplayName(): String = ComposeBuddyBundle.message("inspection.wrapper.display")

    override fun getShortName(): String = "ComposeWrapperInspection"

    override fun getStaticDescription(): String = ComposeBuddyBundle.message("inspection.wrapper.description")

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : KtVisitorVoid() {
            override fun visitNamedFunction(function: KtNamedFunction) {
                super.visitNamedFunction(function)
                val analysis = ComposeFunctionSupport.analyzeWrapper(function) ?: return
                SmartInspectionSupport.registerProblem(
                    holder,
                    function.nameIdentifier ?: function,
                    ComposeBuddyBundle.message("inspection.wrapper.description"),
                    ExpandWrapperSignatureQuickFix(),
                    GenerateWrapperPropsQuickFix(),
                )
            }
        }
    }
}
