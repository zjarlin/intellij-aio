package site.addzero.composebuddy.features.containerwrap

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.composebuddy.support.ComposeNamingSupport
import site.addzero.composebuddy.support.ComposeRefactorWriteSupport

class ContainerWrapRefactor(
    private val project: Project,
) {
    fun apply(analysis: ContainerWrapAnalysisResult) {
        val factory = KtPsiFactory(project)
        ComposeRefactorWriteSupport.run(project, ComposeBuddyBundle.message("command.wrap.higher.order.container")) {
            val functionName = ComposeNamingSupport.uniqueFunctionName(
                analysis.function,
                "${analysis.targetCall.calleeExpression?.text ?: "Container"}Container",
            )
            val lambdaText = analysis.targetCall.lambdaArguments.firstOrNull()?.text ?: "{\n}"
            val valueArgs = analysis.targetCall.valueArguments.joinToString(", ") { it.text }
            val declaration = buildString {
                appendLine("@androidx.compose.runtime.Composable")
                appendLine("private fun $functionName(content: @androidx.compose.runtime.Composable () -> Unit) {")
                appendLine("    ${analysis.targetCall.calleeExpression?.text}($valueArgs) { content() }")
                appendLine("}")
            }
            val owner: KtNamedFunction = analysis.function
            owner.parent.addBefore(factory.createDeclaration(declaration.trim()), owner)
            owner.parent.addBefore(factory.createWhiteSpace("\n\n"), owner)
            analysis.targetCall.replace(factory.createExpression("$functionName $lambdaText"))
        }
    }
}
