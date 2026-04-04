package site.addzero.composebuddy.features.sectionsplit

import com.intellij.openapi.project.Project
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.composebuddy.support.ComposeNamingSupport
import site.addzero.composebuddy.support.ComposeRefactorWriteSupport

class SectionSplitRefactor(
    private val project: Project,
) {
    fun apply(analysis: SectionSplitAnalysisResult) {
        val function = analysis.function
        val sectionText = analysis.sectionCall.text
        val usedParameters = function.valueParameters.filter { parameter ->
            ReferencesSearch.search(parameter, LocalSearchScope(analysis.sectionCall))
                .findAll()
                .mapNotNull { it.element as? KtNameReferenceExpression }
                .isNotEmpty()
        }
        val factory = KtPsiFactory(project)
        ComposeRefactorWriteSupport.run(project, ComposeBuddyBundle.message("command.split.layout.section")) {
            val sectionName = ComposeNamingSupport.uniqueFunctionName(function, "Section")
            val declaration = buildString {
                appendLine("@androidx.compose.runtime.Composable")
                appendLine("private fun $sectionName(${usedParameters.joinToString(", ") { it.text }}) {")
                appendLine("    $sectionText")
                appendLine("}")
            }
            function.parent.addBefore(factory.createWhiteSpace("\n\n"), function)
            function.parent.addBefore(factory.createDeclaration(declaration.trim()), function)
            val callText = "$sectionName(${usedParameters.joinToString(", ") { "${it.name} = ${it.name}" }})"
            analysis.sectionCall.replace(factory.createExpression(callText))
        }
    }
}
