package site.addzero.smart.intentions.modulelock.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import site.addzero.smart.intentions.hiddenfiles.IdeKitBundle
import site.addzero.smart.intentions.modulelock.ModuleLockProjectService

class LockModuleAction : DumbAwareAction(
    IdeKitBundle.message("action.module.lock.text"),
    IdeKitBundle.message("action.module.lock.description"),
    AllIcons.Nodes.Module,
) {
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(event: AnActionEvent) {
        val project = event.project
        val modules = ModuleLockActionSupport.getSelectedModules(event)
        val service = project?.service<ModuleLockProjectService>()

        event.presentation.isVisible = project != null && modules.isNotEmpty()
        event.presentation.isEnabled = service != null && modules.any { module -> !service.isLocked(module) }
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val modules = ModuleLockActionSupport.getSelectedModules(event)
        if (modules.isEmpty()) {
            return
        }
        project.service<ModuleLockProjectService>().lockModules(modules)
    }
}
