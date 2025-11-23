package site.addzero.ddl.parser

import site.addzero.util.lsi.assist.TypeChecker
import site.addzero.util.lsi.type.TypeMapper

/**
 * 字段类型映射器
 *
 * 提供Java类型到数据库类型的映射逻辑
 * 
 * @deprecated 推荐直接使用 TypeChecker 和 TypeMapper
 * 该类保留用于向后兼容，内部委托给 LSI 体系的类型检查器和映射器
 */
@Deprecated(
    message = "Use TypeChecker and TypeMapper from LSI system instead",
    replaceWith = ReplaceWith("TypeChecker", "site.addzero.util.lsi.assist.TypeChecker")
)
class FieldTypeMapper {

    /**
     * 判断是否为整型
     */
    fun isIntType(javaType: String): Boolean = TypeChecker.isIntType(javaType)

    /**
     * 判断是否为长整型
     */
    fun isLongType(javaType: String): Boolean = TypeChecker.isLongType(javaType)

    /**
     * 判断是否为字符串类型
     */
    fun isStringType(javaType: String): Boolean = TypeChecker.isStringType(javaType)

    /**
     * 判断是否为长文本类型
     */
    fun isTextType(javaType: String, fieldName: String): Boolean =
        TypeChecker.isTextType(javaType, fieldName)

    /**
     * 判断是否为字符类型
     */
    fun isCharType(javaType: String): Boolean = TypeChecker.isCharType(javaType)

    /**
     * 判断是否为布尔类型
     */
    fun isBooleanType(javaType: String): Boolean = TypeChecker.isBooleanType(javaType)

    /**
     * 判断是否为日期类型
     */
    fun isDateType(javaType: String): Boolean = TypeChecker.isDateType(javaType)

    /**
     * 判断是否为时间类型
     */
    fun isTimeType(javaType: String): Boolean = TypeChecker.isTimeType(javaType)

    /**
     * 判断是否为日期时间类型
     */
    fun isDateTimeType(javaType: String): Boolean = TypeChecker.isDateTimeType(javaType)

    /**
     * 判断是否为BigDecimal类型
     */
    fun isBigDecimalType(javaType: String): Boolean = TypeChecker.isBigDecimalType(javaType)

    /**
     * 判断是否为浮点类型
     */
    fun isDoubleType(javaType: String): Boolean = TypeChecker.isDoubleType(javaType)
    
    /**
     * 获取默认长度
     */
    fun getDefaultLength(javaType: String, fieldName: String): Int =
        TypeMapper.getDefaultLength(javaType, fieldName)

    /**
     * 获取默认精度（用于DECIMAL）
     */
    fun getDefaultPrecision(javaType: String): Int =
        TypeMapper.getDefaultPrecision(javaType)

    /**
     * 获取默认小数位数（用于DECIMAL）
     */
    fun getDefaultScale(javaType: String): Int =
        TypeMapper.getDefaultScale(javaType)
}
