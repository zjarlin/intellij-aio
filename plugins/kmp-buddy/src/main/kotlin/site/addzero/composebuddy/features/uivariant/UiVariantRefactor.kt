package site.addzero.composebuddy.features.uivariant

import com.intellij.openapi.project.Project
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.composebuddy.support.ComposeNamingSupport
import site.addzero.composebuddy.support.ComposeRefactorWriteSupport

class UiVariantRefactor(
    private val project: Project,
) {
    fun apply(analysis: UiVariantAnalysisResult) {
        val function = analysis.function
        val parameter = analysis.booleanParameter
        val factory = KtPsiFactory(project)
        val enumName = ComposeNamingSupport.uniqueTypeName(function, "${function.name ?: "Composable"}UiVariant")
        ComposeRefactorWriteSupport.run(project, ComposeBuddyBundle.message("command.convert.boolean.to.variant")) {
            ReferencesSearch.search(parameter, LocalSearchScope(function.bodyExpression ?: function))
                .findAll()
                .mapNotNull { it.element as? KtNameReferenceExpression }
                .forEach { reference ->
                    reference.replace(factory.createExpression("${parameter.name} == $enumName.Primary"))
                }
            val oldParameterList = function.valueParameterList?.text ?: "()"
            val newParameters = function.valueParameters.map {
                if (it == parameter) {
                    "${it.name}: $enumName = $enumName.Primary"
                } else {
                    it.text
                }
            }
            val newFunction = function.replace(
                factory.createFunction(function.text.replace(oldParameterList, "(${newParameters.joinToString(", ")})"))
            )
            val declaration = "private enum class $enumName { Primary, Secondary }"
            newFunction.parent.addBefore(factory.createWhiteSpace("\n\n"), newFunction)
            newFunction.parent.addBefore(factory.createDeclaration(declaration), newFunction)
        }
    }
}
