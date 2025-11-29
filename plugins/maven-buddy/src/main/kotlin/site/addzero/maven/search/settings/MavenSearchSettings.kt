package site.addzero.maven.search.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Maven 搜索插件设置
 */
@State(
    name = "MavenSearchSettings",
    storages = [Storage("MavenSearchPlugin.xml")]
)
class MavenSearchSettings : PersistentStateComponent<MavenSearchSettings> {

    /**
     * 每页结果数量
     */
    var pageSize: Int = 50

    /**
     * 是否启用分页搜索
     */
    var enablePagination: Boolean = true

    /**
     * 是否自动复制到剪贴板
     */
    var autoCopyToClipboard: Boolean = true

    /**
     * 搜索超时时间（秒）
     */
    var searchTimeout: Int = 10

    /**
     * 防抖延迟时间（毫秒）
     */
    var debounceDelay: Int = 500

    /**
     * 是否需要手动确认才触发搜索
     */
    var requireManualTrigger: Boolean = false

    override fun getState(): MavenSearchSettings = this

    override fun loadState(state: MavenSearchSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        /**
         * 全局配置目录
         */
        val configDir: String
            get() = "${System.getProperty("user.home")}/.config/maven-buddy"

        fun getInstance(): MavenSearchSettings {
            return ApplicationManager.getApplication()
                .getService(MavenSearchSettings::class.java)
        }
    }
}
