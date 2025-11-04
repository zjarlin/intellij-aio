package com.addzero.util.psi.javaclass

import com.addzero.util.psi.PsiTypeUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.*

/**
 * Java PsiClass 相关工具类
 */
object PsiClassUtil {
    
    fun guessTableName(psiClass: PsiClass): String? {
        val text = psiClass.name?.toUnderlineCase()

        // 获取所有注解
        val guessTableNameByAnno = guessTableNameByAnno(psiClass)

        val firstNonBlank = cn.hutool.core.util.StrUtil.firstNonBlank(guessTableNameByAnno, text)
        return firstNonBlank
    }

    fun guessTableNameByAnno(psiClass: PsiClass): @NlsSafe String? {
        val annotations = psiClass.annotations
        for (annotation in annotations) {
            val qualifiedName = annotation.qualifiedName
            when (qualifiedName) {
                "com.baomidou.mybatisplus.annotation.TableName" -> {
                    // 获取 MyBatis Plus 的 @TableName 注解值
                    val tableNameValue = annotation.findAttributeValue("value")
                    return tableNameValue?.text.extractMarkdownBlockContent()
                }

                "org.babyfish.jimmer.sql.Table" -> {
                    // 获取 Jimmer 的 @Table 注解值
                    val nameValue = annotation.findAttributeValue("name")
                    return nameValue?.text?.extractMarkdownBlockContent()
                }

                "javax.persistence.Table",
                "jakarta.persistence.Table",
                    -> {
                    // 获取 JPA 的 @Table 注解值
                    val nameValue = annotation.findAttributeValue("name")
                    return nameValue?.text?.extractMarkdownBlockContent()
                }
            }
        }
        return null
    }

    fun extractInterfaceMetaInfo(psiClass: PsiClass): List<JavaFieldMetaInfo> {
        val fieldMetaInfoList = mutableListOf<JavaFieldMetaInfo>()

        // 遍历类中所有的方法（接口中的字段会生成 getter 方法）
        val methods = psiClass.methods

        //暂时不对java做外键支持哈

        methods.filter {
            //直接排除集合
            val collectionType = it.returnType?.PsiTypeUtil.isCollectionType()
            val not = collectionType?.not()
            val b = not ?: false
            b

        }.forEach { method ->
            val annotation = method.getAnnotation("org.babyfish.jimmer.sql.JoinColumn")
            if (annotation != null) {
                val column = annotation.findAttributeValue("name")
                val text = column?.text
                val mayColumn = cn.hutool.core.util.StrUtil.firstNonBlank(text, method.name + "Id")

                val comment = getCommentFunByMethod(method)
                val type = String::class.java
                fieldMetaInfoList.add(
                    JavaFieldMetaInfo(
                        name = mayColumn!!.removeAnyQuote(),
                        type = type,
                        genericType = type,
                        comment = comment.removeAnyQuote()
                    )
                )

            }

            val fieldName = method.name
            val returnType = method.returnType!!
            // 获取字段注释
            val comment = getCommentFunByMethod(method)
            // 将属性信息添加到列表中
            val type = getJavaClassFromPsiType(returnType)
            fieldMetaInfoList.add(
                JavaFieldMetaInfo(
                    name = fieldName.removeAnyQuote(),
                    type = type,
                    genericType = type,
                    comment = comment.removeAnyQuote()
                )
            )
        }

        return fieldMetaInfoList
    }

    fun getJavaClassFromPsiType(psiType: PsiType): Class<*> {
        val clazz = psiType.clazz()
        val name = clazz?.name
        if (name.isNullOrBlank()) {
            return String::class.java
        }
        val javaType2RefType = javaType2RefType(name)
        if (javaType2RefType.isNullOrBlank()) {
            return String::class.java
        }
        return com.intellij.util.ReflectionUtil.getClassOrNull(javaType2RefType) ?: String::class.java
    }

    fun getCommentFunByMethod(method: PsiMethod): String {
        val comment = method.docComment?.text ?: ""
        val equals = method.name == SettingContext.settings.id

        // 这里应该是获取方法注释的逻辑，暂时返回 null
        return cleanDocComment(
            if (equals) {
                "主键"
            } else {
                comment
            }
        )
    }

    fun getClassMetaInfo(psiClass: PsiClass): Pair<String, String?> {
        // 获取类名
        val classComment = cleanDocComment(psiClass.docComment?.text)

        // 获取类的注释

        return Pair(classComment, guessTableName(psiClass))
    }

    fun getJavaFieldMetaInfo(psiClass: PsiClass): List<JavaFieldMetaInfo> {
        if (psiClass.isInterface) {
            return extractInterfaceMetaInfo(psiClass)
        }

        val fieldsMetaInfo = mutableListOf<JavaFieldMetaInfo>()

        // 获取所有字段
        val fields = psiClass.allFields.filter { it.isDbField() }.toList()

        for (field in fields) {
            val fieldType = field.type // 字段类型
            //假设字段是集合类型,多半是关联属性

            if (fieldType.PsiTypeUtil.isCollectionType()) {
                //直接排除集合

                continue
            }

            val fieldName = field.name // 字段名称
            val guessColumnName = guessColumnName(field)
            val firstNonBlank = cn.hutool.core.util.StrUtil.firstNonBlank(guessColumnName, fieldName)


            val fieldTypeName = fieldType.presentableText // 可读的类型名称
            // 获取字段注释
            val fieldComment = guessFieldComment(field)

            // 获取字段的类对象
            val typeClass = try {
                val javaType2RefType = javaType2RefType(fieldTypeName)
                com.intellij.util.ReflectionUtil.getClassOrNull(javaType2RefType)
            } catch (e: ClassNotFoundException) {
                Any::class.java // 如果类未找到，使用 Any
            }
            // 创建 JavaFieldMetaInfo 对象并添加到列表
            fieldsMetaInfo.add(
                JavaFieldMetaInfo(
                    firstNonBlank!!.removeAnyQuote(), typeClass, typeClass, fieldComment.removeAnyQuote()
                )
            )
        }
        return fieldsMetaInfo
    }

    private fun guessFieldComment(psiField: PsiField): String {
        if (psiField.name == SettingContext.settings.id) {
            return "主键"
        }
        val annotations = psiField.annotations

        for (annotation in annotations) {
            val qualifiedName = annotation.qualifiedName
            val des = when (qualifiedName) {
                "io.swagger.annotations.ApiModelProperty" -> {
                    annotation.findAttributeValue("value")?.text ?: ""
                }

                "io.swagger.v3.oas.annotations.media.Schema" -> {
                    annotation.findAttributeValue("description")?.text ?: ""
                }

                "com.alibaba.excel.annotation.ExcelProperty" -> {
                    // 获取ExcelProperty注解的value属性
                    val value = annotation.findAttributeValue("value")
                    value?.text
                }

                "cn.idev.excel.annotation.ExcelProperty" -> {
                    // 获取ExcelProperty注解的value属性
                    val value = annotation.findAttributeValue("value")
                    value?.text
                }

                "cn.afterturn.easypoi.excel.annotation.Excel" -> {
                    // 获取Excel注解的name属性
                    annotation.findAttributeValue("name")?.text
                }

                else -> {
                    null
                }
            }
            if (!des.isNullOrBlank()) {
                return des?.removeAnyQuote() ?: ""
            }
        }
        val text = cleanDocComment(psiField.docComment?.text)
        if (cn.hutool.core.util.StrUtil.isBlank(text)) {
            return ""
        }
        return text!!

    }

    private fun cleanDocComment(docComment: String?): String {
        if (docComment == null) return ""

        // 使用正则表达式去除注释符号和多余空格
        val trim = docComment.replace(Regex("""/\*\*?"""), "")  // 去除开头的 /* 或 /**
            .replace(Regex("""\*"""), "")      // 去除行内的 *
            .replace(Regex("""\*/"""), "")     // 去除结尾的 */
            .replace(Regex("""/"""), "")     // 去除结尾的 */
            .replace(Regex("""\n"""), " ")      // 将换行替换为空格
            .replace(Regex("""\s+"""), " ")    // 合并多个空格为一个
            .trim()

        return trim                             // 去除首尾空格
    }

    fun isStaticField(field: PsiField): Boolean {
        val hasModifierProperty = field.hasModifierProperty(PsiModifier.STATIC)
        return hasModifierProperty
    }

    private fun guessColumnName(field: PsiField): String? {
        field.annotations.forEach { annotationEntry ->
            val kotlinFqName = annotationEntry.qualifiedName
            val jimmerColumnRef = "org.babyfish.jimmer.sql.Column"
            val mpColumnRef = "com.baomidou.mybatisplus.annotation.TableField"

            when (kotlinFqName) {

                jimmerColumnRef -> {
                    val annotationValue = AnnotationUtils.getAnnotationValue(annotationEntry, "name")
                    return annotationValue
                }

                mpColumnRef -> {
                    val annotationValue = AnnotationUtils.getAnnotationValue(annotationEntry, "value")
                    return annotationValue
                }

            }
        }
        return null

    }

    fun isJavaPojo(
        element: PsiElement?
    ): Boolean {
        val psiClass = com.intellij.psi.util.PsiTreeUtil.getParentOfType(element, PsiClass::class.java)
        val b = psiClass != null && PsiValidateUtil.isValidTarget(null, psiClass).first
        return b
    }

    fun isJavaPojo(
        editor: Editor?, file: PsiFile?
    ): Boolean {
        val element = getCurrentPsiElement(editor, file)
        val javaPojo = isJavaPojo(element)
        val b1 = file?.language is com.intellij.lang.java.JavaLanguage
        return javaPojo && b1
    }

    private fun getCurrentPsiElement(
        editor: Editor?, file: PsiFile?
    ): PsiElement? {
        if (editor == null || file == null) return null
        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset)
        return element
    }
}

fun PsiField.isDbField(): Boolean {
    val staticField = PsiClassUtil.isStaticField(this)

    val collectionType = PsiTypeUtil.isCollectionType(this)
    return !staticField && !collectionType
}