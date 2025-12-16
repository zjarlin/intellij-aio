package site.addzero.util.catalogutil

import cn.hutool.core.io.FileUtil
import com.intellij.openapi.project.Project
import java.io.File
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
            val file = FileUtil.file(gradleDir, "libs.versions.toml")
            val createNewFile = file.createNewFile()
            libsVersionsFile =file
        }
        return libsVersionsFile!!
    }
    fun wrightToToml(project: Project, content: String?): Unit {
        if (content.isNullOrBlank()) {
            return
        }
        val basePath = project.basePath
        val gradleDir = basePath?.let { File(it, "gradle") }
        gradleDir?.mkdirs()
        val file = FileUtil.file(gradleDir, "libs.versions.toml")
        FileUtil.writeUtf8String(content, file)
    }



}
