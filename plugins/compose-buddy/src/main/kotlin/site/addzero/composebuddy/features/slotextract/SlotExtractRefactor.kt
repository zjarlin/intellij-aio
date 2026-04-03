package site.addzero.composebuddy.features.slotextract

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.KtPsiFactory
import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.composebuddy.support.ComposeRefactorWriteSupport

class SlotExtractRefactor(
    private val project: Project,
) {
    fun apply(analysis: SlotExtractAnalysisResult) {
        val factory = KtPsiFactory(project)
        ComposeRefactorWriteSupport.run(project, ComposeBuddyBundle.message("command.extract.slot.parameter")) {
            val function = analysis.function
            val oldParameterList = function.valueParameterList?.text ?: "()"
            val newParameters = function.valueParameters.map { it.text } + "content: @androidx.compose.runtime.Composable () -> Unit"
            val lambdaText = analysis.call.lambdaArguments.firstOrNull()?.text ?: "{ }"
            val updatedCallText = "${analysis.call.calleeExpression?.text}(${analysis.call.valueArguments.joinToString(", ") { it.text }}, content = content)"
            val updatedFunctionText = function.text
                .replace(oldParameterList, "(${newParameters.joinToString(", ")})")
                .replace(analysis.call.text, updatedCallText)
            function.replace(factory.createFunction(updatedFunctionText))
            analysis.call.replace(factory.createExpression("${analysis.call.calleeExpression?.text} ${lambdaText.removePrefix("{").removeSuffix("}")}"))
        }
    }
}
