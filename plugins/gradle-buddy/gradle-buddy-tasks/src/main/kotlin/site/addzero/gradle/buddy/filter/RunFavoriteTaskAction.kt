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
        val resolved = resolveTaskNameFromId(e.actionManager.getId(this))
        e.presentation.isEnabledAndVisible = e.project != null && resolved.isNotEmpty()
        if (resolved.isNotEmpty()) {
            e.presentation.text = resolved
            e.presentation.description = "Run '$resolved' for current module"
            if (e.presentation.icon == null) {
                e.presentation.icon = getDefaultIcon(resolved)
            }
        }
    }

    companion object {
        fun getDefaultIcon(taskName: String): Icon = when (taskName.lowercase()) {
            "build" -> AllIcons.Actions.Compile
            "clean" -> AllIcons.Actions.GC
            "test" -> AllIcons.RunConfigurations.TestState.Run
            "jar" -> AllIcons.FileTypes.Archive
            "publishtomavenlocal" -> AllIcons.Actions.Upload
            "publishtomavencentral" -> AllIcons.Actions.Upload
            "compilekotlin" -> AllIcons.Actions.Compile
            "kspkotlin", "kspcommonmainmetadata" -> AllIcons.Actions.RealIntentionBulb
            "runide" -> AllIcons.Actions.Execute
            "signplugin" -> AllIcons.Nodes.PpLib
            "publishplugin" -> AllIcons.Actions.Upload
            else -> AllIcons.Actions.Execute
        }
    }
}
