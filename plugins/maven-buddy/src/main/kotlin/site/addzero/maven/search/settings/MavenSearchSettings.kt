package site.addzero.maven.search.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import site.addzero.maven.search.DependencyFormat

/**
 * Maven 搜索插件设置
 */
@State(
    name = "MavenSearchSettings",
    storages = [Storage("MavenSearchPlugin.xml")]
)
class MavenSearchSettings : PersistentStateComponent<MavenSearchSettings> {

    /**
     * 依赖格式：Maven / Gradle Kotlin / Gradle Groovy
     */
    var dependencyFormat: DependencyFormat = DependencyFormat.GRADLE_KOTLIN

    /**
     * 搜索结果数量限制（已废弃，使用 pageSize）
     */
    @Deprecated("Use pageSize instead")
    var maxResults: Int = 20

    /**
     * 每页结果数量（增大以获取更多结果）
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
     * 建议值：300-800ms
     * - 300ms：快速响应，适合快速输入的用户
     * - 500ms：平衡选项（默认）
     * - 800ms：减少请求，适合网络较慢的环境
     */
    var debounceDelay: Int = 500

    /**
     * 是否需要手动确认才触发搜索
     * true：需要按 Enter 键才搜索
     * false：自动搜索（使用防抖）
     */
    var requireManualTrigger: Boolean = false

    /**
     * 历史记录存储路径
     * 默认: ~/.config/maven-buddy/history.json
     */
    var historyStoragePath: String = defaultHistoryPath()

    /**
     * 缓存存储路径
     * 默认: ~/.config/maven-buddy/cache.json
     */
    var cacheStoragePath: String = defaultCachePath()

    override fun getState(): MavenSearchSettings = this

    override fun loadState(state: MavenSearchSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): MavenSearchSettings {
            return ApplicationManager.getApplication()
                .getService(MavenSearchSettings::class.java)
        }

        private val configDir: String
            get() = "${System.getProperty("user.home")}/.config/maven-buddy"

        /**
         * 默认历史记录存储路径
         */
        fun defaultHistoryPath(): String = "$configDir/history.json"

        /**
         * 默认缓存存储路径
         */
        fun defaultCachePath(): String = "$configDir/cache.json"
    }
}
