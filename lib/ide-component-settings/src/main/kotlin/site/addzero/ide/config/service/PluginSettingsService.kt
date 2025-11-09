package site.addzero.ide.config.service

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import java.util.*

/**
 * 插件设置服务，用于持久化存储配置数据
 */
@Service(Service.Level.PROJECT)
@State(
    name = "PluginSettingsService",
    storages = [Storage("pluginSettings.xml")]
)
class PluginSettingsService : SimplePersistentStateComponent<PluginSettingsService.State>(State()) {
    
    class State : BaseState() {
        // 使用Map存储所有配置项的键值对
        var configData by map<String, String>()
    }
    
    companion object {
        fun getInstance(project: Project): PluginSettingsService {
            return project.getService(PluginSettingsService::class.java)
        }
    }
    
    /**
     * 保存配置数据
     */
    fun saveConfigData(data: Map<String, Any?>) {
        val stringData = data.mapValues { it.value?.toString() ?: "" }
        state.configData.clear()
        stringData.forEach { (key, value) ->
            state.configData[key] = value
        }
    }
    
    /**
     * 获取配置数据
     */
    fun getConfigData(): Map<String, String> {
        val result = HashMap<String, String>()
        state.configData.forEach { (key, value) ->
            result[key] = value
        }
        return result
    }
    
    /**
     * 清除所有配置数据
     */
    fun clearConfigData() {
        state.configData.clear()
    }
}