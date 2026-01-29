package site.addzero.gradle.sleep.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VfsUtil
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

      LoadResult.NoOpenFiles -> notify(
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

  fun loadOnlyCurrentFile(project: Project, file: VirtualFile) {
    val activeModules = OnDemandModuleLoader.detectModulesFromFile(project, file)
    if (activeModules.isEmpty()) {
      notify(
        project,
        "No Modules Detected",
        "Could not detect a Gradle module for the current file.",
        NotificationType.WARNING
      )
      return
    }

    val (validModules, excludedModules) = OnDemandModuleLoader.partitionModules(activeModules)
    val success = OnDemandModuleLoader.applyOnDemandLoading(project, activeModules, syncAfter = true)
    if (success) {
      val excludedInfo = if (excludedModules.isNotEmpty()) {
        "\nExcluded: ${excludedModules.sorted().joinToString(", ")}"
      } else {
        ""
      }
      notify(
        project,
        "On-Demand Loading Applied",
        "Loaded: ${validModules.size}, Excluded: ${excludedModules.size}, Total: ${validModules.size + excludedModules.size}\n${validModules.sorted().joinToString("\n")}$excludedInfo",
        NotificationType.INFORMATION
      )
    } else {
      notify(project, "Load Failed", "Failed to apply settings", NotificationType.ERROR)
    }
  }

  fun loadModulesUnderRoot(project: Project, rootPath: String) {
    val trimmed = rootPath.trim()
    if (trimmed.isEmpty()) {
      notify(
        project,
        "Root Directory Required",
        "Please enter a root directory to include modules from.",
        NotificationType.WARNING
      )
      return
    }

    val basePath = project.basePath
    if (basePath == null) {
      notify(project, "No Project Base", "Project base path is not available.", NotificationType.ERROR)
      return
    }

    val rootFile = if (trimmed.startsWith("/")) {
      java.io.File(trimmed)
    } else {
      java.io.File(basePath, trimmed)
    }

    val rootVfs = VfsUtil.findFileByIoFile(rootFile, true)
    if (rootVfs == null || !rootVfs.isDirectory) {
      notify(
        project,
        "Invalid Root Directory",
        "Directory not found: ${rootFile.path}",
        NotificationType.WARNING
      )
      return
    }

    if (!rootVfs.path.startsWith(basePath)) {
      notify(
        project,
        "Root Outside Project",
        "Root must be within the project: ${rootVfs.path}",
        NotificationType.WARNING
      )
      return
    }

    val activeModules = OnDemandModuleLoader.discoverModulesUnderRoot(project, rootVfs)
    if (activeModules.isEmpty()) {
      notify(
        project,
        "No Modules Found",
        "No Gradle modules found under: ${rootFile.path}",
        NotificationType.WARNING
      )
      return
    }

    val (validModules, excludedModules) = OnDemandModuleLoader.partitionModules(activeModules)
    val success = OnDemandModuleLoader.applyOnDemandLoading(project, activeModules, syncAfter = true)
    if (success) {
      val excludedInfo = if (excludedModules.isNotEmpty()) {
        "\nExcluded: ${excludedModules.sorted().joinToString(", ")}"
      } else {
        ""
      }
      notify(
        project,
        "On-Demand Loading Applied",
        "Loaded: ${validModules.size}, Excluded: ${excludedModules.size}, Total: ${validModules.size + excludedModules.size}\n${validModules.sorted().joinToString("\n")}$excludedInfo",
        NotificationType.INFORMATION
      )
    } else {
      notify(project, "Load Failed", "Failed to apply settings", NotificationType.ERROR)
    }
  }

  private fun notify(project: Project, title: String, content: String, type: NotificationType) {
    NotificationGroupManager.getInstance()
      .getNotificationGroup("GradleModuleSleep")
      .createNotification(title, content, type)
      .notify(project)
  }
}
