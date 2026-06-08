package site.addzero.composebuddy.intentions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.composebuddy.support.ComponentLibrarySupport
import site.addzero.composebuddy.support.MoveComposeComponentWithDependenciesSupport
import site.addzero.composebuddy.support.MoveToSharedSourceSetSupport

class MoveComposeComponentWithDependenciesIntention : PsiElementBaseIntentionAction(), DumbAware {
    override fun getFamilyName(): String = ComposeBuddyBundle.message("plugin.name")

    override fun getText(): String = ComposeBuddyBundle.message("intention.move.component.with.dependencies")

    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val function = element.getStrictParentOfType<KtNamedFunction>() ?: return false
        if (!MoveComposeComponentWithDependenciesSupport.isMovableComponent(function)) {
            return false
        }
        return function.containingKtFile.virtualFile != null &&
            ComponentLibrarySupport.getComponentLibraryRootPath() != null
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val function = element.getStrictParentOfType<KtNamedFunction>() ?: return
        if (!MoveComposeComponentWithDependenciesSupport.isMovableComponent(function)) {
            return
        }
        val ktFile = function.containingKtFile
        ktFile.virtualFile ?: return
        val componentLibraryRootPath = ComponentLibrarySupport.getComponentLibraryRootPath()
        if (componentLibraryRootPath == null) {
            showError(
                project,
                editor,
                ComposeBuddyBundle.message("refactor.move.component.with.dependencies.error.no.library"),
            )
            return
        }
        val plan = MoveComposeComponentWithDependenciesSupport.buildPlan(project, ktFile, componentLibraryRootPath)
        if (plan == null) {
            showError(project, editor, ComposeBuddyBundle.message("refactor.move.component.with.dependencies.error"))
            return
        }
        val movedFileCount = MoveToSharedSourceSetSupport.movePlans(
            project = project,
            plans = plan.movePlans,
            commandName = ComposeBuddyBundle.message("command.move.component.with.dependencies"),
        )
        if (movedFileCount == 0) {
            showError(project, editor, ComposeBuddyBundle.message("refactor.move.component.with.dependencies.error.directory"))
        }
    }

    private fun showError(project: Project, editor: Editor?, message: String) {
        CommonRefactoringUtil.showErrorHint(
            project,
            editor,
            message,
            ComposeBuddyBundle.message("refactor.move.component.with.dependencies.title"),
            null,
        )
    }
}
