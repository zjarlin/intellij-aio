package com.addzero.addl.autoddlstarter.generator

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
import com.addzero.addl.autoddlstarter.generator.consts.DM
import com.addzero.addl.autoddlstarter.generator.consts.MYSQL
import com.addzero.addl.autoddlstarter.generator.consts.ORACLE
import com.addzero.addl.autoddlstarter.generator.consts.POSTGRESQL
import com.addzero.addl.autoddlstarter.generator.entity.DDlRangeContext
import com.addzero.addl.autoddlstarter.generator.entity.FieldMapping
import com.addzero.addl.autoddlstarter.generator.entity.JavaFieldMetaInfo
import com.addzero.addl.autoddlstarter.generator.ex.DMSQLDDLGenerator
import com.addzero.addl.autoddlstarter.generator.ex.MysqlDDLGenerator
import com.addzero.addl.autoddlstarter.generator.ex.OracleDDLGenerator
import com.addzero.addl.autoddlstarter.generator.ex.PostgreSQLDDLGenerator
import com.addzero.addl.ktututil.equalsIgnoreCase
import com.addzero.addl.settings.SettingContext
import com.addzero.addl.util.DialogUtil
import com.addzero.addl.util.JlStrUtil.ignoreCaseIn
import com.addzero.addl.util.JlStrUtil.ignoreCaseNotIn
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*

fun filterBaseEneity(dDlRangeContext: DDlRangeContext): Boolean {
    val settings = SettingContext.settings
    val id = settings.id
    val createBy = settings.createBy
    val updateBy = settings.updateBy
    val createTime = settings.createTime
    val updateTime = settings.updateTime

    return dDlRangeContext.colName ignoreCaseNotIn listOf(
        id, createBy, updateBy, createTime, updateTime
    )
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

        fun javaType2RefType(javaType: String): String {
            val javaClass = fieldMappings.find {
                val equalsIgnoreCase = it.javaClassSimple.equalsIgnoreCase(javaType)
                equalsIgnoreCase
            }?.javaClassRef
            if (javaType ignoreCaseIn listOf("clob", "object")) {
                return String::class.java.name
            }
            if (javaClass == null) {
                DialogUtil.showWarningMsg("未找到java类型${javaType} 的映射关系,请联系作者适配")
                return String::class.java.name
            }
            return javaClass
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
            FieldMapping(::isStringType, "varchar", "varchar", "varchar2", "VARCHAR", "(255)", String::class),
            FieldMapping(::isCharType, "char", "character", "char", "VARCHAR", "(255)", String::class),
            FieldMapping(::isTextType, "text", "text", "clob", "CLOB", "", String::class),
            FieldMapping(
                ::isDateTimeType, "datetime", "timestamp", "timestamp", "TIMESTAMP", "", LocalDateTime::class
            ),
            FieldMapping(::isDateType, "date", "date", "date", "TIMESTAMP", "", Date::class),
            FieldMapping(::isTimeType, "time", "time", "timestamp", "TIMESTAMP", "", LocalTime::class),
            FieldMapping(::isIntType, "int", "integer", "number", "INT", "", Integer::class),
            FieldMapping(
                ::isDoubleType, "double", "double precision", "binary_double", "DOUBLE", "(6,2)", Double::class
            ),
            FieldMapping(::isBigDecimalType, "decimal", "numeric", "number", "NUMERIC", "(19,2)", BigDecimal::class),
            FieldMapping(::isLongType, "long", "bigint", "number", "BIGINT", "", Long::class),
            FieldMapping(::isBooleanType, "boolean", "boolean", "number", "INT", "", Boolean::class),
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
            }
        }

    }
}