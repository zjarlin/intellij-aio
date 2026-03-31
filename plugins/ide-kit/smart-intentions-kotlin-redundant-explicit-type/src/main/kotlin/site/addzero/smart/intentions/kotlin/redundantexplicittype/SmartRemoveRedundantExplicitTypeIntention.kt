package site.addzero.smart.intentions.kotlin.redundantexplicittype

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.KtProperty
import site.addzero.smart.intentions.core.AbstractSmartKotlinPropertyIntention
import site.addzero.smart.intentions.core.SmartIntentionsMessages

class SmartRemoveRedundantExplicitTypeIntention : AbstractSmartKotlinPropertyIntention() {
    override fun getText(): String {
        return SmartIntentionsMessages.REMOVE_REDUNDANT_EXPLICIT_TYPE
    }

    override fun isApplicableTo(property: KtProperty, caretOffset: Int): Boolean {
        return RedundantExplicitTypeSupport.isApplicable(property, caretOffset)
    }

    override fun invokeForProperty(project: Project, editor: Editor?, property: KtProperty) {
        RedundantExplicitTypeSupport.apply(property)
    }
}
