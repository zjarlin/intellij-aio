package site.addzero.addl.action.anycodegen.impl

import site.addzero.addl.action.anycodegen.AbsGen
import site.addzero.addl.autoddlstarter.generator.entity.PsiFieldMetaInfo
import site.addzero.addl.autoddlstarter.generator.filterBaseEntity
import site.addzero.addl.ktututil.toCamelCase
import site.addzero.addl.ktututil.toUnderlineCase
import java.util.*
import com.intellij.openapi.actionSystem.ActionUpdateThread

private const val EXCEL_READ_DTO = """ExcelDTO"""

class GenExcelDTO : AbsGen() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun genCode4Java(psiFieldMetaInfo: PsiFieldMetaInfo): String {
        val (pkg, classname, classcomment, javaFieldMetaInfos) = psiFieldMetaInfo


        val filter = javaFieldMetaInfos?.filter {
            filterBaseEntity(it.name)
        }?.map {
            val toCamelCase = it.name.toUnderlineCase().lowercase(Locale.getDefault()).toCamelCase()
            val copy = it.copy(name = toCamelCase)
            copy
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
            import cn.idev.excel.annotation.ExcelProperty;
           import lombok.Data; 
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

            ?.map {
                val toCamelCase = it.name.toUnderlineCase().lowercase(Locale.getDefault()).toCamelCase()
                val copy = it.copy(name = toCamelCase)
                copy
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
                    ${toCamelCase}= that.$toCamelCase
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
             val that=this
               
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
            import cn.idev.excel.annotation.ExcelProperty;
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
