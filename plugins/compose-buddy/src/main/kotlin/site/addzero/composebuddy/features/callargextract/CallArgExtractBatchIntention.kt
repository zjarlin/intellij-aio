package site.addzero.composebuddy.features.callargextract

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import site.addzero.composebuddy.ComposeBuddyBundle

class CallArgExtractBatchIntention : PsiElementBaseIntentionAction() {
    override fun getFamilyName(): String = ComposeBuddyBundle.message("plugin.name")

    override fun getText(): String = ComposeBuddyBundle.message("intention.extract.placeholder.call.arguments")

    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        return CallArgExtractAnalysis.analyzeBatch(element) != null
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val analysis = CallArgExtractAnalysis.analyzeBatch(element) ?: return
        CallArgExtractRefactor(project).apply(analysis)
    }
}
