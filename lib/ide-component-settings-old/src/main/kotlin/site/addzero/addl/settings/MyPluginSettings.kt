package site.addzero.addl.settings

import site.addzero.addl.consts.*
import site.addzero.ide.dynamicform.annotation.*

@FormConfig(
    title = "AutoDDL设置",
    description = "配置AutoDDL插件的各项参数"
)
@FormGroups(
    groups = [
        FormGroup(name = "template", title = "模板配置", order = 1),
        FormGroup(name = "ai", title = "AI模型配置", order = 2),
        FormGroup(name = "db", title = "数据库配置", order = 3),
        FormGroup(name = "dict", title = "字典配置", order = 4),
        FormGroup(name = "intention", title = "意图配置", order = 5)
    ]
)
data class MyPluginSettings(
    @ComboBox(
        label = "控制器模板风格",
        group = "template",
        order = 1,
        options = ["INHERITANCE", "STANDALONE"],
        description = "选择控制器的代码生成风格"
    )
    @JvmField var controllerStyle: String = "STANDALONE",
    
    @TextField(
        label = "模型Key",
        group = "ai",
        order = 1,
        required = true,
        placeholder = "请输入API密钥",
        description = "AI服务的API密钥"
    )
    @JvmField var modelKey: String = "",
    
    @ComboBox(
        label = "模型厂商",
        group = "ai",
        order = 2,
        options = [DASH_SCOPE, OLLAMA, DeepSeek],
        description = "选择AI模型提供商"
    )
    @JvmField var modelManufacturer: String = DeepSeek,
    
    @ComboBox(
        label = "在线模型",
        group = "ai",
        order = 3,
        options = [QWEN_TURBO, QWEN_1_5B_INSTRUCT, QWEN_1_5B_CODER_INSTRUCT, QWEN_MAX, DeepSeekOnlineModel, DeepSeekOnlineModelCoder],
        description = "选择在线AI模型"
    )
    @JvmField var modelNameOnline: String = DeepSeekOnlineModel,
    
    @TextField(
        label = "Ollama远程URL",
        group = "ai",
        order = 4,
        placeholder = "http://localhost:11434",
        description = "Ollama服务的远程地址"
    )
    @JvmField var ollamaUrl: String = "http://localhost:11434",
    
    @ComboBox(
        label = "离线Ollama模型",
        group = "ai",
        order = 5,
        options = [QWEN2_5_CODER_0_5B, QWEN2_5_1_5B, QWEN2_5_CODER_1_5B, CODEGEMMA, DEEPSEEK_R1, QWQ],
        description = "选择本地Ollama模型"
    )
    @JvmField var modelNameOffline: String = DEEPSEEK_R1,
    
    @TextField(
        label = "温度参数",
        group = "ai",
        order = 6,
        placeholder = "0.4",
        description = "AI生成的随机性，0-1之间，值越大越随机"
    )
    @JvmField var temPerature: String = "0.4",
    
    @TextField(
        label = "Flyway文件保存路径",
        group = "db",
        order = 1,
        description = "DDL文件的保存位置",
        placeholder = "src/main/resources/db/migration/autoddl"
    )
    @JvmField var flaywayPath: String = "src/main/resources/db/migration/autoddl",
    
    @TextField(
        label = "DDL元数据保存路径",
        group = "db",
        order = 2,
        description = "实体元数据JSON文件的保存位置",
        placeholder = "src/main/resources/db/meta"
    )
    @JvmField var entityDdlContextMetaJsonPath: String = "src/main/resources/db/meta",
    
    @ComboBox(
        label = "数据库类型",
        group = "db",
        order = 3,
        options = ["mysql", "oracle", "pg", "dm", "h2", "tdengine"],
        description = "选择目标数据库类型"
    )
    @JvmField var dbType: String = "mysql",
    
    @TextField(
        label = "规范ID字段",
        group = "db",
        order = 4,
        placeholder = "id",
        description = "主键字段名称"
    )
    @JvmField var id: String = "id",
    
    @TextField(
        label = "规范ID类型",
        group = "db",
        order = 5,
        placeholder = "BIGINT",
        description = "主键字段的数据库类型"
    )
    @JvmField var idType: String = "BIGINT",
    
    @TextField(
        label = "规范创建人字段",
        group = "db",
        order = 6,
        placeholder = "create_by",
        description = "创建人字段名称"
    )
    @JvmField var createBy: String = "create_by",
    
    @TextField(
        label = "规范更新人字段",
        group = "db",
        order = 7,
        placeholder = "update_by",
        description = "更新人字段名称"
    )
    @JvmField var updateBy: String = "update_by",
    
    @TextField(
        label = "规范创建时间字段",
        group = "db",
        order = 8,
        placeholder = "create_time",
        description = "创建时间字段名称"
    )
    @JvmField var createTime: String = "create_time",
    
    @TextField(
        label = "规范更新时间字段",
        group = "db",
        order = 9,
        placeholder = "update_time",
        description = "更新时间字段名称"
    )
    @JvmField var updateTime: String = "update_time",
    
    @TextField(
        label = "枚举生成的包路径",
        group = "dict",
        order = 1,
        placeholder = "./",
        description = "生成枚举类的目标包路径"
    )
    @JvmField var enumPkg: String = "./",
    
    @ComboBox(
        label = "枚举项注解模板",
        group = "dict",
        order = 2,
        description = "选择枚举项的注解模板（默认Jimmer）",
        options = [JimmerAnno, ""]
    )
    @JvmField var enumAnnotation: String = JimmerAnno,
    
    @ComboBox(
        label = "Swagger意图注解",
        group = "intention",
        order = 1,
        description = "选择Swagger注解版本（默认Swagger3）",
        options = [Swagger3Anno, Swagger2Anno]
    )
    @JvmField var swaggerAnnotation: String = Swagger3Anno,
    
    @ComboBox(
        label = "Excel意图注解",
        group = "intention",
        order = 2,
        description = "选择Excel注解库（默认FastExcel/EasyExcel）",
        options = [FastExcelAnno, PoiAnno]
    )
    @JvmField var excelAnnotation: String = FastExcelAnno,
    
    @TextField(
        label = "自定义意图注解",
        group = "intention",
        order = 3,
        description = "从注释元数据生成相应注解的模板",
        placeholder = "@CustomAnno(\"{}\")"
    )
    @JvmField var customAnnotation: String = CustomAnno,
    
    @TextField(
        label = "垃圾代码注解",
        group = "intention",
        order = 4,
        description = "标记需要清理的代码",
        placeholder = "Shit"
    )
    @JvmField var shitAnnotation: String = "Shit"
)
