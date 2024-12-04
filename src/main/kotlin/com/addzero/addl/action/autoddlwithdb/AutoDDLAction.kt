package com.addzero.addl.action.autoddlwithdb

import ai.grazie.utils.toDistinctTypedArray
import cn.hutool.core.util.StrUtil
import com.addzero.addl.action.autoddlwithdb.scanner.findJavaEntityClasses
import com.addzero.addl.action.autoddlwithdb.scanner.findktEntityClasses
import com.addzero.addl.autoddlstarter.generator.IDatabaseGenerator.Companion.getDatabaseDDLGenerator
import com.addzero.addl.autoddlstarter.generator.entity.DDLContext
import com.addzero.addl.autoddlstarter.generator.entity.DDLFLatContext
import com.addzero.addl.autoddlstarter.generator.entity.DDlRangeContext
import com.addzero.addl.autoddlstarter.generator.entity.toDDLContext
import com.addzero.addl.autoddlstarter.generator.factory.DDLContextFactory4JavaMetaInfo.createDDLContext
import com.addzero.addl.autoddlstarter.generator.factory.DDLContextFactory4JavaMetaInfo.createDDLContext4KtClass
import com.addzero.addl.ktututil.JlCollUtil.differenceBy
import com.addzero.addl.ktututil.JlCollUtil.intersectBy
import com.addzero.addl.ktututil.equalsIgnoreCase
import com.addzero.addl.ktututil.toJson
import com.addzero.addl.settings.SettingContext
import com.addzero.addl.util.DialogUtil
import com.addzero.addl.util.ShowContentUtil
import com.addzero.common.kt_util.isNotBlank
import com.intellij.database.model.DasColumn
import com.intellij.database.model.DasNamespace
import com.intellij.database.model.DasTable
import com.intellij.database.psi.DbDataSource
import com.intellij.database.psi.DbElement
import com.intellij.database.psi.DbTable
import com.intellij.database.util.DasUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiModifier
import isKotlinProject
import org.jetbrains.annotations.Unmodifiable
import java.sql.Connection

class AutoDDLAction : AnAction() {

    private lateinit var dataSource: DbDataSource

    /**
     * 检测是否应该显示菜单项
     */
    override fun update(event: AnActionEvent) {
        val selected = event.getData(LangDataKeys.PSI_ELEMENT_ARRAY)
        event.presentation.isVisible = selected.shouldShowMainEntry()
    }

    private fun Array<PsiElement>?.shouldShowMainEntry(): Boolean {
        if (this == null) {
            return false
        }
        return all {
            if (it !is DbElement) {
                return@all false
            }
            val typeName = it.typeName
//            ShowContentUtil.showErrorMsg(typeName)
            typeName in arrayOf("schema", "database", "架构", "数据库")
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val dbType = SettingContext.settings.dbType
        val project = e.project ?: return
        val schema = e.getData(LangDataKeys.PSI_ELEMENT) as? DasNamespace ?: return
        dataSource = findDataSource(schema) ?: return
        //实体扫包获取ddl上下文
        val pkgContext = scanDdlContext(project).flatMap { it.toDDLContext() }
        if (pkgContext.isEmpty()) {
            ShowContentUtil.showErrorMsg("未扫描到实体结构")
            return
        }

        //从数据库生成ddl上下文
        val ddlContexts = ddlContextsByDataSource(dataSource).flatMap { it.toDDLContext() }


//        pkgContext.differenceBy()
        // 计算差集

//        val diff = Streams.getGenericDiffs(pkgContext, ddlContexts, { it.tableEnglishName }, { it.colName })
        val diff = pkgContext.differenceBy(
            ddlContexts,
            { a, b -> a.tableEnglishName.equalsIgnoreCase(b.tableEnglishName) },
            { a, b ->
                a.colName.equalsIgnoreCase(b.colName)
            },
        )


        //再找出差异类型字段生成DML语句
//        val dmls= genDML(diffTypeContext)

        //判断没有差异就报错
        if (diff.isEmpty()) {
            DialogUtil.showInfoMsg("实体包路径结构与数据库名称无差异(表名和列名)")
            difftypeJson(pkgContext, ddlContexts, project)
            return
        }

        val toFlatDDLContext = diff.toDDLContext()


        val databaseDDLGenerator = getDatabaseDDLGenerator(dbType)


// 将 DDL 语句分类收集
        val (createTableSqls, addColumnSqls) = toFlatDDLContext.partition { context ->
            !isTabExit(context.tableEnglishName, ddlContexts)
        }.let { (createContexts, alterContexts) ->
            // 生成创建表的 SQL
            val createSqls = createContexts.map { context ->
                databaseDDLGenerator.generateCreateTableDDL(context)
            }

            // 生成添加列的 SQL
            val alterSqls = alterContexts.map { context ->
                databaseDDLGenerator.generateAddColDDL(context)
            }

            createSqls to alterSqls
        }

// 组合最终的 SQL，先创建表，再添加列
        val finalSql = buildString {
            // 添加创建表的 SQL
            if (createTableSqls.isNotEmpty()) {
                appendLine("-- Create Tables")
                appendLine(createTableSqls.joinToString(System.lineSeparator()))
            }

            // 添加分隔符
            if (createTableSqls.isNotEmpty() && addColumnSqls.isNotEmpty()) {
                appendLine()
                appendLine("-- ----------------------------------------")
                appendLine()
            }

            // 添加修改表的 SQL
            if (addColumnSqls.isNotEmpty()) {
                appendLine("-- Add Columns")
                appendLine(addColumnSqls.joinToString(System.lineSeparator()))
            }
        }
        ShowContentUtil.openTextInEditor(
            project,
            finalSql,
            "diff_ddl.sql",
            ".sql",
//            project!!.basePath
        )


        difftypeJsonSee(pkgContext, ddlContexts, project)


    }

    private fun difftypeJson(pkgContext: List<DDLFLatContext>, ddlContexts: List<DDLFLatContext>, project: Project) {

        val diffTypeContext = pkgContext.intersectBy(ddlContexts, { a, b -> a.tableEnglishName.equalsIgnoreCase(b.tableEnglishName) }, { a, b -> a.colName.equalsIgnoreCase(b.colName) }, { a, b -> !a.colType.equalsIgnoreCase(b.colType) })

        if (diffTypeContext.isNotEmpty()) {
            //警告类型不同的字段
            val toDDLContext = diffTypeContext.toDDLContext()
            val toJson1 = toDDLContext.toJson()
            if (toJson1.isNotBlank()) {
                DialogUtil.showWarningMsg(
                    "实体与数据库类型存在差异，请注意修改" + "" + "(未来版本会实现类型隐式适配数据库类型ddl语句!)"
                )
                ShowContentUtil.openTextInEditor(
                    project,
                    toJson1,
                    "diff_structure_type_atypism",
                    ".json",
                    //            project!!.basePath
                )

            }
        }


    }


    private fun difftypeJsonSee(
        pkgContext: List<DDLFLatContext>,
        ddlContexts: List<DDLFLatContext>,
        project: Project,
    ) {
        // 使用流式语法构建类型差异JSON
        val diffJson = pkgContext.groupBy { it.tableEnglishName.lowercase() }.mapValues { (tableName, entityColumns) ->
                // 获取数据库中对应表的列类型映射
                val dbColumnTypes = ddlContexts.filter { it.tableEnglishName.equals(tableName, ignoreCase = true) }.associate { it.colName.lowercase() to it.colType }

                // 找出类型不同的列
                entityColumns.mapNotNull { entityCol ->
                        val colName = entityCol.colName.lowercase()
                        dbColumnTypes[colName]?.let { dbType ->
                            if (!entityCol.colType.equals(dbType, ignoreCase = true)) {
                                colName to "${entityCol.colType}    <=    $dbType"
                            } else null
                        }
                    }.takeIf { it.isNotEmpty() }?.joinToString(",\n") { (colName, typeDiff) ->
                        """    "$colName": "$typeDiff""""
                    }
            }.filterValues { it != null }.takeIf { it.isNotEmpty() }?.let { diffMap ->
                // 使用字符串模板构建最终的JSON
                buildString {
                    appendLine("{")
                    append(diffMap.entries.joinToString(",\n") { (tableName, columnDiffs) ->
                        """  "$tableName": {
                    |$columnDiffs
                    |  }""".trimMargin()
                    })
                    appendLine("\n}")
                }
            } ?: "{}"

        if (diffJson != "{}") {
            DialogUtil.showWarningMsg(
                "实体与数据库类型存在差异，请注意修改(未来版本会实现类型隐式适配数据库类型ddl语句!)"
            )
            ShowContentUtil.openTextInEditor(
                project, diffJson, "diff_structure_type_atypism", ".json"
            )
        }
    }

    private fun genDML(diffTypeContext: List<DDLFLatContext>): String {
        TODO("Not yet implemented")
    }

    private fun ddlContextsByDataSource(dataSource: DbDataSource): @Unmodifiable MutableList<DDLContext> {
        val dbms = dataSource.dbms
        val tables = DasUtil.getTables(dataSource)

        // 获取所有表
        val ddlContexts = tables.map { dbTable ->
            val columns = DasUtil.getColumns(dbTable).toList()
            convertToDDLContext(dbTable, columns, dbms.name)
        }.toList()
        return ddlContexts
    }


    private fun scanDdlContext(project: Project): List<DDLContext> {
        val dbType = SettingContext.settings.dbType
        //        val scanPkg = ""

        val ddlContexts = if (isKotlinProject(project)) {
            val findAllEntityClasses = findktEntityClasses(project)
            val map = findAllEntityClasses.map {
                val createDDLContext = createDDLContext4KtClass(it, dbType)
                createDDLContext
            }
            map
        } else {
            val findJavaEntityClasses = findJavaEntityClasses(project)
            val map = findJavaEntityClasses.map {
                val createDDLContext = createDDLContext(it, dbType)
                createDDLContext
            }
            map
        }

        val toList = ddlContexts.map {
            val tableEnglishName = it.tableEnglishName
            val removeSurrounding = tableEnglishName.removeSurrounding("\"")
            it.tableEnglishName = removeSurrounding
            it
        }.toList()
        return toList
    }


    private fun convertToDDLContext(dbTable: DasTable, columns: List<DasColumn>, dbType: String): DDLContext {
        return DDLContext(tableChineseName = dbTable.comment ?: dbTable.name, tableEnglishName = dbTable.name, databaseType = dbType, databaseName = dbTable.dasParent?.name ?: "", dto = columns.map { column ->
            // 解析类型和长度
            val (type, length) = parseTypeAndLength(column.dasType.specification ?: "")

            DDlRangeContext(
                colName = column.name, colType = type, colLength = length, colComment = column.comment ?: "", isPrimaryKey = if (DasUtil.isPrimary(column)) "Y" else "N", isSelfIncreasing = if (DasUtil.isAutoGenerated(column)) "Y" else "N"
            )
        })
    }

    /**
     * 解析数据类型和长度
     * @param specification 类型规格（例如："VARCHAR(255)"）
     * @return Pair<类型, 长度>
     */
    private fun parseTypeAndLength(specification: String): Pair<String, String> {
        // 使用正则表达式匹配类型和长度
        val regex = """(\w+)\s*(?:\(([^)]+)\))?""".toRegex()
        val matchResult = regex.find(specification)

        return if (matchResult != null) {
            val (type, length) = matchResult.destructured
            Pair(type.trim(), length.trim())
        } else {
            // 如果没有匹配到长度，返回原始类型和空字符串
            Pair(specification.trim(), "")
        }
    }

    private fun findDataSource(element: DasNamespace): DbDataSource? {
        var current: Any? = element
        while (current != null) {
            if (current is DbDataSource) {
                return current
            }
            current = when (current) {
                is DasNamespace -> current.dasParent
                else -> null
            }
        }
        return null
    }


    private fun getEntityTableName(entityClass: PsiClass): String {
        // 首先查找@Table注解
        entityClass.annotations.forEach { annotation ->
            if (annotation.qualifiedName?.endsWith(".Table") == true) {
                // 获取name属性值
                annotation.findAttributeValue("name")?.let { nameValue ->
                    val tableName = nameValue.text.trim('"')
                    if (tableName.isNotEmpty()) {
                        return tableName
                    }
                }
            }
        }
        // 如果没有找到@Table注解或name属性，使用类名转换为下划线命名
        return camelToUnderline(entityClass.name ?: "")
    }

    private fun camelToUnderline(camelCase: String): String {
        return camelCase.replace(Regex("([a-z])([A-Z])"), "$1_$2").toLowerCase()
    }

    private fun generateCreateTableSql(entityClass: PsiClass): String {
        val tableName = getEntityTableName(entityClass)
        val columns = mutableListOf<String>()

        // 获取所有字段
        entityClass.allFields.forEach { field ->
            // 跳过静态字段和被@Transient标记的字段
            if (!field.hasModifierProperty(PsiModifier.STATIC) && !isTransientField(field)) {
                val columnDef = generateColumnDefinition(field)
                if (columnDef.isNotEmpty()) {
                    columns.add(columnDef)
                }
            }
        }

        return """
            CREATE TABLE IF NOT EXISTS $tableName (
                ${columns.joinToString(",\n    ")}
            );
        """.trimIndent()
    }

    private fun isTransientField(field: PsiField): Boolean {
        return field.annotations.any { annotation ->
            annotation.qualifiedName?.endsWith(".Transient") == true
        }
    }

    private fun generateColumnDefinition(field: PsiField): String {
        val columnName = getColumnName(field)
        val columnType = getColumnType(field)
        val constraints = getColumnConstraints(field)

        return "$columnName $columnType $constraints".trim()
    }

    private fun getColumnName(field: PsiField): String {
        // 查找@Column注解的name属性
        field.annotations.forEach { annotation ->
            if (annotation.qualifiedName?.endsWith(".Column") == true) {
                annotation.findAttributeValue("name")?.let { nameValue ->
                    val columnName = nameValue.text.trim('"')
                    if (columnName.isNotEmpty()) {
                        return columnName
                    }
                }
            }
        }
        // 默认使用字段名转下划线
        return camelToUnderline(field.name)
    }

    private fun getColumnType(field: PsiField): String {
        val type = field.type
        return when {
            type.equalsToText("java.lang.String") -> "VARCHAR(255)"
            type.equalsToText("java.lang.Integer") || type.equalsToText("int") -> "INT"
            type.equalsToText("java.lang.Long") || type.equalsToText("long") -> "BIGINT"
            type.equalsToText("java.lang.Boolean") || type.equalsToText("boolean") -> "BOOLEAN"
            type.equalsToText("java.math.BigDecimal") -> "DECIMAL(19,2)"
            type.equalsToText("java.util.Date") || type.equalsToText("java.time.LocalDateTime") -> "DATETIME"
            type.equalsToText("java.time.LocalDate") -> "DATE"
            else -> "VARCHAR(255)"
        }
    }

    private fun getColumnConstraints(field: PsiField): String {
        val constraints = mutableListOf<String>()

        // 处理非空约束
        if (field.annotations.any { it.qualifiedName?.endsWith(".NotNull") == true }) {
            constraints.add("NOT NULL")
        }

        // 处理主键
        if (field.annotations.any { it.qualifiedName?.endsWith(".Id") == true }) {
            constraints.add("PRIMARY KEY")
            // 如果有@GeneratedValue注解，添加自增
            if (field.annotations.any { it.qualifiedName?.endsWith(".GeneratedValue") == true }) {
                constraints.add("AUTO_INCREMENT")
            }
        }

        return constraints.joinToString(" ")
    }

    private fun processDDLOperations(
        connection: Connection,
        entityClasses: List<PsiClass>,
        existingTables: Map<String, DbTable>,
        indicator: ProgressIndicator,
    ) {
        val total = entityClasses.size.toDouble()
        entityClasses.forEachIndexed { index, entityClass ->
            indicator.fraction = index / total
            indicator.text2 = "Processing ${entityClass.name}"

            val tableName = getEntityTableName(entityClass)
            if (!existingTables.containsKey(tableName?.toLowerCase())) {
                val createTableSql = generateCreateTableSql(entityClass)
                executeDDL(connection, createTableSql)
            } else {
                val table = existingTables[tableName?.toLowerCase()]
                if (table != null) {
                    val alterSqlList = generateAlterTableSql(entityClass, table)
                    alterSqlList.forEach { alterSql ->
                        executeDDL(connection, alterSql)
                    }
                }
            }
        }
    }

    private fun generateAlterTableSql(entityClass: PsiClass, existingTable: DbTable): List<String> {
        val alterStatements = mutableListOf<String>()
        val tableName = getEntityTableName(entityClass)

        // 获取现有列
        val columns = DasUtil.getColumns(existingTable)
        val existingColumns = columns.associateBy { it.name }

        // 检查每个字段
        entityClass.allFields.forEach { field ->
            if (!field.hasModifierProperty(PsiModifier.STATIC) && !isTransientField(field)) {
                val columnName = getColumnName(field).toLowerCase()
                if (!existingColumns.containsKey(columnName)) {
                    // 如果列不存在，生成ADD COLUMN语句
                    val columnDef = generateColumnDefinition(field)
                    alterStatements.add("ALTER TABLE $tableName ADD COLUMN $columnDef;")
                }
                // TODO: 可以添加修改列类型的辑
            }
        }

        return alterStatements
    }

    private fun executeDDL(connection: Connection, sql: String?) {
        if (!sql.isNullOrBlank()) {
            connection.createStatement().use { statement ->
                statement.execute(sql)
            }
        }
    }

    private fun isTabExit(tableName: String, ddlContexts: List<DDLFLatContext>): Boolean {
        // 获取所有表

        val toArray = ddlContexts.map {
            val name = it.tableEnglishName
            name
        }.toDistinctTypedArray()
        val containsAny = StrUtil.containsAnyIgnoreCase(tableName, *toArray)
        return containsAny


        // 检查表名是否存在（不区分大小写）
//        return tables.any { table ->
//            val name = table.name
//            name.equals(tableName, ignoreCase = true)
//        }
    }
}