package site.addzero.gradle.buddy.notification

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import site.addzero.gradle.buddy.GradleBuddyIcons

/**
 * Label action that also triggers Organize when clicked.
 */
class VersionCatalogToolbarLabelAction : AnAction(
    "Version Catalog",
    "Sort and de-duplicate the version catalog",
    GradleBuddyIcons.VersionCatalogBanner
), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        VersionCatalogSorter(project).sort(file)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
