package site.addzero.composebuddy.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.composebuddy.support.MoveToSharedSourceSetSupport
import java.nio.file.Paths

abstract class SetSharedSourceSetForModuleAction(
    private val sourceSetName: String,
) : AnAction(), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val moduleRoot = resolveSelectedModuleRoot(event)
        event.presentation.isEnabledAndVisible = event.project != null && moduleRoot != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val moduleRoot = resolveSelectedModuleRoot(event) ?: return
        MoveToSharedSourceSetSupport.rememberSharedSourceSet(Paths.get(moduleRoot.path), sourceSetName)
        NotificationGroupManager.getInstance()
            .getNotificationGroup("KMP Buddy Notifications")
            .createNotification(
                ComposeBuddyBundle.message(
                    "action.shared.source.set.module.updated",
                    moduleRoot.name,
                    sourceSetName,
                ),
                NotificationType.INFORMATION,
            )
            .notify(project)
    }

    private fun resolveSelectedModuleRoot(event: AnActionEvent): VirtualFile? {
        val selectedFiles = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY).orEmpty()
        if (selectedFiles.size == 1) {
            val selectedDirectory = selectedFiles.single().takeIf { it.isValid && it.isDirectory }
            return selectedDirectory?.takeIf(::isModuleRoot)
        }
        if (selectedFiles.isNotEmpty()) {
            return null
        }
        val module = event.getData(LangDataKeys.MODULE) ?: return null
        return module.moduleContentRoots.singleOrNull()?.takeIf(::isModuleRoot)
    }

    private fun isModuleRoot(directory: VirtualFile): Boolean {
        return directory.findChild("build.gradle.kts") != null || directory.findChild("build.gradle") != null
    }

    private val Module.moduleContentRoots: List<VirtualFile>
        get() = ModuleRootManager.getInstance(this).contentRoots.toList()
}

class SetShareLogicSourceSetForModuleAction : SetSharedSourceSetForModuleAction(
    MoveToSharedSourceSetSupport.SHARE_LOGIC_SOURCE_SET,
)

class SetShareUiSourceSetForModuleAction : SetSharedSourceSetForModuleAction(
    MoveToSharedSourceSetSupport.SHARE_UI_SOURCE_SET,
)
