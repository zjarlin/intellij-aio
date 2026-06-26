package site.addzero.smart.intentions.kotlin.inheritbase

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import site.addzero.smart.intentions.core.SmartIntentionsMessages

class SmartInheritFromBaseIntention : PsiElementBaseIntentionAction(), IntentionAction {
    override fun getFamilyName(): String {
        return SmartIntentionsMessages.FAMILY_NAME
    }

    override fun getText(): String {
        return SmartIntentionsMessages.INHERIT_FROM_BASE
    }

    override fun startInWriteAction(): Boolean {
        return false
    }

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        if (editor == null || element.containingFile !is KtFile) {
            return false
        }
        val klass = element.getNonStrictParentOfType<KtClass>() ?: return false
        return InheritFromBaseSupport.isApplicable(klass, editor.caretModel.offset)
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val activeEditor = editor ?: return
        val klass = element.getNonStrictParentOfType<KtClass>() ?: return
        if (!InheritFromBaseSupport.isApplicable(klass, activeEditor.caretModel.offset)) {
            return
        }
        InheritFromBaseSupport.chooseAndApply(project, activeEditor, klass)
    }
}
