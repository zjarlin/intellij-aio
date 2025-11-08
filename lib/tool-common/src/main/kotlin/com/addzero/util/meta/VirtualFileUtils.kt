package com.addzero.util.meta

import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import org.jetbrains.jps.model.java.JavaSourceRootType

/**
 * VirtualFile 相关的工具类
 * 提供了获取 VirtualFile 语言类型、源码根目录、生成目录等相关功能
 */
object VirtualFileUtils {

    /**
     * 扩展属性：获取 VirtualFile 的语言类型
     * 根据文件扩展名判断语言类型
     * @return Language 枚举值，包括 Java、Kotlin 等
     * @throws IllegalFileFormatException 当文件类型不支持时抛出异常
     */
    val VirtualFile.language: Language
        get() {
            return when (val fileType = extension) {
                "java" -> Language.Java
                "kt" -> Language.Kotlin
                else -> throw IllegalFileFormatException(fileType ?: "<no-type>")
            }
        }

    /**
     * 获取元素所在模块的生成代码根目录
     * 查找路径中包含 "generated-sources" 或 "generated" 的目录
     * @param element PsiElement 元素
     * @return VirtualFile 生成代码根目录，找不到则返回 null
     */
    fun generateRoot(element: PsiElement): VirtualFile? {
        val generateRoot by lazy {
            root(element).firstOrNull { file -> "generated-sources" in file.path || "generated" in file.path }
        }
        return generateRoot
    }

    /**
     * 获取元素所在模块的源码根目录
     * 查找路径中包含 "src" 的目录
     * @param element PsiElement 元素
     * @return VirtualFile 源码根目录，找不到则返回 null
     */
    fun sourceRoot(element: PsiElement): VirtualFile? {
        val sourceRoot by lazy {
            root(element).firstOrNull { file -> "src" in file.path }
        }
        return sourceRoot
    }

    /**
     * 获取元素所在模块的 DTO 根目录
     * 在源码根目录同级查找 dto 目录
     * @param element PsiElement 元素
     * @return VirtualFile DTO 根目录，找不到则返回 null
     */
    fun dtoRoot(element: PsiElement): VirtualFile? {
        val dtoRootPath = sourceRoot(element)?.toNioPath()?.resolveSibling("dto") ?: return null
        return VirtualFileManager.getInstance().findFileByNioPath(dtoRootPath)
    }

    /**
     * 获取元素所在模块的所有源码根目录
     * @param element PsiElement 元素
     * @return List<VirtualFile> 源码根目录列表
     */
    fun root(element: PsiElement): List<VirtualFile> {
        val roots by lazy {
            val module = ModuleUtil.findModuleForPsiElement(element) ?: return@lazy emptyList()
            ModuleRootManager
                    .getInstance(module)
                    .getSourceRoots(JavaSourceRootType.SOURCE)
        }
        return roots
    }
}
