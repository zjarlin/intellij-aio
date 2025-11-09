package site.addzero.util.catalogutil

import com.intellij.openapi.project.Project
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * 版本目录管理工具类
 */

/**
 * 获取项目的 gradle 目录
 */
fun Project.getGradleDir(): File? {
    val basePath = this.basePath ?: return null
    val gradleDir = File(basePath, "gradle")
    if (!gradleDir.exists()) {
        gradleDir.mkdirs()
    }
    return gradleDir
}

/**
 * 获取版本目录文件路径
 */
fun Project.getLibsVersionsTomlFile(): File? {
    val gradleDir = this.getGradleDir() ?: return null
    val tomlFile = File(gradleDir, "libs.versions.toml")
    return if (tomlFile.exists()) tomlFile else null
}

/**
 * 获取或创建版本目录文件
 */
fun Project.getVersionCatalog(): File {
    var libsVersionsFile = this.getLibsVersionsTomlFile()
    if (libsVersionsFile == null) {
        val gradleDir = this.getGradleDir()
        if (gradleDir != null) {
            libsVersionsFile = File(gradleDir, "libs.versions.toml")
            libsVersionsFile.createNewFile()
        }
    }
    return libsVersionsFile!!
}

/**
 * 将内容写入版本目录文件
 */
fun Project.wrightToToml(content: String?) {
    if (content.isNullOrBlank()) {
        return
    }
    val gradleDir = this.getGradleDir()
    if (gradleDir != null) {
        val file = File(gradleDir, "libs.versions.toml")
        file.writeText(content, StandardCharsets.UTF_8)
    }
}
