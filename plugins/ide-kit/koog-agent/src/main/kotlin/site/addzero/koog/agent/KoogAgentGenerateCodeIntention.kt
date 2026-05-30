package site.addzero.koog.agent

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiElement

class KoogAgentGenerateCodeIntention : PsiElementBaseIntentionAction(), IntentionAction {
    override fun getFamilyName(): String {
        return "ide-kit"
    }

    override fun getText(): String {
        return "(ide-kit) 根据注释生成代码"
    }

    override fun startInWriteAction(): Boolean {
        return false
    }

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val activeEditor = editor ?: return false
        return project.service<KoogAgentCommentGenerationService>()
            .canGenerateAt(activeEditor.document, activeEditor.caretModel.offset)
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val activeEditor = editor ?: return
        val contextScope = chooseContextScope(project) ?: return
        project.service<KoogAgentCommentGenerationService>()
            .generateAt(activeEditor.document, activeEditor.caretModel.offset, contextScope)
    }

    private fun chooseContextScope(project: Project): KoogAgentContextScope? {
        return when (
            Messages.showDialog(
                project,
                "选择发送给 AI 的上下文范围。",
                "(ide-kit) 根据注释生成代码",
                arrayOf("就近上下文", "整个文件", "取消"),
                0,
                Messages.getQuestionIcon(),
            )
        ) {
            0 -> KoogAgentContextScope.NEARBY
            1 -> KoogAgentContextScope.FULL_FILE
            else -> null
        }
    }
}
