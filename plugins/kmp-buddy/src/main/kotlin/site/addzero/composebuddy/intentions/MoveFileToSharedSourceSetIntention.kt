package site.addzero.composebuddy.intentions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.jetbrains.kotlin.psi.KtFile
import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.composebuddy.support.MoveToSharedSourceSetSupport

class MoveFileToSharedSourceSetIntention : PsiElementBaseIntentionAction(), DumbAware {
    override fun getFamilyName(): String = ComposeBuddyBundle.message("plugin.name")

    override fun getText(): String = ComposeBuddyBundle.message("intention.move.file.to.shared")

    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val file = (element.containingFile as? KtFile)?.virtualFile ?: return false
        return MoveToSharedSourceSetSupport.buildPlan(file) != null
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val file = (element.containingFile as? KtFile)?.virtualFile ?: return
        val plan = MoveToSharedSourceSetSupport.buildPlan(file)
        if (plan == null) {
            showError(project, editor, ComposeBuddyBundle.message("refactor.move.file.to.shared.error"))
            return
        }
        val movedFileCount = MoveToSharedSourceSetSupport.movePlans(project, listOf(plan))
        if (movedFileCount == 0) {
            showError(project, editor, ComposeBuddyBundle.message("refactor.move.file.to.shared.error.directory"))
        }
    }

    private fun showError(project: Project, editor: Editor?, message: String) {
        CommonRefactoringUtil.showErrorHint(
            project,
            editor,
            message,
            ComposeBuddyBundle.message("refactor.move.file.to.shared.title"),
            null,
        )
    }
}
