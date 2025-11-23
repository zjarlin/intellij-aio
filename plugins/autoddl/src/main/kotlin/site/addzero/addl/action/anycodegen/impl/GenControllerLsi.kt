package site.addzero.addl.action.anycodegen.impl

import com.intellij.openapi.actionSystem.ActionUpdateThread
import site.addzero.addl.action.anycodegen.AbsGenLsi
import site.addzero.addl.action.anycodegen.entity.LsiClassMetaInfo
import site.addzero.addl.settings.SettingContext
import site.addzero.util.str.toCamelCase

/**
 * 生成 Controller
 * 
 * 基于 LSI 抽象层实现，支持 Java 和 Kotlin
 * 支持两种风格：继承式（INHERITANCE）和独立式（STANDALONE）
 */
class GenControllerLsi : AbsGenLsi() {
    
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun genCode(metaInfo: LsiClassMetaInfo): String {
        val packageName = metaInfo.packageName
        val className = metaInfo.className ?: "UnnamedClass"
        val toCamelCase = className.toCamelCase()
        val lowerFirst = className.replaceFirstChar { it.lowercase() }

        // 判断使用哪种风格
        if (SettingContext.settings.controllerStyle == "INHERITANCE") {
            return generateInheritanceStyleController(packageName, className, lowerFirst)
        }

        return generateStandaloneStyleController(packageName, className, lowerFirst)
    }

    /**
     * 生成继承式 Controller（简洁版）
     */
    private fun generateInheritanceStyleController(
        packageName: String?,
        className: String,
        lowerFirst: String
    ): String {
        return """
        package $packageName
        
        import ${packageName}.${className}ExcelDTO
        import site.addzero.web.infra.jimmer.base.BaseCrudController
        import site.addzero.web.infra.jimmer.base.BaseFastExcelApi
        import ${packageName}.${className}
        import ${packageName}.dto.${className}Spec
        import ${packageName}.dto.${className}SaveDTO
        import ${packageName}.dto.${className}UpdateDTO
        import ${packageName}.dto.${className}View
        import org.babyfish.jimmer.sql.kt.KSqlClient
        import org.springframework.web.bind.annotation.*
        
        @RestController
        @RequestMapping("/$lowerFirst")
        class ${className}Controller(
            private val kSqlClient: KSqlClient
        ) : BaseCrudController<${className}, ${className}Spec, ${className}SaveDTO, ${className}UpdateDTO, ${className}View>,
            BaseFastExcelApi<${className}, ${className}Spec, ${className}ExcelDTO> {
        }
        """.trimIndent()
    }

    /**
     * 生成独立式 Controller（完整版）
     */
    private fun generateStandaloneStyleController(
        packageName: String?,
        className: String,
        lowerFirst: String
    ): String {
        return """
        package $packageName
        
        import cn.idev.excel.FastExcel
        import cn.idev.excel.cache.Ehcache.BATCH_COUNT
        import cn.idev.excel.context.AnalysisContext
        import cn.idev.excel.read.listener.ReadListener
        import cn.idev.excel.util.ListUtils
        import site.addzero.common.kt_util.isNotEmpty
        import site.addzero.common.kt_util.isNotNew
        import site.addzero.web.infra.upload.DownloadUtil
        import ${packageName}.dto.${className}SaveDTO
        import ${packageName}.dto.${className}Spec
        import ${packageName}.dto.${className}UpdateDTO
        import ${packageName}.dto.${className}View
        import ${packageName}.${className}ExcelDTO
        import io.swagger.v3.oas.annotations.Operation
        import org.babyfish.jimmer.Page
        import org.babyfish.jimmer.sql.ast.mutation.AffectedTable
        import org.babyfish.jimmer.sql.kt.KSqlClient
        import org.babyfish.jimmer.sql.kt.ast.table.makeOrders
        import org.springframework.web.bind.annotation.*
        import org.springframework.web.multipart.MultipartFile
        
        @RestController
        @RequestMapping("/$lowerFirst")
        class ${className}Controller(
            private val sql: KSqlClient
        ) {
            @GetMapping("/page")
            @Operation(summary = "分页查询")
            fun page(
                spec: ${className}Spec,
                @RequestParam(defaultValue = "0") pageNum: Int,
                @RequestParam(defaultValue = "10") pageSize: Int,
            ): Page<${className}View> {
                val createQuery = sql.createQuery(${className}::class) {
                    where(spec)
                    orderBy(table.makeOrders("id desc"))
                    select(
                        table.fetch(${className}View::class)
                    )
                }
                return createQuery.fetchPage(pageNum, pageSize)
            }
        
            @GetMapping("listAll")
            @Operation(summary = "查询所有")
            fun list(): List<${className}View> {
                val createQuery = sql.createQuery(${className}::class) {
                    select(table.fetch(${className}View::class))
                }
                return createQuery.execute()
            }
        
            @PostMapping("/saveBatch")
            @Operation(summary = "批量保存")
            fun saveBatch(
                @RequestBody input: List<${className}SaveDTO>,
            ): Int {
                val toList = input.map { it.toEntity() }.toList()
                return sql.saveEntities(toList).totalAffectedRowCount
            }
        
            @GetMapping("/findById")
            @Operation(summary = "id查询单条")
            fun findById(id: String): ${className}? {
                return sql.findById(${className}::class, id)
            }
        
            @DeleteMapping("/delete")
            @Operation(summary = "批量删除")
            fun deleteByIds(@RequestParam vararg ids: String): Int {
                return sql.deleteByIds(
                    ${className}::class, listOf(*ids)
                ).totalAffectedRowCount
            }
        
            @PostMapping("/save")
            @Operation(summary = "保存")
            fun save(@RequestBody inputDTO: ${className}SaveDTO): Int {
                return sql.save(inputDTO).totalAffectedRowCount
            }
        
            @PostMapping("/update")
            @Operation(summary = "编辑")
            fun edit(@RequestBody inputDTO: ${className}UpdateDTO): Int {
                return sql.update(inputDTO).totalAffectedRowCount
            }
        
            @PostMapping("/import")
            fun import(@RequestPart file: MultipartFile): Map<AffectedTable, Int> {
                val dataDtoList = file.inputStream.use {
                    val readListener = object : ReadListener<${className}ExcelDTO> {
                        val caches: MutableList<${className}ExcelDTO> = ListUtils.newArrayListWithExpectedSize(BATCH_COUNT)
        
                        override fun invoke(input: ${className}ExcelDTO, context: AnalysisContext?) {
                            caches.add(input)
                            if (caches.size >= BATCH_COUNT) {
                                caches.clear()
                            }
                        }
        
                        override fun doAfterAllAnalysed(context: AnalysisContext?) {
                            println("所有数据解析完成")
                        }
                    }
                    FastExcel.read(it, ${className}ExcelDTO::class.java, readListener).sheet().doRead()
                    readListener.caches
                }
        
                val map = dataDtoList.filter { it.isNotEmpty() }.filter { it.isNotNew() }.map { it.toEntity() }
                return sql.saveEntities(map).affectedRowCountMap
            }
        
            @GetMapping("/export")
            fun exportExcel(
                fileName: String,
                spec: ${className}Spec,
                sheetName: String = "sheet1"
            ) {
                val data = sql.createQuery(${className}::class) {
                    where(spec)
                    select(table.fetchBy {
                        allTableFields()
                    })
                }.execute()
        
                val toList = data.map { it.toExcelDTO() }.toList()
        
                DownloadUtil.downloadExcel(fileName) {
                    FastExcel.write(it, ${className}ExcelDTO::class.java).sheet(sheetName).doWrite(toList)
                }
            }
        }
        """.trimIndent()
    }

    override val fileSuffix: String
        get() = "Controller"

    override val fileTypeSuffix: List<String>
        get() = listOf(".java", ".kt")
}
