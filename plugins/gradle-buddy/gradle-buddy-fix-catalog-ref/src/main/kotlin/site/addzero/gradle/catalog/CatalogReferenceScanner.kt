package site.addzero.gradle.catalog

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlTable
import site.addzero.gradle.buddy.settings.GradleBuddySettingsService

/**
 * 扫描 TOML 版本目录文件，提取所有声明的依赖别名
 */
class CatalogReferenceScanner(private val project: Project) {

    /**
     * 扫描项目中所有的版本目录文件
     * @return Map<目录名称, 依赖别名集合>
     */
    fun scanAllCatalogs(): Map<String, Set<String>> {
        val catalogs = mutableMapOf<String, MutableSet<String>>()

        // 查找所有 .toml 文件
        val tomlFiles = findVersionCatalogFiles()

        for (virtualFile in tomlFiles) {
            val catalogName = getCatalogName(virtualFile)
            val aliases = extractDependencyAliases(virtualFile)
            catalogs.getOrPut(catalogName) { mutableSetOf() }.addAll(aliases)
        }

        return catalogs
    }

    /**
     * 查找项目中的版本目录文件。
     *
     * 优先通过 GradleBuddySettingsService.resolveVersionCatalogFile() 获取配置的 TOML，
     * 再通过 GradleSettings.linkedProjectsSettings 获取所有 Gradle root 下的 TOML，
     * 最后 fallback 到 basePath 递归扫描。
     */
    private fun findVersionCatalogFiles(): List<VirtualFile> {
        val result = mutableListOf<VirtualFile>()
        val lfs = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
        val seen = mutableSetOf<String>()

        // 1. 通过 GradleBuddySettingsService 获取配置的 TOML 文件
        try {
            val settingsService = GradleBuddySettingsService.getInstance(project)
            val configuredFile = settingsService.resolveVersionCatalogFile(project)
            if (configuredFile.exists()) {
                val vf = lfs.findFileByIoFile(configuredFile)
                if (vf != null && seen.add(vf.path)) {
                    result.add(vf)
                }
            }
        } catch (_: Throwable) {
            // GradleBuddySettingsService 不可用时跳过
        }

        // 2. 通过 GradleSettings 获取所有 linked Gradle project root 下的 TOML
        try {
            val gradleSettings = org.jetbrains.plugins.gradle.settings.GradleSettings.getInstance(project)
            for (linkedProject in gradleSettings.linkedProjectsSettings) {
                val rootPath = linkedProject.externalProjectPath
                val rootDir = lfs.findFileByPath(rootPath) ?: continue
                val gradleDir = rootDir.findChild("gradle") ?: continue
                if (!gradleDir.isDirectory) continue
                gradleDir.children.forEach { file ->
                    if (file.extension == "toml" && file.name.endsWith(".versions.toml")) {
                        if (seen.add(file.path)) {
                            result.add(file)
                        }
                    }
                }
            }
        } catch (_: Throwable) {
            // GradleSettings 不可用时跳过
        }

        // 3. fallback: basePath 递归扫描
        val basePath = project.basePath
        if (basePath != null) {
            val baseDir = lfs.findFileByPath(basePath)
            if (baseDir != null) {
                // 先检查根目录的 gradle 目录
                val gradleDir = baseDir.findChild("gradle")
                if (gradleDir != null && gradleDir.isDirectory) {
                    gradleDir.children.forEach { file ->
                        if (file.extension == "toml" && file.name.endsWith(".versions.toml")) {
                            if (seen.add(file.path)) {
                                result.add(file)
                            }
                        }
                    }
                }
                // 递归查找子项目
                findGradleDirectoriesRecursively(baseDir, result, 0, 5, seen)
            }
        }

        return result
    }

    /**
     * 递归查找 gradle 目录
     */
    private fun findGradleDirectoriesRecursively(
        dir: VirtualFile,
        result: MutableList<VirtualFile>,
        depth: Int,
        maxDepth: Int,
        seen: MutableSet<String> = mutableSetOf()
    ) {
        if (depth > maxDepth) return
        if (!dir.isDirectory) return

        // 跳过一些不需要扫描的目录
        val skipDirs = setOf("build", "out", ".gradle", ".idea", "node_modules", "target", ".git")
        if (dir.name in skipDirs) return

        // 检查当前目录是否有 gradle 子目录
        val gradleDir = dir.findChild("gradle")
        if (gradleDir != null && gradleDir.isDirectory) {
            gradleDir.children.forEach { file ->
                if (file.extension == "toml" && file.name.endsWith(".versions.toml")) {
                    if (seen.add(file.path)) {
                        result.add(file)
                    }
                }
            }
        }

        // 递归扫描子目录
        dir.children.forEach { child ->
            if (child.isDirectory) {
                findGradleDirectoriesRecursively(child, result, depth + 1, maxDepth, seen)
            }
        }
    }

    /**
     * 从文件名推断目录名称
     * 例如: libs.versions.toml -> libs
     */
    private fun getCatalogName(file: VirtualFile): String {
        val fileName = file.nameWithoutExtension
        // 移除 .versions 后缀
        return fileName.removeSuffix(".versions")
    }

    /**
     * 从 TOML 文件中提取依赖别名
     */
    private fun extractDependencyAliases(file: VirtualFile): Set<String> {
        val aliases = mutableSetOf<String>()

        val psiFile = PsiManager.getInstance(project).findFile(file) as? TomlFile ?: return emptySet()

        // 查找 [libraries]、[plugins]、[versions]、[bundles] 表
        psiFile.children.forEach { element ->
            if (element is TomlTable) {
                val header = element.header.key?.text
                when (header) {
                    "libraries" -> {
                        element.entries.forEach { entry ->
                            if (entry is TomlKeyValue) {
                                val key = entry.key.text
                                val alias = convertTomlKeyToDotNotation(key)
                                aliases.add(alias)
                            }
                        }
                    }
                    "plugins" -> {
                        element.entries.forEach { entry ->
                            if (entry is TomlKeyValue) {
                                val key = entry.key.text
                                val alias = "plugins." + convertTomlKeyToDotNotation(key)
                                aliases.add(alias)
                            }
                        }
                    }
                    "versions" -> {
                        element.entries.forEach { entry ->
                            if (entry is TomlKeyValue) {
                                val key = entry.key.text
                                val alias = "versions." + convertTomlKeyToDotNotation(key)
                                aliases.add(alias)
                            }
                        }
                    }
                    "bundles" -> {
                        element.entries.forEach { entry ->
                            if (entry is TomlKeyValue) {
                                val key = entry.key.text
                                val alias = "bundles." + convertTomlKeyToDotNotation(key)
                                aliases.add(alias)
                            }
                        }
                    }
                }
            }
        }

        return aliases
    }

    /**
     * 扫描所有版本目录文件，提取 [plugins] 中的 plugin ID → accessor 映射
     * @return Map<目录名称, Map<pluginId, aliasAccessor>>
     *
     * 例如 TOML 中:
     *   koin-compiler = { id = "io.insert-koin.koin-compiler", version.ref = "..." }
     * 返回:
     *   "libs" -> { "io.insert-koin.koin-compiler" -> "plugins.koin.compiler" }
     */
    fun scanPluginIdToAlias(): Map<String, Map<String, String>> {
        val result = mutableMapOf<String, MutableMap<String, String>>()

        val tomlFiles = findVersionCatalogFiles()
        for (virtualFile in tomlFiles) {
            val catalogName = getCatalogName(virtualFile)
            val pluginIdMap = extractPluginIdMapping(virtualFile)
            if (pluginIdMap.isNotEmpty()) {
                result.getOrPut(catalogName) { mutableMapOf() }.putAll(pluginIdMap)
            }
        }

        return result
    }

    /**
     * 从 TOML 文件中提取 [plugins] 的 pluginId → accessor 映射
     */
    private fun extractPluginIdMapping(file: VirtualFile): Map<String, String> {
        val mapping = mutableMapOf<String, String>()

        val psiFile = PsiManager.getInstance(project).findFile(file) as? TomlFile ?: return emptyMap()

        psiFile.children.forEach { element ->
            if (element is TomlTable) {
                val header = element.header.key?.text
                if (header == "plugins") {
                    element.entries.forEach { entry ->
                        if (entry is TomlKeyValue) {
                            val alias = entry.key.text.trim('"', '\'')
                            val accessor = "plugins." + convertTomlKeyToDotNotation(alias)

                            // 提取 id 字段
                            val pluginId = extractPluginId(entry)
                            if (pluginId != null) {
                                mapping[pluginId] = accessor
                            }
                        }
                    }
                }
            }
        }

        return mapping
    }

    /**
     * 从 TOML plugin entry 中提取 id 字段值
     * 支持格式:
     *   koin-compiler = { id = "io.insert-koin.koin-compiler", version.ref = "..." }
     *   koin-compiler = "io.insert-koin.koin-compiler:1.0.0"
     */
    private fun extractPluginId(entry: TomlKeyValue): String? {
        val value = entry.value ?: return null

        // 内联表格: { id = "...", ... }
        if (value is org.toml.lang.psi.TomlInlineTable) {
            for (kv in value.entries) {
                if (kv.key.text == "id") {
                    val literal = kv.value ?: continue
                    val text = literal.text.trim('"', '\'')
                    if (text.isNotEmpty()) return text
                }
            }
        }

        // 简写字符串格式: "plugin.id:version"
        if (value is org.toml.lang.psi.TomlLiteral) {
            val str = value.text.trim('"', '\'')
            if (str.isNotEmpty()) {
                // 可能是 "plugin.id:version" 或 "plugin.id"
                return str.substringBefore(":")
            }
        }

        return null
    }

    /**
     * 将 TOML 键转换为点分隔格式
     * 例如: my-library -> my.library
     */
    private fun convertTomlKeyToDotNotation(key: String): String {
        // 移除引号
        val cleanKey = key.trim('"', '\'')
        // 将连字符和下划线转换为点
        return cleanKey.replace('-', '.').replace('_', '.')
    }
}
