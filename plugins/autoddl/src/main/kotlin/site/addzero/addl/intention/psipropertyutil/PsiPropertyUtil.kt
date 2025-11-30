package site.addzero.addl.intention.psipropertyutil

import cn.hutool.core.util.StrUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiField
import com.intellij.psi.codeStyle.CodeStyleManager

object PsiPropertyUtil {

    fun addPsiJavaAnnotation(
        project: Project, field: PsiField, docComment:
        String
        , annotationTemplate: String
    ) {
        // 清理文档注释
        val description = cleanDocComment(docComment)

        // 获取注解模板并格式化
        val format = StrUtil.format(annotationTemplate, description)
        if (format.isBlank()) {
            return
        }

        // 使用 PsiElementFactory 创建注解
        val factory = JavaPsiFacade.getElementFactory(project)
//        val escapeSpecialCharacters = format.escapeSpecialCharacters()
        try {
            val annotation = factory.createAnnotationFromText(format, field)

            // 将注解添加到字段上方
            field.modifierList?.addBefore(annotation, field.modifierList?.firstChild)
        } catch (e: Exception) {
            return
        }

        // 格式化代码
        CodeStyleManager.getInstance(project).reformat(field)
    }




}
