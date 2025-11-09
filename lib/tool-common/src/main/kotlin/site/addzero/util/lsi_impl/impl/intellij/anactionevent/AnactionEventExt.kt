package site.addzero.util.lsi_impl.impl.intellij.anactionevent

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

fun AnActionEvent.getEditor(): Editor? {
    val editor = this.dataContext.getData(CommonDataKeys.EDITOR)
    return editor
}

fun AnActionEvent.getPsiFile(): PsiFile? {
    val editor = this.dataContext.getData(CommonDataKeys.PSI_FILE)
    return editor
}
