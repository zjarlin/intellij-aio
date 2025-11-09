package site.addzero.ide.config.example

import site.addzero.ide.config.annotation.*
import site.addzero.ide.config.model.InputType

/**
 * 数据库配置类
 */
@SettingRoute("基础配置")
@Configurable
data class DatabaseConfig(
    @ConfigField(
        label = "数据库主机",
        description = "数据库服务器地址"
    )
    val host: String = "localhost",

    @ConfigField(
        label = "数据库端口",
        description = "数据库服务端口",
        inputType = InputType.NUMBER
    )
    val port: Int = 3306,

    @ConfigField(
        label = "用户名",
        description = "数据库连接用户名",
        required = true
    )
    val username: String = "",

    @ConfigField(
        label = "密码",
        description = "数据库连接密码",
        inputType = InputType.PASSWORD
    )
    val password: String = "",

    @ConfigSelect(
        label = "数据库类型",
        description = "选择数据库类型",
        optionsValue = ["mysql", "postgresql", "oracle"],
        optionsLabel = ["MySQL", "PostgreSQL", "Oracle"]
    )
    val databaseType: String = "mysql",

    @ConfigCheckbox(
        label = "启用SSL连接",
        description = "是否使用SSL加密连接"
    )
    val useSSL: Boolean = false,

    @ConfigList(
        label = "表列表",
        description = "需要处理的数据库表名列表"
    )
    val tables: List<String> = listOf(),

    @ConfigTable(
        label = "连接配置",
        description = "多个数据库连接配置"
    )
    val connections: List<ConnectionConfig> = listOf(),

    @ConfigConditional(
        label = "数据库Schema",
        description = "数据库Schema名称",
        conditionField = "DatabaseConfig.databaseType",
        conditionOperator = "EQUALS",
        conditionValue = "postgresql"
    )
    @ConfigField
    val schema: String = ""
)

/**
 * 常用配置类
 */
@SettingRoute("基础配置")
@Configurable
data class UsefulConfig(
    @ConfigField(
        label = "超时时间",
        description = "请求超时时间（毫秒）",
        inputType = InputType.NUMBER
    )
    val timeout: Int = 5000,

    @ConfigCheckbox(
        label = "调试模式",
        description = "是否启用调试模式"
    )
    val debugMode: Boolean = false,

    @ConfigSelect(
        label = "日志级别",
        description = "设置日志输出级别",
        optionsValue = ["DEBUG", "INFO", "WARN", "ERROR"],
        optionsLabel = ["调试", "信息", "警告", "错误"]
    )
    val logLevel: String = "INFO"
)

/**
 * 性能配置类
 */
@SettingRoute("高级配置")
@Configurable
data class PerformanceConfig(
    @ConfigField(
        label = "最大线程数",
        description = "应用程序最大线程数",
        inputType = InputType.NUMBER
    )
    val maxThreads: Int = 10,

    @ConfigField(
        label = "缓冲区大小",
        description = "缓冲区大小（字节）",
        inputType = InputType.NUMBER
    )
    val bufferSize: Int = 1024,

    @ConfigCheckbox(
        label = "启用缓存",
        description = "是否启用缓存机制"
    )
    val enableCache: Boolean = true
)

/**
 * 连接配置数据类
 */
data class ConnectionConfig(
    @ConfigField(
        label = "连接名称",
        description = "连接的标识名称"
    )
    val name: String = "",

    @ConfigField(
        label = "连接URL",
        description = "数据库连接URL"
    )
    val url: String = "",

    @ConfigSelect(
        label = "驱动类",
        description = "数据库驱动类名",
        optionsValue = ["com.mysql.cj.jdbc.Driver", "org.postgresql.Driver", "oracle.jdbc.driver.OracleDriver"],
        optionsLabel = ["MySQL Driver", "PostgreSQL Driver", "Oracle Driver"]
    )
    val driver: String = "com.mysql.cj.jdbc.Driver"
)