package com.addzero.addl.action.anycodegen.impl

import com.addzero.addl.action.anycodegen.AbsGen
import com.addzero.addl.autoddlstarter.generator.entity.PsiFieldMetaInfo
import com.addzero.addl.autoddlstarter.generator.filterBaseEntity
import com.addzero.addl.ktututil.toCamelCase

class GenJimmerDTO : AbsGen() {

    override fun genCode4Kt(psiFieldMetaInfo: PsiFieldMetaInfo): String {
        val (pkg, classname, classcomment, javaFieldMetaInfos) = psiFieldMetaInfo


        val filter = javaFieldMetaInfos?.filter {
            filterBaseEntity(it.name)
        }

        val fields = filter?.joinToString(System.lineSeparator()) {
            val name = it.name
            val toCamelCase = name.toCamelCase()
            val comment = it.comment
            val mapToType = mapToType(it.type.typeName)
            """
                    @ExcelProperty("$comment")
                    $toCamelCase
                    """
        }

        val trimIndent = """
import cn.idev.excel.annotation.ExcelProperty

specification ${classname}Spec{
#allScalars(this)
}

input ${classname}SaveInputDTO{
#allScalars(this)
}

input ${classname}UpdateInputDTO{
#allScalars(this)
id!
}


${classname}OutVO{
#allScalars
}





            """.trimIndent()


        //${classname}ExcelWriteDTO{
//   $fields
//}

        return trimIndent

    }

    override val javafileTypeSuffix: String
        get() = ".dto"

    override val ktfileTypeSuffix: String
        get() = ".dto"

}