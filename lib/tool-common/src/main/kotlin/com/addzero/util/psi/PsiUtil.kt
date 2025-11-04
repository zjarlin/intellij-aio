package com.addzero.util.psi

import com.addzero.util.psi.javaclass.PsiClassUtil
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.java.JavaLanguage
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

/**
 * Java PsiClass 专用工具类
 */
object PsiUtil {

    fun getCurrentPsiElement(
        editor: Editor?, file: PsiFile?
    ): PsiElement? {
        if (editor == null || file == null) return null
        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset)
        return element
    }

    fun getQualifiedClassName(psiFile: PsiFile): String? {
        val fileNameWithoutExtension = psiFile.virtualFile.nameWithoutExtension
        val packageName = when (psiFile) {
            is com.intellij.psi.PsiJavaFile -> psiFile.packageName
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

    fun addComment(project: Project, field: PsiField) {
        // 创建新的文档注释
        val factory = PsiElementFactory.getInstance(project)
        val newDocComment = factory.createDocCommentFromText("/** */")
        field.addBefore(newDocComment, field.firstChild)
    }

    /**
     * @param [psiField]
     * @return [String]
     */
    fun guessFieldComment(psiField: PsiField): String {
        return PsiClassUtil.guessFieldComment(psiField)
    }

    fun guessTableName(psiClass: PsiClass): String? {
        return PsiClassUtil.guessTableName(psiClass)
    }

    fun guessTableNameByAnno(psiClass: PsiClass): @NlsSafe String? {
        return PsiClassUtil.guessTableNameByAnno(psiClass)
    }

    fun extractInterfaceMetaInfo(psiClass: PsiClass): List<JavaFieldMetaInfo> {
        return PsiClassUtil.extractInterfaceMetaInfo(psiClass)
    }

    fun getJavaClassFromPsiType(psiType: PsiType): Class<*> {
        return PsiClassUtil.getJavaClassFromPsiType(psiType)
    }

    fun getCommentFunByMethod(method: PsiMethod): String {
        return PsiClassUtil.getCommentFunByMethod(method)
    }

    fun getClassMetaInfo(psiClass: PsiClass): Pair<String, String?> {
        return PsiClassUtil.getClassMetaInfo(psiClass)
    }

    fun isStaticField(field: PsiField): Boolean {
        return PsiClassUtil.isStaticField(field)
    }

    fun getJavaFieldMetaInfo(psiClass: PsiClass): List<JavaFieldMetaInfo> {
        return PsiClassUtil.getJavaFieldMetaInfo(psiClass)
    }

    data class PsiCtx(
        val editor: Editor?,
        val psiClass: PsiClass?,
        val psiFile: PsiFile?,
        val virtualFile: VirtualFile,
        val any: Array<PsiClass>?,

        )

    fun allpsiCtx(project: Project): PsiCtx {
        // 获取所有 Java 文件
        val files = FileTypeIndex.getFiles(JavaFileType.INSTANCE, GlobalSearchScope.projectScope(project))

        files.map {
            val psiFile = PsiManager.getInstance(project).findFile(it)
        }
        return TODO("提供返回值")
    }

    fun isJavaPojo(
        element: PsiElement?
    ): Boolean {
        return PsiClassUtil.isJavaPojo(element)
    }

    fun isJavaPojo(
        editor: Editor?, file: PsiFile?
    ): Boolean {
        return PsiClassUtil.isJavaPojo(editor, file)
    }

    fun psiCtx(project: Project): PsiCtx {
        val instance = FileEditorManager.getInstance(project)

        val editor = instance.selectedTextEditor
        val virtualFile = instance.getSelectedEditor()?.file

        val psiFile = PsiManager.getInstance(project).findFile(virtualFile!!)

        val psiClass = PsiTreeUtil.findChildOfType(psiFile, PsiClass::class.java)

        val any = if (psiFile is PsiJavaFile) {
            // 一个文件中可能会定义有多个Class，因此返回的是一个数组
            val classes: Array<PsiClass> = psiFile.getClasses()
            classes
        } else {
            null
        }

        return PsiCtx(editor, psiClass, psiFile, virtualFile, any)
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
                false
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

    fun getFilePathPair(element: PsiElement): PsiEleInfo {
        // 获取包名
        val packageName = when (val containingFile = element.containingFile) {
            is PsiJavaFile -> containingFile.packageName
            else -> ""
        }

        // 获取文件所在目录路径
        val virtualFile = element.containingFile?.virtualFile
        val directoryPath = virtualFile?.parent?.path ?: ""

        return PsiEleInfo(packageName, directoryPath)
    }
}

fun PsiField.isDbField(): Boolean {
    return PsiClassUtil.isDbField(this)
}