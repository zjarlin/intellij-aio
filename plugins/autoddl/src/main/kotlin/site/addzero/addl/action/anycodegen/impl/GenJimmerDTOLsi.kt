package site.addzero.addl.action.anycodegen.impl

import com.intellij.openapi.actionSystem.ActionUpdateThread
import site.addzero.addl.action.anycodegen.AbsGenLsi
import site.addzero.addl.action.anycodegen.entity.LsiClassMetaInfo
import site.addzero.addl.autoddlstarter.generator.filterBaseEntity
import site.addzero.addl.ktututil.toCamelCase

/**
 * 生成 Jimmer DTO 规范文件
 * 
 * 基于 LSI 抽象层实现，支持 Java 和 Kotlin
 */
class GenJimmerDTOLsi : AbsGenLsi() {
    
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun genCode(metaInfo: LsiClassMetaInfo): String {
        val className = metaInfo.className ?: "UnnamedClass"
        
        // 过滤基础实体字段
        val filteredFields = metaInfo.fields.filter { field ->
            field.name?.let { filterBaseEntity(it) } ?: false
        }

        // 生成 Jimmer DTO 规范
        val trimIndent = """
specification ${className}Spec {
    #allScalars(this)
}

input ${className}SaveDTO {
    #allScalars(this)
    id?
}

input ${className}UpdateDTO {
    #allScalars(this)
    id!
}

${className}View {
    #allScalars
}
        """.trimIndent()

        return trimIndent
    }

    override val fileSuffix: String
        get() = ".dto"
}
