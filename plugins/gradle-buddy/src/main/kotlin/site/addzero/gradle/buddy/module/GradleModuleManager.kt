package site.addzero.gradle.buddy.module

import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gradle.service.project.open.linkAndSyncGradleProject

/**
 * Gradle 模块管理器
 * 通过修改 settings.gradle.kts 动态控制模块的 include/exclude
 */
object GradleModuleManager {

    private val logger = Logger.getInstance(GradleModuleManager::class.java)

    // 不可释放的模块（构建必需）
    private val PROTECTED_MODULES = setOf("buildSrc", "build-logic", "gradle")
    
    // include 语句正则 - 匹配 include(":module:path")
    private val INCLUDE_PATTERN = Regex("""^(\s*)(include\s*\(\s*["']:?([^"']+)["']\s*\))""", RegexOption.MULTILINE)
    // 匹配被注释的 include: //include(":module") 或 // include(":module")
    private val COMMENTED_INCLUDE_PATTERN = Regex("""^(\s*)//\s*(include\s*\(\s*["']:?([^"']+)["']\s*\))""", RegexOption.MULTILINE)
    
    // includeBuild 语句正则（这些不能被释放）
    private val INCLUDE_BUILD_PATTERN = Regex("""includeBuild\s*\(\s*["']([^"']+)["']\s*\)""")

    /**
     * 获取 settings.gradle.kts 或 settings.gradle 文件
     */
    fun getSettingsFile(project: Project): VirtualFile? {
        val baseDir = project.guessProjectDir() ?: return null
        return baseDir.findChild("settings.gradle.kts") 
            ?: baseDir.findChild("settings.gradle")
    }

    /**
     * 解析当前已 include 的模块
     */
    fun getIncludedModules(project: Project): List<ModuleInfo> {
        val settingsFile = getSettingsFile(project) ?: return emptyList()
        val content = String(settingsFile.contentsToByteArray())
        
        val modules = mutableListOf<ModuleInfo>()
        
        INCLUDE_PATTERN.findAll(content).forEach { match ->
            val modulePath = match.groupValues[3]
            val moduleName = modulePath.trimStart(':').split(':').last()
            
            if (!isProtectedModule(moduleName)) {
                modules.add(ModuleInfo(
                    path = modulePath,
                    name = moduleName,
                    isIncluded = true,
                    matchRange = match.range
                ))
            }
        }
        
        return modules
    }

    /**
     * 解析已被注释掉（excluded）的模块
     */
    fun getExcludedModules(project: Project): List<ModuleInfo> {
        val settingsFile = getSettingsFile(project) ?: return emptyList()
        val content = String(settingsFile.contentsToByteArray())
        
        val modules = mutableListOf<ModuleInfo>()
        
        COMMENTED_INCLUDE_PATTERN.findAll(content).forEach { match ->
            val modulePath = match.groupValues[3]
            val moduleName = modulePath.trimStart(':').split(':').last()
            
            if (!isProtectedModule(moduleName)) {
                modules.add(ModuleInfo(
                    path = modulePath,
                    name = moduleName,
                    isIncluded = false,
                    matchRange = match.range
                ))
            }
        }
        
        return modules
    }

    /**
     * 释放（exclude）指定模块 - 通过注释 include 语句
     */
    fun excludeModule(project: Project, modulePath: String): Boolean {
        if (isProtectedModule(modulePath)) {
            logger.warn("Cannot exclude protected module: $modulePath")
            return false
        }
        
        val settingsFile = getSettingsFile(project) ?: return false
        
        return try {
            WriteCommandAction.runWriteCommandAction(project) {
                var content = String(settingsFile.contentsToByteArray())
                
                // 查找并注释掉 include 语句 - 生成格式: //include(":module")
                val pattern = Regex("""^(\s*)(include\s*\(\s*["']${Regex.escape(modulePath)}["']\s*\))""", RegexOption.MULTILINE)
                content = pattern.replace(content) { match ->
                    "${match.groupValues[1]}//${match.groupValues[2]}"
                }
                
                settingsFile.setBinaryContent(content.toByteArray())
            }
            logger.info("Excluded module: $modulePath")
            true
        } catch (e: Exception) {
            logger.error("Failed to exclude module: $modulePath", e)
            e.printStackTrace()
            false
        }
    }

    /**
     * 恢复（include）指定模块 - 通过取消注释 include 语句
     */
    fun includeModule(project: Project, modulePath: String): Boolean {
        val settingsFile = getSettingsFile(project) ?: return false
        
        return try {
            WriteCommandAction.runWriteCommandAction(project) {
                var content = String(settingsFile.contentsToByteArray())
                
                // 查找被注释的 include 语句并恢复
                val pattern = Regex("""^(\s*)//\s*(include\s*\(\s*["']${Regex.escape(modulePath)}["']\s*\))(\s*//.*)?""", RegexOption.MULTILINE)
                content = pattern.replace(content) { match ->
                    "${match.groupValues[1]}${match.groupValues[2]}"
                }
                
                settingsFile.setBinaryContent(content.toByteArray())
            }
            logger.info("Included module: $modulePath")
            true
        } catch (e: Exception) {
            logger.error("Failed to include module: $modulePath", e)
            e.printStackTrace()
            false
        }
    }

    /**
     * 批量释放模块
     */
    fun excludeModules(project: Project, modulePaths: List<String>): Int {
        var count = 0
        modulePaths.forEach { path ->
            if (excludeModule(project, path)) count++
        }
        if (count > 0) {
            triggerGradleSync(project)
        }
        return count
    }

    /**
     * 批量恢复模块
     */
    fun includeModules(project: Project, modulePaths: List<String>): Int {
        var count = 0
        modulePaths.forEach { path ->
            if (includeModule(project, path)) count++
        }
        if (count > 0) {
            triggerGradleSync(project)
        }
        return count
    }

    /**
     * 触发 Gradle 同步
     */
    fun triggerGradleSync(project: Project) {
        val settingsFile = getSettingsFile(project) ?: return
        
        @Suppress("OPT_IN_USAGE")
        GlobalScope.launch(Dispatchers.EDT) {
            try {
                linkAndSyncGradleProject(project, settingsFile.path)
            } catch (e: Exception) {
                logger.error("Failed to trigger Gradle sync", e)
                e.printStackTrace()
            }
        }
    }

    /**
     * 检查模块是否受保护（不能被释放）
     */
    private fun isProtectedModule(modulePath: String): Boolean {
        val moduleName = modulePath.trimStart(':').split(':').last()
        return PROTECTED_MODULES.any { 
            moduleName.equals(it, ignoreCase = true) || 
            modulePath.contains(it, ignoreCase = true) 
        }
    }

    /**
     * 获取 includeBuild 的路径列表（这些不能被释放）
     */
    fun getIncludeBuildPaths(project: Project): List<String> {
        val settingsFile = getSettingsFile(project) ?: return emptyList()
        val content = String(settingsFile.contentsToByteArray())
        
        return INCLUDE_BUILD_PATTERN.findAll(content).map { it.groupValues[1] }.toList()
    }
}

/**
 * 模块信息
 */
data class ModuleInfo(
    val path: String,
    val name: String,
    val isIncluded: Boolean,
    val matchRange: IntRange
)
