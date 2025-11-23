package site.addzero.addl.autoddlstarter.generator

import cn.hutool.core.date.DateTime
import site.addzero.addl.settings.SettingContext
import site.addzero.util.str.containsAnyIgnoreCase
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*

object FieldPredicateUtil {

    fun isType(f: site.addzero.addl.autoddlstarter.generator.entity.JavaFieldMetaInfo, classes: Array<Class<*>>): Boolean {
        return classes.any { it.isAssignableFrom(f.type) }
    }

    fun isIntType(f: site.addzero.addl.autoddlstarter.generator.entity.JavaFieldMetaInfo): Boolean {
        return isType(f, arrayOf(Int::class.java, Integer::class.java))
    }

    fun isLongType(f: site.addzero.addl.autoddlstarter.generator.entity.JavaFieldMetaInfo): Boolean {
        return isType(f, arrayOf(Long::class.java))
    }

    /**
     * 长文本判断
     * @param [f]
     * @return [Boolean]
     */
    fun isTextType(f: site.addzero.addl.autoddlstarter.generator.entity.JavaFieldMetaInfo): Boolean {


        val fieldName = f.name
        val javaType = f.type

        val isPg = SettingContext.settings.dbType == "pg"

        val assignableFrom = String::class.java.isAssignableFrom(javaType)

        if (isPg && assignableFrom) {
            return true
        }
        val containsAnyIgnoreCase = fieldName.containsAnyIgnoreCase("url", "base64", "text", "path", "introduction")
        return containsAnyIgnoreCase && isType(f, arrayOf(String::class.java)) && assignableFrom
    }

    fun isStringType(f: site.addzero.addl.autoddlstarter.generator.entity.JavaFieldMetaInfo): Boolean {
        return isType(f, arrayOf(String::class.java))

    }

    fun isCharType(f: site.addzero.addl.autoddlstarter.generator.entity.JavaFieldMetaInfo): Boolean {
        return isType(f, arrayOf(Char::class.java))
    }

    fun isBooleanType(f: site.addzero.addl.autoddlstarter.generator.entity.JavaFieldMetaInfo): Boolean {
        return isType(f, arrayOf(Boolean::class.java))
    }

    fun isDateType(f: site.addzero.addl.autoddlstarter.generator.entity.JavaFieldMetaInfo): Boolean {
        return isType(f, arrayOf(Date::class.java, DateTime::class.java, LocalDate::class.java))
    }

    fun isTimeType(f: site.addzero.addl.autoddlstarter.generator.entity.JavaFieldMetaInfo): Boolean {
        return isType(f, arrayOf(Date::class.java, DateTime::class.java, LocalTime::class.java))
    }

    fun isDateTimeType(f: site.addzero.addl.autoddlstarter.generator.entity.JavaFieldMetaInfo): Boolean {
        return isType(f, arrayOf(Date::class.java, DateTime::class.java, LocalDateTime::class.java))
    }

    fun isBigDecimalType(f: site.addzero.addl.autoddlstarter.generator.entity.JavaFieldMetaInfo): Boolean {
        return isType(f, arrayOf(BigDecimal::class.java))
    }

    fun isDoubleType(f: site.addzero.addl.autoddlstarter.generator.entity.JavaFieldMetaInfo): Boolean {
        return isType(f, arrayOf(Double::class.java))
    }

}
