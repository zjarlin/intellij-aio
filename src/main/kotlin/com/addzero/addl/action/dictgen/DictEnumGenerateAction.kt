package com.addzero.addl.action.dictgen

import cn.hutool.core.util.StrUtil
import com.addzero.addl.action.base.BaseAction
import com.addzero.addl.util.DialogUtil
import com.addzero.addl.util.PinYin4JUtils
import com.intellij.database.DataBus
import com.intellij.database.console.session.DatabaseSessionManager
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.connection.DatabaseDepartment
import com.intellij.database.model.DasNamespace
import com.intellij.database.psi.DbDataSource
import com.intellij.database.psi.DbElement
import com.intellij.database.psi.DbPsiFacade
import com.intellij.database.util.DasUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.database.datagrid.DataConsumer
import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.DataRequest
import com.intellij.database.model.DasTable
import com.intellij.database.run.ui.DataAccessType
import com.intellij.util.QueryExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.jetbrains.kotlin.idea.KotlinFileType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.coroutines.CoroutineContext
import com.intellij.database.view.DatabaseView
import com.intellij.database.util.DbImplUtil
import com.intellij.database.util.SearchPath
import com.intellij.database.util.ObjectPaths
import java.sql.DriverManager
import com.intellij.database.dataSource.DatabaseConnectionManager

class DictEnumGenerateAction : BaseAction(), CoroutineScope {
    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Default

//    override fun dispose() {
//        job.cancel()
//        super.dispose()
//    }

    private lateinit var dataSource: DbDataSource
    private val targetPackage = "com.addzero.common.enums.dict" // 生成枚举的目标包路径

    override fun update(event: AnActionEvent) {
        val selected = event.getData(LangDataKeys.PSI_ELEMENT_ARRAY)
        event.presentation.isVisible = selected.shouldShowMainEntry()
    }

    private fun Array<PsiElement>?.shouldShowMainEntry(): Boolean {
        if (this == null) return false
        return all {
            if (it !is DbElement) return@all false
            it.typeName in arrayOf("schema", "database", "架构", "数据库")
        }
    }

    override fun doActionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val schema = e.getData(LangDataKeys.PSI_ELEMENT) as? DasNamespace ?: return
        dataSource = findDataSource(schema) ?: return

        ApplicationManager.getApplication().invokeLater {
            try {
                // 获取字典数据并生成枚举
                val dictData = getDictData(dataSource)
                if (dictData.isEmpty()) {
                    DialogUtil.showErrorMsg("未找到字典数据或字典项数据")
                    return@invokeLater
                }

                // 生成枚举类
                generateEnums(project, dictData)
            } catch (ex: Exception) {
                logger.error(ex)
                DialogUtil.showErrorMsg("生成枚举失败: ${ex.message}")
            }
        }
    }

    private fun findDataSource(element: DasNamespace): DbDataSource? {
        var current: Any? = element
        while (current != null) {
            if (current is DbDataSource) return current
            current = when (current) {
                is DasNamespace -> current.dasParent
                else -> null
            }
        }
        return null
    }

    private data class DictInfo(
        val id: String,
        val code: String,
        val description: String,
    )

    private data class DictItemInfo(
        val dictId: String,
        val itemCode: String,
        val itemDescription: String,
    )

    private fun getDictData(dataSource: DbDataSource): Map<DictInfo, List<DictItemInfo>> {
        val dictTable = DasUtil.getTables(dataSource)
            .find { it.name.equals("sys_dict", ignoreCase = true) }
            ?: throw IllegalStateException("未找到字典表(sys_dict)")

        val dictItemTable = DasUtil.getTables(dataSource)
            .find { it.name.equals("sys_dict_item", ignoreCase = true) }
            ?: throw IllegalStateException("未找到字典项表(sys_dict_item)")

        val dictInfos = mutableListOf<DictInfo>()
        val dictItems = mutableListOf<DictItemInfo>()

        // 获取数据库连接
        val localDataSource = DbImplUtil.getMaybeLocalDataSource(dataSource)
            ?: throw IllegalStateException("Cannot get local data source")

        // 使用 Builder 模式获取连接
        DatabaseConnectionManager.getInstance()
            .build(dataSource.project, localDataSource)
            .setAskPassword(false)  // 不询问密码，使用已保存的密码
            .create()?.use { connectionRef ->
                val connection = connectionRef.get()

                // 查询字典表
                DbImplUtil.executeAndGetResult(
                    connection,
                    """
                    SELECT id, code, description 
                    FROM sys_dict 
                    WHERE del_flag = '0' 
                    ORDER BY code
                    """.trimIndent()
                ) { rs ->
                    while (rs.next()) {
                        dictInfos.add(DictInfo(
                            id = rs.getString("id") ?: "",
                            code = rs.getString("code") ?: "",
                            description = rs.getString("description") ?: ""
                        ))
                    }
                    dictInfos
                }

                // 查询字典项表
                DbImplUtil.executeAndGetResult(
                    connection,
                    """
                    SELECT dict_id, code, description 
                    FROM sys_dict_item 
                    WHERE del_flag = '0' 
                    ORDER BY dict_id, sort_order
                    """.trimIndent()
                ) { rs ->
                    while (rs.next()) {
                        dictItems.add(DictItemInfo(
                            dictId = rs.getString("dict_id") ?: "",
                            itemCode = rs.getString("code") ?: "",
                            itemDescription = rs.getString("description") ?: ""
                        ))
                    }
                    dictItems
                }
            }

        return dictInfos.associateWith { dictInfo ->
            dictItems.filter { it.dictId == dictInfo.id }
        }.filterValues { items ->
            items.isNotEmpty()
        }
    }

    private fun generateEnums(
        project: Project,
        dictData: Map<DictInfo, List<DictItemInfo>>,
    ) {
        // 创建目标包目录
        val directory = createPackageDirectory(project, targetPackage)

        var skipCount = 0
        var createCount = 0

        dictData.forEach { (dictInfo, items) ->
            val enumName = dictInfo.code.toEnumName()
            val enumFileName = "$enumName.kt"

            // 检查枚举类是否已存在
            if (directory.findFile(enumFileName) != null) {
                skipCount++
                return@forEach
            }

            val enumContent = generateEnumContent(enumName, dictInfo.description, items)

            WriteCommandAction.runWriteCommandAction(project) {
                // 创建文件
                val psiFile = PsiFileFactory.getInstance(project)
                    .createFileFromText(enumFileName, KotlinFileType.INSTANCE, enumContent)

                // 格式化代码
                CodeStyleManager.getInstance(project).reformat(psiFile)

                // 添加录
                directory.add(psiFile)
                createCount++
            }
        }

        // 显示处理结果
        if (createCount > 0) {
            DialogUtil.showInfoMsg(
                "成功生成 $createCount 个枚举类" + if (skipCount > 0) "，过 $skipCount 个已存在的枚举类" else ""
            )
        } else if (skipCount > 0) {
            DialogUtil.showWarningMsg("所有枚举类($skipCount 个)都已存在，未生成新文件")
        }
    }

    private fun createPackageDirectory(project: Project, packagePath: String): PsiDirectory {
        val baseDir = project.guessProjectDir() ?: throw IllegalStateException("Cannot find project base directory")
        val psiManager = PsiManager.getInstance(project)
        var currentDir = psiManager.findDirectory(baseDir) ?: throw IllegalStateException("Cannot find base directory")

        packagePath.split(".").forEach { name ->
            currentDir = currentDir.findSubdirectory(name) ?: run {
                var newDir: PsiDirectory? = null
                WriteCommandAction.runWriteCommandAction(project) {
                    try {
                        newDir = currentDir.createSubdirectory(name)
                    } catch (e: Exception) {
                        logger.error("Failed to create directory: $name", e)
                        throw IllegalStateException("Failed to create directory: $name", e)
                    }
                }
                newDir ?: throw IllegalStateException("Failed to create directory: $name")
            }
        }

        return currentDir
    }

    private fun generateEnumContent(
        enumName: String,
        description: String,
        items: List<DictItemInfo>,
    ): String {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

        // 生成枚举项
        val enumItems = items.joinToString(",") { item ->
            val itemCode = item.itemCode
            val itemDescription = item.itemDescription
            """
    /**
     * $itemDescription
     */
     @EnumItem(name = "$itemCode") 
    ${itemDescription.toPinyinUpper()}("$itemCode", "$itemDescription")""".trimIndent()
        }

        return """
            package $targetPackage
           import com.fasterxml.jackson.annotation.JsonCreator
           import com.fasterxml.jackson.annotation.JsonValue
 
            /**
             * $description
             *
             * @author AutoDDL
             * @date $timestamp
             */
            enum class $enumName(
                val code: String,
                val desc: String
            ) {
                $enumItems;
                   @JsonValue
    fun getValue(): String {
        return desc
    }
                companion object {
                    @JsonCreator 
                    fun fromCode(code: String): $enumName? = values().find { it.code == code }
                }
            }
        """.trimIndent()
    }

    private fun String.toEnumName(): String {
        val toCamelCase = StrUtil.toCamelCase(this)
        val upperFirstAndAddPre = StrUtil.upperFirstAndAddPre(toCamelCase, "Enum")
        return upperFirstAndAddPre
    }

    private fun String.toEnumConstant(): String {
        return uppercase()
    }
}

private fun String.toPinyinUpper(): String {
    val stringToPinyin = PinYin4JUtils.hanziToPinyin(this, "_")
    val unerline = StrUtil.toUnderlineCase(stringToPinyin)
    val toUpperCase = unerline.uppercase(Locale.getDefault())
    return toUpperCase

}