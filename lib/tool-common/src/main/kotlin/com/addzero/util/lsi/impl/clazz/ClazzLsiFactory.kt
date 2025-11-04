package com.addzero.util.lsi.impl.clazz

import com.addzero.util.lsi.LsiClass
import com.addzero.util.lsi.LsiFactory

/**
 * 基于 Java Class 字节码的 LsiFactory 实现
 */
class ClazzLsiFactory : LsiFactory {
    override fun createLsiClass(clazz: Any): LsiClass? {
        if (clazz is Class<*>) {
            return ClazzLsiClass(clazz)
        }
        return null
    }
}