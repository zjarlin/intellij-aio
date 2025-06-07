package com.addzero.addl.settings

import com.addzero.addl.ai.consts.ChatModels.CODEGEMMA
import com.addzero.addl.ai.consts.ChatModels.DASH_SCOPE
import com.addzero.addl.ai.consts.ChatModels.DEEPSEEK_R1
import com.addzero.addl.ai.consts.ChatModels.DeepSeek
import com.addzero.addl.ai.consts.ChatModels.DeepSeekOnlineModel
import com.addzero.addl.ai.consts.ChatModels.DeepSeekOnlineModelCoder
import com.addzero.addl.ai.consts.ChatModels.OLLAMA
import com.addzero.addl.ai.consts.ChatModels.QWEN2_5_1_5B
import com.addzero.addl.ai.consts.ChatModels.QWEN2_5_CODER_0_5B
import com.addzero.addl.ai.consts.ChatModels.QWEN2_5_CODER_1_5B
import com.addzero.addl.ai.consts.ChatModels.QWEN_1_5B_CODER_INSTRUCT
import com.addzero.addl.ai.consts.ChatModels.QWEN_1_5B_INSTRUCT
import com.addzero.addl.ai.consts.ChatModels.QWEN_MAX
import com.addzero.addl.ai.consts.ChatModels.QWEN_TURBO
import com.addzero.addl.ai.consts.ChatModels.QWQ
import com.addzero.addl.autoddlstarter.generator.consts.*

@SettingsGroup(
    groups = [Group(name = "ai", title = "AI模型配置", order = 1), Group(
        name = "template", title = "模板配置", order = 2
    ), Group(name = "db", title = "数据库配置", order = 3), Group(name = "dict", title = "字典配置", order = 4), Group(
        name = "intention", title = "意图配置", order = 5
    )]
)
//@State(
//    name = "AutoDDLSettings",
//    storages = [Storage("AutoDDLSettings.xml")]
//)
data class MyPluginSettings(
    // AI模型配置组
    @ConfigField(
        label = "控制器模板风格",
        type = FieldType.DROPDOWN,
        options = ["INHERITANCE", "STANDALONE"],
        group = "template",
        order = 1
    ) @JvmField var controllerStyle: String = "STANDALONE",

    @ConfigField(label = "模型Key", group = "ai", order = 1) @JvmField var modelKey: String = "",

    @ConfigField(
        label = "模型厂商", type = FieldType.DROPDOWN, options = [DASH_SCOPE, OLLAMA, DeepSeek], group = "ai", order = 2
    ) @JvmField var modelManufacturer: String = DeepSeek,

    @ConfigField(
        label = "在线模型",
        type = FieldType.DROPDOWN,
        options = [QWEN_TURBO, QWEN_1_5B_INSTRUCT, QWEN_1_5B_CODER_INSTRUCT, QWEN_MAX, DeepSeekOnlineModel, DeepSeekOnlineModelCoder],
        group = "ai",
        order = 3
    ) @JvmField var modelNameOnline: String = DeepSeekOnlineModel,

    @ConfigField(
        label = "ollama远程url", group = "ai", order = 4
    ) @JvmField var ollamaUrl: String = "http://localhost:11434",

    @ConfigField(
        label = "离线ollama模型",
        type = FieldType.DROPDOWN,
        options = [QWEN2_5_CODER_0_5B, QWEN2_5_1_5B, QWEN2_5_CODER_1_5B, CODEGEMMA, DEEPSEEK_R1, QWQ],
        group = "ai",
        order = 5
    ) @JvmField var modelNameOffline: String = DEEPSEEK_R1,

    @JvmField var temPerature: String = "0.4",

//    @ConfigField(
//        label = "模块路径(单模块不填,多模块例  backend/service)", group = "db", order = 0
//    )
//    @JvmField
//    var modulePath: String = "",

    @ConfigField(label = "flayway文件保存路径", group = "db", order = 0) @JvmField
    var flaywayPath: String = "src/main/resources/db/migration/autoddl",

    @ConfigField(label = "ddl元数据保存路径", group = "db", order = 0)
    @JvmField
    var entityDdlContextMetaJsonPath: String = "src/main/resources/db/meta",

    // 数据库配置组
    @ConfigField(
        label = "数据库类型",
        type = FieldType.DROPDOWN,
        options = [MYSQL, ORACLE, POSTGRESQL, DM, H2],
        group = "db",
        order = 1
    ) @JvmField var dbType: String = MYSQL,

    @ConfigField(label = "规范id", group = "db", order = 2) @JvmField var id: String = "id",

    @ConfigField(label = "规范id类型", group = "db", order = 3) @JvmField var idType: String = "BIGINT",

    @ConfigField(label = "规范create_by", group = "db", order = 4) @JvmField var createBy: String = "create_by",

    @ConfigField(label = "规范update_by", group = "db", order = 5) @JvmField var updateBy: String = "update_by",

    @ConfigField(label = "规范create_time", group = "db", order = 6) @JvmField var createTime: String = "create_time",

    @ConfigField(label = "规范update_time", group = "db", order = 7) @JvmField var updateTime: String = "update_time",

    // 字典配置组
    @ConfigField(label = "枚举生成的包路径", group = "dict", order = 1) @JvmField var enumPkg: String = "./",

//    @ConfigField(label = "规范枚举表名称", group = "dict", order = 2) @JvmField var dictTableName: String = "sys_dict",
//
//    @ConfigField(label = "规范枚举表id", group = "dict", order = 3) @JvmField var did: String = "id",
//
//    @ConfigField(label = "规范枚举表分组编码", group = "dict", order = 4) @JvmField var dcode: String = "dict_code",
//
//    @ConfigField(label = "规范枚举表分组名称", group = "dict", order = 5) @JvmField var ddes: String = "dict_name",
//
//    @ConfigField(label = "规范枚举项表名称", group = "dict", order = 6) @JvmField var itemTableName: String = "sys_dict_item",
//
//    @ConfigField(label = "规范枚举项外键", group = "dict", order = 7) @JvmField var exdictid: String = "dict_id",
//
//    @ConfigField(label = "规范枚举项code", group = "dict", order = 8) @JvmField var icode: String = "item_value",
//
//    @ConfigField(label = "规范枚举项name", group = "dict", order = 9) @JvmField var ides: String = "item_text",

    @ConfigField(
        label = "枚举项注解模板(默认jimmer)"
//        , type = FieldType.TEXT

        , type = FieldType.DROPDOWN, options = [JimmerAnno, ""], group = "dict", order = 10
    ) @JvmField var enumAnnotation: String = JimmerAnno,


    @ConfigField(
        label = "swagger意图注解(默认swagger3)",
//        , type = FieldType.TEXT
        group = "intention", type = FieldType.DROPDOWN, options = [Swagger3Anno, Swagger2Anno], order = 1
    ) @JvmField var swaggerAnnotation: String = Swagger3Anno,


    @ConfigField(
        label = "excel意图注解(默认fastexcel/easyexcel)",
//        , type = FieldType.TEXT
        group = "intention", type = FieldType.DROPDOWN, options = [FastExcelAnno, PoiAnno], order = 2
    ) @JvmField var excelAnnotation: String = FastExcelAnno,


    @ConfigField(
        label = "自定义意图注解(注释元数据生成相应注解)",
//        , type = FieldType.TEXT
        group = "intention", type = FieldType.TEXT, order = 3
    ) @JvmField var customAnnotation: String = CustomAnno,

    @ConfigField(
        label = "垃圾代码注解",
        group = "intention",
        type = FieldType.TEXT,
        order = 4
    ) @JvmField var shitAnnotation: String = "Shit",


    ) {
}
