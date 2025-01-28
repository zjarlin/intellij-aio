package com.addzero.addl.util.fieldinfo

import cn.hutool.core.util.ClassUtil
import cn.hutool.core.util.StrUtil
import com.addzero.addl.autoddlstarter.generator.IDatabaseGenerator.Companion.javaType2RefType
import com.addzero.addl.autoddlstarter.generator.IDatabaseGenerator.Companion.ktType2RefType
import com.addzero.addl.autoddlstarter.generator.entity.JavaFieldMetaInfo
import com.addzero.addl.ktututil.isCollectionType
import com.addzero.addl.ktututil.toUnderlineCase
import com.addzero.addl.settings.SettingContext
import com.addzero.addl.util.*
import com.addzero.addl.util.kt_util.hasAnnotation
import com.addzero.addl.util.kt_util.isStatic
import com.addzero.common.kt_util.isBlank
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtProperty


//private const val NOCOMMENT = "no_comment_found"
private const val NOCOMMENT = ""


object PsiUtil {


    fun getQualifiedClassName(psiFile: PsiFile): String? {
        val fileNameWithoutExtension = psiFile.virtualFile.nameWithoutExtension
        val packageName = when (psiFile) {
            is KtFile -> psiFile.packageFqName.asString()
            is PsiJavaFile -> psiFile.packageName
            else -> null
        }
        return if (packageName != null) {
            "$packageName.$fileNameWithoutExtension"
        } else {
            fileNameWithoutExtension
        }
    }

    fun getPackagePath(psiFile: PsiFile?): String? {
        val qualifiedClassName = getQualifiedClassName(psiFile!!)
        return qualifiedClassName
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


    fun guessFieldComment(ktProperty: KtProperty): String {
        if (ktProperty.name == SettingContext.settings.id) {
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
        if (psiField.name == SettingContext.settings.id) {
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
        val guessTableNameByAnno = guessTableNameByAnno(psiClass)

//        todo cleancode
//        showInfoMsg("guessTableNameByAnno: $guessTableNameByAnno")

        val firstNonBlank = StrUtil.firstNonBlank(guessTableNameByAnno, text)
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
                        name = mayColumn!!.removeAnyQuote(), type = type, genericType =
                            type, comment = comment.removeAnyQuote()
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
                    name = fieldName.removeAnyQuote(), type = type, genericType = type, comment = comment.removeAnyQuote()
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


    fun getClassMetaInfo(psiClass: PsiClass): Pair<String, String?> {
        // 获取类名
        val classComment = cleanDocComment(psiClass.docComment?.text)

        // 获取类的注释

        return Pair(classComment, guessTableName(psiClass))
    }


    fun isStaticField(field: PsiField): Boolean {
        val hasModifierProperty = field.hasModifierProperty(PsiModifier.STATIC)
        return hasModifierProperty
    }

//    fun isStaticField(ktProperty: KtProperty): Boolean {
//        val toLightElements = ktProperty.getMaybeLightElement()
//        val psiField = toLightElements as PsiField
//        val staticField = isStaticField(psiField)
//        return staticField
//    }

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
            val guessColumnName = guessColumnName(field)
            val firstNonBlank = StrUtil.firstNonBlank(guessColumnName, fieldName)


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
            fieldsMetaInfo.add(JavaFieldMetaInfo(firstNonBlank!!.removeAnyQuote(), typeClass, typeClass, fieldComment.removeAnyQuote()))
        }
        return fieldsMetaInfo
    }


    fun guessTableName(psiClass: KtClass): String? {
        val text = psiClass.name?.toUnderlineCase()
        // 获取所有注解
        val guessTableNameByAnno = guessTableNameByAnno(psiClass)

        //todo cleancode
//        showInfoMsg("guessTableNameByAnno: $guessTableNameByAnno")

        val firstNonBlank = StrUtil.firstNonBlank(guessTableNameByAnno, text)
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
            val finalColumn = StrUtil.firstNonBlank(columnName, fieldName)


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


    fun psiCtx(project: Project): PsiCtx {
        val instance = FileEditorManager.getInstance(project)


        val editor = instance.selectedTextEditor
        //        val virtualFile = editor.virtualFile
        val virtualFile = instance.getSelectedEditor()?.file

        val psiFile = PsiManager.getInstance(project).findFile(virtualFile!!)

        val ktClass = virtualFile.ktClass(project)

        val psiClass = PsiTreeUtil.findChildOfType(psiFile, PsiClass::class.java)
        // 如果是Java文件，可以转换成PsiJavaFile

        // 先获取到文件后再获取文件所在包
//        val daoPackage = (psiClass?.getContainingFile() as PsiJavaFile).packageName
//        val name = psiClass.name
//        val addPrefixIfNot = daoPackage.addSuffixIfNot(".$name")
//        val loadClass = ClassUtil.loadClass<Any>(addPrefixIfNot)

        val any = if (psiFile is PsiJavaFile) {
            // 一个文件中可能会定义有多个Class，因此返回的是一个数组
            val classes: Array<PsiClass> = psiFile.getClasses()
            classes
        } else if (psiFile is KtFile) {
            val classes = psiFile.classes
            classes
        } else {
            null
        }

        return PsiCtx(editor, psiClass, ktClass, psiFile, virtualFile, any)
    }


    fun getClassMetaInfo4KtClass(psiClass: KtClass): Pair<String, String?> {
        // 获取类名
        val classComment = cleanDocComment(psiClass.docComment?.text)
        // 获取类的注释
        return Pair(classComment, guessTableName(psiClass))
    }

    // 添加判断项目类型的方法
    fun isKotlinProject(project: Project): Boolean {
        val buildGradle = project.guessProjectDir()?.findChild("build.gradle.kts")
            ?: project.guessProjectDir()?.findChild("build.gradle")

        return when {
            // 检查是否有 Kotlin 构建文件
            buildGradle != null -> {
                val content = buildGradle.inputStream.reader().readText()
                content.contains("kotlin") || content.contains("org.jetbrains.kotlin")
            }
            // 检查是否有 Kotlin 源文件
            else -> {
                val kotlinFiles = FileTypeIndex.getFiles(KotlinFileType.INSTANCE, GlobalSearchScope.projectScope(project))
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
    data class PsiEleInfo(val packageName:String,val directoryPath:String)

    fun getFilePathPair(element: PsiElement): PsiEleInfo {
        // 获取包名
        val packageName = when (val containingFile = element.containingFile) {
            is PsiJavaFile -> containingFile.packageName
            is KtFile -> containingFile.packageFqName.asString()
            else -> ""
        }

        // 获取文件所在目录路径
        val virtualFile = element.containingFile?.virtualFile
        val directoryPath = virtualFile?.parent?.path ?: ""

        return PsiEleInfo(packageName, directoryPath)
    }



}

fun KtProperty.isDbField(): Boolean {
    val staticField = this.isStatic()
    val collectionType = PsiTypeUtil.isCollectionType(this)
    return !staticField && !collectionType
}

fun PsiField.isDbField(): Boolean {
    val staticField = PsiUtil.isStaticField(this)

    val collectionType = PsiTypeUtil.isCollectionType(this)
    return !staticField && !collectionType
}