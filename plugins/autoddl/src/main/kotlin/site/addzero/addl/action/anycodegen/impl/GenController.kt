package site.addzero.addl.action.anycodegen.impl

import site.addzero.addl.action.anycodegen.AbsGen
import site.addzero.addl.autoddlstarter.generator.entity.DDLContext
import site.addzero.addl.autoddlstarter.generator.entity.PsiFieldMetaInfo
import site.addzero.addl.ktututil.toCamelCase
import site.addzero.addl.settings.SettingContext
import com.intellij.openapi.actionSystem.ActionUpdateThread

class GenController : AbsGen() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun genCode4Kt(psiFieldMetaInfo: PsiFieldMetaInfo): String {
        val (pkg, classname, classcomment, javaFieldMetaInfos) = psiFieldMetaInfo
        val toCamelCase = classname?.toCamelCase()
        val lowerFirst = classname?.replaceFirstChar { it.lowercase() } ?: ""
        val trimIndent = """
package $pkg
import $pkg.${classname}ExcelDTO
import site.addzero.web.infra.jimmer.base.BaseCrudController
import site.addzero.web.infra.jimmer.base.BaseFastExcelApi
import site.addzero.web.modules.dotfiles.${classname}
import site.addzero.web.modules.dotfiles.dto.${classname}Spec
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
        if (SettingContext.settings.controllerStyle == "INHERITANCE") {
            return trimIndent
        }

        return """
            package $pkg
            
            import cn.idev.excel.FastExcel
            import cn.idev.excel.cache.Ehcache.BATCH_COUNT
            import cn.idev.excel.context.AnalysisContext
            import cn.idev.excel.read.listener.ReadListener
            import cn.idev.excel.util.ListUtils
            import site.addzero.common.kt_util.isNotEmpty
            import site.addzero.common.kt_util.isNotNew
            import site.addzero.web.infra.upload.DownloadUtil
            import $pkg.dto.${classname}SaveDTO
            import $pkg.dto.${classname}Spec
            import $pkg.dto.${classname}UpdateDTO
            import $pkg.dto.${classname}View
            import io.swagger.v3.oas.annotations.Operation
            import org.babyfish.jimmer.Page
            import org.babyfish.jimmer.sql.ast.mutation.AffectedTable
            import org.babyfish.jimmer.sql.kt.KSqlClient
            import org.babyfish.jimmer.sql.kt.ast.table.makeOrders
            import org.springframework.web.bind.annotation.*
            import org.springframework.web.multipart.MultipartFile
            
            @RestController
            @RequestMapping("/${lowerFirst}")
            class ${classname}Controller(
                private val sql: KSqlClient
            ) {
                @GetMapping("/page")
                @Operation(summary = "分页查询")
                fun page(
                    spec: ${classname}Spec,
                    @RequestParam(defaultValue = "0") pageNum: Int,
                    @RequestParam(defaultValue = "10") pageSize: Int,
                ): Page<${classname}View> {
                    val createQuery = sql.createQuery(${classname}::class) {
                        where(spec)
                        orderBy(table.makeOrders("id desc"))
                        select(
                            table.fetch(${classname}View::class)
                        )
                    }
                    return createQuery.fetchPage(pageNum, pageSize)
                }
            
                @GetMapping("listAll")
                @Operation(summary = "查询所有")
                fun list(): List<${classname}View> {
                    val createQuery = sql.createQuery(${classname}::class) {
                        select(table.fetch(${classname}View::class))
                    }
                    return createQuery.execute()
                }
            
                @PostMapping("/saveBatch")
                @Operation(summary = "批量保存")
                fun saveBatch(
                    @RequestBody input: List<${classname}SaveDTO>,
                ): Int {
                    val toList = input.map { it.toEntity() }.toList()
                    return sql.saveEntities(toList).totalAffectedRowCount
                }
            
                @GetMapping("/findById")
                @Operation(summary = "id查询单条")
                fun findById(id: String): ${classname}? {
                    return sql.findById(${classname}::class, id)
                }
            
                @DeleteMapping("/delete")
                @Operation(summary = "批量删除")
                fun deleteByIds(@RequestParam vararg ids: String): Int {
                    return sql.deleteByIds(
                        ${classname}::class, listOf(*ids)
                    ).totalAffectedRowCount
                }
            
                @PostMapping("/save")
                @Operation(summary = "保存")
                fun save(@RequestBody inputDTO: ${classname}SaveDTO): Int {
                    return sql.save(inputDTO).totalAffectedRowCount
                }
            
                @PostMapping("/update")
                @Operation(summary = "编辑")
                fun edit(@RequestBody inputDTO: ${classname}UpdateDTO): Int {
                    return sql.update(inputDTO).totalAffectedRowCount
                }
            
                @PostMapping("/import")
                fun import(@RequestPart file: MultipartFile): Map<AffectedTable, Int> {
                    val dataDtoList = file.inputStream.use {
                        val readListener = object : ReadListener<${classname}ExcelDTO> {
                            val caches: MutableList<${classname}ExcelDTO> = ListUtils.newArrayListWithExpectedSize(BATCH_COUNT)
            
                            override fun invoke(input: ${classname}ExcelDTO, context: AnalysisContext?) {
                                caches.add(input)
                                if (caches.size >= BATCH_COUNT) {
                                    caches.clear()
                                }
                            }
            
                            override fun doAfterAllAnalysed(context: AnalysisContext?) {
                                println("所有数据解析完成")
                            }
                        }
                        FastExcel.read(it, ${classname}ExcelDTO::class.java, readListener).sheet().doRead()
                        readListener.caches
                    }
            
                    val map = dataDtoList.filter { it.isNotEmpty() }.filter { it.isNotNew() }.map { it.toEntity() }
                    return sql.saveEntities(map).affectedRowCountMap
                }
            
                @GetMapping("/export")
                fun exportExcel(
                    fileName: String, 
                    spec: ${classname}Spec, 
                    sheetName: String = "sheet1"
                ) {
                    val data = sql.createQuery(${classname}::class) {
                        where(spec)
                        select(table.fetchBy {
                            allTableFields()
                        })
                    }.execute()
            
                    val toList = data.map { it.toExcelDTO() }.toList()
            
                    DownloadUtil.downloadExcel(fileName) {
                        FastExcel.write(it, ${classname}ExcelDTO::class.java).sheet(sheetName).doWrite(toList)
                    }
                }
            }
        """.trimIndent()


    }


    private fun generateControllerCode(ddlContext: DDLContext): String {
        val entityName = ddlContext.tableEnglishName
        val entityNameLower = entityName.replaceFirstChar { it.lowercase() }

        return """
            package site.addzero.web.modules.second_brain.$entityNameLower
            
            import cn.idev.excel.FastExcel
            import cn.idev.excel.cache.Ehcache.BATCH_COUNT
            import cn.idev.excel.context.AnalysisContext
            import cn.idev.excel.read.listener.ReadListener
            import cn.idev.excel.util.ListUtils
            import site.addzero.common.kt_util.isNotEmpty
            import site.addzero.common.kt_util.isNotNew
            import site.addzero.web.infra.upload.DownloadUtil
            import site.addzero.web.modules.second_brain.$entityNameLower.dto.${entityName}SaveDTO
            import site.addzero.web.modules.second_brain.$entityNameLower.dto.${entityName}Spec
            import site.addzero.web.modules.second_brain.$entityNameLower.dto.${entityName}UpdateDTO
            import site.addzero.web.modules.second_brain.$entityNameLower.dto.${entityName}View
            import io.swagger.v3.oas.annotations.Operation
            import org.babyfish.jimmer.Page
            import org.babyfish.jimmer.sql.ast.mutation.AffectedTable
            import org.babyfish.jimmer.sql.kt.KSqlClient
            import org.babyfish.jimmer.sql.kt.ast.table.makeOrders
            import org.springframework.web.bind.annotation.*
            import org.springframework.web.multipart.MultipartFile
            
            @RestController
            @RequestMapping("/$entityNameLower")
            class ${entityName}Controller(
                private val sql: KSqlClient
            ) {
                @GetMapping("/page")
                @Operation(summary = "分页查询")
                fun page(
                    spec: ${entityName}Spec,
                    @RequestParam(defaultValue = "0") pageNum: Int,
                    @RequestParam(defaultValue = "10") pageSize: Int,
                ): Page<${entityName}View> {
                    val createQuery = sql.createQuery(${entityName}::class) {
                        where(spec)
                        orderBy(table.makeOrders("id desc"))
                        select(
                            table.fetch(${entityName}View::class)
                        )
                    }
                    return createQuery.fetchPage(pageNum, pageSize)
                }
            
                @GetMapping("listAll")
                @Operation(summary = "查询所有")
                fun list(): List<${entityName}View> {
                    val createQuery = sql.createQuery(${entityName}::class) {
                        select(table.fetch(${entityName}View::class))
                    }
                    return createQuery.execute()
                }
            
                @PostMapping("/saveBatch")
                @Operation(summary = "批量保存")
                fun saveBatch(
                    @RequestBody input: List<${entityName}SaveDTO>,
                ): Int {
                    val toList = input.map { it.toEntity() }.toList()
                    return sql.saveEntities(toList).totalAffectedRowCount
                }
            
                @GetMapping("/findById")
                @Operation(summary = "id查询单条")
                fun findById(id: String): ${entityName}? {
                    return sql.findById(${entityName}::class, id)
                }
            
                @DeleteMapping("/delete")
                @Operation(summary = "批量删除")
                fun deleteByIds(@RequestParam vararg ids: String): Int {
                    return sql.deleteByIds(
                        ${entityName}::class, listOf(*ids)
                    ).totalAffectedRowCount
                }
            
                @PostMapping("/save")
                @Operation(summary = "保存")
                fun save(@RequestBody inputDTO: ${entityName}SaveDTO): Int {
                    return sql.save(inputDTO).totalAffectedRowCount
                }
            
                @PostMapping("/update")
                @Operation(summary = "编辑")
                fun edit(@RequestBody inputDTO: ${entityName}UpdateDTO): Int {
                    return sql.update(inputDTO).totalAffectedRowCount
                }
            
                @PostMapping("/import")
                fun import(@RequestPart file: MultipartFile): Map<AffectedTable, Int> {
                    val dataDtoList = file.inputStream.use {
                        val readListener = object : ReadListener<${entityName}ExcelDTO> {
                            val caches: MutableList<${entityName}ExcelDTO> = ListUtils.newArrayListWithExpectedSize(BATCH_COUNT)
            
                            override fun invoke(input: ${entityName}ExcelDTO, context: AnalysisContext?) {
                                caches.add(input)
                                if (caches.size >= BATCH_COUNT) {
                                    caches.clear()
                                }
                            }
            
                            override fun doAfterAllAnalysed(context: AnalysisContext?) {
                                println("所有数据解析完成")
                            }
                        }
                        FastExcel.read(it, ${entityName}ExcelDTO::class.java, readListener).sheet().doRead()
                        readListener.caches
                    }
            
                    val map = dataDtoList.filter { it.isNotEmpty() }.filter { it.isNotNew() }.map { it.toEntity() }
                    return sql.saveEntities(map).affectedRowCountMap
                }
            
                @GetMapping("/export")
                fun exportExcel(
                    fileName: String, 
                    spec: ${entityName}Spec, 
                    sheetName: String = "sheet1"
                ) {
                    val data = sql.createQuery(${entityName}::class) {
                        where(spec)
                        select(table.fetchBy {
                            allTableFields()
                        })
                    }.execute()
            
                    val toList = data.map { it.toExcelDTO() }.toList()
            
                    DownloadUtil.downloadExcel(fileName) {
                        FastExcel.write(it, ${entityName}ExcelDTO::class.java).sheet(sheetName).doWrite(toList)
                    }
                }
            }
        """.trimIndent()
    }


    override val suffix: String
        get() = "Controller"

    override val javafileTypeSuffix: String
        get() = ".java"
    override val pdir: String
        get() = ""

    override val ktfileTypeSuffix: String
        get() = ".kt"


}
