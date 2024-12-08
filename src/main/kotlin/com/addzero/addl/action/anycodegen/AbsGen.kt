package com.addzero.addl.action.anycodegen

import cn.hutool.core.util.StrUtil
import com.addzero.addl.autoddlstarter.generator.entity.PsiFieldMetaInfo
import com.addzero.addl.util.ShowContentUtil
import com.addzero.addl.util.extractMarkdownBlockContent
import com.addzero.addl.util.fieldinfo.PsiUtil
import com.addzero.addl.util.fieldinfo.PsiUtil.psiCtx
import com.addzero.addl.util.getParentPathAndmkdir
import com.addzero.addl.util.removeAny
import com.addzero.common.kt_util.addSuffixIfNot
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import org.jetbrains.kotlin.psi.KtFile

abstract class AbsGen : AnAction() {
    fun mapToType(type: String?): String {
        return when (type) {
            "String" -> "String"
            "Int" -> "Integer"
            "BigDecimal" -> "BigDecimal"
            "LocalDateTime" -> "LocalDateTime"
            else -> "String"
        }
    }

    open fun genCode4Java(psiFieldMetaInfo: PsiFieldMetaInfo): String = genCode4Kt(psiFieldMetaInfo)
    abstract fun genCode4Kt(psiFieldMetaInfo: PsiFieldMetaInfo): String

    open val suffix: String = ""
    abstract val javafileTypeSuffix: String
    abstract val ktfileTypeSuffix: String
    open val pdir: String = ""


    open fun fullName(psiFile: PsiFile?): String {
        val name = psiFile?.name.extractMarkdownBlockContent()
        val removeAny = name.removeAny(javafileTypeSuffix, ktfileTypeSuffix,".kt",".java")
//        val firstNonBlank = StrUtil.firstNonBlank(ktfileTypeSuffix, javafileTypeSuffix)
        val addSuffixIfNot = removeAny.addSuffixIfNot(suffix)
        return addSuffixIfNot
    }


    fun getQualifiedClassName(psiFile: PsiFile): String? {
        // 获取类名（去掉文件扩展名）
        val fileNameWithoutExtension = psiFile.virtualFile.nameWithoutExtension

        // 获取包名
        val packageName = when (psiFile) {
            is KtFile -> psiFile.packageFqName.asString() // Kotlin 文件的包名
            is PsiJavaFile -> psiFile.packageName // Java 文件的包名
            else -> null
        }

        // 如果获取不到包名，直接返回类名
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

    override fun actionPerformed(e: AnActionEvent) {
        // 获取当前项目和编辑器上下文
        val project: Project = e.project ?: return

        val (editor, psiClass, ktClass, psiFile, virtualFile, classPath) = psiCtx(project)
        val packagePath = getPackagePath(psiFile)
        val qualifiedClassName = getQualifiedClassName(psiFile!!)

        val fullname = fullName(psiFile)
        if (ktClass == null) {
            var extractInterfaceMetaInfo = psiClass?.let { PsiUtil.getJavaFieldMetaInfo(it) }

            if (extractInterfaceMetaInfo != null && extractInterfaceMetaInfo.isEmpty()) {
                extractInterfaceMetaInfo = psiClass?.let { PsiUtil.extractInterfaceMetaInfo(it) }
            }

            val name = psiClass?.name
            val extractMarkdownBlockContent = psiClass?.text.extractMarkdownBlockContent()


            val psiFieldMetaInfo = PsiFieldMetaInfo(packagePath,name, extractMarkdownBlockContent, extractInterfaceMetaInfo)


            val let = genCode4Java(psiFieldMetaInfo)

            val addSuffixIfNot = name.addSuffixIfNot(suffix)

            val filePath = virtualFile.path
            val filePath1 = filePath.getParentPathAndmkdir(pdir)
            ShowContentUtil.openTextInEditor(
                project, let!!, fullname, javafileTypeSuffix, filePath1,false
            )
            return
        }
        val className = ktClass.name ?: "UnnamedClass"
        val extractInterfaceMetaInfo = PsiUtil.extractInterfaceMetaInfo(ktClass)

        val name = ktClass?.name
        val extractMarkdownBlockContent = ktClass?.text.extractMarkdownBlockContent()
        val psiFieldMetaInfo = PsiFieldMetaInfo(packagePath,name, extractMarkdownBlockContent, extractInterfaceMetaInfo)


        val generateKotlinEntity = genCode4Kt(psiFieldMetaInfo)
        val filePath = virtualFile.path
        val filePath1 = filePath.getParentPathAndmkdir(pdir)

        ShowContentUtil.openTextInEditor(
            project, generateKotlinEntity, fullname, ktfileTypeSuffix, filePath1,false
        )
    }

}