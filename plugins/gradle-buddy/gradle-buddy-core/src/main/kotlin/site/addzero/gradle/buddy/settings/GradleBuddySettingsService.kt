package site.addzero.gradle.buddy.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import org.jetbrains.plugins.gradle.settings.GradleSettings
import java.io.File
import java.util.LinkedHashMap
import java.util.LinkedHashSet

@Service(Service.Level.PROJECT)
@State(
    name = "GradleBuddySettings",
    storages = [Storage("gradleBuddySettings.xml")]
)
class GradleBuddySettingsService : PersistentStateComponent<GradleBuddySettingsService.State> {

    data class State(
        var defaultTasks: MutableList<String> = DEFAULT_TASKS.toMutableList(),
        var versionCatalogPath: String = "",
        var versionCatalogPathCustomized: Boolean = false,
        var externalLibraryRepoPath: String = DEFAULT_EXTERNAL_LIBRARY_REPO_PATH,
        var silentUpsertToml: Boolean = false,
        var normalizeDedupStrategy: String = "MAJOR_VERSION",
        var preferredMirrorIndex: Int = 0,
        var autoUpdateWrapper: Boolean = false
    )

    private data class RawCatalogCandidate(
        val file: File,
        val preferredPath: String,
        val score: Int
    )

    private data class CatalogCandidate(
        val file: File,
        val storagePath: String,
        val score: Int
    )

    private var myState = State()
    private val catalogCandidateCacheLock = Any()

    @Volatile
    private var cachedCatalogCandidates: List<CatalogCandidate> = emptyList()

    @Volatile
    private var cachedCatalogCandidatesKey: String? = null

    @Volatile
    private var cachedCatalogCandidatesAt: Long = 0L

    override fun getState(): State = myState

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, myState)
        if (myState.versionCatalogPath.isBlank()) {
            myState.versionCatalogPathCustomized = false
        } else if (!myState.versionCatalogPathCustomized &&
            myState.versionCatalogPath.trim() != DEFAULT_VERSION_CATALOG_PATH
        ) {
            myState.versionCatalogPathCustomized = true
        }
        if (myState.externalLibraryRepoPath.isBlank()) {
            myState.externalLibraryRepoPath = DEFAULT_EXTERNAL_LIBRARY_REPO_PATH
        }
    }

    fun getDefaultTasks(): List<String> = myState.defaultTasks.toList()

    fun setDefaultTasks(tasks: List<String>) {
        myState.defaultTasks = tasks.toMutableList()
    }

    fun getVersionCatalogPath(): String = myState.versionCatalogPath

    fun getConfiguredVersionCatalogPath(): String = myState.versionCatalogPath.trim()

    fun isVersionCatalogPathCustomized(): Boolean {
        return myState.versionCatalogPathCustomized && myState.versionCatalogPath.isNotBlank()
    }

    fun getEffectiveVersionCatalogPath(project: Project): String {
        if (isVersionCatalogPathCustomized()) {
            return getConfiguredVersionCatalogPath()
        }

        if (findDefaultCatalogFile(project) != null) {
            return DEFAULT_VERSION_CATALOG_PATH
        }

        val detectedPath = getDetectedCatalogCandidates(project).firstOrNull()?.storagePath
        if (!detectedPath.isNullOrBlank()) {
            return detectedPath
        }

        return getConfiguredVersionCatalogPath().ifBlank { DEFAULT_VERSION_CATALOG_PATH }
    }

    fun getVersionCatalogPathCandidates(project: Project): List<String> {
        val candidates = LinkedHashSet<String>()
        val configuredPath = getConfiguredVersionCatalogPath()
        val effectivePath = getEffectiveVersionCatalogPath(project)

        if (configuredPath.isNotBlank()) {
            candidates += configuredPath
        }
        if (effectivePath.isNotBlank()) {
            candidates += effectivePath
        }
        getDetectedCatalogCandidates(project).mapTo(candidates) { it.storagePath }

        if (candidates.isEmpty()) {
            candidates += DEFAULT_VERSION_CATALOG_PATH
        }
        return candidates.toList()
    }

    fun setVersionCatalogPath(path: String) {
        val normalizedPath = path.trim()
        myState.versionCatalogPath = normalizedPath
        myState.versionCatalogPathCustomized = normalizedPath.isNotBlank()
    }

    fun clearVersionCatalogPathOverride() {
        myState.versionCatalogPath = ""
        myState.versionCatalogPathCustomized = false
    }

    fun getExternalLibraryRepoPath(): String = myState.externalLibraryRepoPath

    fun setExternalLibraryRepoPath(path: String) {
        myState.externalLibraryRepoPath = path.trim().ifBlank { DEFAULT_EXTERNAL_LIBRARY_REPO_PATH }
    }

    fun addDefaultTask(task: String) {
        if (task !in myState.defaultTasks) {
            myState.defaultTasks.add(task)
        }
    }

    fun removeDefaultTask(task: String) {
        myState.defaultTasks.remove(task)
    }

    fun isSilentUpsertToml(): Boolean = myState.silentUpsertToml

    fun setSilentUpsertToml(enabled: Boolean) {
        myState.silentUpsertToml = enabled
    }

    fun getNormalizeDedupStrategy(): String = myState.normalizeDedupStrategy

    fun setNormalizeDedupStrategy(strategy: String) {
        myState.normalizeDedupStrategy = strategy
    }

    fun getPreferredMirrorIndex(): Int = myState.preferredMirrorIndex.coerceIn(0, 2)

    fun setPreferredMirrorIndex(index: Int) {
        myState.preferredMirrorIndex = index.coerceIn(0, 2)
    }

    fun isAutoUpdateWrapper(): Boolean = myState.autoUpdateWrapper

    fun setAutoUpdateWrapper(enabled: Boolean) {
        myState.autoUpdateWrapper = enabled
    }

    fun resetToDefaults() {
        myState.defaultTasks = DEFAULT_TASKS.toMutableList()
        clearVersionCatalogPathOverride()
        myState.externalLibraryRepoPath = DEFAULT_EXTERNAL_LIBRARY_REPO_PATH
    }

    fun resolveExternalLibraryRepoRoot(project: Project): File {
        val rawPath = getExternalLibraryRepoPath().trim().ifBlank { DEFAULT_EXTERNAL_LIBRARY_REPO_PATH }
        val expandedPath = expandHomeAwarePath(rawPath)
        val configured = File(expandedPath)
        if (configured.isAbsolute) {
            return configured
        }

        val basePath = project.basePath ?: return configured.absoluteFile
        return File(basePath, expandedPath).absoluteFile
    }

    fun resolveVersionCatalogFile(project: Project): File {
        if (isVersionCatalogPathCustomized()) {
            return resolveCatalogFile(project, getConfiguredVersionCatalogPath())
        }

        findDefaultCatalogFile(project)?.let { return it }
        getDetectedCatalogCandidates(project).firstOrNull()?.file?.let { return it }
        return resolveCatalogFile(project, DEFAULT_VERSION_CATALOG_PATH)
    }

    fun collectGradleSearchRoots(project: Project): List<File> {
        val roots = LinkedHashSet<File>()
        collectBaseGradleSearchRoots(project).forEach(roots::add)

        val effectivePath = getEffectiveVersionCatalogPath(project)
        val resolvedCatalogFile = resolveVersionCatalogFile(project).absoluteFile
        inferRootFromConfiguredPath(resolvedCatalogFile, effectivePath)
            ?.let { roots += File(it).absoluteFile }
        collectAncestorGradleRoots(resolvedCatalogFile.parentFile?.absolutePath)
            .mapTo(roots) { File(it).absoluteFile }

        return roots.toList()
    }

    private fun resolveCatalogFile(project: Project, catalogPath: String): File {
        val normalizedPath = catalogPath.trim().ifBlank { DEFAULT_VERSION_CATALOG_PATH }
        val configuredFile = File(normalizedPath)
        if (configuredFile.isAbsolute) {
            return configuredFile
        }

        val searchRoots = collectBaseGradleSearchRoots(project)
        for (root in searchRoots) {
            val candidate = File(root, normalizedPath)
            if (candidate.exists()) {
                return candidate
            }
        }

        findCatalogFileInAncestors(project.basePath, normalizedPath)?.let { return it }

        if (searchRoots.isNotEmpty()) {
            return File(searchRoots.first(), normalizedPath)
        }

        val basePath = project.basePath ?: return File(normalizedPath)
        return File(basePath, normalizedPath)
    }

    private fun findDefaultCatalogFile(project: Project): File? {
        val matches = LinkedHashSet<File>()
        for (root in collectBaseGradleSearchRoots(project)) {
            val candidate = File(root, DEFAULT_VERSION_CATALOG_PATH)
            if (candidate.exists()) {
                matches += candidate.absoluteFile
            }
        }
        findCatalogFileInAncestors(project.basePath, DEFAULT_VERSION_CATALOG_PATH)?.let(matches::add)
        return matches.firstOrNull()
    }

    private fun getDetectedCatalogCandidates(project: Project): List<CatalogCandidate> {
        val cacheKey = buildCatalogCandidateCacheKey(project)
        val now = System.currentTimeMillis()
        if (cacheKey == cachedCatalogCandidatesKey && now - cachedCatalogCandidatesAt <= CATALOG_CANDIDATE_CACHE_TTL_MS) {
            return cachedCatalogCandidates
        }

        synchronized(catalogCandidateCacheLock) {
            val refreshedNow = System.currentTimeMillis()
            if (cacheKey == cachedCatalogCandidatesKey &&
                refreshedNow - cachedCatalogCandidatesAt <= CATALOG_CANDIDATE_CACHE_TTL_MS
            ) {
                return cachedCatalogCandidates
            }

            val scannedCandidates = scanDetectedCatalogCandidates(project)
            cachedCatalogCandidates = scannedCandidates
            cachedCatalogCandidatesKey = cacheKey
            cachedCatalogCandidatesAt = refreshedNow
            return scannedCandidates
        }
    }

    private fun scanDetectedCatalogCandidates(project: Project): List<CatalogCandidate> {
        val rawCandidates = LinkedHashMap<String, RawCatalogCandidate>()
        val searchRoots = collectBaseGradleSearchRoots(project)

        searchRoots.forEachIndexed { index, root ->
            scanCatalogFiles(root).forEach { catalogFile ->
                val absoluteFile = catalogFile.absoluteFile
                val absolutePath = normalizePath(absoluteFile.absolutePath)
                val preferredPath = toPreferredCatalogPath(absoluteFile, root)
                val score = scoreCatalogCandidate(absoluteFile, root, project.basePath, index)

                val existing = rawCandidates[absolutePath]
                if (existing == null || score > existing.score) {
                    rawCandidates[absolutePath] = RawCatalogCandidate(
                        file = absoluteFile,
                        preferredPath = preferredPath,
                        score = score
                    )
                }
            }
        }

        val candidates = rawCandidates.values.toList()
        if (candidates.isEmpty()) {
            return emptyList()
        }

        val ambiguousPreferredPaths = candidates
            .groupingBy { it.preferredPath }
            .eachCount()
            .filterValues { it > 1 }
            .keys

        return candidates.map { candidate ->
            val storagePath = if (candidate.preferredPath in ambiguousPreferredPaths) {
                normalizePath(candidate.file.absolutePath)
            } else {
                candidate.preferredPath
            }
            CatalogCandidate(candidate.file, storagePath, candidate.score)
        }.sortedWith(
            compareByDescending<CatalogCandidate> { it.score }
                .thenBy { it.storagePath.length }
                .thenBy { it.storagePath }
        )
    }

    private fun scanCatalogFiles(root: File): List<File> {
        if (!root.exists() || !root.isDirectory) {
            return emptyList()
        }

        val result = mutableListOf<File>()
        root.walkTopDown()
            .maxDepth(MAX_CATALOG_SCAN_DEPTH)
            .onEnter { dir ->
                val name = dir.name
                !name.startsWith(".") && name !in CATALOG_SCAN_SKIP_DIRS
            }
            .forEach { file ->
                if (file.isFile && file.name == VERSION_CATALOG_FILE_NAME) {
                    result += file.absoluteFile
                }
            }
        return result
    }

    private fun toPreferredCatalogPath(file: File, root: File): String {
        val relativePath = file.relativeToOrNull(root)?.path?.let(::normalizePath)
        return if (!relativePath.isNullOrBlank()) {
            relativePath
        } else {
            normalizePath(file.absolutePath)
        }
    }

    private fun scoreCatalogCandidate(file: File, root: File, projectBasePath: String?, rootIndex: Int): Int {
        val relativePath = file.relativeToOrNull(root)?.path?.let(::normalizePath).orEmpty()
        val normalizedBasePath = projectBasePath?.let(::normalizePath)
        val normalizedFilePath = normalizePath(file.absolutePath)

        var score = 0
        if (relativePath == DEFAULT_VERSION_CATALOG_PATH) {
            score += 100
        }
        if (!normalizedBasePath.isNullOrBlank() && normalizedFilePath.startsWith(normalizedBasePath)) {
            score += 20
        }
        score += (10 - rootIndex).coerceAtLeast(0)
        score -= relativePath.count { it == '/' }
        return score
    }

    private fun buildCatalogCandidateCacheKey(project: Project): String {
        return collectBaseGradleSearchRoots(project).joinToString("|") { normalizePath(it.absolutePath) }
    }

    private fun collectBaseGradleSearchRoots(project: Project): List<File> {
        val paths = LinkedHashSet<String>()

        try {
            GradleSettings.getInstance(project).linkedProjectsSettings
                .mapNotNullTo(paths) { it.externalProjectPath?.takeIf(String::isNotBlank) }
        } catch (_: Throwable) {
        }

        project.basePath?.takeIf(String::isNotBlank)?.let(paths::add)
        collectAncestorGradleRoots(project.basePath).forEach(paths::add)

        return paths.map { File(it).absoluteFile }.distinctBy { normalizePath(it.absolutePath) }
    }

    private fun inferRootFromConfiguredPath(catalogFile: File, configuredPath: String): String? {
        val configuredFile = File(configuredPath)
        if (configuredFile.isAbsolute) {
            return null
        }

        val normalizedCatalogPath = normalizePath(catalogFile.absolutePath)
        val normalizedConfiguredPath = normalizePath(configuredPath)
            .trimStart('.')
            .trimStart('/')

        if (normalizedConfiguredPath.isEmpty()) {
            return null
        }

        val suffix = "/$normalizedConfiguredPath"
        if (!normalizedCatalogPath.endsWith(suffix)) {
            return null
        }

        return normalizedCatalogPath.removeSuffix(suffix).ifBlank { "/" }
    }

    private fun findCatalogFileInAncestors(basePath: String?, catalogRelPath: String): File? {
        var current = basePath?.let(::File)?.absoluteFile ?: return null
        var depth = 0

        while (depth < MAX_ANCESTOR_DEPTH) {
            val candidate = File(current, catalogRelPath)
            if (candidate.exists()) {
                return candidate
            }
            current = current.parentFile ?: break
            depth++
        }

        return null
    }

    private fun collectAncestorGradleRoots(basePath: String?): List<String> {
        val result = mutableListOf<String>()
        var current = basePath?.let(::File)?.absoluteFile
        var depth = 0

        while (current != null && depth < MAX_ANCESTOR_DEPTH) {
            if (current.resolve("settings.gradle.kts").exists() || current.resolve("settings.gradle").exists()) {
                result += current.absolutePath
            }
            current = current.parentFile
            depth++
        }

        return result
    }

    private fun expandHomeAwarePath(path: String): String {
        val home = System.getProperty("user.home").orEmpty()
        return when {
            path == "~" -> home
            path.startsWith("~/") -> home + path.removePrefix("~")
            path.startsWith("${'$'}HOME/") -> home + "/" + path.removePrefix("${'$'}HOME/")
            path == "${'$'}HOME" -> home
            path.startsWith("\${HOME}/") -> home + "/" + path.removePrefix("\${HOME}/")
            path == "\${HOME}" -> home
            else -> path
        }
    }

    private fun normalizePath(path: String): String = path.replace('\\', '/')

    companion object {
        val DEFAULT_TASKS = listOf(
            "clean",
            "compileKotlin",
            "build",
            "test",
            "jar",
            "publishToMavenLocal",
            "publishToMavenCentral",
            "kspKotlin",
            "kspCommonMainMetadata",
            "signPlugin",
            "publishPlugin",
            "runIde"
        )

        const val DEFAULT_VERSION_CATALOG_PATH = "gradle/libs.versions.toml"
        const val DEFAULT_EXTERNAL_LIBRARY_REPO_PATH = "${'$'}HOME/IdeaProjects/addzero-lib"
        private const val VERSION_CATALOG_FILE_NAME = "libs.versions.toml"
        private const val MAX_ANCESTOR_DEPTH = 8
        private const val MAX_CATALOG_SCAN_DEPTH = 8
        private const val CATALOG_CANDIDATE_CACHE_TTL_MS = 10_000L
        private val CATALOG_SCAN_SKIP_DIRS = setOf("build", "out", ".gradle", "node_modules", "target")

        fun getInstance(project: Project): GradleBuddySettingsService = project.service()
    }
}
