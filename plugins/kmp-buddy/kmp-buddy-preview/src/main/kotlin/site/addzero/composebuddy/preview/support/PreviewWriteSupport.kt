package site.addzero.composebuddy.preview.support

import com.intellij.openapi.project.Project
import site.addzero.smart.intentions.core.SmartPsiWriteSupport

object PreviewWriteSupport {
    fun run(project: Project, commandName: String, action: () -> Unit) {
        SmartPsiWriteSupport.runWriteCommand(project, commandName, action)
    }
}
