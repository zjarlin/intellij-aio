package site.addzero.deploy.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import site.addzero.deploy.DeployTarget
import site.addzero.deploy.DeployTrigger
import site.addzero.deploy.TriggerType
import javax.swing.JComponent

/**
 * 触发器配置对话框
 */
class TriggerDialog(
    private val project: Project,
    private val availableTargets: List<DeployTarget>
) : DialogWrapper(project) {

    private var selectedTarget: String = availableTargets.firstOrNull()?.name ?: ""
    private var selectedTriggerType: TriggerType = TriggerType.GIT_PUSH
    private val branchField = JBTextField("master")

    init {
        title = "Add Deploy Trigger"
        init()
    }

    override fun createCenterPanel(): JComponent = panel {
        row("Deploy Target:") {
            comboBox(availableTargets.map { it.name ?: "" })
                .bindItem(
                    getter = { selectedTarget },
                    setter = { selectedTarget = it ?: "" }
                )
        }
        
        row("Trigger Type:") {
            comboBox(TriggerType.entries.map { it.name })
                .bindItem(
                    getter = { selectedTriggerType.name },
                    setter = { selectedTriggerType = TriggerType.valueOf(it ?: "MANUAL") }
                )
                .comment("GIT_PUSH: Trigger on git push; GIT_COMMIT: Trigger on commit")
        }
        
        row("Git Branch:") {
            cell(branchField)
                .columns(COLUMNS_SHORT)
                .comment("Branch pattern to match (e.g., master, main, release/*)")
        }
    }

    override fun doValidate(): ValidationInfo? {
        if (selectedTarget.isBlank()) {
            return ValidationInfo("Please select a deploy target")
        }
        if (branchField.text.isBlank()) {
            return ValidationInfo("Git branch pattern is required", branchField)
        }
        return null
    }

    fun getTrigger(): DeployTrigger = DeployTrigger().apply {
        targetName = selectedTarget
        triggerType = selectedTriggerType
        gitBranch = branchField.text
        enabled = true
    }
}
