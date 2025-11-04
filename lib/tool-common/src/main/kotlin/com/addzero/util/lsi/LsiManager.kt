package com.addzero.util.lsi

import com.addzero.util.lsi.impl.clazz.ClazzLsiFactory
import com.addzero.util.lsi.impl.kt.KtLsiFactory
import com.addzero.util.lsi.impl.psi.PsiLsiFactory

/**
 * LSI 管理器
 * 负责根据不同的平台/语言环境提供相应的 LSI 实现
 */
object LsiManager {
    private val factories = mutableMapOf<String, LsiFactory>()

    init {
        // 注册默认的工厂实现
        registerFactory("psi", PsiLsiFactory())
        registerFactory("kt", KtLsiFactory())
        registerFactory("clazz", ClazzLsiFactory())
    }

    /**
     * 注册 LSI 工厂
     */
    fun registerFactory(name: String, factory: LsiFactory) {
        factories[name] = factory
    }

    /**
     * 获取指定名称的 LSI 工厂
     */
    fun getFactory(name: String): LsiFactory? {
        return factories[name]
    }

    /**
     * 获取所有已注册的工厂名称
     */
    fun getAvailableFactories(): Set<String> {
        return factories.keys
    }

    /**
     * 根据平台类型获取合适的工厂
     */
    fun getFactoryForPlatform(platform: String): LsiFactory? {
        return when (platform.lowercase()) {
            "java", "psi" -> factories["psi"]
            "kotlin", "kt" -> factories["kt"]
            "class", "clazz", "bytecode" -> factories["clazz"]
            else -> null
        }
    }
}