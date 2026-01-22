package site.addzero.gradle.catalog

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlTable
import org.toml.lang.psi.ext.TomlLiteralKind
import org.toml.lang.psi.ext.kind

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
     * 查找项目中的版本目录文件
     */
    private fun findVersionCatalogFiles(): List<VirtualFile> {
        val result = mutableListOf<VirtualFile>()

        // 查找 gradle/libs.versions.toml 等标准位置
        val basePath = project.basePath ?: run {
            println("[CatalogReferenceScanner] No base path")
            return emptyList()
        }
        println("[CatalogReferenceScanner] Base path: $basePath")

        val baseDir = com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(basePath) ?: run {
            println("[CatalogReferenceScanner] Base dir not found")
            return emptyList()
        }

        // 1. 查找当前项目根目录的 gradle 目录
        val gradleDir = baseDir.findChild("gradle")
        if (gradleDir != null && gradleDir.isDirectory) {
            println("[CatalogReferenceScanner] Found gradle dir: ${gradleDir.path}")
            gradleDir.children.forEach { file ->
                if (file.extension == "toml" && file.name.endsWith(".versions.toml")) {
                    println("[CatalogReferenceScanner] Found TOML: ${file.path}")
                    result.add(file)
                }
            }
        } else {
            println("[CatalogReferenceScanner] No gradle dir in root")
        }

        // 2. 递归查找所有子项目的 gradle 目录（支持多模块项目）
        findGradleDirectoriesRecursively(baseDir, result, 0, 5)

        println("[CatalogReferenceScanner] Total TOML files found: ${result.size}")
        result.forEach { println("[CatalogReferenceScanner]   - ${it.path}") }

        return result
    }

    /**
     * 递归查找 gradle 目录
     */
    private fun findGradleDirectoriesRecursively(
        dir: VirtualFile,
        result: MutableList<VirtualFile>,
        depth: Int,
        maxDepth: Int
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
                    if (!result.contains(file)) {
                        result.add(file)
                    }
                }
            }
        }

        // 递归扫描子目录
        dir.children.forEach { child ->
            if (child.isDirectory) {
                findGradleDirectoriesRecursively(child, result, depth + 1, maxDepth)
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

        val psiFile = PsiManager.getInstance(project).findFile(file) as? TomlFile ?: run {
            println("[CatalogReferenceScanner] Failed to parse TOML: ${file.path}")
            return emptySet()
        }

        println("[CatalogReferenceScanner] Parsing TOML: ${file.path}")

        // 查找 [libraries] 和 [plugins] 表
        psiFile.children.forEach { element ->
            if (element is TomlTable) {
                val header = element.header.key?.text
                println("[CatalogReferenceScanner]   Found table: [$header]")
                when (header) {
                    "libraries" -> {
                        // 提取 libraries 中的所有键
                        element.entries.forEach { entry ->
                            if (entry is TomlKeyValue) {
                                val key = entry.key.text
                                // 转换为点分隔格式
                                val alias = convertTomlKeyToDotNotation(key)
                                println("[CatalogReferenceScanner]     Library: $key -> $alias")
                                aliases.add(alias)
                            }
                        }
                    }
                    "plugins" -> {
                        // 提取 plugins 中的所有键，添加 plugin 前缀
                        element.entries.forEach { entry ->
                            if (entry is TomlKeyValue) {
                                val key = entry.key.text
                                // 对于 plugins，需要特殊处理
                                // gradle-plugin-ksp -> gradle.plugin.ksp
                                val alias = convertPluginKeyToDotNotation(key)
                                println("[CatalogReferenceScanner]     Plugin: $key -> $alias")
                                aliases.add(alias)
                            }
                        }
                    }
                }
            }
        }

        println("[CatalogReferenceScanner] Total aliases extracted: ${aliases.size}")

        return aliases
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

    /**
     * 将插件键转换为点分隔格式
     * 例如: gradle-plugin-ksp -> gradle.plugin.ksp
     */
    private fun convertPluginKeyToDotNotation(key: String): String {
        val cleanKey = key.trim('"', '\'')
        // 特殊处理 gradle-plugin- 前缀
        if (cleanKey.startsWith("gradle-plugin-")) {
            val pluginName = cleanKey.removePrefix("gradle-plugin-")
            return "gradle.plugin." + pluginName.replace('-', '.').replace('_', '.')
        }
        return cleanKey.replace('-', '.').replace('_', '.')
    }
}
