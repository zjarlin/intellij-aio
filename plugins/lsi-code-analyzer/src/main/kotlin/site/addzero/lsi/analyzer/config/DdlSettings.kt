package site.addzero.lsi.analyzer.config

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import java.nio.file.Path
import java.nio.file.Paths

/**
 * DDL 生成设置配置
 *
 * 持久化存储用户的 DDL 生成偏好设置
 */
@Service
@State(name = "DdlSettings", storages = [Storage("DdlSettings.xml")])
class DdlSettings : PersistentStateComponent<DdlSettings> {

    /**
     * DDL 文件保存目录路径
     * 支持变量: {projectDir} - 项目根目录, {entityName} - 实体名称
     */
    var ddlSaveDirectory: String = "{projectDir}/.autoddl/{entityName}"

    /**
     * 是否自动保存 DDL 到文件
     */
    var autoSaveDdl: Boolean = false

    /**
     * DDL 文件名模板（遵循 Flyway 命名规范）
     * 可用变量: {table}, {dialect}, {timestamp}, {version}
     * 默认格式: V{timestamp}__Create_{table}_{dialect}.sql
     */
    var ddlFileNameTemplate: String = "V{timestamp}__Create_{table}_{dialect}.sql"

    /**
     * 是否在生成后打开文件
     */
    var openFileAfterGeneration: Boolean = true

    /**
     * 获取动态生成的保存目录（支持变量替换）
     * @param entityName 实体名称
     * @param projectBasePath 项目根目录
     * @return 完整的保存路径
     */
    fun getDynamicSaveDirectory(entityName: String, projectBasePath: String): Path {
        val resolvedPath = ddlSaveDirectory
            .replace("{projectDir}", projectBasePath)
            .replace("{entityName}", entityName)
        return Paths.get(resolvedPath)
    }

    /**
     * 设置保存目录
     */
    fun setSaveDirectory(path: String) {
        ddlSaveDirectory = path
    }

    /**
     * 生成文件名
     */
    fun generateFileName(tableName: String, dialect: String): String {
        val timestamp = System.currentTimeMillis().toString()
        val version = generateVersionNumber()

        return ddlFileNameTemplate
            .replace("{table}", tableName)
            .replace("{dialect}", dialect.lowercase())
            .replace("{timestamp}", timestamp)
            .replace("{version}", version)
    }

    /**
     * 生成版本号（基于时间戳的格式化版本）
     * 格式: YYYYMMDDHHMM
     */
    private fun generateVersionNumber(): String {
        val now = java.time.LocalDateTime.now()
        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmm")
        return now.format(formatter)
    }

    override fun getState(): DdlSettings = this

    override fun loadState(state: DdlSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): DdlSettings {
            return ApplicationManager.getApplication().getService(DdlSettings::class.java)
        }
    }
}