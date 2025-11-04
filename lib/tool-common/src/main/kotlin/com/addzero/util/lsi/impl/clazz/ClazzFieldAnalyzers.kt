package com.addzero.util.lsi.impl.clazz

import java.lang.reflect.Field
import java.util.*

/**
 * Java Field 分析器集合
 */
object ClazzFieldAnalyzers {

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

        fun isCollectionType(field: Field): Boolean {
            return COLLECTION_TYPES.any { it.isAssignableFrom(field.type) }
        }
    }
    
    /**
     * 静态字段分析器
     */
    object StaticFieldAnalyzer {
        fun isStaticField(field: Field): Boolean {
            return java.lang.reflect.Modifier.isStatic(field.modifiers)
        }
    }
    
    /**
     * 常量字段分析器
     */
    object ConstantFieldAnalyzer {
        fun isConstantField(field: Field): Boolean {
            return java.lang.reflect.Modifier.isFinal(field.modifiers) && 
                   java.lang.reflect.Modifier.isStatic(field.modifiers)
        }
    }
}