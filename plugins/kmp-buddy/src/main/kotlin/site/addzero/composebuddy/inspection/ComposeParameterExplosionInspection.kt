package site.addzero.composebuddy.inspection

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import site.addzero.composebuddy.ComposeBuddyBundle

class ComposeParameterExplosionInspection : LocalInspectionTool() {
    override fun getDefaultLevel(): HighlightDisplayLevel = HighlightDisplayLevel.DO_NOT_SHOW

    override fun getGroupDisplayName(): String = ComposeBuddyBundle.message("plugin.name")

    override fun getDisplayName(): String = ComposeBuddyBundle.message("inspection.explosion.display")

    override fun getShortName(): String = "ComposeParameterExplosionInspection"

    override fun getStaticDescription(): String = ComposeBuddyBundle.message("inspection.explosion.description")

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return PsiElementVisitor.EMPTY_VISITOR
    }
}
