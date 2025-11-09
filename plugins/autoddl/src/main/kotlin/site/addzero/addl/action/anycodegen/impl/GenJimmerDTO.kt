package site.addzero.addl.action.anycodegen.impl

import com.intellij.openapi.actionSystem.ActionUpdateThread
import site.addzero.addl.action.anycodegen.AbsGen
import site.addzero.addl.autoddlstarter.generator.entity.PsiFieldMetaInfo
import site.addzero.addl.autoddlstarter.generator.filterBaseEntity
import site.addzero.addl.ktututil.toCamelCase

class GenJimmerDTO : AbsGen() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

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

specification ${classname}Spec{
#allScalars(this)
}

input ${classname}SaveDTO{
#allScalars(this)
id?
}

input ${classname}UpdateDTO{
#allScalars(this)
id!
}


${classname}View{
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
