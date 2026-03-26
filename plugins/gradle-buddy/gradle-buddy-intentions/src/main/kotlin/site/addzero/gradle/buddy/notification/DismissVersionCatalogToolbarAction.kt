package site.addzero.gradle.buddy.notification

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import site.addzero.gradle.buddy.i18n.GradleBuddyActionI18n

class DismissVersionCatalogToolbarAction : AnAction(), DumbAware {

    init {
        syncPresentation()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        PropertiesComponent.getInstance(project)
            .setValue(VersionCatalogNotificationSettings.BANNER_DISABLED_KEY, true)
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
            "action.dismiss.version.catalog.toolbar.title",
            "action.dismiss.version.catalog.toolbar.description"
        )
    }
}
