package com.addzero.util.lsi_impl.impl.intellij.project

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project


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


