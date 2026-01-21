package site.addzero.gradle.sleep.loader

import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gradle.service.project.open.linkAndSyncGradleProject
import site.addzero.gradle.sleep.util.StringUtils.toKebabCase
import java.io.File

/**
 * 按需模块加载器
 * 基于当前打开的标签页，只加载对应的模块
 */
object OnDemandModuleLoader {

    private val logger = Logger.getInstance(OnDemandModuleLoader::class.java)

    // 受保护的模块（始终包含，但不生成 include）
    private val PROTECTED_MODULES = setOf("buildSrc", "build-logic", "gradle")

    // 应该排除不生成 include 的模块后缀
    private val EXCLUDED_MODULE_SUFFIXES = listOf("build-logic", "buildSrc", "buildLogic")

    /**
     * 分离有效模块和被排除的模块
     */
    fun partitionModules(modules: Set<String>): Pair<Set<String>, Set<String>> {
        val validModules = modules
            .filter { it != ":" && it.isNotBlank() }
            .filterNot { modulePath -> EXCLUDED_MODULE_SUFFIXES.any { suffix -> modulePath.endsWith(":$suffix") } }
            .toSet()
        val excludedModules = modules
            .filter { it != ":" && it.isNotBlank() }
            .filter { modulePath -> EXCLUDED_MODULE_SUFFIXES.any { suffix -> modulePath.endsWith(":$suffix") } }
            .toSet()
        return validModules to excludedModules
    }

    // 按需加载配置文件名
    private const val ACTIVE_MODULES_FILE = ".gradle-buddy/active-modules.gradle.kts"

    /**
     * 获取当前打开的所有标签页文件
     */
    fun getOpenEditorFiles(project: Project): List<VirtualFile> {
        return FileEditorManager.getInstance(project).openFiles.toList()
    }

    /**
     * 从打开的文件推导模块路径（包含递归依赖）
     * @return 模块路径集合，格式如 ":plugins:gradle-buddy"
     */
    fun detectModulesFromOpenFiles(project: Project): Set<String> {
        val openFiles = getOpenEditorFiles(project)
        val projectBasePath = project.basePath ?: return emptySet()

        val directModules = openFiles
            .mapNotNull { file -> detectModulePath(file, projectBasePath) }
            .filter { it != ":" } // 排除根项目
            .toSet()

        // 递归推导依赖模块
        return expandWithDependencies(project, directModules)
    }

    /**
     * 递归展开模块及其依赖
     */
    private fun expandWithDependencies(project: Project, modules: Set<String>): Set<String> {
        val result = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        val queue = modules.toMutableList()

        while (queue.isNotEmpty()) {
            val modulePath = queue.removeFirst()
            if (modulePath in visited) continue

            visited.add(modulePath)
            result.add(modulePath)

            // 解析此模块的依赖
            val dependencies = extractProjectDependencies(project, modulePath)
            dependencies.forEach { dep ->
                if (dep !in visited) {
                    queue.add(dep)
                }
            }
        }

        return result
    }

    /**
     * 从模块的 build.gradle.kts 文件中提取项目依赖
     */
    private fun extractProjectDependencies(project: Project, modulePath: String): Set<String> {
        val buildFile = findBuildFile(project, modulePath) ?: return emptySet()

        return try {
            val content = String(buildFile.contentsToByteArray())
            val projectBasePath = project.basePath
            parseProjectDependencies(content, projectBasePath)
        } catch (e: Exception) {
            logger.warn("Failed to parse dependencies for $modulePath", e)
            emptySet()
        }
    }

    /**
     * 查找模块的 build.gradle.kts 文件
     */
    private fun findBuildFile(project: Project, modulePath: String): VirtualFile? {
        val projectBasePath = project.basePath ?: return null
        val moduleRelativePath = modulePath.removePrefix(":").replace(':', '/')
        val moduleDirPath = File(projectBasePath, moduleRelativePath)

        val buildFileKts = File(moduleDirPath, "build.gradle.kts")
        if (buildFileKts.exists()) {
            return VfsUtil.findFileByIoFile(buildFileKts, true)
        }

        val buildFile = File(moduleDirPath, "build.gradle")
        if (buildFile.exists()) {
            return VfsUtil.findFileByIoFile(buildFile, true)
        }

        return null
    }

    /**
     * 解析 build.gradle.kts 内容，提取项目依赖
     * 支持两种格式:
     * 1. [配置](project(":path:to:module"))  - 如 implementation(project(":lib:tool-awt"))
     * 2. [配置](projects.path.to.module)     - 如 implementation(projects.lib.toolAwt)
     *
     * 支持的配置: implementation, api, compileOnly, runtimeOnly, testImplementation, testCompileOnly 等
     */
    private fun parseProjectDependencies(content: String, projectBasePath: String? = null): Set<String> {
        val dependencies = mutableSetOf<String>()

        // 移除注释行，避免匹配到注释掉的依赖
        val effectiveContent = content.lines()
            .filterNot { it.trim().startsWith("//") }
            .joinToString("\n")

        // 格式1: project(":path:to:module")
        // 匹配任何依赖配置 + project()，如: implementation(project(":xxx"))
        val projectPattern = Regex("""(?:api|implementation|compileOnly|runtimeOnly|testImplementation|testCompileOnly|testRuntimeOnly|annotationProcessor|kapt|ksp)\s*\(\s*project\(\s*["']([^"']+)["']\s*\)""")
        projectPattern.findAll(effectiveContent).forEach { match ->
            dependencies.add(match.groupValues[1])
        }

        // 格式2: projects.path.to.module
        // 匹配任何依赖配置 + projects.xxx，如: implementation(projects.lib.toolAwt)
        // 注意: Gradle type-safe accessors 会将 kebab-case 目录名转成 camelCase
        // 例如 tool-awt -> toolAwt，这里需要逆向转换回实际的目录名
        val projectsPattern = Regex("""(?:api|implementation|compileOnly|runtimeOnly|testImplementation|testCompileOnly|testRuntimeOnly|annotationProcessor|kapt|ksp)\s*\(\s*projects\.([a-zA-Z0-9.]+)""")
        projectsPattern.findAll(effectiveContent).forEach { match ->
            val projectAccessor = match.groupValues[1]
            val modulePath = resolveModulePath(projectAccessor)
            if (modulePath != null) {
                dependencies.add(modulePath)
            } else {
                logger.warn("Could not resolve module path for projects.$projectAccessor")
            }
        }

        return dependencies
    }

    /**
     * 解析 projects accessor 路径到 Gradle 模块路径
     * 支持模糊匹配，因为 camelCase -> kebab-case 的转换不是一一对应的
     * 例如: singletonAdapterApi 可能对应 singleton-adapter-api 或 singleton-adapter-kcp
     */
    private fun resolveModulePath(projectAccessor: String, projectBasePath: String? = null): String? {
        val segments = projectAccessor.split('.')
        val convertedSegments = segments.map { it.toKebabCase() }
        val baseModulePath = ":${convertedSegments.joinToString(":")}"

        // 如果项目路径可用，尝试验证目录是否存在
        projectBasePath?.let { basePath ->
            val moduleRelativePath = baseModulePath.removePrefix(":").replace(':', '/')
            val moduleDirPath = java.io.File(basePath, moduleRelativePath)

            if (!moduleDirPath.exists()) {
                // 目录不存在，尝试模糊匹配
                val parentDir = moduleDirPath.parentFile
                val lastSegmentName = moduleDirPath.name
                val lastSegmentAccessor = segments.last()

                if (parentDir.exists()) {
                    val candidates = parentDir.listFiles()?.filter { it.isDirectory }?.filter { dir ->
                        dir.name.startsWith(lastSegmentName.split('-')[0])
                    }

                    if (candidates != null && candidates.isNotEmpty()) {
                        if (candidates.size == 1) {
                            val matchedPath = parentDir.toPath().relativize(candidates[0].toPath()).toString().replace(java.io.File.separatorChar, ':')
                            val resolvedPath = ":${baseModulePath.substringBeforeLast(':')}:$matchedPath"
                            logger.info("Resolved '$projectAccessor' to '$resolvedPath' via fuzzy match")
                            return resolvedPath
                        } else {
                            logger.warn("Multiple candidates for '$projectAccessor': ${candidates.map { it.name }}")
                        }
                    }
                }
            }
        }

        return baseModulePath
    }

    /**
     * 检测单个文件所属的模块路径
     */
    private fun detectModulePath(file: VirtualFile, projectBasePath: String): String? {
        if (!file.path.startsWith(projectBasePath)) return null

        var currentDir = file.parent
        while (currentDir != null && currentDir.path.startsWith(projectBasePath)) {
            val hasBuildFile = currentDir.findChild("build.gradle.kts") != null ||
                    currentDir.findChild("build.gradle") != null

            if (hasBuildFile) {
                val relativePath = currentDir.path
                    .removePrefix(projectBasePath)
                    .trimStart('/')

                return when {
                    relativePath.isEmpty() -> ":"
                    else -> ":${relativePath.replace('/', ':')}"
                }
            }
            currentDir = currentDir.parent
        }
        return null
    }

    /**
     * 获取项目中所有可用的模块
     * 通过扫描包含 build.gradle(.kts) 的目录
     */
    fun discoverAllModules(project: Project): List<ModuleDescriptor> {
        val baseDir = project.guessProjectDir() ?: return emptyList()
        val projectBasePath = project.basePath ?: return emptyList()
        val modules = mutableListOf<ModuleDescriptor>()

        fun scanDir(dir: VirtualFile, depth: Int = 0) {
            if (depth > 10) return // 防止过深递归
            if (PROTECTED_MODULES.contains(dir.name)) return

            val hasBuildFile = dir.findChild("build.gradle.kts") != null ||
                    dir.findChild("build.gradle") != null

            if (hasBuildFile && dir != baseDir) {
                val relativePath = dir.path
                    .removePrefix(projectBasePath)
                    .trimStart('/')
                val modulePath = ":${relativePath.replace('/', ':')}"

                modules.add(ModuleDescriptor(
                    path = modulePath,
                    name = dir.name,
                    absolutePath = dir.path
                ))
            }

            // 递归扫描子目录
            dir.children
                .filter { it.isDirectory && !it.name.startsWith(".") && it.name != "build" }
                .forEach { scanDir(it, depth + 1) }
        }

        scanDir(baseDir)
        return modules.sortedBy { it.path }
    }

    /**
     * 生成 settings include 语句
     */
    fun generateIncludeStatements(modules: Set<String>): String {
        if (modules.isEmpty()) return "// No modules to include"

        return modules
            .sorted()
            .joinToString("\n") { modulePath ->
                "include(\"$modulePath\")"
            }
    }

    /**
     * 生成完整的 active-modules.gradle.kts 文件内容
     */
    fun generateActiveModulesFile(modules: Set<String>): String {
        return buildString {
            appendLine("// Auto-generated by Gradle Buddy - On-Demand Module Loading")
            appendLine("// Generated at: ${java.time.LocalDateTime.now()}")
            appendLine("// Active modules based on open editor tabs")
            appendLine()
            appendLine("// Include only active modules")
            modules.sorted().forEach { modulePath ->
                appendLine("include(\"$modulePath\")")
            }
        }
    }

    /**
     * 保存激活的模块配置
     */
    fun saveActiveModulesConfig(project: Project, modules: Set<String>): Boolean {
        val baseDir = project.guessProjectDir() ?: return false
        val content = generateActiveModulesFile(modules)

        return try {
            WriteCommandAction.runWriteCommandAction(project) {
                // 创建 .gradle-buddy 目录
                val buddyDir = baseDir.findChild(".gradle-buddy")
                    ?: baseDir.createChildDirectory(this, ".gradle-buddy")

                // 创建或更新配置文件
                val configFile = buddyDir.findChild("active-modules.gradle.kts")
                    ?: buddyDir.createChildData(this, "active-modules.gradle.kts")

                configFile.setBinaryContent(content.toByteArray())
            }
            logger.info("Saved active modules config: ${modules.size} modules")
            true
        } catch (e: Exception) {
            logger.error("Failed to save active modules config", e)
            false
        }
    }

    /**
     * 应用按需加载 - 修改 settings.gradle.kts
     * 将现有的 include 语句替换为只包含激活模块的版本
     */
    fun applyOnDemandLoading(project: Project, activeModules: Set<String>, syncAfter: Boolean = true): Boolean {
        val settingsFile = findSettingsFile(project) ?: return false

        return try {
            WriteCommandAction.runWriteCommandAction(project) {
                val originalContent = String(settingsFile.contentsToByteArray())
                val newContent = rewriteSettingsWithActiveModules(originalContent, activeModules)
                settingsFile.setBinaryContent(newContent.toByteArray())
            }

            logger.info("Applied on-demand loading with ${activeModules.size} modules")

            if (syncAfter) {
                triggerGradleSync(project, settingsFile)
            }
            true
        } catch (e: Exception) {
            logger.error("Failed to apply on-demand loading", e)
            false
        }
    }

    // Gradle Module Sleep 管理块的标记
    private const val GRADLE_BUDDY_START = "// >>> Gradle Module Sleep: On-Demand Modules (DO NOT EDIT THIS BLOCK) >>>"
    private const val GRADLE_BUDDY_END = "// <<< Gradle Module Sleep: End Of Block <<<"

    /**
     * 重写 settings.gradle.kts 内容，只保留激活的模块
     */
    private fun rewriteSettingsWithActiveModules(originalContent: String, activeModules: Set<String>): String {
        val lines = originalContent.lines()
        val result = StringBuilder()

        // 检查是否已有 Gradle Buddy 管理块
        val startIndex = lines.indexOfFirst { it.trim() == GRADLE_BUDDY_START }
        val endIndex = lines.indexOfFirst { it.trim() == GRADLE_BUDDY_END }

        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            // 替换现有的 Gradle Buddy 块
            for (i in lines.indices) {
                when {
                    i < startIndex -> result.appendLine(lines[i])
                    i == startIndex -> {
                        result.appendLine(generateGradleBuddyBlock(activeModules))
                    }
                    i > endIndex -> result.appendLine(lines[i])
                    // 跳过 startIndex 到 endIndex 之间的内容
                }
            }
        } else {
            // No existing block, process original include statements and add a new block if needed.
            val singleIncludePattern = Regex("""^(\s*)include\s*\(\s*["']([^"']+)["']\s*\)(.*)$""")
            val anyIncludePattern = Regex("""^\s*include\b""")
            val hasIncludeStatements = lines.any { anyIncludePattern.containsMatchIn(it) }

            for (line in lines) {
                val match = singleIncludePattern.find(line)
                if (match != null) {
                    val modulePath = match.groupValues[2]
                    if (activeModules.contains(modulePath)) {
                        result.appendLine(line)
                    } else {
                        result.appendLine("//${line} // excluded by Gradle Buddy")
                    }
                } else {
                    result.appendLine(line)
                }
            }

            // If there are no include statements (e.g. using an auto-modules plugin), add the Gradle Buddy block.
            // This prevents adding duplicate modules if the user has multi-module include() statements.
            if (!hasIncludeStatements && activeModules.isNotEmpty()) {
                result.appendLine()
                result.appendLine(generateGradleBuddyBlock(activeModules))
            }
        }

        return result.toString().trimEnd() + "\n"
    }

    /**
     * 生成 Gradle Buddy 管理块
     */
    private fun generateGradleBuddyBlock(activeModules: Set<String>): String {
        val (validModules, excludedModules) = partitionModules(activeModules)
        return buildString {
            appendLine(GRADLE_BUDDY_START)
            appendLine("// Generated at: ${java.time.LocalDateTime.now()}")
            appendLine("// Loaded: ${validModules.size}, Excluded: ${excludedModules.size}, Total: ${validModules.size + excludedModules.size}")
            if (excludedModules.isNotEmpty()) {
                appendLine("// Excluded (build infrastructure): ${excludedModules.sorted().joinToString(", ")}")
            }
            validModules.sorted().forEach { modulePath ->
                appendLine("include(\"$modulePath\")")
            }
            append(GRADLE_BUDDY_END)
        }
    }

    /**
     * 恢复所有模块（删除 Gradle Buddy 块，取消注释所有 include 语句）
     */
    fun restoreAllModules(project: Project, syncAfter: Boolean = true): Boolean {
        val settingsFile = findSettingsFile(project) ?: return false

        return try {
            WriteCommandAction.runWriteCommandAction(project) {
                val content = String(settingsFile.contentsToByteArray())
                val lines = content.lines()

                // 查找并删除 Gradle Buddy 块
                val startIndex = lines.indexOfFirst { it.trim() == GRADLE_BUDDY_START }
                val endIndex = lines.indexOfFirst { it.trim() == GRADLE_BUDDY_END }

                val filteredLines = if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
                    // 删除 Gradle Buddy 块
                    lines.filterIndexed { index, _ -> index < startIndex || index > endIndex }
                } else {
                    lines
                }

                // 取消所有被 Gradle Buddy 注释的行
                val restored = filteredLines.joinToString("\n") { line ->
                    if (line.contains("// excluded by Gradle Buddy")) {
                        line.replace(Regex("""^(\s*)//"""), "$1")
                            .replace(" // excluded by Gradle Buddy", "")
                    } else {
                        line
                    }
                }
                settingsFile.setBinaryContent(restored.trimEnd().toByteArray())
            }

            logger.info("Restored all modules")

            if (syncAfter) {
                triggerGradleSync(project, settingsFile)
            }
            true
        } catch (e: Exception) {
            logger.error("Failed to restore modules", e)
            false
        }
    }

    /**
     * 查找 settings.gradle.kts 或 settings.gradle
     */
    private fun findSettingsFile(project: Project): VirtualFile? {
        val baseDir = project.guessProjectDir() ?: return null
        return baseDir.findChild("settings.gradle.kts")
            ?: baseDir.findChild("settings.gradle")
    }

    /**
     * 触发 Gradle 同步
     */
    private fun triggerGradleSync(project: Project, settingsFile: VirtualFile) {
        @Suppress("OPT_IN_USAGE")
        GlobalScope.launch(Dispatchers.EDT) {
            try {
                linkAndSyncGradleProject(project, settingsFile.path)
            } catch (e: Exception) {
                logger.error("Failed to trigger Gradle sync", e)
            }
        }
    }

    /**
     * 一键执行：获取标签页 -> 推导模块 -> 应用配置 -> 同步
     */
    fun loadOnlyOpenTabModules(project: Project): LoadResult {
        val openFiles = getOpenEditorFiles(project)
        if (openFiles.isEmpty()) {
            return LoadResult.NoOpenFiles
        }

        val activeModules = detectModulesFromOpenFiles(project)
        if (activeModules.isEmpty()) {
            return LoadResult.NoModulesDetected(openFiles.size)
        }

        val (validModules, excludedModules) = partitionModules(activeModules)
        val success = applyOnDemandLoading(project, activeModules, syncAfter = true)
        return if (success) {
            LoadResult.Success(validModules, excludedModules)
        } else {
            LoadResult.Failed("Failed to apply settings")
        }
    }
}

/**
 * 模块描述符
 */
data class ModuleDescriptor(
    val path: String,       // Gradle 模块路径，如 ":plugins:gradle-buddy"
    val name: String,       // 模块名
    val absolutePath: String // 绝对路径
)

/**
 * 加载结果
 */
sealed class LoadResult {
    data class Success(
        val modules: Set<String>,
        val excludedModules: Set<String> = emptySet()
    ) : LoadResult() {
        val totalModules: Int get() = modules.size + excludedModules.size
    }
    data object NoOpenFiles : LoadResult()
    data class NoModulesDetected(val openFileCount: Int) : LoadResult()
    data class Failed(val reason: String) : LoadResult()
}
