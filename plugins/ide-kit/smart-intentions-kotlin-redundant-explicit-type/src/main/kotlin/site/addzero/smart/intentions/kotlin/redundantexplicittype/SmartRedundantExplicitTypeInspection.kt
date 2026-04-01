package site.addzero.smart.intentions.kotlin.redundantexplicittype

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtVisitorVoid
import site.addzero.smart.intentions.core.SmartInspectionSupport
import site.addzero.smart.intentions.core.SmartIntentionsMessages

class SmartRedundantExplicitTypeInspection : LocalInspectionTool(), CleanupLocalInspectionTool {
    override fun getDefaultLevel(): HighlightDisplayLevel {
        return HighlightDisplayLevel.WEAK_WARNING
    }

    override fun getShortName(): String {
        return SmartIntentionsMessages.REDUNDANT_EXPLICIT_TYPE_SHORT_NAME
    }

    override fun getDisplayName(): String {
        return SmartIntentionsMessages.REDUNDANT_EXPLICIT_TYPE_DISPLAY_NAME
    }

    override fun getGroupDisplayName(): String {
        return SmartIntentionsMessages.FAMILY_NAME
    }

    override fun getStaticDescription(): String {
        return SmartIntentionsMessages.REDUNDANT_EXPLICIT_TYPE_DESCRIPTION
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : KtVisitorVoid() {
            override fun visitProperty(property: KtProperty) {
                super.visitProperty(property)

                val typeReference = property.typeReference ?: return
                if (!RedundantExplicitTypeSupport.isApplicable(property)) {
                    return
                }

                SmartInspectionSupport.registerProblem(
                    holder,
                    typeReference,
                    SmartIntentionsMessages.REDUNDANT_EXPLICIT_TYPE_DESCRIPTION,
                    SmartRemoveRedundantExplicitTypeQuickFix(property),
                )
            }
        }
    }
}
