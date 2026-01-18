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

    /**
     * 为每个 project 依赖查找可能的 Maven 替换
     */
    fun findReplacements(
        dependencies: List<ProjectDependencyInfo>,
        indicator: ProgressIndicator
    ): List<ReplacementCandidate> {
        val candidates = mutableListOf<ReplacementCandidate>()
        
        // 按模块名去重
        val uniqueModules = dependencies.map { it.moduleName }.distinct()
        
        uniqueModules.forEachIndexed { index, moduleName ->
            if (indicator.isCanceled) return candidates
            
            indicator.fraction = index.toDouble() / uniqueModules.size
            indicator.text2 = "Searching Maven for: $moduleName"
            
            try {
                val artifacts = MavenCentralSearchUtil.searchByKeyword(moduleName, 10)
                
                if (artifacts.isNotEmpty()) {
                    // 找到该模块名对应的所有依赖
                    val relatedDeps = dependencies.filter { it.moduleName == moduleName }
                    
                    candidates.add(ReplacementCandidate(
                        moduleName = moduleName,
                        modulePath = relatedDeps.first().modulePath,
                        occurrences = relatedDeps,
                        mavenArtifacts = artifacts,
                        selectedArtifact = artifacts.firstOrNull()
                    ))
                }
            } catch (e: Exception) {
                logger.warn("Failed to search Maven for module: $moduleName", e)
                e.printStackTrace()
            }
        }
        
        return candidates
    }
}

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
