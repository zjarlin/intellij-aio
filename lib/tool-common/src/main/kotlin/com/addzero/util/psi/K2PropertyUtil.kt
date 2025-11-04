package com.addzero.util.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.psi.getKotlinFqName
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.psi.KtProperty

/**
 * K2兼容API
 */
fun KtProperty.getKotlinFqName(): String? {
    return try {
        // K2版本使用kotlinFqName
        this.kotlinFqName?.asString()
    } catch (e: Throwable) {
        try {
            // 兼容旧版本
            this.getKotlinFqName()?.asString()
        } catch (e: Throwable) {
            null
        }
    }
}

fun PsiElement.getKotlinFqName(): String? {
    return try {
        // K2版本使用kotlinFqName
        (this as? KtProperty)?.kotlinFqName?.asString()
    } catch (e: Throwable) {
        try {
            // 兼容旧版本
            this.getKotlinFqName()?.asString()
        } catch (e: Throwable) {
            null
        }
    }
}

/**
 * 获取项目Kotlin版本信息
 */
fun getKotlinVersion(project: Project): String? {
    // 获取项目中的Kotlin插件版本
    val plugin = com.intellij.ide.plugins.PluginManagerCore.getPlugin(
        com.intellij.openapi.extensions.PluginId.getId("org.jetbrains.kotlin")
    )
    return plugin?.version
}