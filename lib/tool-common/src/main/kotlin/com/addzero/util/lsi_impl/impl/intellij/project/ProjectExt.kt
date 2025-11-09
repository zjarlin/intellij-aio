package com.addzero.util.lsi_impl.impl.intellij.project

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.KotlinFileType


/**
 * 获取项目Kotlin版本信息
 */
fun Project.getKotlinVersion(): String? {
    // 获取项目中的Kotlin插件版本
    val plugin = PluginManagerCore.getPlugin(
        PluginId.getId("org.jetbrains.kotlin")
    )
    return plugin?.version
}


fun Project.isKotlinProject(): Boolean {
    // 检查是否存在 Kotlin 文件
    val hasKotlinFiles = FileTypeIndex.containsFileOfType(KotlinFileType.INSTANCE, GlobalSearchScope.projectScope(this))

    // 检查是否存在 Java 文件
    val hasJavaFiles = FileTypeIndex.containsFileOfType(JavaFileType.INSTANCE, GlobalSearchScope.projectScope(this))

    return when {
        hasKotlinFiles && !hasJavaFiles -> true  // 纯 Kotlin 项目
        !hasKotlinFiles && hasJavaFiles -> false // 纯 Java 项目
        hasKotlinFiles && hasJavaFiles -> true   // 混合项目，但包含 Kotlin
        else -> false                            // 默认返回 false
    }
}



fun Project.isJavaProject(): Boolean {
    val modules = ModuleManager.getInstance(this).modules
    return !isKotlinProject() // 如果没有检测到 Kotlin，就认为是 Java 项目
}
