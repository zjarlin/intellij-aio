package com.addzero.addl.util.fieldinfo

import cn.hutool.core.util.StrUtil
import com.addzero.addl.autoddlstarter.generator.IDatabaseGenerator.Companion.javaType2RefType
import com.addzero.addl.autoddlstarter.generator.entity.JavaFieldMetaInfo
import com.addzero.addl.ktututil.toUnderlineCase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*

object PsiUtil {

    private fun cleanDocComment(docComment: String?): String {
        if (docComment == null) return ""

        // 使用正则表达式去除注释符号和多余空格
        return docComment
            .replace(Regex("""/\*\*?"""), "")  // 去除开头的 /* 或 /**
            .replace(Regex("""\*"""), "")      // 去除行内的 *
            .replace(Regex("""\*/"""), "")     // 去除结尾的 */
            .replace(Regex("""\n"""), " ")      // 将换行替换为空格
            .replace(Regex("""\s+"""), " ")    // 合并多个空格为一个
            .trim()                             // 去除首尾空格
    }

    private fun getPsiFileFromEditor(editor: Editor, project: Project): PsiFile? {
        val virtualFile1 = editor.virtualFile
        // 获取当前打开文件的 VirtualFile
        val virtualFile: VirtualFile? = FileEditorManager.getInstance(project).getSelectedEditor(virtualFile1)?.file

        // 如果找到了 VirtualFile，则查找对应的 PsiFile
        return virtualFile?.let { PsiManager.getInstance(project).findFile(it) }
    }


    /**
     * @param [psiField]
     * @return [String]
     */
    fun guessFieldComment(psiField: PsiField): String {
        val annotations = psiField.annotations

        for (annotation in annotations) {
            when (annotation.qualifiedName) {
                "io.swagger.annotations.ApiModelProperty" -> {
                    return annotation.findAttributeValue("value")?.text ?: ""
                }

                "io.swagger.v3.oas.annotations.media.Schema" -> {
                    return annotation.findAttributeValue("description")?.text ?: ""
                }
                // 可以添加其他 Swagger 相关的注解
                // "other.swagger.Annotation" -> { ... }
            }

        }
        val text = cleanDocComment(psiField.docComment?.text)
        if (StrUtil.isBlank(text)) {
            return "no comment found,use | java doc |swagger2 | swagger3"
        }
        return text!!

    }


    private fun guessTableName(psiClass: PsiClass): String? {
        val text = psiClass.name?.toUnderlineCase()

        // 获取所有注解
        val annotations = psiClass.annotations

        for (annotation in annotations) {
            when (annotation.qualifiedName) {
                "com.baomidou.mybatisplus.annotation.TableName" -> {
                    // 处理 MyBatis Plus 的 @Table 注解
                    val tableName = annotation.findAttributeValue("value")?.text ?: ""
                    return tableName
                }

                "org.babyfish.jimmer.sql.Table" -> {
                    // 处理 MyBatis Plus 的 @Table 注解
                    val tableName = annotation.findAttributeValue("name")?.text ?: ""
                    return tableName
                }

            }
        }
        return text
    }


    fun getClassMetaInfo(psiClass: PsiClass): Pair<String, String?> {
        // 获取类名
        val classComment = cleanDocComment(psiClass.docComment?.text)

        // 获取类的注释

        return Pair(classComment, guessTableName(psiClass))
    }

    fun getJavaFieldMetaInfo(psiClass: PsiClass): List<JavaFieldMetaInfo> {
        val fieldsMetaInfo = mutableListOf<JavaFieldMetaInfo>()

        // 获取所有字段
        val fields: Array<PsiField> = psiClass.allFields

        for (field in fields) {
            val fieldName = field.name // 字段名称
            val fieldType: PsiType = field.type // 字段类型
            val fieldTypeName = fieldType.presentableText // 可读的类型名称

            // 获取字段注释
            val fieldComment = guessFieldComment(field)

            // 获取字段的类对象
            val typeClass = try {
                val javaType2RefType = javaType2RefType(fieldTypeName)
                Class.forName(javaType2RefType)
            } catch (e: ClassNotFoundException) {
                Any::class.java // 如果类未找到，使用 Any
            }
            // 创建 JavaFieldMetaInfo 对象并添加到列表
            fieldsMetaInfo.add(JavaFieldMetaInfo(fieldName, typeClass, typeClass, fieldComment))
        }
        return fieldsMetaInfo
    }


}