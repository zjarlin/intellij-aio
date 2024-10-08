package com.addzero.addl.settings

import Description
import FieldType

data class Settings(
    @field:Description("*阿里模型API Key", "")
    var aliLingjiModelKey: String = "",
    @field:Description(
        "模型名称(可空,默认qwen2.5-1.5b-instruct),想体验更好请用付费模型",
        defaultValue = "qwen2.5-coder-1.5b-instruct",
        type = FieldType.DROPDOWN,
        options = ["qwen-turbo",
//            "qwen2.5-0.5b-instruct",
            "qwen2.5-1.5b-instruct",
            "qwen2.5-coder-1.5b-instruct",
//            "qwen2.5-3b-instruct",
            "qwen-max"]
    )
    var modelName: String = "",
)