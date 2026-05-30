package site.addzero.smart.intentions.kotlin.shortenqualifiedname

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtFile
import site.addzero.smart.intentions.core.SmartIntentionsMessages

class SmartShortenProjectQualifiedNamesIntention : PsiElementBaseIntentionAction(), IntentionAction {
    override fun getFamilyName(): String {
        return SmartIntentionsMessages.FAMILY_NAME
    }

    override fun getText(): String {
        return SmartIntentionsMessages.SHORTEN_PROJECT_QUALIFIED_NAMES
    }

    override fun startInWriteAction(): Boolean {
        return false
    }

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        return element.containingFile is KtFile
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        if (element.containingFile !is KtFile) {
            return
        }
        ProjectShortenQualifiedNameSupport.apply(project)
    }
}
