package site.addzero.vibetask.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import site.addzero.vibetask.model.ProjectModule
import java.io.File

@Service(Service.Level.PROJECT)
class ModuleDetectorService(private val project: Project) {

    private val logger = Logger.getInstance(ModuleDetectorService::class.java)

    /**
     * 检测项目中的所有模块
     */
    fun detectModules(): List<ProjectModule> {
        val modules = mutableListOf<ProjectModule>()
        val basePath = project.basePath ?: return modules

        try {
            // 1. 尝试从 Gradle settings 读取模块
            modules.addAll(detectGradleModules(basePath))

            // 2. 扫描常见目录结构
            modules.addAll(detectCommonStructure(basePath))

            // 去重（按路径）
            val uniqueModules = modules.distinctBy { it.path }
                .sortedBy { it.path }

            logger.info("Detected ${uniqueModules.size} modules in project ${project.name}")
            return uniqueModules

        } catch (e: Exception) {
            logger.error("Failed to detect modules", e)
            return modules
        }
    }

    /**
     * 根据路径猜测当前文件所属模块
     */
    fun detectModuleForFile(filePath: String): ProjectModule? {
        val modules = detectModules()
        if (modules.isEmpty()) return null

        // 找到路径匹配的最深模块
        return modules
            .filter { filePath.contains(it.path) }
            .maxByOrNull { it.path.length }
    }

    /**
     * 刷新模块缓存
     */
    fun refreshModules(): List<ProjectModule> {
        return detectModules()
    }

    private fun detectGradleModules(basePath: String): List<ProjectModule> {
        val modules = mutableListOf<ProjectModule>()

        // 读取 settings.gradle.kts
        val settingsFile = File(basePath, "settings.gradle.kts")
        if (settingsFile.exists()) {
            modules.addAll(parseGradleSettings(settingsFile.readText(), basePath))
        }

        // 读取 settings.gradle
        val settingsGroovyFile = File(basePath, "settings.gradle")
        if (settingsGroovyFile.exists()) {
            modules.addAll(parseGradleSettings(settingsGroovyFile.readText(), basePath))
        }

        return modules
    }

    private fun parseGradleSettings(content: String, basePath: String): List<ProjectModule> {
        val modules = mutableListOf<ProjectModule>()

        // 匹配 include("module:name") 或 include("name")
        val includeRegex = """include\s*\(\s*["']([^"']+)["']\s*\)""".toRegex()
        val matches = includeRegex.findAll(content)

        matches.forEach { match ->
            val includePath = match.groupValues[1]
            val moduleName = includePath.substringAfterLast(':')
            val modulePath = includePath.replace(':', '/').let {
                if (it.startsWith('/')) it.substring(1) else it
            }

            val type = detectModuleType(basePath, modulePath)
            val buildSystem = ProjectModule.BuildSystem.GRADLE

            modules.add(ProjectModule(
                name = moduleName,
                path = modulePath,
                type = type,
                buildSystem = buildSystem
            ))
        }

        return modules
    }

    private fun detectCommonStructure(basePath: String): List<ProjectModule> {
        val modules = mutableListOf<ProjectModule>()
        val baseDir = File(basePath)

        if (!baseDir.exists()) return modules

        // 常见 monorepo 目录结构
        val commonDirs = listOf(
            "plugins" to ProjectModule.ModuleType.PLUGIN,
            "lib" to ProjectModule.ModuleType.LIB,
            "libs" to ProjectModule.ModuleType.LIB,
            "packages" to ProjectModule.ModuleType.LIB,
            "apps" to ProjectModule.ModuleType.APP,
            "applications" to ProjectModule.ModuleType.APP,
            "modules" to ProjectModule.ModuleType.UNKNOWN,
            "projects" to ProjectModule.ModuleType.UNKNOWN,
            "services" to ProjectModule.ModuleType.APP,
            "packages" to ProjectModule.ModuleType.LIB
        )

        commonDirs.forEach { (dirName, defaultType) ->
            val dir = File(basePath, dirName)
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { subDir ->
                    if (subDir.isDirectory && isValidModule(subDir)) {
                        val moduleName = subDir.name
                        val modulePath = "$dirName/${subDir.name}"
                        val type = detectModuleType(basePath, modulePath, defaultType)
                        val buildSystem = detectBuildSystem(subDir)

                        // 避免重复添加（已经在 Gradle settings 中检测到的）
                        if (!modules.any { it.path == modulePath }) {
                            modules.add(ProjectModule(
                                name = moduleName,
                                path = modulePath,
                                type = type,
                                buildSystem = buildSystem
                            ))
                        }
                    }
                }
            }
        }

        return modules
    }

    private fun isValidModule(dir: File): Boolean {
        // 检查是否是有效模块（包含构建文件或源代码目录）
        return dir.listFiles()?.any { file ->
            file.name in listOf(
                "build.gradle", "build.gradle.kts",
                "pom.xml", "package.json",
                "src"
            )
        } == true
    }

    private fun detectModuleType(
        basePath: String,
        modulePath: String,
        defaultType: ProjectModule.ModuleType = ProjectModule.ModuleType.UNKNOWN
    ): ProjectModule.ModuleType {
        val fullPath = "$basePath/$modulePath"
        val dir = File(fullPath)

        if (!dir.exists()) return defaultType

        // 根据路径关键词判断
        return when {
            modulePath.contains("plugin", ignoreCase = true) -> ProjectModule.ModuleType.PLUGIN
            modulePath.contains("lib", ignoreCase = true) -> ProjectModule.ModuleType.LIB
            modulePath.contains("app", ignoreCase = true) -> ProjectModule.ModuleType.APP
            modulePath.contains("service", ignoreCase = true) -> ProjectModule.ModuleType.APP
            else -> defaultType
        }
    }

    private fun detectBuildSystem(dir: File): ProjectModule.BuildSystem {
        return when {
            File(dir, "build.gradle").exists() || File(dir, "build.gradle.kts").exists() ->
                ProjectModule.BuildSystem.GRADLE
            File(dir, "pom.xml").exists() ->
                ProjectModule.BuildSystem.MAVEN
            File(dir, "package.json").exists() ->
                ProjectModule.BuildSystem.NPM
            else -> ProjectModule.BuildSystem.UNKNOWN
        }
    }

    companion object {
        fun getInstance(project: Project): ModuleDetectorService = project.getService(ModuleDetectorService::class.java)
    }
}
