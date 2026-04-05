package site.addzero.smart.intentions.kotlin.classtointerface

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import site.addzero.smart.intentions.core.SmartIntentionsMessages
import site.addzero.smart.intentions.core.SmartPsiWriteSupport

class SmartConvertClassToInterfaceIntention : PsiElementBaseIntentionAction(), IntentionAction {
    override fun getFamilyName(): String {
        return SmartIntentionsMessages.FAMILY_NAME
    }

    override fun getText(): String {
        return SmartIntentionsMessages.CONVERT_CLASS_TO_INTERFACE
    }

    override fun startInWriteAction(): Boolean {
        return false
    }

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val klass = element.getNonStrictParentOfType<KtClass>() ?: return false
        val caretOffset = editor?.caretModel?.offset ?: element.textOffset
        return ClassToInterfaceSupport.isApplicable(klass, caretOffset)
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val klass = element.getNonStrictParentOfType<KtClass>() ?: return
        val caretOffset = editor?.caretModel?.offset ?: element.textOffset
        if (!ClassToInterfaceSupport.isApplicable(klass, caretOffset)) {
            return
        }
        SmartPsiWriteSupport.runWriteCommand(project, text) {
            ClassToInterfaceSupport.apply(klass)
        }
    }
}
