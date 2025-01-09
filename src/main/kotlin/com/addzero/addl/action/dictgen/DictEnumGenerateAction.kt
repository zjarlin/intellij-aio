package com.addzero.addl.action.dictgen

import cn.hutool.core.util.StrUtil
import com.addzero.addl.settings.SettingContext
import com.addzero.addl.util.*
import com.addzero.addl.util.fieldinfo.PsiUtil
import com.intellij.database.model.DasNamespace
import com.intellij.database.psi.DbDataSource
import com.intellij.database.psi.DbElement
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.jetbrains.kotlin.idea.KotlinFileType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.coroutines.CoroutineContext

class DictEnumGenerateAction : AnAction(), CoroutineScope {
    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Default

//    override fun dispose() {
//        job.cancel()
//        super.dispose()
//    }

    private lateinit var dataSource: DbDataSource
//    private val targetPackage = "com.addzero.common.enums" // 生成枚举的目标包路径

    override fun update(event: AnActionEvent) {
        val selected = event.getData(LangDataKeys.PSI_ELEMENT_ARRAY)
        event.presentation.isVisible = selected.shouldShowMainEntry()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val schema = e.getData(LangDataKeys.PSI_ELEMENT) as? DasNamespace ?: return
        dataSource = findDataSource(schema) ?: return

        ApplicationManager.getApplication()?.invokeLater {
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
                DialogUtil.showErrorMsg("生成枚举失败: ${ex.message}")
            }
        }
    }

    private fun Array<PsiElement>?.shouldShowMainEntry(): Boolean {
        if (this == null) return false
        return all {
            if (it !is DbElement) return@all false
            it.typeName in arrayOf("schema", "database", "架构", "数据库")
        }
    }

//    override fun doActionPerformed(e: AnActionEvent) {
//    }

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
        val settings = com.addzero.addl.settings.SettingContext.settings


        val dictTabName = settings.dictTableName

        val itemTabName = settings.itemTableName


        // 使用 JOIN 查询获取字典和字典项数据
        val did = settings.did
        val dcode = settings.dcode
        val ddes = settings.ddes


        val idictid = settings.exdictid
        val icode = settings.icode
        val ides = settings.ides


//        val dictTable = DasUtil.getTables(dataSource)
//            .find { it.name.equals(dictTabName, ignoreCase = true) }
//            ?: throw IllegalStateException("未找到字典表($dictTabName)")
//        val dictItemTable = DasUtil.getTables(dataSource)
//            .find { it.name.equals("$itemTabName", ignoreCase = true) }
//            ?: throw IllegalStateException("未找到字典项表($itemTabName)")

        val query = QueryHandler.create<Map<DictInfo, List<DictItemInfo>>>(
            """
            SELECT d.$did as dict_id,
                   d.$dcode as dict_code, 
                   d.$ddes as dict_desc,
                   i.$icode as item_code,
                   i.$ides as item_desc
            FROM $dictTabName d
            LEFT JOIN $itemTabName i ON d.$did = i.$idictid
            """.trimIndent()
        ) { rs ->
            val result = mutableMapOf<DictInfo, MutableList<DictItemInfo>>()

            while (rs.next()) {
                val dictInfo = DictInfo(
                    id = rs.getString("dict_id") ?: "", code = rs.getString("dict_code") ?: "", description = rs.getString("dict_desc") ?: ""
                )

                val itemCode = rs.getString("item_code")
                val itemDesc = rs.getString("item_desc")

                if (itemCode != null) {
                    val dictItem = DictItemInfo(
                        dictId = dictInfo.id, itemCode = itemCode, itemDescription = itemDesc
                    )

                    result.getOrPut(dictInfo) { mutableListOf() }.add(dictItem)
                }
            }

            result
        }

        // 执行查询
        val sql = query.sqlBuilder.build()
        return DatabaseUtil.executeQuery(dataSource, sql, query.handler).filterValues { items -> items.isNotEmpty() }
    }

    private fun generateEnums(
        project: Project,
        dictData: Map<DictInfo, List<DictItemInfo>>,
    ) {
        val packagePath = SettingContext.settings.enumPkg
        val directory = createPackageDirectory(project, packagePath)
        var skipCount = 0
        var createCount = 0

        val isKotlin = PsiUtil.isKotlinProject(project)

        dictData.forEach { (dictInfo, items) ->
            val enumName = dictInfo.code.toEnumName()
            val fileName = "$enumName${if (isKotlin) ".kt" else ".java"}"

            // 检查枚举类是否已存在
            if (directory.findFile(fileName) != null) {
                DialogUtil.showWarningMsg("$fileName 已存在,请检查文件或字典表中是否重复定义")
                skipCount++
                return@forEach
            }

            val enumContent = generateEnumContent(enumName, dictInfo.description, items, isKotlin)
            val fileType = if (isKotlin) KotlinFileType.INSTANCE else JavaFileType.INSTANCE

            WriteCommandAction.runWriteCommandAction(project) {
                val psiFile = PsiFileFactory.getInstance(project)
                    .createFileFromText(fileName, fileType, enumContent)
                CodeStyleManager.getInstance(project).reformat(psiFile)
                directory.add(psiFile)
                createCount++
            }
        }

        // 显示处理结果
        if (createCount > 0) {
            DialogUtil.showInfoMsg(
                "成功生成 $createCount 个枚举类" + if (skipCount > 0) "，跳过 $skipCount 个已存在的枚举类" else ""
            )
        } else if (skipCount > 0) {
            DialogUtil.showWarningMsg("所有枚举类($skipCount 个)都已存在，未生成新文件")
        }
    }

    private fun createPackageDirectory(project: Project, packagePath: String): PsiDirectory {
        // 尝试获取当前激活的模块
        val activeModule = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()?.let {
            PsiManager.getInstance(project).findFile(it)
        }?.let {
            ModuleUtil.findModuleForPsiElement(
                it
            )
        }

        // 获取源代码根目录
        val sourceRoot = when {
            // 如果有活动模块，使用模块的源代码根目录
            activeModule != null -> {
                ModuleRootManager.getInstance(activeModule)
                    .sourceRoots
                    .firstOrNull { root ->
                        // 优先选择 src/main/kotlin 目录
                        root.path.contains("src/main/kotlin") ||
                        root.path.contains("src/main/java")
                    }
            }
            // 否则使用项目根目录
            else -> project.guessProjectDir()
        } ?: throw IllegalStateException("Cannot find source root directory")

        val psiManager = PsiManager.getInstance(project)
        var currentDir = psiManager.findDirectory(sourceRoot)
            ?: throw IllegalStateException("Cannot find source directory")

        // 创建包路径目录
        packagePath.split(".").forEach { name ->
            currentDir = currentDir.findSubdirectory(name) ?: run {
                var newDir: PsiDirectory? = null
                WriteCommandAction.runWriteCommandAction(project) {
                    try {
                        newDir = currentDir.createSubdirectory(name)
                    } catch (e: Exception) {
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
        isKotlin: Boolean
    ): String {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val packageName = SettingContext.settings.enumPkg

        return when (isKotlin) {
            true -> generateKotlinEnum(packageName, enumName, description, items, timestamp)
            false -> generateJavaEnum(packageName, enumName, description, items, timestamp)
        }
    }

    private fun generateKotlinEnum(
        packageName: String,
        enumName: String,
        description: String,
        items: List<DictItemInfo>,
        timestamp: String
    ): String {
        val enumItems = items.joinToString(",\n") { item ->
            val itemCode = item.itemCode
            val itemDescription = item.itemDescription
            val format = StrUtil.format(SettingContext.settings.enumAnnotation, itemCode)
            """
    /**
     * $itemDescription
     */
     $format
    ${itemDescription.toPinyinUpper()}("$itemCode", "$itemDescription")""".trimIndent()
        }

        return """
            package $packageName
            
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

    private fun generateJavaEnum(
        packageName: String,
        enumName: String,
        description: String,
        items: List<DictItemInfo>,
        timestamp: String
    ): String {
        val enumItems = items.joinToString(",\n") { item ->
            val itemCode = item.itemCode
            val itemDescription = item.itemDescription
            val format = StrUtil.format(SettingContext.settings.enumAnnotation, itemCode)
            """
    /**
     * $itemDescription
     */
    $format
    ${itemDescription.toPinyinUpper()}("$itemCode", "$itemDescription")""".trimIndent()
        }

        return """
            package $packageName;
            
            import com.fasterxml.jackson.annotation.JsonCreator;
            import com.fasterxml.jackson.annotation.JsonValue;
            import java.util.Arrays;
            
            /**
             * $description
             *
             * @author AutoDDL
             * @date $timestamp
             */
            public enum $enumName {
                $enumItems;
                
                private final String code;
                private final String desc;
                
                $enumName(String code, String desc) {
                    this.code = code;
                    this.desc = desc;
                }
                
                public String getCode() {
                    return code;
                }
                
                @JsonValue
                public String getDesc() {
                    return desc;
                }
                
                @JsonCreator
                public static $enumName fromCode(String code) {
                    return Arrays.stream(values())
                        .filter(e -> e.code.equals(code))
                        .findFirst()
                        .orElse(null);
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
    val toValidVariableName = JlStrUtil.toValidVariableName(toUpperCase, JlStrUtil.VariableType.CONSTANT)

    return toValidVariableName
}