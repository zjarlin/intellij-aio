package site.addzero.diagnostic.config

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

@State(
    name = "DiagnosticExclusionConfig",
    storages = [Storage("problem4ai-exclusions.xml")]
)
@Service(Service.Level.PROJECT)
class DiagnosticExclusionConfig : PersistentStateComponent<ExclusionState> {

    private var state = ExclusionState()

    companion object {
        fun getInstance(project: Project): DiagnosticExclusionConfig =
            project.getService(DiagnosticExclusionConfig::class.java)

        val DEFAULT_PATTERNS = listOf(
            "**/build/**",
            "**/out/**",
            "**/target/**",
            "**/bin/**",
            "**/obj/**",
            "**/node_modules/**",
            "**/.gradle/**",
            "**/.idea/**",
            "**/.vscode/**",
            "**/generated/**",
            "**/gen/**",
            "*.min.js",
            "*.min.css",
            "*.log",
            "*.tmp",
            "**/.git/**",
            "**/.svn/**"
        )
    }

    override fun getState(): ExclusionState = state

    override fun loadState(state: ExclusionState) {
        this.state = state
    }

    fun isExcluded(file: VirtualFile): Boolean {
        if (file.isDirectory) return true

        val filePath = file.path
        val patterns = getEffectivePatterns()

        return patterns.any { pattern ->
            matchesPattern(filePath, pattern)
        }
    }

    fun shouldScanFile(file: VirtualFile): Boolean {
        if (file.isDirectory) return true
        return !isExcluded(file)
    }

    fun getEffectivePatterns(): List<String> {
        val patterns = mutableListOf<String>()
        if (state.useDefaultPatterns) {
            patterns.addAll(DEFAULT_PATTERNS)
        }
        patterns.addAll(state.customPatterns)
        return patterns
    }

    fun getCustomPatterns(): List<String> = state.customPatterns.toList()

    fun setCustomPatterns(patterns: List<String>) {
        state.customPatterns = patterns.filter { it.isNotBlank() }.distinct()
    }

    fun addCustomPattern(pattern: String) {
        if (pattern.isNotBlank() && !state.customPatterns.contains(pattern)) {
            state.customPatterns = state.customPatterns + pattern
        }
    }

    fun removeCustomPattern(pattern: String) {
        state.customPatterns = state.customPatterns.filter { it != pattern }
    }

    fun isUseDefaultPatterns(): Boolean = state.useDefaultPatterns

    fun setUseDefaultPatterns(use: Boolean) {
        state.useDefaultPatterns = use
    }

    /**
     * 获取启用的文件扩展名列表
     */
    fun getEnabledFileExtensions(): List<String> {
        return state.enabledFileExtensions.takeIf { it.isNotEmpty() }
            ?: ExclusionState.DEFAULT_FILE_EXTENSIONS
    }

    /**
     * 设置启用的文件扩展名列表
     */
    fun setEnabledFileExtensions(extensions: List<String>) {
        state.enabledFileExtensions = extensions
            .map { it.lowercase().removePrefix(".") }
            .filter { it.isNotBlank() }
            .distinct()
    }

    /**
     * 检查文件扩展名是否应该被扫描
     */
    fun isEnabledExtension(file: VirtualFile): Boolean {
        val ext = file.extension?.lowercase() ?: return false
        return ext in getEnabledFileExtensions()
    }

    /**
     * 从.gitignore加载排除规则，只在首次调用时执行
     */
    fun loadFromGitignore(project: Project) {
        if (state.gitignoreLoaded) return

        val baseDir = project.basePath ?: return
        val gitignoreFile = java.io.File(baseDir, ".gitignore")

        if (!gitignoreFile.exists()) {
            state.gitignoreLoaded = true
            return
        }

        val patterns = gitignoreFile.readLines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { convertGitignorePattern(it) }
            .filter { it.isNotBlank() }

        val existing = state.customPatterns.toMutableSet()
        existing.addAll(patterns)
        state.customPatterns = existing.toList()
        state.gitignoreLoaded = true
    }

    private fun matchesPattern(filePath: String, pattern: String): Boolean {
        return try {
            when {
                filePath == pattern -> true
                pattern.contains("**") -> matchGlobPattern(filePath, pattern)
                pattern.contains("*") -> matchGlobPattern(filePath, pattern)
                pattern.endsWith("/") -> filePath.contains("/${pattern.removeSuffix("/")}/")
                        || filePath.startsWith("${pattern.removeSuffix("/")}/")
                else -> filePath.contains(pattern)
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun matchGlobPattern(filePath: String, pattern: String): Boolean {
        val regex = pattern
            .replace(".**", "PLACEHOLDER_DOTSTAR")
            .replace("**", "PLACEHOLDER_DOUBLESTAR")
            .replace("*", "PLACEHOLDER_STAR")
            .replace("?", "PLACEHOLDER_QUESTION")
            .replace(".", "\\.")
            .replace("PLACEHOLDER_DOTSTAR", ".*")
            .replace("PLACEHOLDER_DOUBLESTAR", ".*")
            .replace("PLACEHOLDER_STAR", "[^/]*")
            .replace("PLACEHOLDER_QUESTION", ".")
            .replace("/", "\\/")

        return try {
            val fullPattern = if (pattern.startsWith("**/")) {
                ".*" + regex.removePrefix("\\/")
            } else if (pattern.startsWith("/")) {
                regex
            } else {
                ".*" + regex
            }
            filePath.matches(Regex(fullPattern))
        } catch (e: Exception) {
            filePath.contains(pattern.replace("*", "").replace("**", ""))
        }
    }

    private fun convertGitignorePattern(pattern: String): String {
        return when {
            pattern.startsWith("/") -> pattern
            pattern.startsWith("**/") -> pattern
            pattern.contains("/") -> "**/$pattern"
            else -> "**/$pattern"
        }
    }
}

data class ExclusionState(
    var customPatterns: List<String> = emptyList(),
    var useDefaultPatterns: Boolean = true,
    var gitignoreLoaded: Boolean = false,
    var enabledFileExtensions: List<String> = DEFAULT_FILE_EXTENSIONS
) {
    companion object {
        val DEFAULT_FILE_EXTENSIONS = listOf("java", "kt")
    }
}
