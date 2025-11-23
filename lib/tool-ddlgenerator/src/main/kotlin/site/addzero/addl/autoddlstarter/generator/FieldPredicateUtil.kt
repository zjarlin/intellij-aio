package site.addzero.addl.autoddlstarter.generator

import cn.hutool.core.date.DateTime
import cn.hutool.core.util.StrUtil
import site.addzero.addl.autoddlstarter.generator.consts.POSTGRESQL
import site.addzero.addl.autoddlstarter.generator.entity.JavaFieldMetaInfo
import site.addzero.addl.settings.SettingContext
import org.apache.commons.lang3.StringUtils.containsAnyIgnoreCase
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*
import kotlin.jvm.java

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

        val isPg = SettingContext.settings.dbType == _root_ide_package_.site.addzero.addl.autoddlstarter.generator.consts.POSTGRESQL

        val assignableFrom = String::class.java.isAssignableFrom(javaType)

        if (isPg && assignableFrom) {
            return true
        }

        return containsAnyIgnoreCase(
            fieldName,
            "url",
            "base64",
            "text",
            "path",
            "introduction"
        ) && isType(f, arrayOf(String::class.java)) && assignableFrom
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
