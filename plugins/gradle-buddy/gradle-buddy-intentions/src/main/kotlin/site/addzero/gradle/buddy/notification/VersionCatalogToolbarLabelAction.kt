package site.addzero.gradle.buddy.notification

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import site.addzero.gradle.buddy.GradleBuddyIcons

/**
 * A label action that displays "Version Catalog" text in the floating toolbar.
 * This makes the toolbar more visible and recognizable.
 */
class VersionCatalogToolbarLabelAction : AnAction(
    "Version Catalog",
    "Gradle Buddy Version Catalog helpers",
    GradleBuddyIcons.VersionCatalogBanner
), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        // No-op: this is just a label
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun displayTextInToolbar(): Boolean = true
}
