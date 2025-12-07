package site.addzero.autoddl.jimmer.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Jimmer DDL 插件配置
 */
@State(
    name = "JimmerDdlSettings",
    storages = [Storage("jimmer-ddl.xml")]
)
@Service(Service.Level.PROJECT)
class JimmerDdlSettings : PersistentStateComponent<JimmerDdlSettings> {
    
    /**
     * DDL 输出目录（相对于项目根目录）
     */
    var outputDirectory: String = ".autoddl/jimmer"
    
    /**
     * 是否自动执行生成的 DDL
     */
    var autoExecute: Boolean = false
    
    /**
     * 执行前是否需要确认
     */
    var confirmBeforeExecute: Boolean = true
    
    /**
     * 是否生成回滚SQL
     */
    var generateRollback: Boolean = true
    
    /**
     * 选择的数据源名称（从 Spring 配置自动解析的多数据源中选择）
     */
    var selectedDataSourceName: String = ""
    
    /**
     * 是否包含索引
     */
    var includeIndexes: Boolean = true
    
    /**
     * 是否包含外键
     */
    var includeForeignKeys: Boolean = true
    
    /**
     * 是否包含注释
     */
    var includeComments: Boolean = true
    
    /**
     * 扫描的包路径（多个用逗号分隔）
     * 默认为空，表示扫描整个项目
     */
    var scanPackages: String = ""

    // ========== 手动 JDBC 配置（当无法自动解析 Spring 配置时使用） ==========
    
    /**
     * 手动配置的 JDBC URL
     * 优先级高于自动解析
     */
    var manualJdbcUrl: String = ""
    
    /**
     * 手动配置的 JDBC 用户名
     */
    var manualJdbcUsername: String = ""
    
    /**
     * 手动配置的 JDBC 密码
     */
    var manualJdbcPassword: String = ""
    
    override fun getState(): JimmerDdlSettings = this
    
    override fun loadState(state: JimmerDdlSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }
    
    companion object {
        fun getInstance(project: Project): JimmerDdlSettings {
            return project.service<JimmerDdlSettings>()
        }
    }
}
