package site.addzero.gradle.buddy.intentions.projectdep

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import site.addzero.gradle.buddy.i18n.GradleBuddyBundle

class PublishProjectDependencyFromSourceIntention : IntentionAction, PriorityAction {

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.NORMAL

    override fun getFamilyName(): String = GradleBuddyBundle.message("common.family.gradle.buddy")

    override fun getText(): String = GradleBuddyBundle.message("intention.publish.project.dependency.from.source")

    override fun startInWriteAction(): Boolean = false

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.Html(
            GradleBuddyBundle.message("intention.publish.project.dependency.from.source.preview")
        )
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        if (editor == null) {
            return false
        }
        return ProjectDependencySourcePublishSupport.findTarget(project, file, editor.caretModel.offset) != null
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        if (editor == null) {
            return
        }
        val target = ProjectDependencySourcePublishSupport.findTarget(project, file, editor.caretModel.offset) ?: return
        ProjectDependencySourcePublishSupport.execute(project, target)
    }
}
