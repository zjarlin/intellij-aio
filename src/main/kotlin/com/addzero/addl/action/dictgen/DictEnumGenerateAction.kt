//package com.addzero.addl.action.dictgen
//
//import cn.hutool.core.util.StrUtil
//import com.addzero.addl.settings.SettingContext
//import com.addzero.addl.util.*
//import com.addzero.util.psi.PsiUtil
//import com.intellij.database.model.DasNamespace
//import com.intellij.database.psi.DbDataSource
//import com.intellij.database.psi.DbElement
//import com.intellij.ide.highlighter.JavaFileType
//import com.intellij.openapi.actionSystem.AnAction
//import com.intellij.openapi.actionSystem.AnActionEvent
//import com.intellij.openapi.actionSystem.LangDataKeys
//import com.intellij.openapi.application.ApplicationManager
//import com.intellij.openapi.command.WriteCommandAction
//import com.intellij.openapi.fileEditor.FileEditorManager
//import com.intellij.openapi.module.ModuleUtil
//import com.intellij.openapi.project.Project
//import com.intellij.openapi.project.guessProjectDir
//import com.intellij.openapi.roots.ModuleRootManager
//import com.intellij.psi.PsiDirectory
//import com.intellij.psi.PsiElement
//import com.intellij.psi.PsiFileFactory
//import com.intellij.psi.PsiManager
//import com.intellij.psi.codeStyle.CodeStyleManager
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.Job
//import org.jetbrains.kotlin.idea.KotlinFileType
//import java.time.LocalDateTime
//import java.time.format.DateTimeFormatter
//import java.util.*
//import kotlin.coroutines.CoroutineContext
//
//class DictEnumGenerateAction : AnAction(), CoroutineScope {
//    private val job = Job()
//    override val coroutineContext: CoroutineContext
//        get() = job + Dispatchers.Default
//
////    override fun dispose() {
////        job.cancel()
////        super.dispose()
////    }
//
//    private lateinit var dataSource: DbDataSource
////    private val targetPackage = "com.addzero.common.enums" // 生成枚举的目标包路径
//
//    override fun update(event: AnActionEvent) {
//        val selected = event.getData(LangDataKeys.PSI_ELEMENT_ARRAY)
//        event.presentation.isVisible = selected.shouldShowMainEntry()
//    }
//
//    override fun actionPerformed(e: AnActionEvent) {
//        val project = e.project ?: return
//        val schema = e.getData(LangDataKeys.PSI_ELEMENT) as? DasNamespace ?: return
//        dataSource = findDataSource(schema) ?: return
//
//        ApplicationManager.getApplication()?.invokeLater {
//            try {
//                // 获取字典数据并生成枚举
//                val dictData = getDictData(dataSource)
//                if (dictData.isEmpty()) {
//                    DialogUtil.showErrorMsg("未找到字典数据或字典项数据")
//                    return@invokeLater
//                }
//
//                // 生成枚举类
//                generateEnums(project, dictData)
//            } catch (ex: Exception) {
//                DialogUtil.showErrorMsg("生成枚举失败: ${ex.message}")
//            }
//        }
//    }
//
//    private fun Array<PsiElement>?.shouldShowMainEntry(): Boolean {
//        if (this == null) return false
//        return all {
//            if (it !is DbElement) return@all false
//            it.typeName in arrayOf("schema", "database", "架构", "数据库")
//        }
//    }
//
////    override fun doActionPerformed(e: AnActionEvent) {
////    }
//
//    private fun findDataSource(element: DasNamespace): DbDataSource? {
//        var current: Any? = element
//        while (current != null) {
//            if (current is DbDataSource) return current
//            current = when (current) {
//                is DasNamespace -> current.dasParent
//                else -> null
//            }
//        }
//        return null
//    }
//
//    private fun getDictData(dataSource: DbDataSource): Map<DictInfo, List<DictItemInfo>> {
//        val settings = com.addzero.addl.settings.SettingContext.settings
//
//
//        val dictTabName = settings.dictTableName
//
//        val itemTabName = settings.itemTableName
//
//
//        // 使用 JOIN 查询获取字典和字典项数据
//        val did = settings.did
//        val dcode = settings.dcode
//        val ddes = settings.ddes
//
//
//        val idictid = settings.exdictid
//        val icode = settings.icode
//        val ides = settings.ides
//
//
////        val dictTable = DasUtil.getTables(dataSource)
////            .find { it.name.equals(dictTabName, ignoreCase = true) }
////            ?: throw IllegalStateException("未找到字典表($dictTabName)")
////        val dictItemTable = DasUtil.getTables(dataSource)
////            .find { it.name.equals("$itemTabName", ignoreCase = true) }
////            ?: throw IllegalStateException("未找到字典项表($itemTabName)")
//
//        val query = QueryHandler.create<Map<DictInfo, List<DictItemInfo>>>(
//            """
//            SELECT d.$did as dict_id,
//                   d.$dcode as dict_code,
//                   d.$ddes as dict_desc,
//                   i.$icode as item_code,
//                   i.$ides as item_desc
//            FROM $dictTabName d
//            LEFT JOIN $itemTabName i ON d.$did = i.$idictid
//            """.trimIndent()
//        ) { rs ->
//            val result = mutableMapOf<DictInfo, MutableList<DictItemInfo>>()
//
//            while (rs.next()) {
//                val dictInfo = DictInfo(
//                    id = rs.getString("dict_id") ?: "", code = rs.getString("dict_code") ?: "", description = rs.getString("dict_desc") ?: ""
//                )
//
//                val itemCode = rs.getString("item_code")
//                val itemDesc = rs.getString("item_desc")
//
//                if (itemCode != null) {
//                    val dictItem = DictItemInfo(
//                        dictId = dictInfo.id, itemCode = itemCode, itemDescription = itemDesc
//                    )
//
//                    result.getOrPut(dictInfo) { mutableListOf() }.add(dictItem)
//                }
//            }
//
//            result
//        }
//
//        // 执行查询
//        val sql = query.sqlBuilder.build()
//        return DatabaseUtil.executeQuery(dataSource, sql, query.handler).filterValues { items -> items.isNotEmpty() }
//    }
//
//
//}
