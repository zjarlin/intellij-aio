package com.addzero.util.psi.javaclass

import com.addzero.util.lsi.toLsiField
import com.intellij.openapi.editor.Editor
import com.intellij.psi.*
import site.addzero.util.str.removeAnyQuote

/**
 * Java PsiClass 相关工具类
 */
object PsiClassUtil {



    fun PsiClass.extractInterfaceMetaInfo(): List<JavaFieldMetaInfo> {
        val fieldMetaInfoList = mutableListOf<JavaFieldMetaInfo>()

        // 遍历类中所有的方法（接口中的字段会生成 getter 方法）
        val methods = this.methods

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
            val type = returnType.getJavaClassFromPsiType()
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

        return Pair(classComment, psiClass.guessTableName())
    }

    fun getJavaFieldMetaInfo(psiClass: PsiClass): List<JavaFieldMetaInfo> {
        if (psiClass.isInterface) {
            return psiClass.extractInterfaceMetaInfo()
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
            val guessColumnName = field.toLsiField().columnName
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


    fun PsiElement?.isJavaPojo(): Boolean {
        val psiClass = com.intellij.psi.util.PsiTreeUtil.getParentOfType(this, PsiClass::class.java)
        val b = psiClass != null && PsiValidateUtil.isValidTarget(null, psiClass).first
        return b
    }

    fun PsiFile?.isJavaPojo(
        editor: Editor?
    ): Boolean {
        val element = getCurrentPsiElement(editor)
        val javaPojo = element.isJavaPojo()
        val b1 = this?.language is com.intellij.lang.java.JavaLanguage
        return javaPojo && b1
    }

    private fun PsiFile?.getCurrentPsiElement(
        editor: Editor?
    ): PsiElement? {
        if (editor == null || this == null) return null
        val offset = editor.caretModel.offset
        val element = this.findElementAt(offset)
        return element
    }
}
