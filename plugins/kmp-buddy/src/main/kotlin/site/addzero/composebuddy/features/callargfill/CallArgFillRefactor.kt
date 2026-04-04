package site.addzero.composebuddy.features.callargfill

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.KtPsiFactory
import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.composebuddy.support.ComposeCallSupport
import site.addzero.composebuddy.support.ComposeRefactorWriteSupport

class CallArgFillRefactor(
    private val project: Project,
) {
    fun apply(analysis: CallArgFillAnalysisResult) {
        val factory = KtPsiFactory(project)
        val replacementNames = analysis.replacements.map { it.argumentName }.toSet()
        ComposeRefactorWriteSupport.run(project, ComposeBuddyBundle.message("command.fill.call.arguments.from.parameters")) {
            val newArguments = analysis.call.valueArguments.map { argument ->
                val argumentName = argument.getArgumentName()?.asName?.identifier
                if (argumentName != null && argumentName in replacementNames) {
                    "$argumentName = $argumentName"
                } else {
                    argument.text
                }
            }
            val newCallText = ComposeCallSupport.buildCallText(analysis.call, newArguments, analysis.call.lambdaArguments)
            analysis.call.replace(factory.createExpression(newCallText))
        }
    }
}
