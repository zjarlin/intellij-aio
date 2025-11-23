package site.addzero.addl.action.anycodegen

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.highlighter.isKotlinDecompiledFile
import site.addzero.addl.action.anycodegen.entity.LsiClassMetaInfo
import site.addzero.addl.action.anycodegen.util.toLsiClass
import site.addzero.addl.util.PsiValidateUtil
import site.addzero.addl.util.getParentPathAndmkdir
import site.addzero.util.ShowContentUtil.openTextInEditor
import site.addzero.util.jvmstr.extractMarkdownBlockContent
import site.addzero.util.lsi.clazz.LsiClass
import site.addzero.util.lsi.file.LsiFile
import site.addzero.util.lsi_impl.impl.intellij.project.toVirtualFile
import site.addzero.util.lsi_impl.impl.intellij.virtualfile.toLsiFile
import site.addzero.util.lsi_impl.impl.psi.project.psiCtx
import site.addzero.util.str.addSuffixIfNot
import site.addzero.util.str.removeAny

/**
 * 基于 LSI 抽象层的代码生成基类
 *
 * 使用 LSI (Language Structure Interface) 实现语言无关的代码生成
 *
 * 子类只需要实现 genCode 方法，传入 LsiClassMetaInfo 即可
 */
abstract class AbsGenLsi(lsiClass: LsiFile) : AnAction() {

    /**
     * 生成代码
     * @return 生成的代码字符串
     */
    abstract fun genCode(): String

    /**
     * 文件后缀（不含点）
     * 例如：".dto" -> "dto"
     */
    abstract val fileSuffix: String

    /**
     * 文件类型后缀（用于从原文件名中移除）
     * 例如：".java", ".kt"
     */
    open val fileTypeSuffix: List<String> = listOf(".kt", ".java")

    /**
     * 生成文件的父目录（相对于当前文件）
     * 例如："../dto" 表示在同级创建 dto 目录
     */
    open val parentDir: String = ""

    /**
     * 生成的文件全名（不含扩展名）
     *
     * 默认实现：移除文件类型后缀，添加自定义后缀
     * 例如：User.kt -> User.dto
     */
    open fun fullName(psiFile: LsiFile?): String {
        val name1 = psiFile?.name
        val name = extractMarkdownBlockContent(name1)
        val removeAny = name.removeAny(*fileTypeSuffix.toTypedArray())
        return removeAny.addSuffixIfNot(fileSuffix)
    }

    /**
     * 获取生成文件的扩展名
     * 默认从 fileSuffix 推导
     */
    open fun getFileExtension(): String {
        return when {
            fileSuffix.startsWith(".") -> fileSuffix
            else -> ".$fileSuffix"
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val toVirtualFile = e.project?.toVirtualFile()
        val toLsiFile: LsiFile = toVirtualFile.toLsiFile()
        toVirtualFile.toLsiFile
//        val kotlinDecompiledFile = toVirtualFile.isKotlinDecompiledFile
        val psiCtx = project?.psiCtx()
        val (_, psiClass, ktClass, _, _, _) = psiCtx(project ?: return)

        // 使用工具类检查是否为POJO或Jimmer实体
        val isValidTarget = PsiValidateUtil.isValidTarget(ktClass, psiClass)

        e.presentation.isEnabled = project != null && isValidTarget.first
    }

    final override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        performAction(project, e)
    }

    protected open fun performAction(project: Project, e: AnActionEvent) {
        val (editor, psiClass, ktClass, psiFile, virtualFile, classPath) = project.psiCtx()

        // 转换为 LsiClass
        val lsiClass = when {
            ktClass != null -> ktClass.toLsiClass()
            psiClass != null -> psiClass.toLsiClass()
            else -> return
        }

        // 创建 LsiClassMetaInfo
        val metaInfo = LsiClassMetaInfo.from(lsiClass)

        // 生成代码
        val generatedCode = genCode(metaInfo)

        // 生成文件名
        val fullname = fullName(psiFile)

        // 获取文件路径
        val filePath = virtualFile.path
        val filePath1 = filePath.getParentPathAndmkdir(parentDir)

        // 在编辑器中打开生成的代码
        project.openTextInEditor(
            generatedCode,
            fullname,
            getFileExtension(),
            filePath1,
            false
        )
    }
}

