package site.addzero.util.lsi_impl.impl.psi.element

import site.addzero.util.lsi_impl.impl.psi.model.PsiEleInfo
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.kotlin.psi.KtFile


inline fun <reified T : PsiElement> PsiElement?.convertTo(): T? {
    return PsiTreeUtil.getParentOfType<T>(this, T::class.java)
}

fun PsiElement.getFilePathPair(): PsiEleInfo {
    // 获取包名
    val packageName = when (val containingFile = this.containingFile) {
        is PsiJavaFile -> containingFile.packageName
        is KtFile -> containingFile.packageFqName.asString()
        else -> ""
    }
    val virtualFile = this.containingFile?.virtualFile
    val directoryPath = virtualFile?.parent?.path ?: ""
    return PsiEleInfo(packageName, directoryPath)
}

/**
 * 获取PsiElement所在文件的路径
 */
fun PsiElement.getFilePath(): String {
    val virtualFile = this.containingFile?.virtualFile
    return virtualFile?.parent?.path ?: ""
}

/**
 * 获取元素所在模块的生成代码根目录
 * 查找路径中包含 "generated-sources" 或 "generated" 的目录
 * @param this@generateRoot PsiElement 元素
 * @return VirtualFile 生成代码根目录，找不到则返回 null
 */
fun PsiElement.generateRoot(): VirtualFile? {
    val generateRoot by lazy {
        this.root().firstOrNull { file -> "generated-sources" in file.path || "generated" in file.path }
    }
    return generateRoot
}

/**
 * 获取元素所在模块的源码根目录
 * 查找路径中包含 "src" 的目录
 * @param this@sourceRoot PsiElement 元素
 * @return VirtualFile 源码根目录，找不到则返回 null
 */
fun PsiElement.sourceRoot(): VirtualFile? {
    val sourceRoot by lazy {
        root().firstOrNull { file -> "src" in file.path }
    }
    return sourceRoot
}

/**
 * 获取元素所在模块的 DTO 根目录
 * 在源码根目录同级查找 dto 目录
 * @param this@dtoRoot PsiElement 元素
 * @return VirtualFile DTO 根目录，找不到则返回 null
 */
fun PsiElement.dtoRoot(): VirtualFile? {
    val dtoRootPath = sourceRoot()?.toNioPath()?.resolveSibling("dto") ?: return null
    return VirtualFileManager.getInstance().findFileByNioPath(dtoRootPath)
}

/**
 * 获取元素所在模块的所有源码根目录
 * @param this@root PsiElement 元素
 * @return List<VirtualFile> 源码根目录列表
 */
fun PsiElement.root(): List<VirtualFile> {
    val roots by lazy {
        val module = ModuleUtil.findModuleForPsiElement(this) ?: return@lazy emptyList()
        ModuleRootManager
            .getInstance(module)
            .getSourceRoots(JavaSourceRootType.SOURCE)
    }
    return roots
}

