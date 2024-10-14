package com.addzero.addl.util.fieldinfo

import cn.hutool.core.util.ClassUtil
import cn.hutool.core.util.StrUtil
import com.addzero.addl.autoddlstarter.generator.IDatabaseGenerator.Companion.javaType2RefType
import com.addzero.addl.autoddlstarter.generator.IDatabaseGenerator.Companion.ktType2RefType
import com.addzero.addl.autoddlstarter.generator.entity.JavaFieldMetaInfo
import com.addzero.addl.ktututil.toUnderlineCase
import com.intellij.psi.*
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtProperty

private const val NOCOMMENT = "no_comment_found"


object PsiUtil {

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


    fun guessFieldComment(ktProperty: KtProperty): String {
        if (ktProperty.name=="id") {
            return "主键"
        }
        // 获取 KtProperty 上的所有注解
        val annotations = ktProperty.annotationEntries
        // 遍历所有注解
        for (annotation in annotations) {
            // 获取注解的全名
            val qualifiedName = annotation.name

            // 根据注解的全名进行不同的处理
            when (qualifiedName) {
                "io.swagger.annotations.ApiModelProperty" -> {
                    // 获取注解的 value 属性
                    return ""
                }

                "io.swagger.v3.oas.annotations.media.Schema" -> {
                    // 获取注解的 description 属性
                    return ""

                }
            }
        }

        // 如果没有找到 Swagger 注解，则尝试获取 文档注释
        val docComment = ktProperty.docComment
        val text = cleanDocComment(docComment?.text)

        if (text.isNullOrBlank()) {
            return NOCOMMENT
        }

        return text!!
    }


    /**
     * @param [psiField]
     * @return [String]
     */
    fun guessFieldComment(psiField: PsiField): String {
        if (psiField.name=="id") {
            return "主键"
        }
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
            return NOCOMMENT
        }
        return text!!

    }


    fun guessTableName(psiClass: PsiClass): String? {
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


    fun extractInterfaceMetaInfo(psiClass: PsiClass): List<JavaFieldMetaInfo> {
        val fieldMetaInfoList = mutableListOf<JavaFieldMetaInfo>()

        // 遍历类中所有的方法（接口中的字段会生成 getter 方法）
        val methods = psiClass.methods

        methods.forEach { method ->
            // 判断是否是 getter 方法
            val fieldName = method.name

            val returnType = method.returnType!!

            // 获取字段注释
            val comment = getCommentFunByMethod(method)

            // 将属性信息添加到列表中
            val type = getJavaClassFromPsiType(returnType)
            fieldMetaInfoList.add(
                JavaFieldMetaInfo(
                    name = fieldName,
                    type = type,
                    genericType = type,
                    comment = comment
                )
            )
        }

        return fieldMetaInfoList
    }

    fun getJavaClassFromPsiType(psiType: PsiType): Class<*> {
        val clazz = psiType.clazz()
        val name = clazz?.name
        val javaType2RefType = javaType2RefType(name!!)
        return ClassUtil.loadClass<Any>(javaType2RefType) ?: String::class.java
    }

    fun getCommentFunByMethod(method: PsiMethod): String {
        val comment = method.docComment?.text ?: NOCOMMENT
        val equals = method.name == "id"

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
                ClassUtil.loadClass<Any>(javaType2RefType)
            } catch (e: ClassNotFoundException) {
                Any::class.java // 如果类未找到，使用 Any
            }
            // 创建 JavaFieldMetaInfo 对象并添加到列表
            fieldsMetaInfo.add(JavaFieldMetaInfo(fieldName, typeClass, typeClass, fieldComment))
        }
        return fieldsMetaInfo
    }

    fun guessTableName(psiClass: KtClass): String? {
        val text = psiClass.name?.toUnderlineCase()
        // 获取所有注解
        val annotations = psiClass.annotations

        for (annotation in annotations) {

            when (annotation.name) {
                "com.baomidou.mybatisplus.annotation.TableName" -> {
                    // 处理 MyBatis Plus 的 @Table 注解
//                    val tableName = annotation.findAttributeValue("value")?.text ?: ""
//                    return tableName
                    return ""
                }

                "org.babyfish.jimmer.sql.Table" -> {
                    // 处理 MyBatis Plus 的 @Table 注解
//                    val tableName = annotation.findAttributeValue("name")?.text ?: ""
//                    return tableName
                    return ""
                }

            }
        }
        return text

    }


    fun getJavaFieldMetaInfo4KtClass(psiClass: KtClass): MutableList<JavaFieldMetaInfo> {
        val fieldsMetaInfo = mutableListOf<JavaFieldMetaInfo>()
        // 获取所有字段
        val fields = psiClass?.properties()
        for (field in fields!!) {
            val fieldName = field.name // 字段名称
            val typeReference = field.typeReference
            val ktFieldTypeName = typeReference?.text
            // 返回 Java 类型的字符串表示形式
            // 获取字段注释
            val fieldComment = guessFieldComment(field)

            // 获取字段的类对象
            val typeClass = try {
//                val javaType2RefType = javaType2RefType(ktFieldTypeName!!)

                ClassUtil.loadClass<Any>(
                    if (StrUtil.isBlank(ktFieldTypeName)) {
                        String::class.java.name
                    } else {
                        ktType2RefType(ktFieldTypeName!!)
                    }
                )

            } catch (e: ClassNotFoundException) {
                Any::class.java // 如果类未找到，使用 Any
            }
            // 创建 JavaFieldMetaInfo 对象并添加到列表
            fieldsMetaInfo.add(JavaFieldMetaInfo(fieldName!!, typeClass, typeClass, fieldComment))
        }
        return fieldsMetaInfo

    }


    fun getClassMetaInfo4KtClass(psiClass: KtClass): Pair<String, String?> {
        // 获取类名
        val classComment = cleanDocComment(psiClass.docComment?.text)
        // 获取类的注释
        return Pair(classComment, guessTableName(psiClass))

    }


}