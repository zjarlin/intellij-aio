package site.addzero.cargo.buddy.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import site.addzero.cargo.buddy.model.CargoCrate
import site.addzero.cargo.buddy.model.CargoCrateResolver
import site.addzero.cargo.buddy.publish.CargoPublishPlan
import site.addzero.cargo.buddy.publish.CargoPublishPlanResolver
import site.addzero.cargo.buddy.runner.CargoCommandRunner
import javax.swing.Icon

class RunCargoCommandAction : AnAction {
    private var commandName: String = ""

    constructor() : super()

    constructor(commandName: String, icon: Icon? = null) : super(
        commandName,
        "Run cargo $commandName for current crate",
        icon ?: defaultIcon(commandName),
    ) {
        this.commandName = commandName
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val command = resolveCommandName(e.actionManager.getId(this))
        if (command.isBlank()) return

        val cargoCrate = CargoCrateResolver.resolveCurrentCrate(project) ?: return
        if (command == PUBLISH_RECURSIVE_COMMAND) {
            resolveAndRunRecursivePublish(project, cargoCrate)
            return
        }
        if (command == "publish" && !confirmPublish(cargoCrate.displayName)) return
        CargoCommandRunner.run(project, cargoCrate, command)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val command = resolveCommandName(e.actionManager.getId(this))
        val cargoCrate = project?.let(CargoCrateResolver::resolveCurrentCrate)

        e.presentation.isEnabledAndVisible = project != null && command.isNotBlank() && cargoCrate != null
        if (command.isNotBlank()) {
            e.presentation.text = commandText(command)
            e.presentation.description = cargoCrate?.let {
                commandDescription(command, it.displayName)
            } ?: commandDescription(command, "current crate")
            if (e.presentation.icon == null) {
                e.presentation.icon = defaultIcon(command)
            }
        }
    }

    private fun resolveCommandName(actionId: String?): String {
        if (commandName.isNotBlank()) return commandName
        if (actionId != null && actionId.startsWith(ACTION_ID_PREFIX)) {
            commandName = actionId.removePrefix(ACTION_ID_PREFIX)
        }
        return commandName
    }

    private fun resolveAndRunRecursivePublish(
        project: Project,
        cargoCrate: CargoCrate,
    ) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Resolving Cargo publish order",
            true,
        ) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val publishPlan = CargoPublishPlanResolver.resolve(cargoCrate, indicator)
                    ApplicationManager.getApplication().invokeLater {
                        if (confirmRecursivePublish(publishPlan)) {
                            CargoCommandRunner.runPublishPlan(project, cargoCrate, publishPlan)
                        }
                    }
                } catch (_: ProcessCanceledException) {
                    return
                } catch (e: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(
                            project,
                            e.message ?: e.javaClass.simpleName,
                            "Resolve Cargo Publish Order Failed",
                        )
                    }
                }
            }
        })
    }

    private fun confirmPublish(crateDisplayName: String): Boolean {
        return Messages.showOkCancelDialog(
            "Run cargo publish for $crateDisplayName?",
            "Publish Cargo Crate",
            "Publish",
            "Cancel",
            AllIcons.Actions.Upload,
        ) == Messages.OK
    }

    private fun confirmRecursivePublish(publishPlan: CargoPublishPlan): Boolean {
        return Messages.showOkCancelDialog(
            publishPlan.confirmationMessage(),
            if (publishPlan.hasLocalDependencies) "Publish Cargo Crates" else "Publish Cargo Crate",
            "Publish",
            "Cancel",
            AllIcons.Actions.Upload,
        ) == Messages.OK
    }

    private fun commandText(command: String): String {
        return if (command == PUBLISH_RECURSIVE_COMMAND) {
            "publish deps"
        } else {
            command
        }
    }

    private fun commandDescription(
        command: String,
        crateDisplayName: String,
    ): String {
        return if (command == PUBLISH_RECURSIVE_COMMAND) {
            "Publish local dependencies before $crateDisplayName"
        } else {
            "Run cargo $command for $crateDisplayName"
        }
    }

    companion object {
        private const val ACTION_ID_PREFIX = "CargoBuddy.Command."
        private const val PUBLISH_RECURSIVE_COMMAND = "publishRecursive"

        fun defaultIcon(commandName: String): Icon = when (commandName.lowercase()) {
            "build" -> AllIcons.Actions.Compile
            "check" -> AllIcons.Actions.Checked
            "test" -> AllIcons.RunConfigurations.TestPassed
            "clippy" -> AllIcons.Actions.RealIntentionBulb
            "clean" -> AllIcons.Actions.GC
            "publish" -> AllIcons.Actions.Upload
            PUBLISH_RECURSIVE_COMMAND.lowercase() -> AllIcons.Nodes.Deploy
            else -> AllIcons.Actions.Execute
        }
    }
}
