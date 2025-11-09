package site.addzero.addl.util.meta

// 为了保持向后兼容性，提供对新模块中工具类的引用
import site.addzero.util.meta.language
import site.addzero.util.meta.generateRoot
import site.addzero.util.meta.sourceRoot
import site.addzero.util.meta.dtoRoot
import site.addzero.util.meta.root

// 为保持兼容性，创建函数和属性别名
val com.intellij.openapi.vfs.VirtualFile.language: site.addzero.addl.util.meta.Language
    get() = this.language as site.addzero.addl.util.meta.Language

fun generateRoot(element: com.intellij.psi.PsiElement) = generateRoot(element)
fun sourceRoot(element: com.intellij.psi.PsiElement) = sourceRoot(element)
fun dtoRoot(element: com.intellij.psi.PsiElement) = dtoRoot(element)
fun root(element: com.intellij.psi.PsiElement) = root(element)
