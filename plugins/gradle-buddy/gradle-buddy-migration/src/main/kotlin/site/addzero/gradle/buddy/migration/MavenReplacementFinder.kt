package site.addzero.gradle.buddy.migration

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import site.addzero.network.call.maven.util.MavenArtifact
import site.addzero.network.call.maven.util.MavenCentralSearchUtil

/**
 * 查找 Maven 中的替换依赖
 */
object MavenReplacementFinder {

    private val logger = Logger.getInstance(MavenReplacementFinder::class.java)
    private const val DEFAULT_SEARCH_LIMIT = 10

    /**
     * 为每个 project 依赖查找可能的 Maven 替换
     */
    fun findReplacements(
        dependencies: List<ProjectDependencyInfo>,
        indicator: ProgressIndicator
    ): MigrationLookupResult {
        val candidates = mutableListOf<ReplacementCandidate>()
        val missingPublishCandidates = mutableListOf<PublishCommandCandidate>()
        
        // 按真实 module path 去重；解析失败时再退回模块名。
        val uniqueModules = dependencies
            .groupBy { it.canonicalModulePath ?: it.rawModulePath }
            .values
            .map { related -> related.first().moduleName to related }
        
        uniqueModules.forEachIndexed { index, (moduleName, relatedDeps) ->
            if (indicator.isCanceled) {
                return MigrationLookupResult(candidates, missingPublishCandidates)
            }
            
            indicator.fraction = index.toDouble() / uniqueModules.size
            indicator.text2 = "Searching Maven for: $moduleName"
            
            try {
                val artifacts = searchArtifacts(moduleName)
                
                if (artifacts.isNotEmpty()) {
                    candidates.add(ReplacementCandidate(
                        moduleName = moduleName,
                        modulePath = relatedDeps.first().rawModulePath,
                        occurrences = relatedDeps,
                        mavenArtifacts = artifacts,
                        selectedArtifact = artifacts.firstOrNull()
                    ))
                } else {
                    val first = relatedDeps.first()
                    val canonicalModulePath = first.canonicalModulePath
                    val sourceRootPath = first.sourceRootPath
                    if (!canonicalModulePath.isNullOrBlank() && !sourceRootPath.isNullOrBlank()) {
                        missingPublishCandidates.add(
                            PublishCommandCandidate(
                                moduleName = moduleName,
                                rawModulePath = first.rawModulePath,
                                canonicalModulePath = canonicalModulePath,
                                sourceRootPath = sourceRootPath,
                                occurrences = relatedDeps
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                logger.warn("Failed to search Maven for module: $moduleName", e)
                e.printStackTrace()
            }
        }
        
        return MigrationLookupResult(
            replacements = candidates,
            publishCommandCandidates = missingPublishCandidates
        )
    }

    /**
     * 供单个依赖的复制/迁移动作复用，避免重复实现 Maven Central 搜索逻辑。
     */
    fun searchArtifacts(moduleName: String, limit: Int = DEFAULT_SEARCH_LIMIT): List<MavenArtifact> {
        return try {
            MavenCentralSearchUtil.searchByKeyword(moduleName, limit)
        } catch (e: Exception) {
            logger.warn("Failed to search Maven for module: $moduleName", e)
            emptyList()
        }
    }
}

data class MigrationLookupResult(
    val replacements: List<ReplacementCandidate>,
    val publishCommandCandidates: List<PublishCommandCandidate>
)

/**
 * 替换候选项
 */
data class ReplacementCandidate(
    val moduleName: String,
    val modulePath: String,
    val occurrences: List<ProjectDependencyInfo>,
    val mavenArtifacts: List<MavenArtifact>,
    var selectedArtifact: MavenArtifact?,
    var selected: Boolean = false
) {
    val occurrenceCount: Int get() = occurrences.size
    
    val filesAffected: Set<String> 
        get() = occurrences.map { it.file.name }.toSet()
}

data class PublishCommandCandidate(
    val moduleName: String,
    val rawModulePath: String,
    val canonicalModulePath: String,
    val sourceRootPath: String,
    val occurrences: List<ProjectDependencyInfo>,
    var selected: Boolean = false
) {
    val occurrenceCount: Int get() = occurrences.size

    val filesAffected: Set<String>
        get() = occurrences.map { it.file.name }.toSet()

    val command: String
        get() = "./gradlew $canonicalModulePath:publishToMavenCentral"

    fun toQueueEntry(): PublishCommandEntry {
        return PublishCommandEntry(
            moduleName = moduleName,
            modulePath = canonicalModulePath,
            rootPath = sourceRootPath,
            command = command
        )
    }
}
