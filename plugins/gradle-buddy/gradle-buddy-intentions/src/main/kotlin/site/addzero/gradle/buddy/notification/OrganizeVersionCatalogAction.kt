package site.addzero.gradle.buddy.notification

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import site.addzero.gradle.buddy.i18n.GradleBuddyActionI18n

class OrganizeVersionCatalogAction : AnAction(), DumbAware {

    init {
        syncPresentation()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        VersionCatalogSorter(project).sort(file)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        syncPresentation(e.presentation)
        e.presentation.isEnabledAndVisible = e.project != null
    }

    private fun syncPresentation(presentation: com.intellij.openapi.actionSystem.Presentation? = null) {
        GradleBuddyActionI18n.sync(
            this,
            presentation,
            "action.organize.version.catalog.title",
            "action.organize.version.catalog.description"
        )
    }
}
