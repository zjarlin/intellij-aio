package site.addzero.aiannotator.settings

data class AiAnnotatorSettings(
    // AI 配置
    @JvmField var aiProvider: String = "DeepSeek",
    @JvmField var aiApiKey: String = "",
    @JvmField var aiModel: String = "deepseek-chat",
    @JvmField var aiBaseUrl: String = "https://api.deepseek.com",
    @JvmField var temperature: Double = 0.3,
    
    // 注解模板配置
    @JvmField var swaggerAnnotation: String = "@Schema(description = \"{}\")",
    @JvmField var excelAnnotation: String = "@ExcelProperty(\"{}\")",
    @JvmField var customAnnotation: String = "@ApiModelProperty(value = \"{}\")",
    
    // 功能开关
    @JvmField var enableAiGuessing: Boolean = true,
    @JvmField var enableBatchProcessing: Boolean = true
)
