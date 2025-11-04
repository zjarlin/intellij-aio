package com.addzero.addl.util.psi

// 为了保持向后兼容性，提供对新模块中工具类的引用
import com.addzero.util.psi.nameIdentifier
import com.addzero.util.psi.annotations
import com.addzero.util.psi.psiClass
import com.addzero.util.psi.prop
import com.addzero.util.psi.ktClass
import com.addzero.util.psi.supers
import com.addzero.util.psi.methods
import com.addzero.util.psi.properties
import com.addzero.util.psi.hasAnnotation
import com.addzero.util.psi.qualifiedName
import com.addzero.util.psi.nullable
import com.addzero.util.psi.clazz

// 为保持兼容性，创建函数别名
fun com.intellij.openapi.vfs.VirtualFile.nameIdentifier(project: com.intellij.openapi.project.Project) = this.nameIdentifier(project)
fun com.intellij.openapi.vfs.VirtualFile.annotations(project: com.intellij.openapi.project.Project) = this.annotations(project)
fun com.intellij.openapi.vfs.VirtualFile.psiClass(project: com.intellij.openapi.project.Project, propPath: List<String> = emptyList()) = this.psiClass(project, propPath)
fun com.intellij.psi.PsiClass.prop(propPath: List<String>, level: Int) = this.prop(propPath, level)
fun com.intellij.openapi.vfs.VirtualFile.ktClass(project: com.intellij.openapi.project.Project) = this.ktClass(project)
fun com.intellij.psi.PsiClass.supers() = this.supers()
fun com.intellij.psi.PsiClass.methods() = this.methods()
fun org.jetbrains.kotlin.psi.KtClass.properties() = this.properties()
fun com.intellij.psi.PsiClass.hasAnnotation(vararg annotations: String) = this.hasAnnotation(*annotations)
fun org.jetbrains.kotlin.psi.KtClass.hasAnnotation(vararg annotations: String) = this.hasAnnotation(*annotations)
val com.intellij.psi.PsiType.nullable: Boolean
    get() = this.nullable
fun com.intellij.psi.PsiType.clazz() = this.clazz()