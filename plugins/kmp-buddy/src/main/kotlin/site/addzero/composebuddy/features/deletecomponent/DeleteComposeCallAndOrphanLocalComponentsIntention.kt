package site.addzero.composebuddy.features.deletecomponent

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import site.addzero.composebuddy.ComposeBuddyBundle

class DeleteComposeCallAndOrphanLocalComponentsIntention : PsiElementBaseIntentionAction(), DumbAware {
    override fun getFamilyName(): String = ComposeBuddyBundle.message("plugin.name")

    override fun getText(): String = ComposeBuddyBundle.message("intention.delete.compose.call.and.orphans")

    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        return DeleteComposeCallAndOrphanLocalComponentsAnalysis.analyze(editor, element) != null
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val analysis = DeleteComposeCallAndOrphanLocalComponentsAnalysis.analyze(editor, element) ?: return
        DeleteComposeCallAndOrphanLocalComponentsRefactor(project).apply(analysis)
    }
}
