package site.addzero.deploy

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

/**
 * 项目级部署配置服务
 */
@Service(Service.Level.PROJECT)
@State(
    name = "JarDeploySettings",
    storages = [Storage("jarDeploy.xml")]
)
class JarDeploySettings : PersistentStateComponent<JarDeployState> {
    
    private var state = JarDeployState()
    
    override fun getState(): JarDeployState = state
    
    override fun loadState(state: JarDeployState) {
        this.state = state
    }
    
    fun getTargets(): List<DeployTarget> = state.targets.toList()
    
    fun addTarget(target: DeployTarget) {
        state.targets.add(target)
    }

    fun removeTarget(name: String) {
        state.targets.removeIf { it.name == name }
    }
    
    fun getTargetByName(name: String): DeployTarget? = state.targets.find { it.name == name }
    
    fun getTriggers(): List<DeployTrigger> = state.triggers.toList()
    
    fun addTrigger(trigger: DeployTrigger) {
        state.triggers.add(trigger)
    }

    fun removeTrigger(targetName: String, triggerType: TriggerType) {
        state.triggers.removeIf { it.targetName == targetName && it.triggerType == triggerType }
    }
    
    fun getConfigurations(): List<DeployConfiguration> = state.configurations.toList()
    
    fun addConfiguration(config: DeployConfiguration) {
        state.configurations.add(config)
    }

    fun updateConfiguration(name: String, config: DeployConfiguration) {
        state.configurations.removeIf { it.name == name }
        state.configurations.add(config)
    }

    fun removeConfiguration(name: String) {
        state.configurations.removeIf { it.name == name }
    }
    
    fun getConfigurationByName(name: String): DeployConfiguration? = 
        state.configurations.find { it.name == name }
    
    companion object {
        fun getInstance(project: Project): JarDeploySettings = 
            project.getService(JarDeploySettings::class.java)
    }
}
