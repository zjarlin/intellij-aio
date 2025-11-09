package com.addzero.util.psi

import com.addzero.util.entity.JavaFieldMetaInfo
import com.addzero.util.lsi.impl.psi.isDbField
import com.addzero.util.lsi.impl.psi.toLsiField
import com.addzero.util.lsi.impl.kt.isDbField as ktIsDbField
import com.addzero.util.lsi.impl.kt.toLsiField as ktToLsiField
import com.addzero.util.psi.PsiTypeUtil.isCollectionType
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import org.apache.commons.lang3.AnnotationUtils
import org.apache.commons.lang3.StringUtils.firstNonBlank
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.SettingContext
import site.addzero.util.str.removeAny
import site.addzero.util.str.removeAnyQuote
import site.addzero.util.str.toUnderLineCase


//private const val NOCOMMENT = "no_comment_found"
private const val NOCOMMENT = ""

/**
 * PSI 工具类
 *
 * @deprecated 此类中的大部分方法已经迁移到 LSI 层，请使用 LSI 相关方法替代
 * - 元数据提取相关方法已迁移到 LsiClass、LsiField、LsiMethod
 * - 保留的方法包括：PSI 导航、上下文管理、JavaFieldMetaInfo 桥接
 */
@Deprecated("Most jimmerProperty migrated to LSI layer, use LsiClass/LsiField/LsiMethod instead")
object PsiUtil {

    fun PsiFile?.getCurrentPsiElement(
        editor: Editor?
    ): PsiElement? {
        if (editor == null || this == null) return null
        val offset = editor.caretModel.offset
        val element = this.findElementAt(offset)
        return element
    }

    fun PsiFile.getQualifiedClassName(): String? {
        val fileNameWithoutExtension = this.virtualFile.nameWithoutExtension
        val packageName = when (this) {
            is KtFile -> this.packageFqName.asString()
            is PsiJavaFile -> this.packageName
            else -> null
        }
        return if (packageName != null) {
            "$packageName.$fileNameWithoutExtension"
        } else {
            fileNameWithoutExtension
        }
    }

    fun PsiFile?.getPackagePath(): String? {
        val qualifiedClassName = this!!.getQualifiedClassName()
        return qualifiedClassName
    }


    /**
     * 清理文档注释
     *
     * @deprecated 此方法已在 LSI 的各个 CommentAnalyzer 中实现，不再需要直接调用
     */
    @Deprecated(
        "Use LsiField.fieldComment or LsiMethod.fieldComment instead",
        ReplaceWith("lsiField.fieldComment", "com.addzero.util.lsi.toLsiField")
    )
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


    fun addComment(project: Project, field: PsiField) {
        // 创建新的文档注释
        val factory = PsiElementFactory.getInstance(project)
        val newDocComment = factory.createDocCommentFromText("/** */")
        field.addBefore(newDocComment, field.firstChild)
    }


    /**
     * 猜测 Kotlin 字段注释
     *
     * @deprecated 使用 LsiField.fieldComment 替代
     */
    @Deprecated(
        "Use LsiField.fieldComment instead",
        ReplaceWith("this.toLsiField().fieldComment", "com.addzero.util.lsi.toLsiField")
    )
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
            if (des.isNotBlank()) {
                return des?.removeAnyQuote()!!
            }
        }

        // 如果没有找到 Swagger 注解，则尝试获取文档注释
        val docComment = this.docComment
        val text = cleanDocComment(docComment?.text)
        if (text.isBlank()) {

//            尝试ai翻译
            val name = this.name!!
//            AiUtil.INIT(name, """
//               我给出字段名(可能),你推测其含义,作为字段的注释,你的返回应该只有纯粹简洁的注释,不要返回其他内容,不要返回原有字段名
//            """.trimIndent()).ask("","")
            return ""
        }

        return text
    }

    /**
     * 猜测 Java 字段注释
     *
     * @deprecated 使用 LsiField.fieldComment 替代
     */
    @Deprecated(
        "Use LsiField.fieldComment instead",
        ReplaceWith("this.toLsiField().fieldComment", "com.addzero.util.lsi.toLsiField")
    )
    fun PsiField.guessFieldComment(idName: String): String {
        if (this.name == idName) {
            return "主键"
        }
        val annotations = this.annotations

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
            if (des.isNotBlank()) {
                return des?.removeAnyQuote() ?: ""
            }
        }
        val text = cleanDocComment(this.docComment?.text)
        if (text.isBlank()) {
            return NOCOMMENT
        }
        return text

    }

    /**
     * 猜测 PsiClass 的表名
     *
     * @deprecated 使用 LsiClass.guessTableEnglishName 替代
     */
    @Deprecated(
        "Use LsiClass.guessTableEnglishName instead",
        ReplaceWith("this.toLsiClass().guessTableEnglishName", "com.addzero.util.lsi.toLsiClass")
    )
    fun guessTableName(psiClass: PsiClass): String? {
        val text = psiClass.name?.toUnderLineCase()

        // 获取所有注解
        val guessTableNameByAnno = guessTableNameByAnno(psiClass)

//        todo cleancode
//        showInfoMsg("guessTableNameOrNull: $guessTableNameOrNull")

        val firstNonBlank = firstNonBlank(guessTableNameByAnno, text)
        return firstNonBlank

    }

    /**
     * 从注解中猜测表名
     *
     * @deprecated 此方法已在 TableNameAnalyzer 中实现，使用 LsiClass.guessTableEnglishName 替代
     */
    @Deprecated(
        "Use LsiClass.guessTableEnglishName instead",
        ReplaceWith("this.toLsiClass().guessTableEnglishName", "com.addzero.util.lsi.toLsiClass")
    )
    fun guessTableNameByAnno(psiClass: PsiClass): @NlsSafe String? {
    }


    fun extractInterfaceMetaInfo(psiClass: PsiClass): List<JavaFieldMetaInfo> {
        val fieldMetaInfoList = mutableListOf<JavaFieldMetaInfo>()

        // 遍历类中所有的方法（接口中的字段会生成 getter 方法）
        val methods = psiClass.methods

        //暂时不对java做外键支持哈

        methods.filter {
            //直接排除集合
            val collectionType = it.returnType?.isCollectionType()
            val not = collectionType?.not()
            val b = not ?: false
            b

        }.forEach { method ->
            val annotation = method.getAnnotation("org.babyfish.jimmer.sql.JoinColumn")
            if (annotation != null) {
                val column = annotation.findAttributeValue("name")
                val text = column?.text
                val mayColumn = StrUtil.firstNonBlank(text, method.name + "Id")

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
        if (name.isBlank()) {
//            DialogUtil.showWarningMsg("暂不支持的PsiClass类型$name,隐式转为String")
            return String::class.java
        }
        val javaType2RefType = javaType2RefType(name!!)
        if (javaType2RefType.isBlank()) {
//            DialogUtil.showWarningMsg("暂不支持的PsiClass类型$name,隐式转为String")
            return String::class.java
        }
        return ClassUtil.loadClass<Any>(javaType2RefType) ?: String::class.java
    }

    /**
     * 获取方法的注释
     *
     * @deprecated 使用 LsiMethod.fieldComment 替代
     */
    @Deprecated(
        "Use LsiMethod.fieldComment instead",
        ReplaceWith("this.toLsiMethod().fieldComment", "com.addzero.util.lsi.toLsiMethod")
    )
    fun getCommentFunByMethod(method: PsiMethod): String {
        val comment = method.docComment?.text ?: NOCOMMENT
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

    /**
     * 获取类的元信息
     *
     * @deprecated 使用 LsiClass.fieldComment 和 LsiClass.guessTableEnglishName 替代
     */
    @Deprecated(
        "Use LsiClass.fieldComment and LsiClass.guessTableEnglishName instead",
        ReplaceWith("Pair(psiClass.toLsiClass().fieldComment, psiClass.toLsiClass().guessTableEnglishName)", "com.addzero.util.lsi.toLsiClass")
    )
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

            if (fieldType.isCollectionType()) {
                //直接排除集合

                continue
            }

            val fieldName = field.name // 字段名称
            val guessColumnName = field.toLsiField().columnName
            val firstNonBlank = StrUtil.firstNonBlank(guessColumnName, fieldName)


            val fieldTypeName = fieldType.presentableText // 可读的类型名称
            // 获取字段注释
            val fieldComment = field.guessFieldComment()

            // 获取字段的类对象
            val typeClass = try {
                val javaType2RefType = javaType2RefType(fieldTypeName)
                ClassUtil.loadClass<Any>(javaType2RefType)
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


    /**
     * 猜测 KtClass 的表名
     *
     * @deprecated 使用 LsiClass.guessTableEnglishName 替代
     */
    @Deprecated(
        "Use LsiClass.guessTableEnglishName instead",
        ReplaceWith("this.toLsiClass().guessTableEnglishName", "com.addzero.util.lsi.toLsiClass")
    )
    fun guessTableName(psiClass: KtClass): String? {
        val text = psiClass.name?.toUnderlineCase()
        // 获取所有注解
        val guessTableNameByAnno = guessTableNameByAnno(psiClass)

        //todo cleancode
//        showInfoMsg("guessTableNameOrNull: $guessTableNameOrNull")

        val firstNonBlank = StrUtil.firstNonBlank(guessTableNameByAnno, text)
        val removeAny = firstNonBlank?.removeAny("\"")
        return removeAny

    }

    /**
     * 从注解中猜测 KtClass 的表名
     *
     * @deprecated 使用 LsiClass.guessTableEnglishName 替代
     */
    @Deprecated(
        "Use LsiClass.guessTableEnglishName instead",
        ReplaceWith("this.toLsiClass().guessTableEnglishName", "com.addzero.util.lsi.toLsiClass")
    )
    fun guessTableNameByAnno(psiClass: KtClass): String? {
        val toLightClass = psiClass.toLightClass()
        val myTabAnno = toLightClass?.getAnnotation("org.babyfish.jimmer.sql.Table")
        val findAttributeValue = myTabAnno?.findAttributeValue("name")
        val text = findAttributeValue?.text
        val removeAny = text?.removeAny("\"")
        return removeAny
//        val annotations = psiClass.annotationEntries
//        for (myanno in annotations) {
//
//            val kotlinFqName = myanno.shortName.asString()
//            when (kotlinFqName) {
//                "com.baomidou.mybatisplus.annotation.TableName" -> {
//                    // 获取 MyBatis Plus 的 @TableName 注解值
//                    return myanno.valueArguments.firstOrNull()?.getArgumentExpression()?.text?.replace("\"", "")
//                }
//
//                "org.babyfish.jimmer.sql.Table" -> {
//                    // 获取 Jimmer 的 @Table 注解值
//                    return myanno.valueArguments.find {
//                        (it.getArgumentName()?.asName?.asString() ?: "value") == "name"
//                    }?.getArgumentExpression()?.text?.replace("\"", "")
//                }
//
//                "javax.persistence.Table",
//                "jakarta.persistence.Table",
//                    -> {
//                    // 获取 JPA 的 @Table 注解值
//                    return myanno.valueArguments.find {
//                        (it.getArgumentName()?.asName?.asString() ?: "name") == "name"
//                    }?.getArgumentExpression()?.text?.replace("\"", "")
//                }
//            }
//        }
//        return null
    }


    fun extractInterfaceMetaInfo(psiClass: KtClass): MutableList<JavaFieldMetaInfo> {
        val fieldsMetaInfo = mutableListOf<JavaFieldMetaInfo>()
        // 获取所有字段
        val fields = psiClass?.properties()?.filter { it.ktIsDbField() }

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


            if (field.ktToLsiField().hasAnnotation("org.babyfish.jimmer.sql.ManyToOne")) {
                val joingColumn = if (field.ktToLsiField().hasAnnotation("org.babyfish.jimmer.sql.JoinColumn")) {
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
            val columnName = field.ktToLsiField().columnName
            val finalColumn = StrUtil.firstNonBlank(columnName, fieldName)


            // 创建 JavaFieldMetaInfo 对象并添加到列表
            val element = JavaFieldMetaInfo(finalColumn!!, typeClass, typeClass, fieldComment)
            fieldsMetaInfo.add(element)
        }
        return fieldsMetaInfo

    }


//    fun guessColumnName(field: KtProperty): String? {
//
//
//        field.annotationEntries.forEach { annotationEntry ->
//            val kotlinFqName = annotationEntry.typeReference?.text
//            val jimmerColumnRef = "org.babyfish.jimmer.sql.Column"
//            val mpColumnRef = "com.baomidou.mybatisplus.annotation.TableField"
//
//            when (kotlinFqName) {
//
//                jimmerColumnRef -> {
//                    val annotationValue = AnnotationUtils.getAnnotationValue(annotationEntry, "name")
//                    return annotationValue
//                }
//
//                mpColumnRef -> {
//                    val annotationValue = AnnotationUtils.getAnnotationValue(annotationEntry, "value")
//                    return annotationValue
//                }
//
//            }
//        }
//        return null
//    }


    data class PsiCtx(
        val editor: Editor?,
        val psiClass: PsiClass?,
        val ktClass: KtClass? = null,
        val psiFile: PsiFile?,
        val virtualFile: VirtualFile,
        val any: Array<PsiClass>?,

        )


    fun allpsiCtx(project: Project): PsiCtx {
        // 获取所有 Kotlin 文件
        val files = FileTypeIndex.getFiles(KotlinFileType.INSTANCE, GlobalSearchScope.projectScope(project))
        val files1 = FileTypeIndex.getFiles(JavaFileType.INSTANCE, GlobalSearchScope.projectScope(project))

        files.map {
            val psiFile = PsiManager.getInstance(project).findFile(it)
            if (psiFile is KtFile) {
                val toList = psiFile.declarations.filterIsInstance<KtClass>().toList()
            }
        }
        return TODO("提供返回值")
    }

    fun isJavaPojo(
        element: PsiElement?
    ): Boolean {
        val psiClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java)
        val b = psiClass != null && PsiValidateUtil.isValidTarget(null, psiClass).first
        return b
    }

    fun isJavaPojo(
        editor: Editor?, file: PsiFile?
    ): Boolean {
        val element = file.getCurrentPsiElement(editor)
        val javaPojo = isJavaPojo(element)
        val b1 = file?.language is JavaLanguage
        return javaPojo && b1
    }


    fun isKotlinPojo(
        element: PsiElement?
    ): Boolean {
        // 检查是否为Kotlin文件
        val ktClass = PsiTreeUtil.getParentOfType(element, KtClass::class.java)
        if (ktClass != null) {
            val first = PsiValidateUtil.isValidTarget(ktClass, null).first
            return first
        }
        return false
    }






    /**
     * 获取 KtClass 的元信息
     *
     * @deprecated 使用 LsiClass.fieldComment 和 LsiClass.guessTableEnglishName 替代
     */
    @Deprecated(
        "Use LsiClass.fieldComment and LsiClass.guessTableEnglishName instead",
        ReplaceWith("Pair(psiClass.toLsiClass().fieldComment, psiClass.toLsiClass().guessTableEnglishName)", "com.addzero.util.lsi.toLsiClass")
    )
    fun getClassMetaInfo4KtClass(psiClass: KtClass): Pair<String, String?> {
        // 获取类名
        val classComment = cleanDocComment(psiClass.docComment?.text)
        // 获取类的注释
        return Pair(classComment, guessTableName(psiClass))
    }

    // 添加判断项目类型的方法
    fun isKotlinProject(project: Project): Boolean {
        val buildGradle = project.guessProjectDir()?.findChild("build.gradle.kts") ?: project.guessProjectDir()
            ?.findChild("build.gradle")

        return when {
            // 检查是否有 Kotlin 构建文件
            buildGradle != null -> {
                val content = buildGradle.inputStream.reader().readText()
                content.contains("kotlin") || content.contains("org.jetbrains.kotlin")
            }
            // 检查是否有 Kotlin 源文件
            else -> {
                val kotlinFiles =
                    FileTypeIndex.getFiles(KotlinFileType.INSTANCE, GlobalSearchScope.projectScope(project))
                val javaFiles = FileTypeIndex.getFiles(JavaFileType.INSTANCE, GlobalSearchScope.projectScope(project))
                kotlinFiles.size >= javaFiles.size
            }
        }
    }

    /**
     * 获取PsiElement所在文件的路径
     */
    fun getFilePath(element: PsiElement): String {
        val virtualFile = element.containingFile?.virtualFile
        return virtualFile?.parent?.path ?: ""
    }

    data class PsiEleInfo(val packageName: String, val directoryPath: String)



}
