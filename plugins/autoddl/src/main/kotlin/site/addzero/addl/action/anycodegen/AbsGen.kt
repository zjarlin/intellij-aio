package site.addzero.addl.action.anycodegen

import site.addzero.addl.autoddlstarter.generator.entity.PsiFieldMetaInfo
import site.addzero.addl.util.*
import site.addzero.addl.util.fieldinfo.PsiUtil
import site.addzero.common.kt_util.addSuffixIfNot
import site.addzero.util.ShowContentUtil.openTextInEditor
import site.addzero.util.psi.PsiUtil.getPackagePath
import site.addzero.util.psi.PsiUtil.getQualifiedClassName
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import site.addzero.util.lsi_impl.impl.psi.project.psiCtx
import site.addzero.util.str.addSuffixIfNot
import site.addzero.util.str.extractMarkdownBlockContent
import site.addzero.util.str.removeAny
import java.io.File

fun main() {
    "site.addzero.addl.action.anycodegen.AbsGen".let {
        val parent = File(it).parent
        println(parent)

    }
}


abstract class AbsGen : AnAction() {

//    override fun getActionUpdateThread(): ActionUpdateThread {
//        return ActionUpdateThread.BGT
//    }


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

        val name = extractMarkdownBlockContent(psiFile?.name)
        val removeAny = name.removeAny(javafileTypeSuffix, ktfileTypeSuffix, ".kt", ".java")
        val addSuffixIfNot = removeAny.addSuffixIfNot(suffix)
        return addSuffixIfNot
    }


    override fun update(e: AnActionEvent) {
        val project = e.project
        val psiCtx = project?.psiCtx()
        val (editor, psiClass, ktClass, psiFile, virtualFile, classPath) = psiCtx(project ?: return)

        // 使用工具类检查是否为POJO或Jimmer实体
        val isValidTarget = PsiValidateUtil.isValidTarget(ktClass, psiClass)

        e.presentation.isEnabled = project != null && isValidTarget.first
    }

    final override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        performAction(project, e)
    }

    protected open fun performAction(project: Project, e: AnActionEvent) {
        val (editor, psiClass, ktClass, psiFile, virtualFile, classPath) = psiCtx(project)
        val packagePath = psiFile.getPackagePath()
        val qualifiedClassName = psiFile!!.getQualifiedClassName()

        val fullname = fullName(psiFile)
        if (ktClass == null) {
            var extractInterfaceMetaInfo = psiClass?.let { PsiUtil.getJavaFieldMetaInfo(it) }

            if (extractInterfaceMetaInfo != null && extractInterfaceMetaInfo.isEmpty()) {
                extractInterfaceMetaInfo = psiClass?.let { PsiUtil.extractInterfaceMetaInfo(it) }
            }

            val name = psiClass?.name
            val extractMarkdownBlockContent = psiClass?.text.extractMarkdownBlockContent()


            val lastIndexOf = packagePath?.lastIndexOf('.')
            val packageName = lastIndexOf?.let { packagePath?.substring(0, it) }


            val psiFieldMetaInfo =
                PsiFieldMetaInfo(packageName, name, extractMarkdownBlockContent, extractInterfaceMetaInfo)
            val generatedCode = genCode4Java(psiFieldMetaInfo)

            val filePath = virtualFile.path
            val filePath1 = filePath.getParentPathAndmkdir(pdir)
            project.openTextInEditor(
                generatedCode, fullname, javafileTypeSuffix, filePath1, false
            )
            return
        }

        val className = ktClass.name ?: "UnnamedClass"
        val extractInterfaceMetaInfo = PsiUtil.extractInterfaceMetaInfo(ktClass)

        val name = ktClass.name
        val extractMarkdownBlockContent = ktClass.text.extractMarkdownBlockContent()
        val psiFieldMetaInfo =
            PsiFieldMetaInfo(packagePath, name, extractMarkdownBlockContent, extractInterfaceMetaInfo)

        val generateKotlinEntity = genCode4Kt(psiFieldMetaInfo)
        val filePath = virtualFile.path
        val filePath1 = filePath.getParentPathAndmkdir(pdir)

        project.openTextInEditor(
            generateKotlinEntity, fullname, ktfileTypeSuffix, filePath1, false
        )
    }
}
