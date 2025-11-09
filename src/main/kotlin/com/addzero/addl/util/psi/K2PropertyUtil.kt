package com.addzero.addl.util.psi

// 为了保持向后兼容性，提供对新模块中工具类的引用
import com.addzero.util.psi.getKotlinFqName as newGetKotlinFqName
import com.addzero.util.psi.getKotlinVersion

// 为保持兼容性，创建函数别名
fun org.jetbrains.kotlin.psi.KtProperty.getKotlinFqName(): String? = this.newGetKotlinFqName()
fun com.intellij.psi.PsiElement.getKotlinFqName(): String? = this.newGetKotlinFqName()
fun getKotlinVersion(project: com.intellij.openapi.project.Project): String? = project.getKotlinVersion()
