package com.github.zjarlin.autoddl.intention.psipropertyutil

import cn.hutool.core.util.StrUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiField
import com.intellij.psi.codeStyle.CodeStyleManager

object PsiPropertyUtil {

    fun cleanDocComment(
    docComment: String

    ): String {
        return docComment
            .replace(Regex("/\\*\\*?|\"\"\"|\"\"\"?"), "")  // 去除开头的 /* 或 /** 和引号
            .replace(Regex("\\*/"), "")     // 去除结尾的 */
            .replace(Regex("\\*"), "")      // 去除行内的 *
            .replace(Regex("\\s+"), " ")    // 合并多个空格为一个
            .replace("/**", "")
            .replace("*/", "")
            .replace("*", "")
            .trim()
    }
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


    fun String.escapeSpecialCharacters(): String {
        return this
            // 基础转义字符
//            .replace("\\", "\\\\")  // 反斜杠
//            .replace("\"", "\\\"")  // 双引号
//            .replace("\b", "\\b")   // 退格
//            .replace("\n", "\\n")   // 换行
//            .replace("\r", "\\r")   // 回车
//            .replace("\t", "\\t")   // 制表符
            .replace("\u000C", "\\f") // 换页
            .replace("'", "\\'")    // 单引号

            // 正则表达式特殊字符
            .replace(Regex("[\\[\\]{}()^$.|*+?]")) { "\\${it.value}" }

            // Unicode 字符
            .replace(Regex("[\\x00-\\x1F\\x7F]")) { "\\u${it.value[0].code.toString(16).padStart(4, '0')}" }

            // HTML/XML 特殊字符
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("&", "&amp;")

            // SQL 注入防护字符
            .replace(";", "\\;")
            .replace("--", "\\--")
            .replace("/*", "\\/\\*")
            .replace("*/", "*\\/")

            // Shell 特殊字符
            .replace("`", "\\`")
            .replace("$", "\\$")
            .replace("!", "\\!")

            // 其他常见特殊字符
            .replace("%", "\\%")
//            .replace("@", "\\@")
            .replace("#", "\\#")
            .replace("~", "\\~")
            .replace("=", "\\=")
            .replace("+", "\\+")

            // 控制字符
            .replace(Regex("\\p{Cntrl}")) { "\\u${it.value[0].code.toString(16).padStart(4, '0')}" }
    }


}