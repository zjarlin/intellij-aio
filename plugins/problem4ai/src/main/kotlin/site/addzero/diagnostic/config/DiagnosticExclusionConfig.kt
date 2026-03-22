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
    @Volatile
    private var compiledMatchers: List<PathPatternMatcher>? = null

    companion object {
        private const val MIN_SCAN_LIMIT = 1
        private const val MAX_SCAN_LIMIT = 10_000

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

        const val DEFAULT_MAX_FULL_SCAN_FILES = 50
        const val DEFAULT_MAX_INCREMENTAL_SCAN_FILES = 50
    }

    override fun getState(): ExclusionState = state

    override fun loadState(state: ExclusionState) {
        this.state = state
        invalidateMatcherCache()
    }

    fun isExcluded(file: VirtualFile): Boolean {
        if (file.isDirectory) {
            return true
        }

        val filePath = normalizePath(file.path)
        return getCompiledMatchers().any { matcher ->
            matcher.matches(filePath)
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
        invalidateMatcherCache()
    }

    fun addCustomPattern(pattern: String) {
        if (pattern.isNotBlank() && !state.customPatterns.contains(pattern)) {
            state.customPatterns = state.customPatterns + pattern
            invalidateMatcherCache()
        }
    }

    fun removeCustomPattern(pattern: String) {
        state.customPatterns = state.customPatterns.filter { it != pattern }
        invalidateMatcherCache()
    }

    fun isUseDefaultPatterns(): Boolean = state.useDefaultPatterns

    fun setUseDefaultPatterns(use: Boolean) {
        state.useDefaultPatterns = use
        invalidateMatcherCache()
    }

    fun getMaxFullScanFiles(): Int {
        return normalizeScanLimit(state.maxFullScanFiles, DEFAULT_MAX_FULL_SCAN_FILES)
    }

    fun setMaxFullScanFiles(limit: Int) {
        state.maxFullScanFiles = normalizeScanLimit(limit, DEFAULT_MAX_FULL_SCAN_FILES)
    }

    fun getMaxIncrementalScanFiles(): Int {
        return normalizeScanLimit(state.maxIncrementalScanFiles, DEFAULT_MAX_INCREMENTAL_SCAN_FILES)
    }

    fun setMaxIncrementalScanFiles(limit: Int) {
        state.maxIncrementalScanFiles = normalizeScanLimit(limit, DEFAULT_MAX_INCREMENTAL_SCAN_FILES)
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
        invalidateMatcherCache()
    }

    private fun invalidateMatcherCache() {
        compiledMatchers = null
    }

    private fun getCompiledMatchers(): List<PathPatternMatcher> {
        compiledMatchers?.let { return it }

        synchronized(this) {
            compiledMatchers?.let { return it }
            val matchers = getEffectivePatterns()
                .mapNotNull { pattern -> createMatcher(pattern) }
            compiledMatchers = matchers
            return matchers
        }
    }

    private fun createMatcher(pattern: String): PathPatternMatcher? {
        val normalizedPattern = normalizePath(pattern).trim()
        if (normalizedPattern.isBlank()) {
            return null
        }

        return when {
            normalizedPattern.contains("**") || normalizedPattern.contains("*") || normalizedPattern.contains("?") -> {
                GlobPathMatcher(normalizedPattern)
            }
            normalizedPattern.endsWith("/") -> {
                val dirPattern = normalizedPattern.removeSuffix("/")
                DirectoryPathMatcher(dirPattern)
            }
            else -> {
                ContainsPathMatcher(normalizedPattern)
            }
        }
    }

    private fun normalizePath(value: String): String {
        return value.replace('\\', '/')
    }

    private fun convertGitignorePattern(pattern: String): String {
        return when {
            pattern.startsWith("/") -> pattern
            pattern.startsWith("**/") -> pattern
            pattern.contains("/") -> "**/$pattern"
            else -> "**/$pattern"
        }
    }

    private fun normalizeScanLimit(value: Int, defaultValue: Int): Int {
        val normalized = if (value > 0) value else defaultValue
        return normalized.coerceIn(MIN_SCAN_LIMIT, MAX_SCAN_LIMIT)
    }

    private fun interface PathPatternMatcher {
        fun matches(filePath: String): Boolean
    }

    private class ContainsPathMatcher(
        private val pattern: String
    ) : PathPatternMatcher {
        override fun matches(filePath: String): Boolean {
            return filePath == pattern || filePath.contains(pattern)
        }
    }

    private class DirectoryPathMatcher(
        pattern: String
    ) : PathPatternMatcher {
        private val directoryPath = pattern.removeSuffix("/")

        override fun matches(filePath: String): Boolean {
            return filePath.contains("/$directoryPath/") || filePath.startsWith("$directoryPath/")
        }
    }

    private class GlobPathMatcher(
        pattern: String
    ) : PathPatternMatcher {
        private val tokens: List<GlobToken>

        init {
            val normalizedPattern = pattern.removePrefix("/")
            val rawTokens = normalizedPattern.split("/")
                .filter { it.isNotBlank() }
                .map { segment ->
                    if (segment == "**") {
                        GlobToken.DoubleStar
                    } else {
                        GlobToken.Segment(segment)
                    }
                }
            tokens = if (pattern.startsWith("/") || rawTokens.firstOrNull() is GlobToken.DoubleStar) {
                rawTokens
            } else {
                listOf(GlobToken.DoubleStar) + rawTokens
            }
        }

        override fun matches(filePath: String): Boolean {
            val pathSegments = filePath.split("/")
                .filter { it.isNotBlank() }
            if (tokens.isEmpty()) {
                return pathSegments.isEmpty()
            }
            return matchesFrom(0, 0, pathSegments, HashMap())
        }

        private fun matchesFrom(
            tokenIndex: Int,
            pathIndex: Int,
            pathSegments: List<String>,
            memo: MutableMap<Pair<Int, Int>, Boolean>
        ): Boolean {
            val key = tokenIndex to pathIndex
            memo[key]?.let { return it }

            val matched = when {
                tokenIndex == tokens.size -> pathIndex == pathSegments.size
                tokens[tokenIndex] === GlobToken.DoubleStar -> {
                    if (tokenIndex == tokens.lastIndex) {
                        true
                    } else {
                        var nextPathIndex = pathIndex
                        var found = false
                        while (nextPathIndex <= pathSegments.size) {
                            if (matchesFrom(tokenIndex + 1, nextPathIndex, pathSegments, memo)) {
                                found = true
                                break
                            }
                            nextPathIndex++
                        }
                        found
                    }
                }
                pathIndex >= pathSegments.size -> false
                else -> {
                    val segmentToken = tokens[tokenIndex] as GlobToken.Segment
                    segmentToken.matches(pathSegments[pathIndex]) &&
                        matchesFrom(tokenIndex + 1, pathIndex + 1, pathSegments, memo)
                }
            }

            memo[key] = matched
            return matched
        }
    }

    private sealed interface GlobToken {
        data object DoubleStar : GlobToken

        class Segment(
            private val glob: String
        ) : GlobToken {
            fun matches(segment: String): Boolean {
                var globIndex = 0
                var segmentIndex = 0
                var starIndex = -1
                var retrySegmentIndex = -1

                while (segmentIndex < segment.length) {
                    if (globIndex < glob.length && (glob[globIndex] == '?' || glob[globIndex] == segment[segmentIndex])) {
                        globIndex++
                        segmentIndex++
                        continue
                    }

                    if (globIndex < glob.length && glob[globIndex] == '*') {
                        starIndex = globIndex
                        retrySegmentIndex = segmentIndex
                        globIndex++
                        continue
                    }

                    if (starIndex >= 0) {
                        globIndex = starIndex + 1
                        retrySegmentIndex++
                        segmentIndex = retrySegmentIndex
                        continue
                    }

                    return false
                }

                while (globIndex < glob.length && glob[globIndex] == '*') {
                    globIndex++
                }
                return globIndex == glob.length
            }
        }
    }
}

data class ExclusionState(
    var customPatterns: List<String> = emptyList(),
    var useDefaultPatterns: Boolean = true,
    var gitignoreLoaded: Boolean = false,
    var enabledFileExtensions: List<String> = DEFAULT_FILE_EXTENSIONS,
    var maxFullScanFiles: Int = DiagnosticExclusionConfig.DEFAULT_MAX_FULL_SCAN_FILES,
    var maxIncrementalScanFiles: Int = DiagnosticExclusionConfig.DEFAULT_MAX_INCREMENTAL_SCAN_FILES
) {
    companion object {
        val DEFAULT_FILE_EXTENSIONS = listOf("java", "kt")
    }
}
