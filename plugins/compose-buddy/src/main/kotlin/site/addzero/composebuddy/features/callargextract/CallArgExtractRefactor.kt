package site.addzero.composebuddy.features.callargextract

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.KtPsiFactory
import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.composebuddy.support.ComposeCallSupport
import site.addzero.composebuddy.support.ComposeRefactorWriteSupport

class CallArgExtractRefactor(
    private val project: Project,
) {
    fun apply(analysis: CallArgExtractAnalysisResult) {
        val factory = KtPsiFactory(project)
        val candidateByName = analysis.candidates.associateBy { it.argumentName }
        ComposeRefactorWriteSupport.run(project, ComposeBuddyBundle.message("command.extract.call.arguments.to.parameters")) {
            val function = analysis.function
            val oldParameterList = function.valueParameterList?.text ?: "()"
            val newParameterTexts = buildList {
                addAll(function.valueParameters.map { it.text })
                addAll(analysis.candidates.map { candidate ->
                    "${candidate.argumentName}: ${candidate.typeText} = ${candidate.defaultValueText}"
                })
            }
            function.replace(
                factory.createFunction(function.text.replace(oldParameterList, "(${newParameterTexts.joinToString(", ")})"))
            )
            val newArguments = analysis.call.valueArguments.map { argument ->
                val argumentName = argument.getArgumentName()?.asName?.identifier
                val candidate = argumentName?.let { candidateByName[it] }
                if (candidate != null) {
                    "${candidate.argumentName} = ${candidate.argumentName}"
                } else {
                    argument.text
                }
            }
            val newCallText = ComposeCallSupport.buildCallText(analysis.call, newArguments, analysis.call.lambdaArguments)
            analysis.call.replace(factory.createExpression(newCallText))
        }
    }
}
