package com.addzero.addl.settings

import Description
import FieldType
import com.addzero.addl.autoddlstarter.generator.consts.DM
import com.addzero.addl.autoddlstarter.generator.consts.MYSQL
import com.addzero.addl.autoddlstarter.generator.consts.ORACLE
import com.addzero.addl.autoddlstarter.generator.consts.POSTGRESQL

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


    @field:Description(
        "默认的数据库类型(在右键菜单上下文中,增加列语句会用到数据库类型,默认是MySQL)",
        defaultValue = MYSQL,
        type = FieldType.DROPDOWN,
        options = [
            MYSQL,
            ORACLE,
            POSTGRESQL,
            DM
        ]
    )
    var defaultDbType: String = "",
)