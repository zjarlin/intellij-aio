package site.addzero.addl.autoddlstarter.generator.defaultconfig

import site.addzero.addl.autoddlstarter.generator.entity.JavaFieldMetaInfo
import site.addzero.addl.settings.SettingContext
import site.addzero.util.psi.PsiUtil.extractInterfaceMetaInfo


object BaseMetaInfoUtil {
    fun String?.isPrimaryKey(): String {
        if (this?.lowercase()!! == SettingContext.settings.id) {
            return "Y"
        }
        return ""
    }

    fun isPrimaryKeyBoolean(fieldName: String?): Boolean {
        return fieldName?.lowercase()!! ==SettingContext.settings.id
    }

    fun isAutoIncrement(fieldName: String?): String {
        return fieldName.isPrimaryKey()
    }

    fun isAutoIncrementBoolean(fieldName: String?): Boolean {
        return isPrimaryKeyBoolean(fieldName)
    }

    /**
     * 处理接口和类的字段元数据逻辑不一样所以需要单独处理
     * @param [clazz]
     * @return [List<JavaFieldMetaInfo>]
     */
    fun javaFieldMetaInfos(clazz: Class<*>): List<JavaFieldMetaInfo> {
        if (clazz.isInterface) {
            return extractInterfaceMetaInfo(clazz)
        }
        return extractClassMetaInfo(clazz)
    }


}
