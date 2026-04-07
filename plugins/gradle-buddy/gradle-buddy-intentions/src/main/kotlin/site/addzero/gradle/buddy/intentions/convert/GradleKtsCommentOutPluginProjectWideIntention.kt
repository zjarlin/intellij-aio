package site.addzero.gradle.buddy.intentions.convert

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import site.addzero.gradle.buddy.i18n.GradleBuddyBundle

/**
 * 将当前插件声明在整个仓库内统一注释掉。
 */
class GradleKtsCommentOutPluginProjectWideIntention : IntentionAction, PriorityAction {

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.HIGH

    override fun getFamilyName(): String = GradleBuddyBundle.message("common.family.gradle.buddy")

    override fun getText(): String = GradleBuddyBundle.message("intention.plugin.comment.out.project.wide")

    override fun startInWriteAction(): Boolean = false

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.Html(
            GradleBuddyBundle.message("intention.plugin.comment.out.project.wide.preview")
        )
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        if (editor == null || !GradlePluginCommentSupport.isTargetGradleKtsFile(file)) {
            return false
        }

        val element = file.findElementAt(editor.caretModel.offset) ?: return false
        val targetPlugin = GradlePluginCommentSupport.detectTargetPlugin(element) ?: return false
        val candidates = GradlePluginCommentSupport.collectFileCandidates(file, targetPlugin.pluginId)
        if (candidates.lineNumbers.isNotEmpty()) {
            return true
        }

        return GradlePluginCommentProjectWideSupport
            .collectRewritePlan(project, targetPlugin.pluginId)
            .filePlans
            .isNotEmpty()
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        val element = file.findElementAt(editor?.caretModel?.offset ?: return) ?: return
        val targetPlugin = GradlePluginCommentSupport.detectTargetPlugin(element) ?: return
        GradlePluginCommentProjectWideSupport.runWithConfirmation(project, targetPlugin)
    }
}
