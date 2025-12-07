package site.addzero.autoddl.jimmer.service

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import site.addzero.autoddl.jimmer.settings.JimmerDdlSettings
import site.addzero.util.db.DatabaseType
import site.addzero.util.ddlgenerator.extension.toCompleteSchemaDDL
import site.addzero.util.lsi.clazz.LsiClass
import site.addzero.util.lsi_impl.impl.psi.clazz.PsiLsiClass
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 差量 DDL 生成器
 */
class DeltaDdlGenerator(private val project: Project) {

    private val settings = JimmerDdlSettings.getInstance(project)

    /**
     * 扫描 Jimmer 实体类
     */
    fun scanJimmerEntities(): List<LsiClass> {
        val entities = mutableListOf<LsiClass>()
        val scope = GlobalSearchScope.projectScope(project)

        // 扫描配置的包路径
        val packages = settings.scanPackages.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        // 如果没有配置包路径或使用默认值，则扫描整个项目
        val shouldScanAll = packages.isEmpty() || 
                           packages.any { it == "com.example.entity" }

        // 搜索 Jimmer 的 @Entity 注解
        findAnnotatedClasses("org.babyfish.jimmer.sql.Entity", scope)?.forEach { psiClass ->
            if (shouldScanAll || packages.any { isInPackage(psiClass, it) }) {
                entities.add(PsiLsiClass(psiClass))
            }
        }

        // 同时支持 JPA 的 @Entity 注解
        findAnnotatedClasses("javax.persistence.Entity", scope)?.forEach { psiClass ->
            if (shouldScanAll || packages.any { isInPackage(psiClass, it) }) {
                entities.add(PsiLsiClass(psiClass))
            }
        }

        return entities.distinctBy { it.qualifiedName }
    }

    /**
     * 生成差量 DDL
     */
    fun generateDeltaDdl(entities: List<LsiClass>, databaseType: DatabaseType): DdlResult {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val outputDir = File(project.basePath, settings.outputDirectory)

        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        // 生成完整 Schema DDL
        val ddl = entities.toCompleteSchemaDDL(
            dialect = databaseType,
            includeIndexes = settings.includeIndexes,
            includeManyToManyTables = true,
            includeForeignKeys = settings.includeForeignKeys
        )

        // 保存到文件
        val ddlFile = File(outputDir, "delta_${timestamp}.sql")
        ddlFile.writeText(ddl)

        // 如果需要，生成回滚SQL（暂时留空，后续实现）
        val rollbackFile = if (settings.generateRollback) {
            val rollback = generateRollbackSql(entities, databaseType)
            val file = File(outputDir, "rollback_${timestamp}.sql")
            file.writeText(rollback)
            file
        } else {
            null
        }

        return DdlResult(
            ddlFile = ddlFile,
            rollbackFile = rollbackFile,
            entityCount = entities.size,
            ddlContent = ddl
        )
    }

    /**
     * 生成回滚 SQL
     */
    private fun generateRollbackSql(entities: List<LsiClass>, databaseType: DatabaseType): String {
        // 简单实现：生成 DROP TABLE 语句
        val dropStatements = entities.map { entity ->
            val tableName = entity.name?.lowercase() ?: "unknown"
            "DROP TABLE IF EXISTS `$tableName`;"
        }

        return """
            |-- Rollback SQL
            |-- Generated at: ${LocalDateTime.now()}
            |
            |${dropStatements.joinToString("\n")}
        """.trimMargin()
    }

    /**
     * 检查类是否在指定包下
     */
    private fun isInPackage(psiClass: PsiClass, packageName: String): Boolean {
        val qualifiedName = psiClass.qualifiedName ?: return false
        return qualifiedName.startsWith(packageName)
    }

    /**
     * 查找带指定注解的类
     */
    private fun findAnnotatedClasses(annotationFQN: String, scope: GlobalSearchScope): Collection<PsiClass>? {
        return try {
            // 使用 JavaPsiFacade 查找注解类
            val javaPsiFacade = com.intellij.psi.JavaPsiFacade.getInstance(project)
            val annotationClass = javaPsiFacade.findClass(annotationFQN, scope) ?: return null

            // 搜索带该注解的类
            AnnotatedElementsSearch.searchPsiClasses(annotationClass, scope).findAll()
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * DDL 生成结果
 */
data class DdlResult(
    val ddlFile: File,
    val rollbackFile: File?,
    val entityCount: Int,
    val ddlContent: String
)
