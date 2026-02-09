package site.addzero.gradle.buddy.filter

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import org.jetbrains.plugins.gradle.util.GradleConstants
import javax.swing.Icon

/**
 * Action that runs a specific Gradle task scoped to the current module.
 *
 * Supports two modes:
 * 1. Programmatic: pass taskName via constructor
 * 2. Static XML registration: set taskName via [setTaskName] (called from plugin.xml action ID convention)
 *
 * The task name is a short name (e.g. "build", "clean"). At execution time,
 * it's prefixed with the current module's Gradle path (e.g. ":lib:gradle-plugin:build").
 */
class RunFavoriteTaskAction : AnAction {

    private var taskName: String = ""

    /** Programmatic constructor */
    constructor(taskName: String, icon: Icon? = null) : super(
        taskName,
        "Run '$taskName' for current module",
        icon ?: getDefaultIcon(taskName)
    ) {
        this.taskName = taskName
    }

    /** No-arg constructor for XML registration. Task name is set from action ID suffix. */
    constructor() : super()

    /**
     * Called by the framework after XML-based instantiation.
     * We extract the task name from the action ID: "GradleBuddy.FavoriteTask.{taskName}"
     */
    private fun resolveTaskNameFromId(actionId: String?): String {
        if (taskName.isNotEmpty()) return taskName
        if (actionId != null && actionId.startsWith("GradleBuddy.FavoriteTask.")) {
            taskName = actionId.removePrefix("GradleBuddy.FavoriteTask.")
        }
        return taskName
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val resolved = resolveTaskNameFromId(e.actionManager.getId(this))
        if (resolved.isEmpty()) return

        val modulePath = GradleModulePathUtil.detectCurrentModulePath(project) ?: return
        val fullTaskName = if (modulePath == ":") resolved else "$modulePath:$resolved"

        val settings = ExternalSystemTaskExecutionSettings().apply {
            externalSystemIdString = GradleConstants.SYSTEM_ID.id
            externalProjectPath = project.basePath ?: return
            taskNames = listOf(fullTaskName)
        }

        ExternalSystemUtil.runTask(
            settings,
            DefaultRunExecutor.EXECUTOR_ID,
            project,
            GradleConstants.SYSTEM_ID
        )
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val resolved = resolveTaskNameFromId(e.actionManager.getId(this))

        if (project == null || resolved.isEmpty()) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        // Hide KMP-only tasks (e.g. kspCommonMainMetadata) when not in a KMP module
        if (resolved in GradleModulePathUtil.KMP_ONLY_TASKS && !GradleModulePathUtil.isKmpModule(project)) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        // Hide IntelliJ plugin tasks (signPlugin, publishPlugin, runIde) when not in an IJ plugin module
        if (resolved in GradleModulePathUtil.INTELLIJ_PLUGIN_ONLY_TASKS && !GradleModulePathUtil.isIntellijPluginModule(project)) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        e.presentation.isEnabledAndVisible = true
        e.presentation.text = resolved
        e.presentation.description = "Run '$resolved' for current module"
        if (e.presentation.icon == null) {
            e.presentation.icon = getDefaultIcon(resolved)
        }
    }

    companion object {
        fun getDefaultIcon(taskName: String): Icon = when (taskName.lowercase()) {
            "build" -> AllIcons.Actions.Compile
            "clean" -> AllIcons.Actions.GC
            "test" -> AllIcons.RunConfigurations.TestPassed
            "jar" -> AllIcons.FileTypes.Archive
            "publishtomavenlocal" -> AllIcons.Actions.Upload
            "publishtomavencentral" -> AllIcons.Nodes.Deploy
            "compilekotlin" -> AllIcons.Nodes.Module
            "kspkotlin", "kspcommonmainmetadata" -> AllIcons.Actions.RealIntentionBulb
            "runide" -> AllIcons.Actions.Execute
            "signplugin" -> AllIcons.Nodes.PpLib
            "publishplugin" -> AllIcons.Actions.Upload
            else -> AllIcons.Actions.Execute
        }
    }
}
