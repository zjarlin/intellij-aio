package site.addzero.addl.autoddlstarter.generator.factory

import site.addzero.addl.autoddlstarter.generator.IDatabaseGenerator.Companion.getDatabaseDDLGenerator
import site.addzero.addl.autoddlstarter.generator.IDatabaseGenerator.Companion.getLength
import site.addzero.addl.autoddlstarter.generator.defaultconfig.BaseMetaInfoUtil.javaFieldMetaInfos
import site.addzero.addl.autoddlstarter.generator.defaultconfig.DefaultMetaInfoUtil
import site.addzero.addl.autoddlstarter.generator.entity.DDLContext
import site.addzero.addl.autoddlstarter.generator.entity.DDlRangeContext
import site.addzero.addl.autoddlstarter.generator.entity.JavaFieldMetaInfo
import site.addzero.addl.ktututil.toUnderlineCase
import site.addzero.addl.util.fieldinfo.PsiUtil
import site.addzero.addl.util.removeAnyQuote
import site.addzero.util.str.biz.isPrimaryKey
import com.intellij.psi.PsiClass
import org.jetbrains.kotlin.psi.KtClass

private const val UNKNOWN_TABLE_NAME = "unknown_table_name"

object DDLContextFactory4JavaMetaInfo {
    
    /**
     * 从LSI类创建DDLContext（语言无关）
     */
    fun createDDLContextFromLsi(lsiClass: site.addzero.util.lsi.clazz.LsiClass, databaseType: String = "mysql"): DDLContext {
        // 提取表名（从注解或类名）
        val className = lsiClass.name ?: UNKNOWN_TABLE_NAME
        val classComment = lsiClass.comment ?: className
        
        val tableEnglishName = className.toUnderlineCase()
        val tableChineseName = classComment.ifBlank { className }

        // 提取字段信息
        val fields = lsiClass.fields.map { field ->
            val fieldName = field.name ?: ""
            val fieldType = field.type?.simpleName ?: "String"
            val fieldComment = field.comment ?: fieldName
            val genericType = "" // LSI层暂不支持泛型信息
            
            JavaFieldMetaInfo(fieldName, fieldType, genericType, fieldComment)
        }

        // 创建范围上下文
        val rangeContexts = fields.map { field ->
            createRangeContext(field, databaseType)
        }

        return DDLContext(
            tableChineseName = tableChineseName.removeAnyQuote(),
            tableEnglishName = tableEnglishName.removeAnyQuote(),
            databaseType = databaseType.removeAnyQuote(),
            dto = rangeContexts,
        )
    }
    fun createDDLContext4KtClass(ktClass: KtClass, databaseType: String = "mysql"): DDLContext {
        var (tableChineseName, tableEnglishName) = PsiUtil.getClassMetaInfo4KtClass (ktClass)
        tableEnglishName= tableEnglishName!!.ifBlank { UNKNOWN_TABLE_NAME }
        tableChineseName=tableChineseName.ifBlank { tableEnglishName!! }

        val javaFieldMetaInfo = PsiUtil.extractInterfaceMetaInfo(ktClass)

        val rangeContexts = javaFieldMetaInfo.map { field ->
            createRangeContext(field, databaseType)
        }
        //解析ktclass字段上的ManyToMany注解,如果有@ManyToMany 再看看是否有@JoinTable 注解 如果有的话,提取表名和两个字段名也形成DDLContext

//        @ManyToMany
//    @JoinTable(
//        name = "BOOK_AUTHOR_MAPPING",
//        joinColumnName = "BOOK_ID",
//        inverseJoinColumnName = "AUTHOR_ID"
//    )


//        toKtClass.getProperties()
//            .filter { it.annotationEntries.map {  } }
//        .map {

//        }





        return DDLContext(
            tableChineseName = tableChineseName.removeAnyQuote(),
            tableEnglishName = tableEnglishName.removeAnyQuote(),
            databaseType = databaseType.removeAnyQuote(),
            dto = rangeContexts,
        )

    }




    fun createDDLContext(psiClass: PsiClass ,databaseType: String = "mysql"): DDLContext {
        var (tableChineseName, tableEnglishName) = PsiUtil.getClassMetaInfo (psiClass)
        tableEnglishName= tableEnglishName!!.ifBlank { UNKNOWN_TABLE_NAME }
        tableChineseName=tableChineseName.ifBlank { tableEnglishName!! }

        val javaFieldMetaInfo = PsiUtil.getJavaFieldMetaInfo(psiClass)

        val rangeContexts = javaFieldMetaInfo.map { field ->
            val createRangeContext = createRangeContext(field, databaseType)
            createRangeContext
        }
        return DDLContext(
            tableChineseName = tableChineseName.removeAnyQuote(),
            tableEnglishName = tableEnglishName.removeAnyQuote(),
            databaseType = databaseType.removeAnyQuote(),
            dto = rangeContexts,
        )

    }




    fun createDDLContext(clazz: Class<*>, databaseType: String = "mysql"): DDLContext {
        val tableChineseName = DefaultMetaInfoUtil.getTableChineseNameFun(clazz)
        val tableEnglishName = PinYin4JUtils.hanziToPinyin(tableChineseName, "_")

        // 遍历类中的所有字段，创建对应的 RangeContext 列表
//        val declaredFields = convertTo.declaredFields
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

    private fun createRangeContext(field: JavaFieldMetaInfo, databaseType: String = "mysql"): DDlRangeContext {
        val fieldName = field.name
        var colName = fieldName
        if (colName.isBlank()) {
            colName = fieldName.toUnderlineCase()
        }
        val javaType = field.type
        val genericType = field.genericType
        val fieldComment = field.comment
        val databaseDDLGenerator = getDatabaseDDLGenerator(databaseType)
        val javaFieldMetaInfo = JavaFieldMetaInfo(fieldName, javaType, genericType, fieldComment)
        val colType = databaseDDLGenerator.mapTypeByJavaType(javaFieldMetaInfo)
        val length = getLength(javaFieldMetaInfo)
        val isPrimaryKey = fieldName.isPrimaryKey()
        val isSelfIncreasing = isPrimaryKey // 这里假设主键即自增

        return DDlRangeContext(
            colName.removeAnyQuote(),
            colType.removeAnyQuote(),
            fieldComment.removeAnyQuote(),
            length.removeAnyQuote(),
            isPrimaryKey.removeAnyQuote(),
            isSelfIncreasing.removeAnyQuote(),
        )
    }

}
