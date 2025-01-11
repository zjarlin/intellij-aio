package com.addzero.addl.settings

import com.addzero.addl.ai.consts.ChatModels.DASH_SCOPE
import com.addzero.addl.ai.consts.ChatModels.OLLAMA
import com.addzero.addl.ai.consts.ChatModels.QWEN2_5_1_5B
import com.addzero.addl.ai.consts.ChatModels.QWEN2_5_CODER_0_5B
import com.addzero.addl.ai.consts.ChatModels.QWEN2_5_CODER_1_5B
import com.addzero.addl.ai.consts.ChatModels.QWEN_1_5B_CODER_INSTRUCT
import com.addzero.addl.ai.consts.ChatModels.QWEN_1_5B_INSTRUCT
import com.addzero.addl.ai.consts.ChatModels.QWEN_MAX
import com.addzero.addl.ai.consts.ChatModels.QWEN_TURBO
import com.addzero.addl.autoddlstarter.generator.consts.DM
import com.addzero.addl.autoddlstarter.generator.consts.MYSQL
import com.addzero.addl.autoddlstarter.generator.consts.ORACLE
import com.addzero.addl.autoddlstarter.generator.consts.POSTGRESQL
import com.intellij.openapi.components.State

@SettingsGroup(
    groups = [Group(name = "ai", title = "AI模型配置", order = 1), Group(name = "db", title = "数据库配置", order = 2), Group(name = "dict", title = "字典配置", order = 3)]
)
//@State(
//    name = "AutoDDLSettings",
//    storages = [Storage("AutoDDLSettings.xml")]
//)
data class MyPluginSettings(
    // AI模型配置组
    @ConfigField(label = "模型Key", group = "ai", order = 1) @JvmField var modelKey: String = "",

    @ConfigField(
        label = "模型厂商", type = FieldType.DROPDOWN, options = [DASH_SCOPE, OLLAMA], group = "ai", order = 2
    ) @JvmField var modelManufacturer: String = DASH_SCOPE,

    @ConfigField(
        label = "在线模型", type = FieldType.DROPDOWN, options = [QWEN_TURBO, QWEN_1_5B_INSTRUCT, QWEN_1_5B_CODER_INSTRUCT, QWEN_MAX], group = "ai", order = 3
    ) @JvmField var modelNameOnline: String = QWEN_1_5B_CODER_INSTRUCT,

    @ConfigField(label = "ollama远程url", group = "ai", order = 4) @JvmField var ollamaUrl: String = "http://localhost:11434",

    @ConfigField(
        label = "离线ollama模型", type = FieldType.DROPDOWN, options = [QWEN2_5_CODER_0_5B, QWEN2_5_1_5B, QWEN2_5_CODER_1_5B], group = "ai", order = 5
    ) @JvmField var modelNameOffline: String = QWEN2_5_CODER_1_5B,

    @JvmField var temPerature: String = "0.4",

    // 数据库配置组
    @ConfigField(
        label = "数据库类型", type = FieldType.DROPDOWN, options = [MYSQL, ORACLE, POSTGRESQL, DM], group = "db", order = 1
    ) @JvmField var dbType: String = MYSQL,

    @ConfigField(label = "规范id", group = "db", order = 2) @JvmField var id: String = "id",

    @ConfigField(label = "规范id类型", group = "db", order = 3) @JvmField var idType: String = "BIGINT",

    @ConfigField(label = "规范create_by", group = "db", order = 4) @JvmField var createBy: String = "create_by",

    @ConfigField(label = "规范update_by", group = "db", order = 5) @JvmField var updateBy: String = "update_by",

    @ConfigField(label = "规范create_time", group = "db", order = 6) @JvmField var createTime: String = "create_time",

    @ConfigField(label = "规范update_time", group = "db", order = 7) @JvmField var updateTime: String = "update_time",

    // 字典配置组
    @ConfigField(label = "枚举生成的包路径", group = "dict", order = 1) @JvmField var enumPkg: String = "./",

    @ConfigField(label = "规范枚举表名称", group = "dict", order = 2) @JvmField var dictTableName: String = "sys_dict",

    @ConfigField(label = "规范枚举表id", group = "dict", order = 3) @JvmField var did: String = "id",

    @ConfigField(label = "规范枚举表分组编码", group = "dict", order = 4) @JvmField var dcode: String = "dict_code",

    @ConfigField(label = "规范枚举表分组名称", group = "dict", order = 5) @JvmField var ddes: String = "dict_name",

    @ConfigField(label = "规范枚举项表名称", group = "dict", order = 6) @JvmField var itemTableName: String = "sys_dict_item",

    @ConfigField(label = "规范枚举项外键", group = "dict", order = 7) @JvmField var exdictid: String = "dict_id",

    @ConfigField(label = "规范枚举项code", group = "dict", order = 8) @JvmField var icode: String = "item_value",

    @ConfigField(label = "规范枚举项name", group = "dict", order = 9) @JvmField var ides: String = "item_text",

    @ConfigField(
        label = "枚举项注解模板(默认jimmer)", type = FieldType.LONG_TEXT, group = "dict", order = 10
    ) @JvmField var enumAnnotation: String = "@EnumItem(name = \"{}\") ",

//    @ConfigField(
//        label = "枚举分隔符",
//        type = FieldType.TEXT,
//        order = 50,
//        group = "enum"
//    )
//    var enumSeparator: String = "-",

    @ConfigField(
        label = "枚举模板",
        group = "enum",
        order = 20,
        type = FieldType.LONG_TEXT
    )
    var enumTemplate: String = defaultEnumTemplate
) {
    companion object {
        private const val defaultEnumTemplate = """
// Java模板:
/*
package ${'$'}{packageName};

/**
 * 自动生成的枚举类
 */
public enum ${'$'}{enumName} {
${'$'}{enumValues}
    ;
${'$'}{codeField}
}
*/

// Kotlin模板:
/*
package ${'$'}{packageName}

/**
 * 自动生成的枚举类
 */
enum class ${'$'}{enumName}${'$'}{constructor} {
${'$'}{enumValues}
${'$'}{codeField}
}
*/"""
    }
}