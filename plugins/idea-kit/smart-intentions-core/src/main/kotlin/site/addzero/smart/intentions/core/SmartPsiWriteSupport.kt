package site.addzero.smart.intentions.core

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project

object SmartPsiWriteSupport {
    fun runWriteCommand(
        project: Project,
        commandName: String,
        block: () -> Unit,
    ) {
        WriteCommandAction.writeCommandAction(project)
            .withName(commandName)
            .run<RuntimeException> {
                block()
            }
    }
}
