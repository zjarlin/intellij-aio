package site.addzero.deploy.ui

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.*
import site.addzero.deploy.DeployTarget
import site.addzero.deploy.DeployTrigger
import site.addzero.deploy.JarDeploySettings
import site.addzero.deploy.TriggerType
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * 部署配置面板
 */
class JarDeployConfigurable(private val project: Project) : Configurable {

    private var mainPanel: JPanel? = null
    private val targetsModel = DefaultListModel<String>()
    private val configsModel = DefaultListModel<String>()
    private val triggerModel = DefaultListModel<String>()
    
    private val targetsList = JBList(targetsModel)
    private val configsList = JBList(configsModel)
    private val triggerList = JBList(triggerModel)

    override fun getDisplayName(): String = "Jar Deploy"

    override fun createComponent(): JComponent {
        loadCurrentSettings()
        
        mainPanel = panel {
            group("SSH Targets") {
                row {
                    val decorator = ToolbarDecorator.createDecorator(targetsList)
                        .setAddAction { addTarget() }
                        .setEditAction { editTarget() }
                        .setRemoveAction { removeTarget() }
                        .createPanel()
                    
                    cell(decorator)
                        .align(Align.FILL)
                        .resizableColumn()
                }.resizableRow()
            }
            
            group("Deploy Configurations") {
                row {
                    val decorator = ToolbarDecorator.createDecorator(configsList)
                        .setAddAction { addConfiguration() }
                        .setEditAction { editConfiguration() }
                        .setRemoveAction { removeConfiguration() }
                        .createPanel()
                    
                    cell(decorator)
                        .align(Align.FILL)
                        .resizableColumn()
                }.resizableRow()
            }
            
            group("Triggers") {
                row {
                    val decorator = ToolbarDecorator.createDecorator(triggerList)
                        .setAddAction { addTrigger() }
                        .setRemoveAction { removeTrigger() }
                        .createPanel()
                    
                    cell(decorator)
                        .align(Align.FILL)
                        .resizableColumn()
                }.resizableRow()
                
                row {
                    comment("Triggers allow automatic deployment on Git push/commit events")
                }
            }
        }
        
        return mainPanel!!
    }

    private fun loadCurrentSettings() {
        val settings = JarDeploySettings.getInstance(project)
        
        targetsModel.clear()
        settings.getTargets().forEach { target ->
            targetsModel.addElement("${target.name} -> ${target.sshConfigName}:${target.remoteDir}")
        }
        
        configsModel.clear()
        settings.getConfigurations().forEach { config ->
            val status = if (config.enabled) "✓" else "✗"
            configsModel.addElement("[$status] ${config.name} -> ${config.targetName} (${config.artifacts.size} artifacts)")
        }
        
        triggerModel.clear()
        settings.getTriggers().filter { it.enabled }.forEach { trigger ->
            val typeStr = when (trigger.triggerType) {
                TriggerType.GIT_PUSH -> "Git Push"
                TriggerType.GIT_COMMIT -> "Git Commit"
                TriggerType.MANUAL -> "Manual"
            }
            triggerModel.addElement("${trigger.targetName} - $typeStr (${trigger.gitBranch})")
        }
    }

    private fun addTarget() {
        val dialog = DeployTargetDialog(project, null)
        if (dialog.showAndGet()) {
            val target = dialog.getTarget()
            JarDeploySettings.getInstance(project).addTarget(target)
            loadCurrentSettings()
        }
    }

    private fun editTarget() {
        val selectedIndex = targetsList.selectedIndex
        if (selectedIndex < 0) return
        
        val settings = JarDeploySettings.getInstance(project)
        val target = settings.getTargets().getOrNull(selectedIndex) ?: return
        
        val dialog = DeployTargetDialog(project, target)
        if (dialog.showAndGet()) {
            val newTarget = dialog.getTarget()
            settings.removeTarget(target.name ?: "")
            settings.addTarget(newTarget)
            loadCurrentSettings()
        }
    }

    private fun removeTarget() {
        val selectedIndex = targetsList.selectedIndex
        if (selectedIndex < 0) return
        
        val settings = JarDeploySettings.getInstance(project)
        val target = settings.getTargets().getOrNull(selectedIndex) ?: return
        settings.removeTarget(target.name ?: "")
        loadCurrentSettings()
    }

    private fun addConfiguration() {
        val settings = JarDeploySettings.getInstance(project)
        val targets = settings.getTargets()
        if (targets.isEmpty()) return
        
        val dialog = DeployConfigurationDialog(project, null, targets)
        if (dialog.showAndGet()) {
            settings.addConfiguration(dialog.getConfiguration())
            loadCurrentSettings()
        }
    }

    private fun editConfiguration() {
        val selectedIndex = configsList.selectedIndex
        if (selectedIndex < 0) return
        
        val settings = JarDeploySettings.getInstance(project)
        val config = settings.getConfigurations().getOrNull(selectedIndex) ?: return
        val targets = settings.getTargets()
        
        val dialog = DeployConfigurationDialog(project, config, targets)
        if (dialog.showAndGet()) {
            settings.updateConfiguration(config.name ?: "", dialog.getConfiguration())
            loadCurrentSettings()
        }
    }

    private fun removeConfiguration() {
        val selectedIndex = configsList.selectedIndex
        if (selectedIndex < 0) return
        
        val settings = JarDeploySettings.getInstance(project)
        val config = settings.getConfigurations().getOrNull(selectedIndex) ?: return
        settings.removeConfiguration(config.name ?: "")
        loadCurrentSettings()
    }

    private fun addTrigger() {
        val settings = JarDeploySettings.getInstance(project)
        val targets = settings.getTargets()
        
        if (targets.isEmpty()) {
            return
        }
        
        val dialog = TriggerDialog(project, targets)
        if (dialog.showAndGet()) {
            val trigger = dialog.getTrigger()
            settings.addTrigger(trigger)
            loadCurrentSettings()
        }
    }

    private fun removeTrigger() {
        val selectedIndex = triggerList.selectedIndex
        if (selectedIndex < 0) return
        
        val settings = JarDeploySettings.getInstance(project)
        val trigger = settings.getTriggers().getOrNull(selectedIndex) ?: return
        settings.removeTrigger(trigger.targetName ?: "", trigger.triggerType)
        loadCurrentSettings()
    }

    override fun isModified(): Boolean = false

    override fun apply() {
        // 设置已在对话框中实时保存
    }

    override fun reset() {
        loadCurrentSettings()
    }
}
