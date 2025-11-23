package site.addzero.addl.intention.custom

import site.addzero.addl.intention.AbstractDocCommentAnnotationAction
import site.addzero.addl.settings.SettingContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import site.addzero.util.lsi_impl.impl.psi.psifile.getCurrentPsiElement

class AddCusTomAnnotationJavaAction : AbstractDocCommentAnnotationAction() {
    override fun getAnnotationTemplate(): String {
        return SettingContext.settings.customAnnotation
    }
    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        val currentPsiElement = file.getCurrentPsiElement(editor)
        val toLsiElement = currentPsiElement.toLsiElement()
        val kotlinPojo = isJavaPojo(editor, file)
        return  kotlinPojo
    }

    override fun getAnnotationNames(): List<String> {
        return listOf()

    }

    override fun getText(): String {
        return "Add Custom Annotation for Java"
    }


}
