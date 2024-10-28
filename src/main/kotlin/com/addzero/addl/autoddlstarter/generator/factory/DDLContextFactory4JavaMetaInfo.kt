package com.addzero.addl.autoddlstarter.generator.factory

import com.addzero.addl.autoddlstarter.generator.IDatabaseGenerator.Companion.getDatabaseDDLGenerator
import com.addzero.addl.autoddlstarter.generator.IDatabaseGenerator.Companion.getLength
import com.addzero.addl.autoddlstarter.generator.consts.MYSQL
import com.addzero.addl.autoddlstarter.generator.defaultconfig.BaseMetaInfoUtil
import com.addzero.addl.autoddlstarter.generator.defaultconfig.BaseMetaInfoUtil.javaFieldMetaInfos
import com.addzero.addl.autoddlstarter.generator.defaultconfig.DefaultMetaInfoUtil
import com.addzero.addl.autoddlstarter.generator.entity.DDLContext
import com.addzero.addl.autoddlstarter.generator.entity.DDlRangeContext
import com.addzero.addl.autoddlstarter.generator.entity.JavaFieldMetaInfo
import com.addzero.addl.ktututil.toUnderlineCase
import com.addzero.addl.util.PinYin4JUtils
import com.addzero.addl.util.fieldinfo.PsiUtil
import com.intellij.psi.PsiClass
import org.jetbrains.kotlin.psi.KtClass

private const val UNKNOWN_TABLE_NAME = "unknown_table_name"

object DDLContextFactory4JavaMetaInfo {



    fun createDDLContext4KtClass(psiClass: KtClass ,databaseType: String = MYSQL): DDLContext {
        var (tableChineseName, tableEnglishName) = PsiUtil.getClassMetaInfo4KtClass (psiClass)
        tableEnglishName= tableEnglishName!!.ifBlank { UNKNOWN_TABLE_NAME }
        tableChineseName=tableChineseName.ifBlank { tableEnglishName!! }

        val javaFieldMetaInfo = PsiUtil.extractInterfaceMetaInfo(psiClass)

        val rangeContexts = javaFieldMetaInfo.map { field ->
            createRangeContext(field, databaseType)
        }
        return DDLContext(
            tableChineseName = tableChineseName,
            tableEnglishName = tableEnglishName,
            databaseType = databaseType,
            dto = rangeContexts,
        )

    }




    fun createDDLContext(psiClass: PsiClass ,databaseType: String = MYSQL): DDLContext {
        var (tableChineseName, tableEnglishName) = PsiUtil.getClassMetaInfo (psiClass)
        tableEnglishName= tableEnglishName!!.ifBlank { UNKNOWN_TABLE_NAME }
        tableChineseName=tableChineseName.ifBlank { tableEnglishName!! }

        val javaFieldMetaInfo = PsiUtil.getJavaFieldMetaInfo(psiClass)

        val rangeContexts = javaFieldMetaInfo.map { field ->
            createRangeContext(field, databaseType)
        }
        return DDLContext(
            tableChineseName = tableChineseName,
            tableEnglishName = tableEnglishName,
            databaseType = databaseType,
            dto = rangeContexts,
        )

    }




    fun createDDLContext(clazz: Class<*>, databaseType: String = MYSQL): DDLContext {
        val tableChineseName = DefaultMetaInfoUtil.getTableChineseNameFun(clazz)
        val tableEnglishName = PinYin4JUtils.hanziToPinyin(tableChineseName, "_")

        // 遍历类中的所有字段，创建对应的 RangeContext 列表
//        val declaredFields = clazz.declaredFields
        val declaredFields = javaFieldMetaInfos(clazz)

        val rangeContexts = declaredFields.map { field ->
            createRangeContext(field, databaseType)
        }
        return DDLContext(
            tableChineseName = tableChineseName,
            tableEnglishName = tableEnglishName,
            databaseType = databaseType,
            dto = rangeContexts,
        )
    }

    private fun createRangeContext(field: JavaFieldMetaInfo, databaseType: String = MYSQL): DDlRangeContext {
        val fieldName = field.name
        var colName = fieldName
        if (colName.isBlank()) {
            colName = fieldName.toUnderlineCase()
        }
        val javaType = field.type
        val genericType = field.genericType
        val fieldComment = field.comment
        val databaseDDLGenerator = getDatabaseDDLGenerator(databaseType)
        val javaFieldMetaInfo = JavaFieldMetaInfo(fieldName, javaType, genericType as Class<*>, fieldComment)
        val colType = databaseDDLGenerator.mapTypeByJavaType(javaFieldMetaInfo)
        val length = getLength(javaFieldMetaInfo)
        val isPrimaryKey = BaseMetaInfoUtil.isPrimaryKey(fieldName)
        val isSelfIncreasing = isPrimaryKey // 这里假设主键即自增

        return DDlRangeContext(
            colName,
            colType,
            fieldComment,
            length,
            isPrimaryKey,
            isSelfIncreasing
        )
    }

}