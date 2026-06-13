package site.addzero.cargo.buddy.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import site.addzero.cargo.buddy.model.CargoCrateResolver
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
        if (command == "publish" && !confirmPublish(cargoCrate.displayName)) return
        CargoCommandRunner.run(project, cargoCrate, command)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val command = resolveCommandName(e.actionManager.getId(this))
        val cargoCrate = project?.let(CargoCrateResolver::resolveCurrentCrate)

        e.presentation.isEnabledAndVisible = project != null && command.isNotBlank() && cargoCrate != null
        if (command.isNotBlank()) {
            e.presentation.text = command
            e.presentation.description = cargoCrate?.let {
                "Run cargo $command for ${it.displayName}"
            } ?: "Run cargo $command for current crate"
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

    private fun confirmPublish(crateDisplayName: String): Boolean {
        return Messages.showOkCancelDialog(
            "Run cargo publish for $crateDisplayName?",
            "Publish Cargo Crate",
            "Publish",
            "Cancel",
            AllIcons.Actions.Upload,
        ) == Messages.OK
    }

    companion object {
        private const val ACTION_ID_PREFIX = "CargoBuddy.Command."

        fun defaultIcon(commandName: String): Icon = when (commandName.lowercase()) {
            "build" -> AllIcons.Actions.Compile
            "check" -> AllIcons.Actions.Checked
            "test" -> AllIcons.RunConfigurations.TestPassed
            "clippy" -> AllIcons.Actions.RealIntentionBulb
            "clean" -> AllIcons.Actions.GC
            "publish" -> AllIcons.Actions.Upload
            else -> AllIcons.Actions.Execute
        }
    }
}
