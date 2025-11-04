package com.addzero.addl.util.fieldinfo

// 为了保持向后兼容性，提供对新模块中工具类的引用
import com.addzero.util.fieldinfo.getFieldInfos
import com.addzero.util.fieldinfo.getFieldInfosRecursive
import com.addzero.util.fieldinfo.getSimpleFieldInfoStr
import com.addzero.util.fieldinfo.isCustomObject
import com.addzero.util.fieldinfo.isList

// 为保持兼容性，创建函数别名
fun getFieldInfos(clazz: Class<*>) = getFieldInfos(clazz)
fun getFieldInfosRecursive(clazz: Class<*>) = getFieldInfosRecursive(clazz)
fun getSimpleFieldInfoStr(clazz: Class<*>) = getSimpleFieldInfoStr(clazz)
fun isCustomObject(clazz: Class<*>) = isCustomObject(clazz)
fun isList(field: java.lang.reflect.Field) = isList(field)