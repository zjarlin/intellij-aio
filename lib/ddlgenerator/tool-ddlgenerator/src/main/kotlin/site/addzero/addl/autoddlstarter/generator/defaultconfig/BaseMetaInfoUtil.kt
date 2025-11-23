package site.addzero.addl.autoddlstarter.generator.defaultconfig

import site.addzero.addl.autoddlstarter.generator.entity.JavaFieldMetaInfo
import site.addzero.util.lsi.clazz.LsiClass
import site.addzero.util.lsi.field.LsiField

class BaseMetaInfoUtil(val idName: String) {
    fun String?.isPrimaryKey(): String {
        if (this?.lowercase()!! == idName) {
            return "Y"
        }
        return ""
    }

    fun isPrimaryKeyBoolean(fieldName: String?): Boolean {
        return fieldName?.lowercase()!! == idName
    }

    fun isAutoIncrement(fieldName: String?): String {
        return fieldName.isPrimaryKey()
    }

    fun isAutoIncrementBoolean(fieldName: String?): Boolean {
        return isPrimaryKeyBoolean(fieldName, idName)
    }

}
