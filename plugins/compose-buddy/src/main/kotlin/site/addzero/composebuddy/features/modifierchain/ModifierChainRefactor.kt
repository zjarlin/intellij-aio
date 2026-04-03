package site.addzero.composebuddy.features.modifierchain

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.KtPsiFactory
import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.composebuddy.support.ComposeNamingSupport
import site.addzero.composebuddy.support.ComposeRefactorWriteSupport

class ModifierChainRefactor(
    private val project: Project,
) {
    fun apply(analysis: ModifierChainAnalysisResult) {
        val factory = KtPsiFactory(project)
        ComposeRefactorWriteSupport.run(project, ComposeBuddyBundle.message("command.extract.modifier.chain")) {
            val functionName = ComposeNamingSupport.uniqueFunctionName(analysis.function, "modifierChainStyle")
            val body = analysis.chainText.removePrefix("Modifier.")
            val declaration = """
                private fun androidx.compose.ui.Modifier.$functionName(): androidx.compose.ui.Modifier {
                    return this.$body
                }
            """.trimIndent()
            analysis.file.add(factory.createWhiteSpace("\n\n"))
            analysis.file.add(factory.createDeclaration(declaration))
            analysis.occurrences.sortedByDescending { it.textRange.startOffset }.forEach { expression ->
                expression.replace(factory.createExpression("Modifier.$functionName()"))
            }
        }
    }
}
