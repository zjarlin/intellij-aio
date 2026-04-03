package site.addzero.composebuddy.features.effectkeys

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.KtPsiFactory
import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.composebuddy.support.ComposeCallSupport
import site.addzero.composebuddy.support.ComposeRefactorWriteSupport

class EffectKeysRefactor(
    private val project: Project,
) {
    fun apply(issue: EffectKeysIssue) {
        val factory = KtPsiFactory(project)
        ComposeRefactorWriteSupport.run(project, ComposeBuddyBundle.message("command.normalize.effect.keys")) {
            val newText = ComposeCallSupport.buildCallText(
                call = issue.call,
                arguments = issue.suggestedKeys,
                lambdaArguments = issue.call.lambdaArguments,
            )
            issue.call.replace(factory.createExpression(newText))
        }
    }
}
