package site.addzero.ide.settings.example

import site.addzero.ide.dynamicform.annotation.*

/**
 * 示例配置
 * 
 * 展示如何使用动态表单注解定义设置
 */
@FormConfig(
    title = "示例设置",
    description = "这是一个完整的配置示例"
)
@FormGroups(
    groups = [
        FormGroup(name = "basic", title = "基础设置", order = 1),
        FormGroup(name = "advanced", title = "高级设置", order = 2),
        FormGroup(name = "network", title = "网络设置", order = 3)
    ]
)
data class ExampleSettings(
    @TextField(
        label = "应用名称",
        group = "basic",
        order = 1,
        required = true,
        placeholder = "请输入应用名称",
        description = "应用的显示名称"
    )
    @JvmField var appName: String = "",
    
    @ComboBox(
        label = "日志级别",
        group = "basic",
        order = 2,
        options = ["DEBUG", "INFO", "WARN", "ERROR"],
        description = "选择日志输出级别"
    )
    @JvmField var logLevel: String = "INFO",
    
    @CheckBox(
        label = "启用自动保存",
        group = "basic",
        order = 3,
        description = "自动保存工作进度"
    )
    @JvmField var autoSave: Boolean = true,
    
    @NumberField(
        label = "端口号",
        group = "advanced",
        order = 1,
        required = true,
        min = 1024.0,
        max = 65535.0,
        description = "服务监听端口"
    )
    @JvmField var port: Int = 8080,
    
    @PasswordField(
        label = "管理员密码",
        group = "advanced",
        order = 2,
        required = true,
        description = "管理员账户密码"
    )
    @JvmField var adminPassword: String = "",
    
    @TextArea(
        label = "备注",
        group = "advanced",
        order = 3,
        rows = 5,
        description = "其他说明信息"
    )
    @JvmField var notes: String = "",
    
    @TextField(
        label = "代理服务器",
        group = "network",
        order = 1,
        placeholder = "http://proxy.example.com:8080",
        description = "HTTP代理服务器地址"
    )
    @JvmField var proxyServer: String = "",
    
    @NumberField(
        label = "超时时间(秒)",
        group = "network",
        order = 2,
        min = 1.0,
        max = 300.0,
        description = "网络请求超时时间"
    )
    @JvmField var timeout: Int = 30
)
