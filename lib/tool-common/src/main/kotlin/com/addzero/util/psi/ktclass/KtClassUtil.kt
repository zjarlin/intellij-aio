package com.addzero.util.psi.ktclass

import com.addzero.util.kt_util.isCollectionType
import com.addzero.util.kt_util.isStatic
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtProperty

/**
 * Kotlin KtClass 相关工具类
 */
object KtClassUtil {
    
    fun guessTableName(psiClass: KtClass): String? {
        val text = psiClass.name?.toUnderlineCase()
        // 获取所有注解
        val guessTableNameByAnno = guessTableNameByAnno(psiClass)

        val firstNonBlank = cn.hutool.core.util.StrUtil.firstNonBlank(guessTableNameByAnno, text)
        val removeAny = firstNonBlank?.removeAny("\"")
        return removeAny
    }

    fun guessTableNameByAnno(psiClass: KtClass): String? {
        val toLightClass = psiClass.toLightClass()
        val myTabAnno = toLightClass?.getAnnotation("org.babyfish.jimmer.sql.Table")
        val findAttributeValue = myTabAnno?.findAttributeValue("name")
        val text = findAttributeValue?.text
        val removeAny = text?.removeAny("\"")
        return removeAny
    }

    fun extractInterfaceMetaInfo(psiClass: KtClass): MutableList<JavaFieldMetaInfo> {
        val fieldsMetaInfo = mutableListOf<JavaFieldMetaInfo>()
        // 获取所有字段
        val fields = psiClass?.properties()?.filter { it.isDbField() }

        for (field in fields!!) {
            if (field.isCollectionType()) {
                continue
            }
            val toUnderlineCase = field.name?.toUnderlineCase()

            val fieldType = field.typeReference?.text

            val typeReference = field.typeReference
            val ktFieldTypeName = typeReference?.text
            // 返回 Java 类型的字符串表示形式
            // 获取字段注释
            val fieldComment = field.guessFieldComment()

            // 获取字段的类对象
            val typeClass = try {
                com.intellij.util.ReflectionUtil.getClassOrNull(
                    if (cn.hutool.core.util.StrUtil.isBlank(ktFieldTypeName)) {
                        String::class.java.name
                    } else {
                        ktType2RefType(ktFieldTypeName!!)
                    }
                )

            } catch (e: ClassNotFoundException) {
                Any::class.java // 如果类未找到，使用 Any
            }


            if (field.hasAnnotation("org.babyfish.jimmer.sql.ManyToOne")) {
                val joingColumn = if (field.hasAnnotation("org.babyfish.jimmer.sql.JoinColumn")) {
                    val map = field.annotationEntries.filter { it.shortName?.asString() == "JoinColumn" }.map {
                        val annotationValue1 = AnnotationUtils.getAnnotationValue(it, "name")
                        annotationValue1
                    }.firstOrNull()
                    map
                } else {
                    toUnderlineCase + "Id"
                }


                // 创建 JavaFieldMetaInfo 对象并添加到列表
                val element = JavaFieldMetaInfo(joingColumn!!, typeClass, typeClass, fieldComment)
                fieldsMetaInfo.add(element)
                return fieldsMetaInfo
            }
            val fieldName = field.name // 字段名称
            //这里优先使用数据库列名，如果没有，则使用字段名
            val columnName = guessColumnName(field)
            val finalColumn = cn.hutool.core.util.StrUtil.firstNonBlank(columnName, fieldName)


            // 创建 JavaFieldMetaInfo 对象并添加到列表
            val element = JavaFieldMetaInfo(finalColumn!!, typeClass, typeClass, fieldComment)
            fieldsMetaInfo.add(element)
        }
        return fieldsMetaInfo

    }

    private fun guessColumnName(field: KtProperty): String? {
        val annotationEntries = field.annotationEntries
        val firstOrNull = annotationEntries.filter {
            val shortName = it.shortName
            val asString = shortName?.asString()
            val equals = asString.equals("Column")
            equals
        }.map {

            val annotationValue1 = AnnotationUtils.getAnnotationValue(it, "name")
            annotationValue1
        }.firstOrNull()
        return firstOrNull ?: field.name?.toUnderlineCase()
    }

    fun KtProperty.guessFieldComment(idName: String): String {
        // 如果是主键字段，直接返回 "主键"
        if (this.name == idName) {
            return "主键"
        }
        // 获取 KtProperty 上的所有注解
        val annotations = this.annotationEntries
        // 遍历所有注解
        for (annotation in annotations) {
            val qualifiedName = annotation.shortName?.asString()

            val des = when (qualifiedName) {
                "ApiModelProperty" -> {
                    // 获取 description 参数值
                    annotation.valueArguments.firstOrNull()?.let { arg ->
                        val value = arg.getArgumentExpression()?.text
                        val removeAnyQuote = value?.removeAnyQuote()
                        removeAnyQuote
                    }
                }

                "Schema" -> {
                    // 获取 description 参数值
                    val des =
                        annotation.valueArguments.filter { it.getArgumentName()?.asName?.asString() == "description" }
                            .map { it.getArgumentExpression()?.text }.firstOrNull()
                    des
                }

                "ExcelProperty" -> {
                    // 获取 description 参数值
                    val des = annotation.valueArguments.filter { it.getArgumentName()?.asName?.asString() == "value" }
                        .map { it.getArgumentExpression()?.text }.firstOrNull()
                    des
                }

                else -> {
                    null
                }
            }
            if (!des.isNullOrBlank()) {
                return des?.removeAnyQuote()!!
            }
        }

        // 如果没有找到 Swagger 注解，则尝试获取文档注释
        val docComment = this.docComment
        val text = cleanDocComment(docComment?.text)
        if (text.isNullOrBlank()) {
            return ""
        }

        return text
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

    fun getClassMetaInfo4KtClass(psiClass: KtClass): Pair<String, String?> {
        // 获取类名
        val classComment = cleanDocComment(psiClass.docComment?.text)
        // 获取类的注释
        return Pair(classComment, guessTableName(psiClass))
    }

    fun isKotlinPojo(
        element: PsiElement?
    ): Boolean {
        // 检查是否为Kotlin文件
        val ktClass = com.intellij.psi.util.PsiTreeUtil.getParentOfType(element, KtClass::class.java)
        if (ktClass != null) {
            val first = PsiValidateUtil.isValidTarget(ktClass, null).first
            return first
        }
        return false
    }

    fun isKotlinPojo(
        editor: Editor?, file: PsiFile?
    ): Boolean {
        // 检查是否为Kotlin文件
        val element = getCurrentPsiElement(editor, file)
        val kotlinPojo = isKotlinPojo(element)
        val b = file?.language is KotlinLanguage
        return kotlinPojo && b
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

fun KtProperty.isDbField(): Boolean {
    val staticField = this.isStatic()
    val collectionType = this.isCollectionType()
    return !staticField && !collectionType
}