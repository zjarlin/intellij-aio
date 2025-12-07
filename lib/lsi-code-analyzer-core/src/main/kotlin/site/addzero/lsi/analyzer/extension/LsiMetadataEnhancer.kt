package site.addzero.lsi.analyzer.extension

import site.addzero.util.lsi.clazz.LsiClass

/**
 * LSI 元数据增强器扩展点接口
 * 
 * 用于扩展 LSI 的元数据解析能力，支持框架特定的注解处理
 * 
 * 扩展方式：
 * 1. IntelliJ Extension Point - 开发插件实现此接口
 * 2. SPI - 在 META-INF/services 中注册实现类
 */
interface LsiMetadataEnhancer<T> {
    
    /** 扩展器唯一标识 */
    val id: String
    
    /** 扩展器名称 */
    val name: String
    
    /** 优先级，数值越小优先级越高 */
    val priority: Int get() = 100
    
    /** 判断是否支持处理该类 */
    fun support(lsiClass: LsiClass): Boolean
    
    /** 增强元数据，返回扩展后的元数据对象 */
    fun enhance(lsiClass: LsiClass, base: LsiClass): T
    
    /** 获取扩展元数据的类型 */
    fun getEnhancedType(): Class<T>
}

/**
 * 元数据增强器注册表
 */
object MetadataEnhancerRegistry {
    
    private val enhancers = mutableListOf<LsiMetadataEnhancer<*>>()
    
    fun register(enhancer: LsiMetadataEnhancer<*>) {
        enhancers.add(enhancer)
        enhancers.sortBy { it.priority }
    }
    
    fun unregister(id: String) {
        enhancers.removeIf { it.id == id }
    }
    
    fun getAll(): List<LsiMetadataEnhancer<*>> = enhancers.toList()
    
    @Suppress("UNCHECKED_CAST")
    fun <T> findEnhancer(lsiClass: LsiClass): LsiMetadataEnhancer<T>? {
        return enhancers.find { it.support(lsiClass) } as? LsiMetadataEnhancer<T>
    }
    
    fun <T> enhance(lsiClass: LsiClass, base: LsiClass): Any? {
        return findEnhancer<T>(lsiClass)?.enhance(lsiClass, base)
    }
    
    fun clear() = enhancers.clear()
}
