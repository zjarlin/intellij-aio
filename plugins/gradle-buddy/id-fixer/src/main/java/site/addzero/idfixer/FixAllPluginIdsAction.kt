package site.addzero.idfixer

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.psi.search.GlobalSearchScope

/**
 * Action to fix all plugin IDs in the entire project.
 *
 * This action scans all build-logic directories, finds all local precompiled script plugins,
 * and replaces all short plugin ID references with fully qualified IDs throughout the project.
 */
class FixAllPluginIdsAction : AnAction("Fix All Plugin IDs in Project") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Fixing Plugin IDs", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Scanning build-logic directories..."

                // Scan for all local plugins
                val scanner = PluginIdScanner(project)
                val buildLogicDirs = scanner.findBuildLogicDirectories()

                if (buildLogicDirs.isEmpty()) {
                    showNotification(project, "No build-logic directories found", NotificationType.WARNING)
                    return
                }

                indicator.text = "Extracting plugin information..."
                val allPlugins = buildLogicDirs.flatMap { scanner.scanBuildLogic(it) }

                if (allPlugins.isEmpty()) {
                    showNotification(project, "No plugins found in build-logic directories", NotificationType.WARNING)
                    return
                }

                // Build plugin ID mapping
                val pluginIdMapping = allPlugins.associateBy { it.shortId }

                indicator.text = "Finding plugin ID references..."

                // Create replacement engine and find all candidates
                val engine = IdReplacementEngine(project, pluginIdMapping)
                val candidates = engine.findReplacementCandidates(GlobalSearchScope.projectScope(project))

                if (candidates.isEmpty()) {
                    showNotification(project, "No plugin IDs need to be fixed", NotificationType.INFORMATION)
                    return
                }

                indicator.text = "Applying replacements..."

                // Apply all replacements
                val result = engine.applyReplacements(candidates)

                // Show result notification
                if (result.isSuccessful()) {
                    showNotification(
                        project,
                        result.getSummaryMessage(),
                        NotificationType.INFORMATION
                    )
                } else {
                    showNotification(
                        project,
                        result.getDetailedMessage(),
                        NotificationType.ERROR
                    )
                }
            }
        })
    }

    private fun showNotification(project: com.intellij.openapi.project.Project, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Gradle Plugin ID Fixer")
            .createNotification(message, type)
            .notify(project)
    }
}
