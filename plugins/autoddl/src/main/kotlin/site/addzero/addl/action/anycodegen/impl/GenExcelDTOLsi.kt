package site.addzero.addl.action.anycodegen.impl

import com.intellij.openapi.actionSystem.ActionUpdateThread
import site.addzero.addl.action.anycodegen.AbsGenLsi
import site.addzero.addl.action.anycodegen.entity.LsiClassMetaInfo
import site.addzero.addl.autoddlstarter.generator.filterBaseEntity
import site.addzero.addl.ktututil.toCamelCase
import site.addzero.addl.ktututil.toUnderlineCase
import java.util.*

private const val EXCEL_READ_DTO = "ExcelDTO"

/**
 * 生成 Excel DTO
 * 
 * 基于 LSI 抽象层实现，支持 Java 和 Kotlin
 * 生成带有 @ExcelProperty 注解的 DTO 类
 */
class GenExcelDTOLsi : AbsGenLsi() {
    
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun genCode(metaInfo: LsiClassMetaInfo): String {
        val packageName = metaInfo.packageName
        val className = metaInfo.className ?: "UnnamedClass"
        
        // 过滤基础实体字段并转换为驼峰命名
        val filteredFields = metaInfo.fields
            .filter { field -> field.name?.let { filterBaseEntity(it) } ?: false }
            .map { field ->
                val fieldName = field.name ?: ""
                val camelCaseName = fieldName.toUnderlineCase()
                    .lowercase(Locale.getDefault())
                    .toCamelCase()
                Triple(camelCaseName, field.typeName ?: "String", field.comment ?: "")
            }

        // 生成字段定义
        val fields = filteredFields.joinToString(System.lineSeparator()) { (name, type, comment) ->
            """
            @ExcelProperty("$comment")
            var $name: ${mapType(type)}? = null
            """.trimIndent()
        }

        // 生成 toEntity 方法
        val toEntityAssignments = filteredFields.joinToString(System.lineSeparator()) { (name, _, _) ->
            "            $name = that.$name"
        }

        val toEntityMethod = """
        fun toEntity(): $className {
            val that = this
            return $className {
$toEntityAssignments
            }
        }
        """.trimIndent()

        // 生成扩展函数
        val toExcelAssignments = filteredFields.joinToString(System.lineSeparator()) { (name, _, _) ->
            "    entity.$name = this.$name"
        }

        val extensionFunction = """
        fun $className.toExcelDTO(): $className$EXCEL_READ_DTO {
            val entity = $className$EXCEL_READ_DTO()
$toExcelAssignments
            return entity
        }
        """.trimIndent()

        return """
        package $packageName
        
        import cn.idev.excel.annotation.ExcelProperty
        
        $extensionFunction
        
        open class $className$EXCEL_READ_DTO {
            $fields
            
            $toEntityMethod
        }
        """.trimIndent()
    }

    private fun mapType(typeName: String): String {
        return when (typeName) {
            "String" -> "String"
            "Int", "Integer" -> "Int"
            "Long" -> "Long"
            "BigDecimal" -> "BigDecimal"
            "LocalDateTime", "Date" -> "LocalDateTime"
            "Boolean" -> "Boolean"
            "Double" -> "Double"
            "Float" -> "Float"
            else -> "String"
        }
    }

    override val fileSuffix: String
        get() = EXCEL_READ_DTO

    override val fileTypeSuffix: List<String>
        get() = listOf(".java", ".kt")
}
