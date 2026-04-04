package site.addzero.composebuddy.features.statemapper

import com.intellij.openapi.project.Project
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.composebuddy.support.ComposeNamingSupport
import site.addzero.composebuddy.support.ComposeRefactorWriteSupport

class StateMapperRefactor(
    private val project: Project,
) {
    fun apply(analysis: StateMapperAnalysisResult) {
        val function = analysis.function
        val usedParameters = function.valueParameters.filter { parameter ->
            ReferencesSearch.search(parameter, LocalSearchScope(analysis.expression))
                .findAll()
                .mapNotNull { it.element as? KtNameReferenceExpression }
                .isNotEmpty()
        }
        val factory = KtPsiFactory(project)
        ComposeRefactorWriteSupport.run(project, ComposeBuddyBundle.message("command.extract.state.mapper")) {
            val mapperName = ComposeNamingSupport.uniqueFunctionName(function, "map${function.name ?: "State"}")
            val declaration = buildString {
                appendLine("private fun $mapperName(${usedParameters.joinToString(", ") { it.text }}) =")
                appendLine("    ${analysis.expression.text}")
            }
            function.parent.addBefore(factory.createWhiteSpace("\n\n"), function)
            function.parent.addBefore(factory.createDeclaration(declaration.trim()), function)
            val callText = "$mapperName(${usedParameters.joinToString(", ") { "${it.name} = ${it.name}" }})"
            analysis.expression.replace(factory.createExpression(callText))
        }
    }
}
