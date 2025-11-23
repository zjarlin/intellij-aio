package site.addzero.ddl.parser

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*

/**
 * 字段类型映射器
 * 
 * 提供Java类型到数据库类型的映射逻辑
 */
class FieldTypeMapper {
    
    /**
     * 判断是否为整型
     */
    fun isIntType(javaType: String): Boolean {
        return javaType in setOf(
            "int", "java.lang.Integer",
            "short", "java.lang.Short",
            "byte", "java.lang.Byte"
        )
    }
    
    /**
     * 判断是否为长整型
     */
    fun isLongType(javaType: String): Boolean {
        return javaType in setOf("long", "java.lang.Long")
    }
    
    /**
     * 判断是否为字符串类型
     */
    fun isStringType(javaType: String): Boolean {
        return javaType == "java.lang.String"
    }
    
    /**
     * 判断是否为长文本类型
     */
    fun isTextType(javaType: String, fieldName: String): Boolean {
        if (!isStringType(javaType)) return false
        
        // 根据字段名判断
        val textKeywords = listOf("url", "base64", "text", "path", "introduction", "content", "description")
        return textKeywords.any { fieldName.contains(it, ignoreCase = true) }
    }
    
    /**
     * 判断是否为字符类型
     */
    fun isCharType(javaType: String): Boolean {
        return javaType in setOf("char", "java.lang.Character")
    }
    
    /**
     * 判断是否为布尔类型
     */
    fun isBooleanType(javaType: String): Boolean {
        return javaType in setOf("boolean", "java.lang.Boolean")
    }
    
    /**
     * 判断是否为日期类型
     */
    fun isDateType(javaType: String): Boolean {
        return javaType in setOf(
            "java.util.Date",
            "java.sql.Date",
            "java.time.LocalDate"
        )
    }
    
    /**
     * 判断是否为时间类型
     */
    fun isTimeType(javaType: String): Boolean {
        return javaType in setOf(
            "java.sql.Time",
            "java.time.LocalTime"
        )
    }
    
    /**
     * 判断是否为日期时间类型
     */
    fun isDateTimeType(javaType: String): Boolean {
        return javaType in setOf(
            "java.util.Date",
            "java.sql.Timestamp",
            "java.time.LocalDateTime",
            "java.time.ZonedDateTime",
            "java.time.OffsetDateTime"
        )
    }
    
    /**
     * 判断是否为BigDecimal类型
     */
    fun isBigDecimalType(javaType: String): Boolean {
        return javaType == "java.math.BigDecimal"
    }
    
    /**
     * 判断是否为浮点类型
     */
    fun isDoubleType(javaType: String): Boolean {
        return javaType in setOf(
            "double", "java.lang.Double",
            "float", "java.lang.Float"
        )
    }
    
    /**
     * 获取默认长度
     */
    fun getDefaultLength(javaType: String, fieldName: String): Int {
        return when {
            isTextType(javaType, fieldName) -> -1  // TEXT类型不需要长度
            isStringType(javaType) -> 255
            isBigDecimalType(javaType) -> -1       // DECIMAL类型使用precision和scale
            else -> -1
        }
    }
    
    /**
     * 获取默认精度（用于DECIMAL）
     */
    fun getDefaultPrecision(javaType: String): Int {
        return if (isBigDecimalType(javaType)) 10 else -1
    }
    
    /**
     * 获取默认小数位数（用于DECIMAL）
     */
    fun getDefaultScale(javaType: String): Int {
        return if (isBigDecimalType(javaType)) 2 else -1
    }
}
