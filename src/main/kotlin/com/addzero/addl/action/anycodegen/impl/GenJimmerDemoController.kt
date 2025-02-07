package com.addzero.addl.action.anycodegen.impl

import cn.hutool.core.util.StrUtil
import com.addzero.addl.action.anycodegen.AbsGen
import com.addzero.addl.autoddlstarter.generator.entity.PsiFieldMetaInfo
import com.addzero.addl.ktututil.toCamelCase

class GenJimmerDemoController : AbsGen() {
    override fun genCode4Kt(psiFieldMetaInfo: PsiFieldMetaInfo): String {
        val (pkg, classname, classcomment, javaFieldMetaInfos) = psiFieldMetaInfo
        val toCamelCase = classname?.toCamelCase()
        val lowerFirst = StrUtil.lowerFirst(classname)
        val trimIndent = """
package $pkg
import $pkg.${classname}ExcelDTO
import com.addzero.web.infra.jimmer.base.BaseCrudController
import com.addzero.web.infra.jimmer.base.BaseFastExcelApi
import com.addzero.web.modules.dotfiles.${classname}
import com.addzero.web.modules.dotfiles.dto.${classname}Spec
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/$lowerFirst")
class ${classname}Controller(
    private val kSqlClient: KSqlClient
) : BaseCrudController<${classname}, ${classname}Spec, ${classname}SaveDTO, ${classname}UpdateDTO,  ${classname}View>
, BaseFastExcelApi<${classname}, ${classname}Spec, ${classname}ExcelDTO> {

}
 
        """.trimIndent()
        return  trimIndent
    }

    override val suffix: String
        get() = "Controller"

    override val javafileTypeSuffix: String
        get() = ".java"
    override val pdir: String
        get() = "controller"

    override val ktfileTypeSuffix: String
        get() = ".kt"


}