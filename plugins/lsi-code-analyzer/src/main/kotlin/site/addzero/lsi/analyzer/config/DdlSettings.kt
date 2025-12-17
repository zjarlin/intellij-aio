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
     * 是否启用文件变化自动检测
     */
    var enableFileChangeDetection: Boolean = true

    // JDBC 连接配置
    var jdbcUrl: String? = null
    var jdbcUsername: String? = null
    var jdbcPassword: String? = null
    // 注意：jdbcDialect 会自动从 URL 推断，不需要手动设置
    @Deprecated("Dialect is automatically inferred from URL", level = DeprecationLevel.WARNING)
    var jdbcDialect: String? = null

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