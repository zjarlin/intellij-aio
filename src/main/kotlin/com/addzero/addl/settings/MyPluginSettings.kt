package com.addzero.addl.settings

import com.addzero.addl.autoddlstarter.generator.consts.MYSQL
import com.addzero.addl.autoddlstarter.generator.consts.QWEN_1_5B_CODER

data class MyPluginSettings(
    var modelKey: String = "",
    var modelType: String = QWEN_1_5B_CODER,
    var dbType: String = MYSQL
)