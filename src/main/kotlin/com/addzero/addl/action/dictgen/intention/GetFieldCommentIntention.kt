package com.addzero.addl.action.dictgen.intention

import com.addzero.addl.action.aitoolwindow.toolwindow.AIChatToolWindow
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

class AskAIIntention : PsiElementBaseIntentionAction(), IntentionAction {
    override fun getText() = "Ask AI Assistant"
    override fun getFamilyName() = text

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        return editor?.selectionModel?.hasSelection() == true
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val selectedText = editor?.selectionModel?.selectedText ?: return
        AIChatToolWindow.getInstance(project).sendMessage(selectedText)
    }
}