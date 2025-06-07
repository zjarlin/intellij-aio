package com.addzero.addl.ctx
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.KotlinFileType

fun isKotlinProject(project: Project): Boolean {
    // 检查是否存在 Kotlin 文件
    val hasKotlinFiles = FileTypeIndex.containsFileOfType(KotlinFileType.INSTANCE, GlobalSearchScope.projectScope(project))

    // 检查是否存在 Java 文件
    val hasJavaFiles = FileTypeIndex.containsFileOfType(JavaFileType.INSTANCE, GlobalSearchScope.projectScope(project))

    return when {
        hasKotlinFiles && !hasJavaFiles -> true  // 纯 Kotlin 项目
        !hasKotlinFiles && hasJavaFiles -> false // 纯 Java 项目
        hasKotlinFiles && hasJavaFiles -> true   // 混合项目，但包含 Kotlin
        else -> false                            // 默认返回 false
    }
}



fun isJavaProject(project: Project): Boolean {
    val modules = ModuleManager.getInstance(project).modules
    return !isKotlinProject(project) // 如果没有检测到 Kotlin，就认为是 Java 项目
}
