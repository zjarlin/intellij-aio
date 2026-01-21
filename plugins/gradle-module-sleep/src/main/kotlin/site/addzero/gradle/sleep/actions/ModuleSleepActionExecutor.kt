package site.addzero.gradle.sleep.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import site.addzero.gradle.sleep.loader.LoadResult
import site.addzero.gradle.sleep.loader.OnDemandModuleLoader

/**
 * Shared helpers for module sleep actions so buttons and banners reuse the same behavior.
 */
object ModuleSleepActionExecutor {

  fun loadOnlyOpenTabs(project: Project) {
    when (val result = OnDemandModuleLoader.loadOnlyOpenTabModules(project)) {
      is LoadResult.Success -> {
        val excludedInfo = if (result.excludedModules.isNotEmpty()) {
          "\nExcluded: ${result.excludedModules.sorted().joinToString(", ")}"
        } else {
          ""
        }
        notify(
          project,
          "On-Demand Loading Applied",
          "Loaded: ${result.modules.size}, Excluded: ${result.excludedModules.size}, Total: ${result.totalModules}\n${result.modules.sorted().joinToString("\n")}$excludedInfo",
          NotificationType.INFORMATION
        )
      }

      is LoadResult.NoOpenFiles -> notify(
        project,
        "No Open Files",
        "Please open some files first to detect required modules.",
        NotificationType.WARNING
      )

      is LoadResult.NoModulesDetected -> notify(
        project,
        "No Modules Detected",
        "Could not detect any Gradle modules from ${result.openFileCount} open files.",
        NotificationType.WARNING
      )

      is LoadResult.Failed -> notify(project, "Load Failed", result.reason, NotificationType.ERROR)
    }
  }

  fun restoreAllModules(project: Project) {
    val success = OnDemandModuleLoader.restoreAllModules(project, syncAfter = true)
    if (success) {
      notify(
        project,
        "All Modules Restored",
        "All excluded modules have been restored. Gradle sync triggered.",
        NotificationType.INFORMATION
      )
    } else {
      notify(
        project,
        "Restore Failed",
        "Failed to restore modules. Check the IDE log for details.",
        NotificationType.ERROR
      )
    }
  }

  private fun notify(project: Project, title: String, content: String, type: NotificationType) {
    NotificationGroupManager.getInstance()
      .getNotificationGroup("GradleModuleSleep")
      .createNotification(title, content, type)
      .notify(project)
  }
}