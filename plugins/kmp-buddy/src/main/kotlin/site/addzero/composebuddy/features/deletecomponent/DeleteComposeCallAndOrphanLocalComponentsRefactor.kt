package site.addzero.composebuddy.features.deletecomponent

import com.intellij.openapi.project.Project
import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.composebuddy.support.ComposeRefactorWriteSupport

class DeleteComposeCallAndOrphanLocalComponentsRefactor(
    private val project: Project,
) {
    fun apply(analysis: DeleteComposeCallAndOrphanLocalComponentsAnalysisResult) {
        ComposeRefactorWriteSupport.run(project, ComposeBuddyBundle.message("command.delete.compose.call.and.orphans")) {
            analysis.callStatements
                .sortedByDescending { statement -> statement.textRange.startOffset }
                .forEach { statement -> statement.delete() }
            analysis.orphanFunctions
                .sortedByDescending { function -> function.textRange.startOffset }
                .forEach { function -> function.delete() }
        }
    }
}
