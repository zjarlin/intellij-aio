package com.addzero.addl.autoddlstarter.generator

import cn.hutool.core.util.StrUtil
import com.addzero.addl.autoddlstarter.generator.FieldPredicateUtil.isBigDecimalType
import com.addzero.addl.autoddlstarter.generator.FieldPredicateUtil.isBooleanType
import com.addzero.addl.autoddlstarter.generator.FieldPredicateUtil.isCharType
import com.addzero.addl.autoddlstarter.generator.FieldPredicateUtil.isDateTimeType
import com.addzero.addl.autoddlstarter.generator.FieldPredicateUtil.isDateType
import com.addzero.addl.autoddlstarter.generator.FieldPredicateUtil.isDoubleType
import com.addzero.addl.autoddlstarter.generator.FieldPredicateUtil.isIntType
import com.addzero.addl.autoddlstarter.generator.FieldPredicateUtil.isLongType
import com.addzero.addl.autoddlstarter.generator.FieldPredicateUtil.isStringType
import com.addzero.addl.autoddlstarter.generator.FieldPredicateUtil.isTextType
import com.addzero.addl.autoddlstarter.generator.FieldPredicateUtil.isTimeType
import com.addzero.addl.autoddlstarter.generator.consts.*
import com.addzero.addl.autoddlstarter.generator.entity.DDlRangeContext
import com.addzero.addl.autoddlstarter.generator.entity.FieldMapping
import com.addzero.addl.autoddlstarter.generator.entity.JavaFieldMetaInfo
import com.addzero.addl.autoddlstarter.generator.ex.*
import com.addzero.addl.ktututil.equalsIgnoreCase
import com.addzero.addl.ktututil.toCamelCase
import com.addzero.addl.settings.SettingContext
import com.addzero.addl.util.DialogUtil
import com.addzero.addl.util.JlStrUtil.ignoreCaseIn
import com.addzero.addl.util.JlStrUtil.ignoreCaseLike
import com.addzero.addl.util.containsAny
import com.addzero.common.kt_util.isBlank
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.*

fun filterBaseEneity(dDlRangeContext: DDlRangeContext): Boolean {
    val colName = dDlRangeContext.colName

    return filterBaseEntity(colName)
}

 fun filterBaseEntity(colName: String): Boolean {
    val settings = SettingContext.settings
    val id = settings.id
    val createBy = settings.createBy
    val updateBy = settings.updateBy
    val createTime = settings.createTime
    val updateTime = settings.updateTime

    if (colName.isBlank()) {
        return false
    }
    val arrayOf = arrayOf(id, createBy, updateBy, createTime, updateTime)
    val arrayOf1 = arrayOf.map { it.toCamelCase() }.toTypedArray()
    arrayOf(id, createBy, updateBy, createTime, updateTime)
    val containsAny = StrUtil.containsAny(colName, *arrayOf)
    val containsAny1 = StrUtil.containsAny(colName, *arrayOf1)
    val b = !(containsAny|| containsAny1)
    return b
}

interface IDatabaseGenerator {


    /**
     * 依据mysql类型推导出各种sql类型
     * @param [mysqlType]
     * @return [String]
     */
    fun mapTypeByMysqlType(mysqlType: String): String

    /**
     * 依据java类型推导出各种sql类型
     * @param [javaFieldMetaInfo]
     * @return [String]
     */
    fun mapTypeByJavaType(javaFieldMetaInfo: JavaFieldMetaInfo): String


    companion object {

        fun getLength(javaFieldMetaInfo: JavaFieldMetaInfo): String {
            return fieldMappings.find { it.predi.test(javaFieldMetaInfo) }?.length!!
        }

        fun getDatabaseDDLGenerator(dbType: String): DatabaseDDLGenerator {
            return databaseType[dbType]!!
        }




        fun ktType2RefType(type: String): String {
            val find = fieldMappings.find {
                it.ktClassSimple.equalsIgnoreCase(type)
            }
//            if (find == null) {
//                return String::class.java.name
//            }
            return find?.javaClassRef ?: String::class.java.name
        }


        var javaTypesEnum: Array<String>
            get() = fieldMappings.map { it.javaClassSimple }.distinct().toTypedArray()
            set(value) = TODO()


        var fieldMappings: List<FieldMapping> = listOf(
            FieldMapping(::isTextType, "text", "text", "clob", "CLOB", "text", "", String::class),
            FieldMapping(::isStringType, "varchar", "varchar", "varchar2", "VARCHAR", "varchar", "(255)", String::class),
            FieldMapping(::isCharType, "char", "character", "char", "VARCHAR", "character", "(255)", String::class),
            FieldMapping( ::isDateTimeType, "datetime", "timestamp with time zone", "timestamp", "TIMESTAMP", "timestamp", "", LocalDateTime::class ),
            FieldMapping(::isDateType, "date", "date", "date", "TIMESTAMP", "date", "", Date::class),
            FieldMapping(::isTimeType, "time", "time with time zone", "timestamp", "TIMESTAMP", "time", "", LocalTime::class),
            FieldMapping(::isIntType, "int", "integer", "number", "INT", "integer", "", Integer::class),
            FieldMapping( ::isDoubleType, "double", "double precision", "binary_double", "DOUBLE", "double precision", "(6,2)", Double::class ),
            FieldMapping(::isBigDecimalType, "decimal", "numeric", "number", "NUMERIC", "numeric", "(19,2)", BigDecimal::class),
            FieldMapping(::isLongType, "long", "bigint", "number", "BIGINT", "bigint", "", Long::class),
            FieldMapping(::isBooleanType, "boolean", "boolean", "number", "INT", "boolean", "", Boolean::class),
        ).onEach { mapping ->
            // 添加计算属性
            mapping.javaClassRef = mapping.classRef.java.name
            mapping.javaClassSimple = mapping.classRef.java.simpleName
        }
        var databaseType: HashMap<String, DatabaseDDLGenerator> = object : HashMap<String, DatabaseDDLGenerator>() {
            init {
                put(MYSQL, MysqlDDLGenerator())
                put(ORACLE, OracleDDLGenerator())
                put(POSTGRESQL, PostgreSQLDDLGenerator())
                put(DM, DMSQLDDLGenerator())
                put(H2, H2SQLDDLGenerator())
                put(TDENGINE, TDengineDDLGenerator())
            }
        }

    }
}
