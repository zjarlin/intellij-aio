package site.addzero.lsi.analyzer.extension

import com.intellij.openapi.extensions.ExtensionPointName
import site.addzero.lsi.analyzer.extension.LsiMetadataEnhancer as CoreLsiMetadataEnhancer
import site.addzero.lsi.analyzer.extension.MetadataEnhancerRegistry
import site.addzero.lsi.analyzer.extension.JimmerMetadataEnhancer as CoreJimmerEnhancer
import site.addzero.lsi.analyzer.metadata.PojoMetadata
import site.addzero.util.lsi.clazz.LsiClass

// 类型别名，方便使用
typealias LsiMetadataEnhancer<T> = CoreLsiMetadataEnhancer<T>
typealias JimmerMetadataEnhancer = CoreJimmerEnhancer

/**
 * IntelliJ Extension Point 定义
 * 
 * 用法：
 * 1. 在 plugin.xml 中声明扩展点
 * 2. 其他插件通过 <extensions> 实现扩展
 * 
 * 示例（其他插件的 plugin.xml）：
 * ```xml
 * <extensions defaultExtensionNs="site.addzero.lsi-code-analyzer">
 *     <metadataEnhancer implementation="com.example.MyCustomEnhancer"/>
 * </extensions>
 * ```
 */
object LsiExtensionPoints {
    
    /**
     * 元数据增强器扩展点
     * 
     * 其他插件可以通过此扩展点添加自定义的元数据解析器
     */
    val METADATA_ENHANCER: ExtensionPointName<CoreLsiMetadataEnhancer<*>> = 
        ExtensionPointName.create("site.addzero.lsi-code-analyzer.metadataEnhancer")
    
    /**
     * 获取所有注册的增强器（包括 Extension Point 和 SPI）
     */
    fun getAllEnhancers(): List<CoreLsiMetadataEnhancer<*>> {
        val epEnhancers = try {
            METADATA_ENHANCER.extensionList
        } catch (e: Exception) {
            emptyList()
        }
        val registryEnhancers = MetadataEnhancerRegistry.getAll()
        
        return (epEnhancers + registryEnhancers)
            .distinctBy { it.id }
            .sortedBy { it.priority }
    }
    
    /**
     * 查找适用的增强器
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> findEnhancer(lsiClass: LsiClass): CoreLsiMetadataEnhancer<T>? {
        return getAllEnhancers().find { it.support(lsiClass) } as? CoreLsiMetadataEnhancer<T>
    }
    
    /**
     * 增强元数据
     */
    fun enhance(lsiClass: LsiClass, base: PojoMetadata): Any? {
        return findEnhancer<Any>(lsiClass)?.enhance(lsiClass, base)
    }
}

/**
 * 扩展点初始化器
 */
object LsiExtensionInitializer {
    
    private var initialized = false
    
    fun initialize() {
        if (initialized) return
        initialized = true
        
        // 注册内置增强器（核心库中的实现）
        MetadataEnhancerRegistry.register(CoreJimmerEnhancer())
        
        // 加载 SPI 增强器
        loadSpiEnhancers()
    }
    
    private fun loadSpiEnhancers() {
        try {
            val loader = java.util.ServiceLoader.load(
                CoreLsiMetadataEnhancer::class.java,
                javaClass.classLoader
            )
            loader.forEach { enhancer ->
                MetadataEnhancerRegistry.register(enhancer)
            }
        } catch (e: Exception) {
            // SPI 加载失败时静默处理
            e.printStackTrace()
        }
    }
}
