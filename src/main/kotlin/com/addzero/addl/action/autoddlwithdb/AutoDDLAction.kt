package com.addzero.addl.action.autoddlwithdb

import com.intellij.database.psi.DbDataSource
import com.intellij.database.psi.DbTable
import com.intellij.database.util.DasUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import java.sql.Connection
import java.sql.DriverManager

class AutoDDLAction : AnAction() {

    override fun update(e: AnActionEvent) {
        val elements = e.getData(LangDataKeys.PSI_ELEMENT_ARRAY)
        val visible = elements?.size == 1 && elements[0] is DbDataSource
        e.presentation.isVisible = visible
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val elements = e.getData(LangDataKeys.PSI_ELEMENT_ARRAY)
        if (elements == null || elements.size != 1 || elements[0] !is DbDataSource) {
            return
        }

        val dataSource = elements[0] as DbDataSource

        ApplicationManager.getApplication().invokeLater {
            val dialog = AutoDDLDialog(project)
            dialog.hideDataSourceSelection()
            if (!dialog.showAndGet()) {
                return@invokeLater
            }

            val packagePath = dialog.selectedPackagePath

            ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Executing DDL Operations") {
                override fun run(indicator: ProgressIndicator) {
                    try {
                        val url = "jdbc:dm://82.157.77.120:5236"
                        val username = "SJZYXT"
                        val password = "sjzyxt2024"

                        indicator.text = "Connecting to database..."
                        indicator.isIndeterminate = true

                        DriverManager.getConnection(url, username, password).use { connection ->
                            indicator.text = "Scanning entity classes..."
                            val entityClasses = scanEntityClasses(project, packagePath)

                            indicator.text = "Loading database metadata..."
                            val existingTables = getDatabaseTables(dataSource)

                            indicator.text = "Processing DDL operations..."
                            indicator.isIndeterminate = false
                            processDDLOperations(connection, entityClasses, existingTables, indicator)

                            invokeLater {
                                Messages.showInfoMessage(project,
                                    "DDL operations completed successfully",
                                    "Success")
                            }
                        }
                    } catch (ex: Exception) {
                        invokeLater {
                            Messages.showErrorDialog(project,
                                "Error: ${ex.message}",
                                "Error")
                        }
                    }
                }
            })
        }
    }

    private fun getDatabaseTables(dataSource: DbDataSource): Map<String, DbTable> {
        val tables = DasUtil.getTables(dataSource)
        val associateBy = tables.filterIsInstance<DbTable>().associateBy { it.name }
        return associateBy
    }

    private fun scanEntityClasses(project: Project, packagePath: String): List<PsiClass> {
        val scope = GlobalSearchScope.projectScope(project)
        val psiPackage = JavaPsiFacade.getInstance(project).findPackage(packagePath)
        val entityClasses = mutableListOf<PsiClass>()

        psiPackage?.classes?.forEach { psiClass ->
            // 检查是否是实体类（有@Entity或@Table注解）
            if (isEntityClass(psiClass)) {
                entityClasses.add(psiClass)
            }
        }

        return entityClasses
    }

    private fun isEntityClass(psiClass: PsiClass): Boolean {
        return psiClass.annotations.any { annotation ->
            val qualifiedName = annotation.qualifiedName
            qualifiedName == "javax.persistence.Entity" || qualifiedName == "jakarta.persistence.Entity" || qualifiedName == "javax.persistence.Table" || qualifiedName == "jakarta.persistence.Table"
        }
    }

    private fun getTableName(entityClass: PsiClass): String {
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
        val tableName = getTableName(entityClass)
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
        indicator: ProgressIndicator
    ) {
        val total = entityClasses.size.toDouble()
        entityClasses.forEachIndexed { index, entityClass ->
            indicator.fraction = index / total
            indicator.text2 = "Processing ${entityClass.name}"

            val tableName = getTableName(entityClass)
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
        val tableName = getTableName(entityClass)

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
                // TODO: 可以添加修改列类型的逻辑
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
}