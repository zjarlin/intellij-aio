package com.addzero.util.catalogutil

import com.intellij.openapi.project.Project
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Paths

/**
 * 版本目录管理工具类
 */


object VersionCatalogPsiUtil {
    /**
     * 获取版本目录文件
     * @param project 项目实例
     * @return 版本目录文件，如果不存在则返回null
     */
    fun getLibsVersionsTomlFile(project: Project): File? {
        val projectBaseDir = project.basePath ?: return null
        val tomlFilePath = Paths.get(projectBaseDir, "gradle", "libs.versions.toml")
        val tomlFile = tomlFilePath.toFile()
        return if (tomlFile.exists()) tomlFile else null
    }

    fun getVersionCatalog(project: Project): File {
        val basePath = project.basePath
        var libsVersionsFile = getLibsVersionsTomlFile(project)
        if (libsVersionsFile == null) {
            val gradleDir = basePath?.let { File(it, "gradle") }
            gradleDir?.mkdirs()
            val file = File(gradleDir, "libs.versions.toml")
            file.createNewFile()
            libsVersionsFile =file
        }
        return libsVersionsFile
    }

    fun wrightToToml(project: Project, content: String?) {
        if (content.isNullOrBlank()) {
            return
        }
        val basePath = project.basePath
        val gradleDir = basePath?.let { File(it, "gradle") }
        gradleDir?.mkdirs()
        val file = File(gradleDir, "libs.versions.toml")
        file.writeText(content, StandardCharsets.UTF_8)
    }
}
