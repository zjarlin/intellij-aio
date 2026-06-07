package site.addzero.composebuddy.features.parametersort

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPsiFactory
import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.composebuddy.support.ComposeRefactorWriteSupport

class ParameterSortRefactor(
    private val project: Project,
) {
    fun apply(result: ParameterSortResult) {
        val function = result.function
        val oldParameterList = function.valueParameterList?.text ?: return
        val newParameterList = renderParameterList(oldParameterList, result.sortedParameters)
        val factory = KtPsiFactory(project)
        ComposeRefactorWriteSupport.run(project, ComposeBuddyBundle.message("command.sort.parameters")) {
            function.replace(factory.createFunction(function.text.replaceFirst(oldParameterList, newParameterList)))
        }
    }

    private fun renderParameterList(oldParameterList: String, parameters: List<KtParameter>): String {
        if (!oldParameterList.contains('\n')) {
            return "(${parameters.joinToString(", ") { it.text }})"
        }
        val lines = oldParameterList.lines()
        val parameterIndent = lines.drop(1)
            .firstOrNull { it.isNotBlank() }
            ?.takeWhile { it.isWhitespace() }
            ?: "    "
        val closingIndent = lines.lastOrNull()?.takeWhile { it.isWhitespace() } ?: ""
        val body = parameters.joinToString(",\n") { parameter -> "$parameterIndent${parameter.text}" }
        return "(\n$body\n$closingIndent)"
    }
}
