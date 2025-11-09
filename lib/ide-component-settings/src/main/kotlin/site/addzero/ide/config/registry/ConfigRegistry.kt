package site.addzero.ide.config.registry

import site.addzero.ide.config.annotation.SettingRoute
import site.addzero.ide.config.factory.ConfigFormFactory
import site.addzero.ide.config.model.ConfigItem
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

/**
 * 配置路由信息数据类
 */
data class ConfigRouteInfo(
    val path: List<String>,
    val configClass: KClass<*>,
    val configItems: List<ConfigItem>
)

/**
 * 配置注册管理器
 * 负责注册和管理所有带 @SettingRoute 注解的配置类
 */
object ConfigRegistry {
    private val registeredConfigs = mutableMapOf<String, ConfigRouteInfo>()

    /**
     * 注册配置类
     *
     * @param configClass 带 @SettingRoute 注解的配置类
     */
    fun registerConfig(configClass: KClass<*>) {
        val settingRouteAnnotation = configClass.findAnnotation<SettingRoute>()
        if (settingRouteAnnotation != null) {
            // 生成唯一标识符
            val id = settingRouteAnnotation.path.joinToString(".") + "." + configClass.simpleName

            // 生成配置项列表
            val configItems = try {
                ConfigFormFactory.generateConfigItems(configClass)
            } catch (e: Exception) {
                System.err.println("Failed to generate config items for ${configClass}: ${e.message}")
                e.printStackTrace()
                emptyList()
            }

            // 创建配置路由信息
            val routeInfo = ConfigRouteInfo(
                path = settingRouteAnnotation.path.toList(),
                configClass = configClass, // 直接使用配置类本身
                configItems = configItems
            )

            registeredConfigs[id] = routeInfo
            System.out.println("Registered config: $id with ${configItems.size} items")
        }
    }

    /**
     * 获取所有注册的配置
     */
    fun getRegisteredConfigs(): Map<String, ConfigRouteInfo> {
        return registeredConfigs.toMap()
    }

    /**
     * 根据路径获取配置
     */
    fun getConfigByPath(path: List<String>): ConfigRouteInfo? {
        return registeredConfigs.values.find { it.path == path }
    }

    /**
     * 清空注册信息
     */
    fun clear() {
        registeredConfigs.clear()
    }
}