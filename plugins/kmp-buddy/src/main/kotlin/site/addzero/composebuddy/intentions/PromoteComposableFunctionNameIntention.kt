package site.addzero.composebuddy.intentions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.RenameProcessor
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.composebuddy.support.ComposePsiSupport

class PromoteComposableFunctionNameIntention : PsiElementBaseIntentionAction(), DumbAware {
    override fun getFamilyName(): String = ComposeBuddyBundle.message("plugin.name")

    override fun getText(): String = ComposeBuddyBundle.message("intention.promote.composable.name")

    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val function = targetFunction(element) ?: return false
        if (!ComposePsiSupport.isComposable(function)) {
            return false
        }
        return promotedName(function) != null
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val function = targetFunction(element) ?: return
        val newName = promotedName(function) ?: return
        RenameProcessor(
            project,
            function,
            newName,
            false,
            false,
        ).run()
    }

    private fun targetFunction(element: PsiElement): KtNamedFunction? {
        return (element as? KtNamedFunction) ?: element.getStrictParentOfType<KtNamedFunction>()
    }

    private fun promotedName(function: KtNamedFunction): String? {
        val currentName = function.name ?: return null
        val firstChar = currentName.firstOrNull() ?: return null
        if (!firstChar.isLowerCase()) {
            return null
        }
        val renamed = currentName.replaceFirstChar { char ->
            if (char.isLowerCase()) {
                char.titlecase()
            } else {
                char.toString()
            }
        }
        if (renamed == currentName) {
            return null
        }
        return renamed
    }
}
