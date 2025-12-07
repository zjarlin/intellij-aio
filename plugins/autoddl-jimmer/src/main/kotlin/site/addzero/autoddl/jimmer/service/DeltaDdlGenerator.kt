package site.addzero.autoddl.jimmer.service

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import site.addzero.autoddl.jimmer.lsi.UnifiedLsiResolver
import site.addzero.autoddl.jimmer.settings.JimmerDdlSettings
import site.addzero.autoddl.jimmer.toolwindow.DdlLogPanel
import site.addzero.util.db.DatabaseType
import site.addzero.util.ddlgenerator.extension.toCompleteSchemaDDL
import site.addzero.util.lsi.clazz.LsiClass
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 差量 DDL 生成器
 * 使用 K2 Analysis API 处理 Kotlin 文件，PSI 处理 Java 文件
 */
class DeltaDdlGenerator(private val project: Project, private val logPanel: DdlLogPanel? = null) {

    private val log = Logger.getInstance(DeltaDdlGenerator::class.java)
    private val settings = JimmerDdlSettings.getInstance(project)

    /**
     * 扫描项目中的 POJO 实体类（支持 Java/Kotlin 混编）
     * - Java 文件使用 lsi-psi 解析
     * - Kotlin 文件使用 lsi-kt2 (K2 Analysis API) 解析
     */
    fun scanJimmerEntities(): List<LsiClass> {
        return ReadAction.compute<List<LsiClass>, Throwable> {
            val entities = mutableListOf<LsiClass>()
            val fileIndex = ProjectFileIndex.getInstance(project)
            var javaCount = 0
            var kotlinCount = 0
            var totalFiles = 0
            var totalClasses = 0
            
            // 显示项目信息
            val projectPath = project.basePath ?: "未知路径"
            val projectName = project.name
            val infoMsg = "当前项目: $projectName ($projectPath)"
            log.info(infoMsg)
            logPanel?.logInfo(infoMsg)
            
            // 检查源码根目录
            val sourceRoots = com.intellij.openapi.roots.ProjectRootManager.getInstance(project).contentSourceRoots
            val rootsMsg = "源码根目录数量: ${sourceRoots.size}"
            log.info(rootsMsg)
            logPanel?.logInfo(rootsMsg)
            
            if (sourceRoots.isEmpty()) {
                val warnMsg = "警告：项目没有配置源码根目录！请确保：\n" +
                        "1. 已在测试 IDE 中打开了包含 Jimmer 实体的项目（不是 intellij-aio 插件项目）\n" +
                        "2. 项目已完成索引（等待右下角进度条完成）\n" +
                        "3. 项目已正确配置（File → Project Structure → Modules）"
                log.warn(warnMsg)
                logPanel?.logError("未找到源码根目录", warnMsg)
                return@compute emptyList()
            }
            
            sourceRoots.forEachIndexed { index, root ->
                val rootMsg = "  源码根 ${index + 1}: ${root.path}"
                log.info(rootMsg)
                logPanel?.logInfo(rootMsg)
            }
            
            val startMsg = "开始扫描 Jimmer 实体类..."
            log.info(startMsg)
            logPanel?.logInfo(startMsg)

            fileIndex.iterateContent { file ->
                if (UnifiedLsiResolver.isSupportedSourceFile(file) && fileIndex.isInSourceContent(file)) {
                    totalFiles++
                    try {
                        val allClasses = UnifiedLsiResolver.resolveClasses(file, project)
                        totalClasses += allClasses.size
                        
                        // 打印详细信息（前5个文件）
                        if (totalFiles <= 5 && allClasses.isNotEmpty()) {
                            val fileMsg = "文件 $totalFiles: ${file.name} (${allClasses.size} 个类)"
                            log.info(fileMsg)
                            logPanel?.logInfo(fileMsg)
                            
                            allClasses.forEach { clazz ->
                                val classMsg = "  - ${clazz.qualifiedName}: 接口=${clazz.isInterface}, " +
                                        "注解=${clazz.annotations.map { it.simpleName ?: it.qualifiedName }}, " +
                                        "isPojo=${clazz.isPojo}"
                                log.info(classMsg)
                                logPanel?.logInfo(classMsg)
                            }
                        }
                        
                        val pojoClasses = allClasses.filter { it.isPojo }
                        entities.addAll(pojoClasses)

                        // 统计
                        when (file.extension?.lowercase()) {
                            "java" -> javaCount += pojoClasses.size
                            "kt" -> kotlinCount += pojoClasses.size
                        }
                    } catch (e: Exception) {
                        val errMsg = "解析文件失败: ${file.path} - ${e.message}"
                        log.warn(errMsg, e)
                        logPanel?.logError(errMsg, e.stackTraceToString())
                    }
                }
                true
            }

            val summaryMsg = "扫描完成: 扫描 $totalFiles 个文件, 找到 $totalClasses 个类, " +
                    "$javaCount 个Java实体 + $kotlinCount 个Kotlin实体 = ${entities.size} 个POJO实体"
            log.info(summaryMsg)
            logPanel?.logInfo(summaryMsg)
            
            entities.distinctBy { it.qualifiedName }
        }
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

        // 生成完整 Schema DDL (需要 ReadAction 访问 PSI)
        val ddl = ReadAction.compute<String, Throwable> {
            entities.toCompleteSchemaDDL(
                dialect = databaseType,
                includeIndexes = settings.includeIndexes,
                includeManyToManyTables = true,
                includeForeignKeys = settings.includeForeignKeys
            )
        }

        // 保存到文件
        val ddlFile = File(outputDir, "delta_${timestamp}.sql")
        ddlFile.writeText(ddl)

        // 如果需要，生成回滚SQL（暂时留空，后续实现）
        val rollbackFile = if (settings.generateRollback) {
            val rollback = ReadAction.compute<String, Throwable> {
                generateRollbackSql(entities, databaseType)
            }
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
