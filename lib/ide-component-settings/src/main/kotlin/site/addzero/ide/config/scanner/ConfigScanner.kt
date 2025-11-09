package site.addzero.ide.config.scanner

import site.addzero.ide.config.annotation.SettingRoute
import site.addzero.ide.config.registry.ConfigRegistry
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

/**
 * 配置扫描器
 * 负责扫描和注册带 @SettingRoute 注解的配置类
 */
object ConfigScanner {
    private var isScanned = false

    /**
     * 扫描并注册所有带 @SettingRoute 注解的配置类
     */
    fun scanAndRegisterConfigs() {
        if (isScanned) return

        // 注册示例配置类
        try {
            // 注册数据库配置
            val databaseConfigClass = Class.forName("site.addzero.ide.config.example.ExampleConfig\$DatabaseConfig").kotlin
            registerIfRouted(databaseConfigClass)
        } catch (e: ClassNotFoundException) {
            System.err.println("DatabaseConfig class not found: ${e.message}")
            // 忽略，示例类可能不存在
        } catch (e: Exception) {
            System.err.println("Error registering DatabaseConfig: ${e.message}")
        }

        try {
            // 注册常用配置
            val usefulConfigClass = Class.forName("site.addzero.ide.config.example.ExampleConfig\$UsefulConfig").kotlin
            registerIfRouted(usefulConfigClass)
        } catch (e: ClassNotFoundException) {
            System.err.println("UsefulConfig class not found: ${e.message}")
            // 忽略，示例类可能不存在
        } catch (e: Exception) {
            System.err.println("Error registering UsefulConfig: ${e.message}")
        }

        try {
            // 注册性能配置
            val performanceConfigClass = Class.forName("site.addzero.ide.config.example.ExampleConfig\$PerformanceConfig").kotlin
            registerIfRouted(performanceConfigClass)
        } catch (e: ClassNotFoundException) {
            System.err.println("PerformanceConfig class not found: ${e.message}")
            // 忽略，示例类可能不存在
        } catch (e: Exception) {
            System.err.println("Error registering PerformanceConfig: ${e.message}")
        }

        try {
            // 注册测试配置
            val testConfigClass = Class.forName("site.addzero.ide.config.example.TestConfig").kotlin
            registerIfRouted(testConfigClass)
        } catch (e: ClassNotFoundException) {
            System.err.println("TestConfig class not found: ${e.message}")
            // 忽略，测试类可能不存在
        } catch (e: Exception) {
            System.err.println("Error registering TestConfig: ${e.message}")
        }

        isScanned = true
    }

    /**
     * 如果类带有 @SettingRoute 注解，则注册它
     */
    private fun registerIfRouted(configClass: KClass<*>) {
        if (configClass.findAnnotation<SettingRoute>() != null) {
            ConfigRegistry.registerConfig(configClass)
        }
    }

    /**
     * 注册指定的配置类
     */
    fun registerConfig(configClass: KClass<*>) {
        ConfigRegistry.registerConfig(configClass)
    }
}
