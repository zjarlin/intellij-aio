package site.addzero.deploy.ui

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.*
import site.addzero.deploy.*
import java.awt.BorderLayout
import javax.swing.DefaultListModel
import javax.swing.JPanel
import javax.swing.ListSelectionModel

/**
 * 工具窗口面板 - 支持多选构建物
 */
class JarDeployPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val targetsModel = DefaultListModel<String>()
    private val configsModel = DefaultListModel<String>()
    private val triggersModel = DefaultListModel<String>()
    
    private val targetsList = JBList(targetsModel)
    private val configsList = JBList(configsModel)
    private val triggersList = JBList(triggersModel)

    init {
        val contentPanel = panel {
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
                
                row {
                    button("Deploy Selected") { deploySelected() }
                    button("Deploy All") { deployAll() }
                }
            }
            
            group("Triggers") {
                row {
                    val decorator = ToolbarDecorator.createDecorator(triggersList)
                        .setAddAction { addTrigger() }
                        .setRemoveAction { removeTrigger() }
                        .createPanel()
                    
                    cell(decorator)
                        .align(Align.FILL)
                        .resizableColumn()
                }.resizableRow()
            }
            
            row {
                button("Refresh") { loadSettings() }
            }
        }
        
        add(contentPanel, BorderLayout.CENTER)
        loadSettings()
    }

    private fun loadSettings() {
        val settings = JarDeploySettings.getInstance(project)
        
        targetsModel.clear()
        settings.getTargets().forEach { target ->
            val status = if (target.enabled) "✓" else "✗"
            targetsModel.addElement("[$status] ${target.name} → ${target.sshConfigName}:${target.remoteDir}")
        }
        
        configsModel.clear()
        settings.getConfigurations().forEach { config ->
            val status = if (config.enabled) "✓" else "✗"
            val artifactCount = config.artifacts.size
            configsModel.addElement("[$status] ${config.name} → ${config.targetName} ($artifactCount artifacts)")
        }
        
        triggersModel.clear()
        settings.getTriggers().forEach { trigger ->
            val typeIcon = when (trigger.triggerType) {
                TriggerType.GIT_PUSH -> "⬆"
                TriggerType.GIT_COMMIT -> "✔"
                TriggerType.MANUAL -> "👆"
            }
            val status = if (trigger.enabled) "ON" else "OFF"
            triggersModel.addElement("[$status] $typeIcon ${trigger.targetName} (${trigger.gitBranch})")
        }
    }

    private fun addTarget() {
        val dialog = DeployTargetDialog(project, null)
        if (dialog.showAndGet()) {
            val target = dialog.getTarget()
            JarDeploySettings.getInstance(project).addTarget(target)
            loadSettings()
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
            loadSettings()
        }
    }

    private fun removeTarget() {
        val selectedIndex = targetsList.selectedIndex
        if (selectedIndex < 0) return
        
        val settings = JarDeploySettings.getInstance(project)
        val target = settings.getTargets().getOrNull(selectedIndex) ?: return
        settings.removeTarget(target.name ?: "")
        loadSettings()
    }

    private fun addConfiguration() {
        val settings = JarDeploySettings.getInstance(project)
        val targets = settings.getTargets()
        
        if (targets.isEmpty()) {
            Messages.showWarningDialog(project, "Please add SSH target first", "No Targets")
            return
        }
        
        val dialog = DeployConfigurationDialog(project, null, targets)
        if (dialog.showAndGet()) {
            val config = dialog.getConfiguration()
            settings.addConfiguration(config)
            loadSettings()
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
            val newConfig = dialog.getConfiguration()
            settings.updateConfiguration(config.name ?: "", newConfig)
            loadSettings()
        }
    }

    private fun removeConfiguration() {
        val selectedIndex = configsList.selectedIndex
        if (selectedIndex < 0) return
        
        val settings = JarDeploySettings.getInstance(project)
        val config = settings.getConfigurations().getOrNull(selectedIndex) ?: return
        settings.removeConfiguration(config.name ?: "")
        loadSettings()
    }

    private fun deploySelected() {
        val selectedIndex = configsList.selectedIndex
        if (selectedIndex < 0) {
            Messages.showInfoMessage(project, "Please select a configuration", "No Selection")
            return
        }
        
        val settings = JarDeploySettings.getInstance(project)
        val config = settings.getConfigurations().getOrNull(selectedIndex) ?: return
        val target = settings.getTargetByName(config.targetName ?: "") ?: return
        
        DeployExecutor.deploy(project, config, target)
    }

    private fun deployAll() {
        val settings = JarDeploySettings.getInstance(project)
        val configs = settings.getConfigurations().filter { it.enabled }
        
        if (configs.isEmpty()) {
            Messages.showInfoMessage(project, "No enabled configurations", "Nothing to Deploy")
            return
        }
        
        configs.forEach { config ->
            val target = settings.getTargetByName(config.targetName ?: "") ?: return@forEach
            DeployExecutor.deploy(project, config, target)
        }
    }

    private fun addTrigger() {
        val settings = JarDeploySettings.getInstance(project)
        val configs = settings.getConfigurations()
        
        if (configs.isEmpty()) {
            Messages.showWarningDialog(project, "Please add deploy configuration first", "No Configurations")
            return
        }
        
        val dialog = TriggerDialog(project, settings.getTargets())
        if (dialog.showAndGet()) {
            val trigger = dialog.getTrigger()
            settings.addTrigger(trigger)
            loadSettings()
        }
    }

    private fun removeTrigger() {
        val selectedIndex = triggersList.selectedIndex
        if (selectedIndex < 0) return
        
        val settings = JarDeploySettings.getInstance(project)
        val trigger = settings.getTriggers().getOrNull(selectedIndex) ?: return
        settings.removeTrigger(trigger.targetName ?: "", trigger.triggerType)
        loadSettings()
    }
}
