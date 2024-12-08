package com.addzero.addl.action.anycodegen.impl

import cn.hutool.core.io.resource.ClassPathResource
import com.addzero.addl.action.anycodegen.AbsGen
import com.addzero.addl.autoddlstarter.generator.entity.PsiFieldMetaInfo
import com.intellij.psi.PsiFile
import org.jetbrains.kotlinx.serialization.compiler.resolve.CallingConventions.encode

class GenJimmerBaseFastExcelApi : AbsGen() {
    override fun genCode4Kt(psiFieldMetaInfo: PsiFieldMetaInfo): String {
        return """
           package com.addzero.web.infra.jimmer.base

import cn.hutool.core.codec.Base64Encoder
import cn.hutool.core.util.StrUtil
import cn.hutool.core.util.TypeUtil
import cn.hutool.extra.spring.SpringUtil
import cn.idev.excel.FastExcel
import cn.idev.excel.context.AnalysisContext
import cn.idev.excel.read.listener.ReadListener
import cn.idev.excel.util.ListUtils
import com.addzero.web.infra.DownloadUtil
import com.addzero.web.infra.constant.ContentTypeEnum
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.babyfish.jimmer.View
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.query.specification.KSpecification
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.multipart.MultipartFile
import java.io.OutputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.function.Consumer
import kotlin.reflect.KClass

object DownloadUtil {

    val httpServletRequest: HttpServletRequest
        get() {
            return SpringUtil.getBean(HttpServletRequest::class.java)
        }
    val httpServletResponse: HttpServletResponse
        get() {
            return SpringUtil.getBean(HttpServletResponse::class.java)
        }

    /**
     * 调用浏览器文件下载
     */
    fun downloadExcel(fileName: String, consumer: Consumer<OutputStream>) {
        download(fileName, consumer, ContentTypeEnum.XLSX)
    }

    fun download(fileName: String, consumer: Consumer<OutputStream>, tab: ContentTypeEnum) {
        download(fileName, consumer, tab, true)
    }

    fun download(fileName: String, consumer: Consumer<OutputStream>, tab: ContentTypeEnum, addPostfix: Boolean) {
        val application: String = tab.application
        val postfix: String = tab.postfix
        httpServletResponse.characterEncoding = "UTF-8"
        //得请求头中的User-Agent
        val agent: String = httpServletRequest.getHeader("User-Agent")

        // 根据不同的客户端进行不同的编码
        var filenameEncoder = ""
        if (agent.contains("MSIE")) {
            // IE浏览器
            filenameEncoder = URLEncoder.encode(fileName, "utf-8")
            filenameEncoder = filenameEncoder.replace("+", " ")
        } else if (agent.contains("Firefox")) {
            // 火狐浏览器
            val encode = Base64Encoder.encode(fileName.toByteArray(StandardCharsets.UTF_8))
            filenameEncoder = "=utf-8B$'encode'="
        } else {
            // 其它浏览器
            filenameEncoder = URLEncoder.encode(fileName, "utf-8")
        }
        if (addPostfix) {
            filenameEncoder = StrUtil.addSuffixIfNot(filenameEncoder, postfix)
        }
        //        filenameEncoder = URLDecoder.decode(fileName, StandardCharsets.UTF_8);
        httpServletResponse.setHeader(
            "Content-disposition", "attachment;filename=$'filenameEncoder'"
        )
        httpServletResponse.contentType = application
        val outputStream: OutputStream = httpServletResponse.outputStream
        consumer.accept(outputStream)
    }


    fun downloadZip(fileName: String, consumer: Consumer<OutputStream>) {
        download(fileName, consumer, ContentTypeEnum.ZIP)
    }
}


class ExcelDataListener<ExcelDTO>() : ReadListener<ExcelDTO> {

    /**
     * 缓存的数据
     */
    val caches: MutableList<ExcelDTO> = ListUtils.newArrayListWithExpectedSize(BATCH_COUNT)

    /**
     * 这个每一条数据解析都会来调用
     */
    override fun invoke(input: ExcelDTO, context: AnalysisContext?) {
        caches.add(input)
        // 达到BATCH_COUNT了，需要去存储一次数据库，防止数据几万条数据在内存，容易OOM
        if (caches.size >= BATCH_COUNT) {
            // 存储完成清理 list
            caches.clear()
        }
    }

    /**
     * 所有数据解析完成了 都会来调用
     *
     * @param context
     */
    override fun doAfterAllAnalysed(context: AnalysisContext?) {
        // 这里也要保存数据，确保最后遗留的数据也存储到数据库
        println("所有数据解析完成")
    }


    companion object {
        /**
         * 每隔600条存储数据库,然后清理list,方便内存回收
         */
        private const val BATCH_COUNT: Int = 600
    }
}

interface BaseFastExcelApi<T : Any, Spec : KSpecification<T>, ExcelWriteDTO : Any> {

    val sql: KSqlClient get() = lazySqlClient
    fun T.toExcelWriteDTO(): ExcelWriteDTO
    fun ExcelWriteDTO.toEntity(): ExcelWriteDTO

    @PostMapping("/import")
    fun import(@RequestPart file: MultipartFile): Int {
        val use = file.inputStream.use {
            val excelDataListener = ExcelDataListener<ExcelWriteDTO>()
            FastExcel.read(it, excelDataListener)
            excelDataListener.caches
        }
        val map = use.map { it.toEntity() }
        val totalAffectedRowCount = sql.saveEntities(map).totalAffectedRowCount
        return totalAffectedRowCount
    }

    @GetMapping("/export")
    fun exportExcel(
        fileName: String, spec: Spec, sheetName: String = "Sheet1",
    ) {
        val data = sql.createQuery(CLASS()) {
            where(spec)
            select(table)
        }.execute()
        val toList = data.map { it.toExcelWriteDTO() }.toList()

        DownloadUtil.downloadExcel(fileName) {
            FastExcel.write(it, ExcelWriteDTOCLASS().java).sheet(sheetName).doWrite(toList)
        }
    }

    companion object {
        private val lazySqlClient: KSqlClient by lazy {
            SpringUtil.getBean(KSqlClient::class.java)
        }
    }


    fun CLASS(): KClass<T> {
        return (TypeUtil.getTypeArgument(javaClass, 0) as Class<T>).kotlin
    }


    fun ExcelWriteDTOCLASS(): KClass<ExcelWriteDTO> {
        return (TypeUtil.getTypeArgument(javaClass, 2) as Class<ExcelWriteDTO>).kotlin
    }

}
 
        """.trimIndent()
    }

    override val javafileTypeSuffix: String
        get() = ".java"

    override fun fullName(psiFile: PsiFile?): String {
        return "BaseFastExcelApi"
    }

    override val ktfileTypeSuffix: String
        get() = ".kt"
    override val pdir: String
        get() = "base"


}