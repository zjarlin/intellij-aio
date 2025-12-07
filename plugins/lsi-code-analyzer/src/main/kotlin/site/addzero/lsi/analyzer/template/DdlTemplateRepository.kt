package site.addzero.lsi.analyzer.template

import site.addzero.lsi.analyzer.ddl.DdlOperationType
import site.addzero.lsi.analyzer.ddl.DdlTemplateManager
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * 标识一个 DDL 模板文件（数据库方言 + 操作类型）
 */
data class DdlTemplateId(
    val dialect: site.addzero.util.db.DatabaseType,
    val operation: DdlOperationType
) {
    val relativePath: String get() = "${dialect.code}/${operation.templateName}.ftl"
    val displayName: String get() = "${dialect.displayName} / ${operation.templateName}"
}

enum class DdlTemplateScope { BUILTIN, PROJECT, GLOBAL }

data class DdlTemplateDescriptor(
    val id: DdlTemplateId,
    val scope: DdlTemplateScope
)

/**
 * 负责枚举/读取/写入 IDE 中可用的 DDL 模板
 */
class DdlTemplateRepository(private val projectPath: String?) {

    private val classLoader = DdlTemplateManager::class.java.classLoader

    fun listBuiltin(): List<DdlTemplateDescriptor> {
        val descriptors = mutableListOf<DdlTemplateDescriptor>()
        site.addzero.util.db.DatabaseType.entries.forEach { dialect ->
            DdlOperationType.entries.forEach { operation ->
                val resourcePath = "templates/ddl/${dialect.code}/${operation.templateName}.ftl"
                if (classLoader.getResource(resourcePath) != null) {
                    descriptors += DdlTemplateDescriptor(DdlTemplateId(dialect, operation), DdlTemplateScope.BUILTIN)
                }
            }
        }
        return descriptors.sortedBy { it.id.displayName }
    }

    fun listCustom(): List<DdlTemplateDescriptor> {
        val descriptors = mutableListOf<DdlTemplateDescriptor>()
        projectPath?.let { path ->
            descriptors += readScope(File(path, ".lsi/templates/ddl"), DdlTemplateScope.PROJECT)
        }
        val home = System.getProperty("user.home")
        if (!home.isNullOrBlank()) {
            descriptors += readScope(File(home, ".lsi/templates/ddl"), DdlTemplateScope.GLOBAL)
        }
        return descriptors.sortedWith(compareBy({ it.id.displayName }, { it.scope.ordinal }))
    }

    fun load(descriptor: DdlTemplateDescriptor): String? {
        return when (descriptor.scope) {
            DdlTemplateScope.BUILTIN -> {
                val resourcePath = "templates/ddl/${descriptor.id.relativePath}"
                classLoader.getResourceAsStream(resourcePath)?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }
            }
            DdlTemplateScope.PROJECT, DdlTemplateScope.GLOBAL -> {
                val file = templateFile(descriptor) ?: return null
                if (!file.exists()) return null
                file.readText(StandardCharsets.UTF_8)
            }
        }
    }

    fun save(descriptor: DdlTemplateDescriptor, content: String) {
        require(descriptor.scope != DdlTemplateScope.BUILTIN) { "无法覆盖内置模板" }
        val file = templateFile(descriptor)
            ?: throw IllegalStateException("无法确定模板保存路径，请确认已打开项目")
        file.parentFile?.mkdirs()
        file.writeText(content, StandardCharsets.UTF_8)
    }

    fun delete(descriptor: DdlTemplateDescriptor) {
        require(descriptor.scope != DdlTemplateScope.BUILTIN) { "无法删除内置模板" }
        templateFile(descriptor)?.takeIf { it.exists() }?.delete()
    }

    fun copyBuiltinTo(scope: DdlTemplateScope, id: DdlTemplateId): DdlTemplateDescriptor {
        require(scope != DdlTemplateScope.BUILTIN) { "复制目标必须是自定义模板" }
        val builtinDescriptor = DdlTemplateDescriptor(id, DdlTemplateScope.BUILTIN)
        val content = load(builtinDescriptor)
            ?: throw IllegalStateException("未找到内置模板 ${id.relativePath}")
        val targetDescriptor = DdlTemplateDescriptor(id, scope)
        save(targetDescriptor, content)
        return targetDescriptor
    }

    fun resolveFile(descriptor: DdlTemplateDescriptor): File? =
        when (descriptor.scope) {
            DdlTemplateScope.BUILTIN -> null
            DdlTemplateScope.PROJECT, DdlTemplateScope.GLOBAL -> templateFile(descriptor)
        }

    private fun templateFile(descriptor: DdlTemplateDescriptor): File? =
        when (descriptor.scope) {
            DdlTemplateScope.PROJECT -> projectPath?.let { File(it, ".lsi/templates/ddl/${descriptor.id.relativePath}") }
            DdlTemplateScope.GLOBAL -> System.getProperty("user.home")?.let { File(it, ".lsi/templates/ddl/${descriptor.id.relativePath}") }
            DdlTemplateScope.BUILTIN -> null
        }

    private fun readScope(dir: File, scope: DdlTemplateScope): List<DdlTemplateDescriptor> {
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        val descriptors = mutableListOf<DdlTemplateDescriptor>()
        dir.listFiles { file -> file.isDirectory }?.forEach { dialectDir ->
            val dialect = site.addzero.util.db.DatabaseType.entries.find { it.templateDir == dialectDir.name } ?: return@forEach
            dialectDir.listFiles { file -> file.isFile && file.extension == "ftl" }?.forEach { templateFile ->
                val operation = DdlOperationType.entries.find { it.templateName == templateFile.nameWithoutExtension }
                    ?: return@forEach
                descriptors += DdlTemplateDescriptor(DdlTemplateId(dialect, operation), scope)
            }
        }
        return descriptors
    }
}
