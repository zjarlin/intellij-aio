package com.addzero.ai.modules.ai.consts

object Promts {
    val JSON_PATTERN_PROMPT =
        """您的响应应该是JSON格式。不包括任何解释，只提供符合RFC8259的JSON响应，遵循此格式，没有偏差。不要在响应中包含markdown代码块。从输出中删除``json标记。这是您的输出必须遵循的JSON模式实例："""

    val DEFAULT_SYSTEM: String = "你是一个友好的AI,请提供有效简短的回答.但是涉及代码回答," +
            "请根据我的上下文精准回答并给出思考过程," + "不要直接复制粘贴我的代码."



}