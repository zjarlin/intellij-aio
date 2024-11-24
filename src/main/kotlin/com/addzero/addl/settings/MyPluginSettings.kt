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


data class MyPluginSettings(

    @ConfigField(label = "模型Key") @JvmField var modelKey: String = "",
    @ConfigField(
        label = "模型厂商",
        type = FieldType.DROPDOWN,
        options = [
            DASH_SCOPE,
            OLLAMA,
//            KIMI_MOONSHOT,
//            OPENAI,
//            ZHIPU,
        ]
    ) @JvmField var modelManufacturer: String = DASH_SCOPE,
//    @ConfigField(label = "模型名称", type = FieldType.DROPDOWN, dependsOn = "modelManufacturer", predicateClass = CountryStatePredicate::class)
    @ConfigField(
        label = "在线模型",
        type = FieldType.DROPDOWN,
        options = [
            QWEN_TURBO, QWEN_1_5B_INSTRUCT, QWEN_1_5B_CODER_INSTRUCT, QWEN_MAX,
        ]
    ) @JvmField var modelNameOnline: String = QWEN_1_5B_CODER_INSTRUCT,


    @ConfigField(label = "ollama远程url") @JvmField var ollamaUrl: String = "http://localhost:11434",
    @ConfigField(
        label = "离线ollama模型",
        type = FieldType.DROPDOWN,
        options = [QWEN2_5_CODER_0_5B, QWEN2_5_1_5B, QWEN2_5_CODER_1_5B]
    ) @JvmField var modelNameOffline: String = QWEN2_5_CODER_1_5B,

//    @ConfigField(
//        label = "温度(0<=Temperature < =1)"
//    )
    @JvmField var temPerature: String = "0.4",


    @ConfigField(
        label = "数据库类型", type = FieldType.DROPDOWN, options = [MYSQL, ORACLE, POSTGRESQL, DM]
    ) @JvmField var dbType: String = MYSQL,



    @ConfigField(label = "同步扫包路径") @JvmField var scanPkg: String = "/",

    ) {

}