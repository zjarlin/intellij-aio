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
            val oldParameterList = function.valueParameterList ?: return@run
            val newParameterTexts = buildList {
                addAll(function.valueParameters.map { it.text })
                addAll(analysis.candidates.map { candidate ->
                    "${candidate.argumentName}: ${candidate.typeText} = ${candidate.defaultValueText}"
                })
            }
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
            val functionStart = function.textRange.startOffset
            val replacements = listOf(
                Replacement(
                    start = oldParameterList.textRange.startOffset - functionStart,
                    end = oldParameterList.textRange.endOffset - functionStart,
                    text = "(${newParameterTexts.joinToString(", ")})",
                ),
                Replacement(
                    start = analysis.call.textRange.startOffset - functionStart,
                    end = analysis.call.textRange.endOffset - functionStart,
                    text = newCallText,
                ),
            )
            val newFunctionText = replacements
                .sortedByDescending { replacement -> replacement.start }
                .fold(function.text) { text, replacement ->
                    text.replaceRange(replacement.start, replacement.end, replacement.text)
                }
            function.replace(factory.createFunction(newFunctionText))
        }
    }

    private data class Replacement(
        val start: Int,
        val end: Int,
        val text: String,
    )
}
