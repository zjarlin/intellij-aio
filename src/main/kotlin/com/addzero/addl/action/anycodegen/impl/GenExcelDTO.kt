package com.addzero.addl.action.anycodegen.impl

import com.addzero.addl.action.anycodegen.AbsGen
import com.addzero.addl.autoddlstarter.generator.entity.PsiFieldMetaInfo
import com.addzero.addl.autoddlstarter.generator.filterBaseEntity
import com.addzero.addl.ktututil.toCamelCase

private const val EXCEL_READ_DTO = """ExcelDTO"""

class GenExcelDTO : AbsGen() {

//    override val pdir: String
//        get() = "dto"

    override fun genCode4Java(psiFieldMetaInfo: PsiFieldMetaInfo): String {
        val (pkg, classname, classcomment, javaFieldMetaInfos) = psiFieldMetaInfo


        val filter = javaFieldMetaInfos?.filter {
            filterBaseEntity(it.name)
        }

        val fields = filter?.joinToString(System.lineSeparator()) {

            """
                    @ExcelProperty("${it.comment}")
                    private ${mapToType(it.type.typeName)} ${it.name};
                    """
        }
        val fullClassName = """${classname}$EXCEL_READ_DTO"""
        return """
           package $pkg;
            import com.alibaba.excel.annotation.ExcelProperty;
            @Data
            public class $fullClassName {
                $fields
            }
        """.trimIndent()

    }

    override fun genCode4Kt(psiFieldMetaInfo: PsiFieldMetaInfo): String {
        val (pkg, classname, classcomment, javaFieldMetaInfos) = psiFieldMetaInfo
        val filter = javaFieldMetaInfos?.filter {
            filterBaseEntity(it.name)
        }
        val fields = filter

            ?.joinToString(System.lineSeparator()) {
                val name = it.name
                val toCamelCase = name.toCamelCase()
                """
                            @ExcelProperty("${it.comment}")
                            var $toCamelCase: ${mapToType(it.type.typeName)}?=null
                            """
            }


        val fields1 = filter?.joinToString(System.lineSeparator()) {
            val name = it.name
            val toCamelCase = name.toCamelCase()
            """
                    ${toCamelCase}= this.$toCamelCase
                    """
        }


        val fields2 = filter?.joinToString(System.lineSeparator()) {
            val name = it.name
            val toCamelCase = name.toCamelCase()
            """
                    entity.${toCamelCase}= this.$toCamelCase
                    """
        }


        val toentityblockk = """
               fun toEntity(): ${classname} {
      return  ${classname} {
            $fields1
        }
    }
        """.trimIndent()


        val toentityblockk1 = """
        fun ${classname}.toExcelDTO(): ${classname}$EXCEL_READ_DTO {
            var entity = ${classname}$EXCEL_READ_DTO()
       $fields2
            return entity
        } 
 
        """.trimIndent()

        return """
           package $pkg;
            import com.alibaba.excel.annotation.ExcelProperty;
           $toentityblockk1 
            public open class ${classname}$EXCEL_READ_DTO{
                $fields
                $toentityblockk
            }
        """.trimIndent()

    }

    override val suffix: String
        get() = "$EXCEL_READ_DTO"
    override val javafileTypeSuffix: String
        get() = ".java"
    override val ktfileTypeSuffix: String
        get() = ".kt"

}