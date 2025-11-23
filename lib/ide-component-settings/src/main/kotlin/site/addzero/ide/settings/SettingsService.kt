package site.addzero.ide.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

/**
 * 通用设置服务基类
 * 
 * 提供类型安全的设置持久化
 * 
 * @param T 设置数据类类型
 */
abstract class SettingsService<T : Any>(
    private val defaultSettings: T,
    private val dataClass: KClass<T>
) : PersistentStateComponent<Map<String, String>> {
    
    private var currentSettings: T = defaultSettings
    
    override fun getState(): Map<String, String> =
        dataClass.memberProperties
            .mapNotNull { property ->
                property.isAccessible = true
                val value = property.getter.call(currentSettings)
                value?.let { property.name to it.toString() }
            }
            .toMap()
    
    override fun loadState(state: Map<String, String>) {
        dataClass.memberProperties.forEach { property ->
            property.isAccessible = true
            state[property.name]?.let { value ->
                runCatching {
                    val field = dataClass.java.getDeclaredField(property.name)
                    field.isAccessible = true
                    field.set(currentSettings, value)
                }
            }
        }
    }
    
    fun getSettings(): T = currentSettings
    
    fun updateSettings(data: Map<String, Any?>) {
        data.forEach { (name, value) ->
            runCatching {
                val field = dataClass.java.getDeclaredField(name)
                field.isAccessible = true
                field.set(currentSettings, value)
            }.onFailure { it.printStackTrace() }
        }
    }
}

/**
 * 应用级设置服务
 */
abstract class ApplicationSettingsService<T : Any>(
    defaultSettings: T,
    dataClass: KClass<T>
) : SettingsService<T>(defaultSettings, dataClass)

/**
 * 项目级设置服务
 */
abstract class ProjectSettingsService<T : Any>(
    private val project: Project,
    defaultSettings: T,
    dataClass: KClass<T>
) : SettingsService<T>(defaultSettings, dataClass)
