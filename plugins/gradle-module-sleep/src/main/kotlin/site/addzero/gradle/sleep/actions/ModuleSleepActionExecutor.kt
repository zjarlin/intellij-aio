package site.addzero.gradle.sleep.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import site.addzero.gradle.sleep.loader.LoadResult
import site.addzero.gradle.sleep.loader.OnDemandModuleLoader
import site.addzero.gradle.sleep.settings.ModuleSleepSettingsService

/**
 * Shared helpers for module sleep actions so buttons and banners reuse the same behavior.
 */
object ModuleSleepActionExecutor {

  fun loadOnlyOpenTabs(project: Project, manualFolderNamesRaw: String? = null) {
    val settings = ModuleSleepSettingsService.getInstance(project)
    if (manualFolderNamesRaw != null) {
      settings.setManualFolderNames(manualFolderNamesRaw)
    }

    val openFiles = OnDemandModuleLoader.getOpenEditorFiles(project)
    val manualModules = OnDemandModuleLoader.findModulesByFolderNames(project, settings.getManualFolderNames())
    val tabModules = OnDemandModuleLoader.detectModulesFromOpenFiles(project)
    val activeModules = OnDemandModuleLoader.expandModulesWithDependencies(project, tabModules + manualModules)

    if (activeModules.isEmpty()) {
      if (openFiles.isEmpty() && manualModules.isEmpty()) {
        notify(
          project,
          "No Open Files",
          "Please open some files first to detect required modules.",
          NotificationType.WARNING
        )
      } else {
        notify(
          project,
          "No Modules Detected",
          "Could not detect any Gradle modules from ${openFiles.size} open files.",
          NotificationType.WARNING
        )
      }
      return
    }

    when (val result = applyActiveModules(project, activeModules)) {
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

      is LoadResult.Failed -> notify(project, "Load Failed", result.reason, NotificationType.ERROR)
      else -> Unit
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
    val settings = ModuleSleepSettingsService.getInstance(project)
    val manualModules = OnDemandModuleLoader.findModulesByFolderNames(project, settings.getManualFolderNames())
    val fileModules = OnDemandModuleLoader.detectModulesFromFile(project, file)
    val activeModules = OnDemandModuleLoader.expandModulesWithDependencies(project, fileModules + manualModules)
    if (activeModules.isEmpty()) {
      notify(
        project,
        "No Modules Detected",
        "Could not detect a Gradle module for the current file.",
        NotificationType.WARNING
      )
      return
    }

    when (val result = applyActiveModules(project, activeModules)) {
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

      LoadResult.NoOpenFiles -> Unit
      is LoadResult.NoModulesDetected -> notify(
        project,
        "No Modules Detected",
        "Could not detect any Gradle modules from the current file.",
        NotificationType.WARNING
      )
      is LoadResult.Failed -> notify(project, "Load Failed", result.reason, NotificationType.ERROR)
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

    when (val result = applyActiveModules(project, activeModules)) {
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

      LoadResult.NoOpenFiles -> Unit
      is LoadResult.NoModulesDetected -> notify(
        project,
        "No Modules Detected",
        "No modules detected under the specified root directory.",
        NotificationType.WARNING
      )
      is LoadResult.Failed -> notify(project, "Load Failed", result.reason, NotificationType.ERROR)
    }
  }

  private fun applyActiveModules(project: Project, activeModules: Set<String>): LoadResult {
    if (activeModules.isEmpty()) {
      return LoadResult.NoModulesDetected(0)
    }
    val (validModules, excludedModules) = OnDemandModuleLoader.partitionModules(activeModules)
    val success = OnDemandModuleLoader.applyOnDemandLoading(project, activeModules, syncAfter = true)
    return if (success) {
      LoadResult.Success(validModules, excludedModules)
    } else {
      LoadResult.Failed("Failed to apply settings")
    }
  }

  private fun notify(project: Project, title: String, content: String, type: NotificationType) {
    val notification = NotificationGroupManager.getInstance()
      .getNotificationGroup("GradleModuleSleep")
      .createNotification(title, content, type)

    notification.notify(project)
  }
}
