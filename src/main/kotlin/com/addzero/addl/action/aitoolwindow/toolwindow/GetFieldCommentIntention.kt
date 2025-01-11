//package com.addzero.addl.action.aitoolwindow.toolwindow
//
//import com.intellij.codeInsight.intention.IntentionAction
//import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
//import com.intellij.openapi.application.ApplicationManager
//import com.intellij.openapi.editor.Editor
//import com.intellij.openapi.project.Project
//import com.intellij.openapi.wm.ToolWindowManager
//import com.intellij.psi.PsiElement
//import com.intellij.util.IncorrectOperationException
//import javax.swing.SwingUtilities
//
//class AskAIIntention : PsiElementBaseIntentionAction(), IntentionAction {
//    override fun getText() = "Ask AI about this"
//    override fun getFamilyName() = text
//
//    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
//        return editor != null && (editor.selectionModel.hasSelection() || element.parent != null)
//    }
//
//    @Throws(IncorrectOperationException::class)
//    override fun invoke(project: Project, editor: Editor, element: PsiElement) {
//        // 获取选中的代码
//        val selectedText = if (editor.selectionModel.hasSelection()) {
//            editor.selectionModel.selectedText
//        } else {
//            // 如果没有选中文本，提示用户选择代码块
//            val elementTextRange = element.textRange
//            editor.selectionModel.setSelection(elementTextRange.startOffset, elementTextRange.endOffset)
//            element.text
//        }
//
//        if (selectedText.isNullOrBlank()) return
//
//        // 构造问题文本
//        val questionText = buildString {
//            append("请帮我分析这段代码：\n\n")
//            append("```")
//            // 尝试获取文件类型
//            val fileType = element.containingFile?.fileType?.defaultExtension ?: ""
//            if (fileType.isNotBlank()) {
//                append(fileType)
//            }
//            append("\n")
//            append(selectedText)
//            append("\n```")
//        }
//
//        // 确保在 EDT 线程中执行 UI 操作
//        ApplicationManager.getApplication().invokeLater {
//            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("AI Chat")
//            if (toolWindow != null) {
//                toolWindow.activate {
//                    SwingUtilities.invokeLater {
//                        val chatWindow = AIChatToolWindow.getInstance(project)
//                        chatWindow.setInputText(questionText)
//                    }
//                }
//            }
//        }
//
//
//
//    }
//
//    override fun startInWriteAction(): Boolean = false
//}