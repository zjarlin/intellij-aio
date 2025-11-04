package com.addzero.util.lsi.impl.clazz

import java.util.*
import java.lang.reflect.Array

/**
 * Java Class Type 分析器集合
 */
object ClazzTypeAnalyzers {

    /**
     * 集合类型分析器
     */
    object CollectionTypeAnalyzer {
        private val COLLECTION_TYPES = setOf(
            Collection::class.java,
            List::class.java,
            Set::class.java,
            Map::class.java,
            ArrayList::class.java,
            LinkedList::class.java,
            HashSet::class.java,
            LinkedHashSet::class.java,
            HashMap::class.java,
            LinkedHashMap::class.java
        )

        fun isCollectionType(clazz: Class<*>): Boolean {
            return COLLECTION_TYPES.any { it.isAssignableFrom(clazz) }
        }
    }
    
    /**
     * 可空性分析器
     */
    object NullabilityAnalyzer {
        fun isNullable(clazz: Class<*>): Boolean {
            // 在Java中，只有基本类型不可为空
            return !clazz.isPrimitive
        }
    }
    
    /**
     * 数组分析器
     */
    object ArrayAnalyzer {
        fun isArray(clazz: Class<*>): Boolean {
            return clazz.isArray
        }
        
        fun getComponentType(clazz: Class<*>): Class<*>? {
            return if (clazz.isArray) clazz.componentType else null
        }
    }
}