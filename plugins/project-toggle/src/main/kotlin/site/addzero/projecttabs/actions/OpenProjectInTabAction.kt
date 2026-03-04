package site.addzero.projecttabs.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.ide.impl.ProjectUtil
import site.addzero.projecttabs.settings.ProjectTabsSettings

/**
 * Action to open a project in new window
 */
class OpenProjectInTabAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val descriptor = FileChooserDescriptor(
            false, true, false, false, false, false
        )
        descriptor.title = "Open Project"
        descriptor.description = "Select a project directory to open"

        FileChooser.chooseFile(
            descriptor,
            e.project,
            null
        ) { virtualFile ->
            virtualFile?.path?.let { path ->
                ProjectUtil.openOrImport(path, null, true)
            }
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = ProjectTabsSettings.getInstance().enabled
    }
}
