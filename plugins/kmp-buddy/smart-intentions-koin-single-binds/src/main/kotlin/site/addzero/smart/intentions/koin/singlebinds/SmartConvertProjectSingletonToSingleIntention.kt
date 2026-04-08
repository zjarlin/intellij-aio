package site.addzero.smart.intentions.koin.singlebinds

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import site.addzero.smart.intentions.core.SmartIntentionsMessages

class SmartConvertProjectSingletonToSingleIntention : PsiElementBaseIntentionAction(), IntentionAction {
    override fun getFamilyName(): String {
        return SmartIntentionsMessages.FAMILY_NAME
    }

    override fun getText(): String {
        return SmartIntentionsMessages.CONVERT_PROJECT_KOIN_SINGLETON_TO_SINGLE
    }

    override fun startInWriteAction(): Boolean {
        return false
    }

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val annotation = element.getStrictParentOfType<KtAnnotationEntry>() ?: return false
        return ProjectKoinScopeAnnotationSwapSupport.isApplicable(
            annotation,
            ProjectKoinScopeAnnotationSwapSupport.singletonToSingle,
        )
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val annotation = element.getStrictParentOfType<KtAnnotationEntry>() ?: return
        if (!ProjectKoinScopeAnnotationSwapSupport.isApplicable(
                annotation,
                ProjectKoinScopeAnnotationSwapSupport.singletonToSingle,
            )
        ) {
            return
        }
        ProjectKoinScopeAnnotationSwapSupport.apply(
            project,
            ProjectKoinScopeAnnotationSwapSupport.singletonToSingle,
        )
    }
}
