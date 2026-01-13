package site.addzero.split.services

import com.intellij.openapi.project.Project
import java.io.File

/**
 * 路径计算工具类
 */
object PathCalculator {

    /**
     * 计算同级目录路径
     * @param moduleDir 源模块目录
     * @param newModuleName 新模块名称
     * @return 新模块的同级目录路径
     */
    fun calculateSiblingPath(moduleDir: File, newModuleName: String): File {
        val parentDir = moduleDir.parentFile
        return File(parentDir, newModuleName)
    }

    /**
     * 将模块目录路径转换为 Gradle 模块路径
     * @param projectRoot 项目根目录
     * @param moduleDir 模块目录
     * @return Gradle 模块路径，例如 ":sub:module-name" 或 ":module-name"
     */
    fun calculateGradlePath(projectRoot: File, moduleDir: File): String {
        val relativePath = moduleDir.relativeTo(projectRoot).path

        // 将路径分隔符替换为 :
        val gradlePath = relativePath.replace(File.separator, ":")

        // 添加前缀 :
        return ":$gradlePath"
    }

    /**
     * 检查模块名称是否合法
     * 只允许字母、数字、下划线、中划线
     */
    fun isValidModuleName(name: String): Boolean {
        if (name.isBlank()) return false
        return name.matches(Regex("[a-zA-Z0-9_-]+"))
    }

    /**
     * 生成默认的新模块名称
     * 例如：module-a -> module-a1
     */
    fun generateDefaultModuleName(sourceModuleName: String): String {
        return "${sourceModuleName}1"
    }
}
