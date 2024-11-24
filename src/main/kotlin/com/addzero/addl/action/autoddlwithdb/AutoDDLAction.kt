package com.addzero.addl.action.autoddlwithdb

import com.addzero.addl.action.autoddlwithdb.scanner.findktEntityClasses
import com.addzero.addl.autoddlstarter.generator.IDatabaseGenerator.Companion.getDatabaseDDLGenerator
import com.addzero.addl.autoddlstarter.generator.entity.DDLContext
import com.addzero.addl.autoddlstarter.generator.entity.DDlRangeContext
import com.addzero.addl.autoddlstarter.generator.entity.toDDLContext
import com.addzero.addl.autoddlstarter.generator.factory.DDLContextFactory4JavaMetaInfo.createDDLContext
import com.addzero.addl.autoddlstarter.generator.factory.DDLContextFactory4JavaMetaInfo.createDDLContext4KtClass
import com.addzero.addl.ktututil.JlCollUtil.differenceBy
import com.addzero.addl.settings.SettingContext
import com.addzero.addl.util.ShowContentUtil
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
            it.typeName in arrayOf("schema", "database")
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val dbType = SettingContext.settings.dbType
        val project = e.project ?: return
        val schema = e.getData(LangDataKeys.PSI_ELEMENT) as? DasNamespace ?: return
        dataSource = findDataSource(schema) ?: return
        val connectionConfig = dataSource.connectionConfig

        val pkgContext = scanDdlContext(project).flatMap { it.toDDLContext() }
        if (pkgContext.isEmpty()) {
            ShowContentUtil.showErrorMsg("未扫描到实体结构")
            return
        }



        val ddlContexts = ddlContextsByDataSource(dataSource).flatMap { it.toDDLContext() }


//        pkgContext.differenceBy()
        // 计算差集
        val diff = pkgContext.differenceBy(
            ddlContexts,
            { a, b -> a.tableEnglishName == b.tableEnglishName },
            { a, b -> a.colName == b.colName },
//            { a, b -> a.colType == b.colType },
        )

        //判断没有差异就报错
        if (diff.isEmpty()) {
            ShowContentUtil.showErrorMsg("实体包路径结构与数据库无差异")
            return
        }

        val toFlatDDLContext = diff.toDDLContext()


        //未来加入实体元数据抽取工厂
        val databaseDDLGenerator = getDatabaseDDLGenerator(dbType)


        val map = toFlatDDLContext.joinToString(System.lineSeparator()) {

            val tableEnglishName = it.tableEnglishName

            //判断表是否存在
            val isTbaleExit = isTabExit(tableEnglishName)


            val sql = if (isTbaleExit) {
                val addColSql = databaseDDLGenerator.generateAddColDDL(it)
                addColSql
            } else {
                val generateCreateTableDDL = databaseDDLGenerator.generateCreateTableDDL(it)
                generateCreateTableDDL
            }
            sql
        }

        ShowContentUtil.openTextInEditor(
            project,
            map,
            "diff_ddl",
            ".sql",
//            project!!.basePath
        )

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
        val scanPkg = SettingContext.settings.scanPkg
        val dbType = SettingContext.settings.dbType
        //        val scanPkg = ""
        val findAllEntityClasses = findktEntityClasses(project)
        val map = findAllEntityClasses.map {
            val createDDLContext = createDDLContext4KtClass(it, dbType)
            createDDLContext
        }
        return map
    }


    private fun convertToDDLContext(dbTable: DasTable, columns: List<DasColumn>, dbType: String): DDLContext {
        return DDLContext(tableChineseName = dbTable.comment ?: dbTable.name, tableEnglishName = dbTable.name, databaseType = dbType, databaseName = dbTable.dasParent?.name ?: "", dto = columns.map { column ->
            DDlRangeContext(
                colName = column.name, colType = column.dasType.specification ?: "", colLength = "", colComment = column.comment ?: "", isPrimaryKey = if (DasUtil.isPrimary(column)) "Y" else "N", isSelfIncreasing = if (DasUtil.isAutoGenerated(column)) "Y" else "N"
            )
        })
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

    private fun isTabExit(tableName: String): Boolean {
        // 获取所有表
        val tables = DasUtil.getTables(dataSource)

        // 检查表名是否存在（不区分大小写）
        return tables.any { table ->
            table.name.equals(tableName, ignoreCase = true)
        }
    }
}