package site.addzero.gradle.buddy.intentions.projectdep

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.psi.PsiDocumentManager
import site.addzero.gradle.buddy.i18n.GradleBuddyActionI18n

class CopyProjectDependencyAsMavenAction : AnAction() {

    init {
        syncPresentation()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.PSI_FILE)
            ?: PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
            ?: return
        val target = CopyProjectDependencyAsMavenSupport.findTarget(project, file, editor.caretModel.offset) ?: return
        CopyProjectDependencyAsMavenSupport.execute(project, editor, target)
    }

    override fun update(e: AnActionEvent) {
        syncPresentation(e.presentation)
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        val file = project?.let {
            e.getData(CommonDataKeys.PSI_FILE)
                ?: PsiDocumentManager.getInstance(it).getPsiFile(editor?.document ?: return@let null)
        }

        e.presentation.isEnabledAndVisible = project != null &&
            editor != null &&
            file != null &&
            CopyProjectDependencyAsMavenSupport.findTarget(project, file, editor.caretModel.offset) != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    private fun syncPresentation(presentation: com.intellij.openapi.actionSystem.Presentation? = null) {
        GradleBuddyActionI18n.sync(
            this,
            presentation,
            "action.copy.project.dependency.as.maven.title",
            "action.copy.project.dependency.as.maven.description"
        )
    }
}
