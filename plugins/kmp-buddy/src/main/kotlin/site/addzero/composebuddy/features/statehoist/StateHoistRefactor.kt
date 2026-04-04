package site.addzero.composebuddy.features.statehoist

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.composebuddy.support.ComposeRefactorWriteSupport

class StateHoistRefactor(
    private val project: Project,
) {
    fun apply(issue: StateHoistIssue) {
        val factory = KtPsiFactory(project)
        val valueName = issue.stateName.replaceFirstChar { it.lowercase() }
        val callbackName = "on${issue.stateName.replaceFirstChar { it.uppercase() }}Change"
        ComposeRefactorWriteSupport.run(project, ComposeBuddyBundle.message("command.hoist.local.state")) {
            val function = issue.function
            val oldParameterList = function.valueParameterList?.text ?: "()"
            val newParameters = function.valueParameters.map { it.text }.toMutableList()
            newParameters += "$valueName: String = ${issue.initialExpression}"
            newParameters += "$callbackName: (String) -> Unit = { }"
            val newFunction = function.replace(
                factory.createFunction(function.text.replace(oldParameterList, "(${newParameters.joinToString(", ")})"))
            )
            issue.property.delete()
            newFunction.collectDescendantsOfType<KtDotQualifiedExpression>()
                .filter { it.receiverExpression.text == issue.property.name && it.selectorExpression?.text == "value" }
                .sortedByDescending { it.textRange.startOffset }
                .forEach { expression ->
                    expression.replace(factory.createExpression(valueName))
                }
        }
    }
}
