package site.addzero.gradle.buddy.migration

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * 执行依赖替换
 */
object DependencyReplacer {

    /**
     * 执行替换操作
     * @return 替换的数量
     */
    fun replace(
        project: Project,
        replacements: List<ReplacementCandidate>
    ): ReplaceResult {
        var totalReplaced = 0
        val modifiedFiles = mutableSetOf<VirtualFile>()
        val errors = mutableListOf<String>()

        WriteCommandAction.runWriteCommandAction(project) {
            // 按文件分组处理
            val byFile = replacements
                .flatMap { candidate ->
                    val artifact = candidate.selectedArtifact ?: return@flatMap emptyList()
                    candidate.occurrences.map { occ -> 
                        Triple(occ.file, occ, artifact) 
                    }
                }
                .groupBy { it.first }

            byFile.forEach { (file, items) ->
                try {
                    var content = String(file.contentsToByteArray())
                    
                    // 从后往前替换，避免位置偏移
                    val sortedItems = items.sortedByDescending { it.second.range.first }
                    
                    sortedItems.forEach { (_, occurrence, artifact) ->
                        val version = artifact.latestVersion.ifBlank { artifact.version }
                        val coordinate = "${artifact.groupId}:${artifact.artifactId}:$version"
                        
                        val replacement = if (file.name.endsWith(".gradle.kts")) {
                            "${occurrence.configType}(\"$coordinate\")"
                        } else {
                            "${occurrence.configType} '$coordinate'"
                        }
                        
                        content = content.replaceRange(occurrence.range, replacement)
                        totalReplaced++
                    }
                    
                    file.setBinaryContent(content.toByteArray())
                    modifiedFiles.add(file)
                } catch (e: Exception) {
                    errors.add("Failed to update ${file.name}: ${e.message}")
                    e.printStackTrace()
                }
            }
        }

        return ReplaceResult(
            totalReplaced = totalReplaced,
            modifiedFiles = modifiedFiles.size,
            errors = errors
        )
    }
}

data class ReplaceResult(
    val totalReplaced: Int,
    val modifiedFiles: Int,
    val errors: List<String>
)
