package site.addzero.gradle.buddy.migration

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile

/**
 * 扫描 Gradle 项目中的 project() 依赖
 */
object ProjectDependencyScanner {

    // 匹配 project(":module-name") 或 project(":path:to:module")
    private val PROJECT_DEPENDENCY_PATTERN = Regex(
        """(implementation|api|compileOnly|runtimeOnly|testImplementation|testCompileOnly|testRuntimeOnly)\s*\(\s*project\s*\(\s*["']:([^"']+)["']\s*\)\s*\)"""
    )

    /**
     * 扫描项目中所有的 project 依赖
     * @return 找到的所有 project 依赖列表
     */
    fun scan(project: Project): List<ProjectDependencyInfo> {
        val basePath = project.basePath ?: return emptyList()
        val baseDir = LocalFileSystem.getInstance().findFileByPath(basePath) ?: return emptyList()
        
        val gradleFiles = findAllGradleFiles(baseDir)
        val dependencies = mutableListOf<ProjectDependencyInfo>()

        gradleFiles.forEach { file ->
            val content = String(file.contentsToByteArray())
            PROJECT_DEPENDENCY_PATTERN.findAll(content).forEach { match ->
                val configType = match.groupValues[1]
                val modulePath = match.groupValues[2]
                val moduleName = extractModuleName(modulePath)
                
                dependencies.add(ProjectDependencyInfo(
                    file = file,
                    configType = configType,
                    modulePath = modulePath,
                    moduleName = moduleName,
                    fullMatch = match.value,
                    range = match.range
                ))
            }
        }

        return dependencies
    }

    /**
     * 从模块路径中提取模块名
     * ":module-name" -> "module-name"
     * ":path:to:module" -> "module"
     */
    private fun extractModuleName(modulePath: String): String {
        return modulePath.trimStart(':').split(':').last()
    }

    /**
     * 查找所有 Gradle 构建文件
     */
    private fun findAllGradleFiles(baseDir: VirtualFile): List<VirtualFile> {
        val gradleFiles = mutableListOf<VirtualFile>()
        VfsUtil.iterateChildrenRecursively(baseDir, { file ->
            !file.name.startsWith(".") && 
            file.name != "build" && 
            file.name != "node_modules" &&
            !file.path.contains("/build/")
        }) { file ->
            if (file.name.endsWith(".gradle.kts") || file.name.endsWith(".gradle")) {
                gradleFiles.add(file)
            }
            true
        }
        return gradleFiles
    }
}

/**
 * Project 依赖信息
 */
data class ProjectDependencyInfo(
    val file: VirtualFile,
    val configType: String,      // implementation, api, etc.
    val modulePath: String,      // :module-name 或 :path:to:module
    val moduleName: String,      // 模块名（最后一段）
    val fullMatch: String,       // 完整匹配的文本
    val range: IntRange          // 在文件中的位置
)
