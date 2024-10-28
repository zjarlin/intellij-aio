import cn.hutool.core.util.StrUtil
import cn.hutool.http.HttpRequest
import com.addzero.addl.FieldDTO
import com.addzero.addl.FormDTO
import com.addzero.addl.ai.agent.dbdesign.getDbaPromtTemplate
import com.addzero.addl.ai.consts.ChatModels.OLLAMA
import com.addzero.addl.ktututil.parseObject
import com.addzero.addl.ktututil.toJson
import com.addzero.addl.settings.MyPluginSettingsService
import com.addzero.addl.util.Dba
import com.addzero.addl.util.JlStrUtil.extractMarkdownBlockContent
import com.addzero.addl.util.ShowSqlUtil.showErrorMsg
import com.addzero.ai.modules.ai.util.ai.ai_abs_builder.AiUtil


data class Qwendto(
    val model: String,
    val messages: List<MyMessage>,
)


data class MyMessage(
    val role: String = "",
    val content: String = "",
)

fun getdashScopeResponse(question: String, prompt: String): String? {
    val settings = MyPluginSettingsService.getInstance().state
// 修改设置项
    val getenvBySetting = settings.modelKey
    // 构建请求内容
    val model = settings.modelNameOnline

    val getenvBySys = System.getenv("DASHSCOPE_API_KEY")
    val apiKey = StrUtil.firstNonBlank(getenvBySetting, getenvBySys)
    if (apiKey.isBlank()) {
        throw RuntimeException("请设置环境变量 DASHSCOPE_API_KEY")
    }
    val baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
    val qwendto = Qwendto(model, listOf(MyMessage("system", prompt), MyMessage("user", question)))
    val toJson = qwendto.toJson()


//    val requestBody = """
//        {
//          "model": "$model",
//          "messages": [
//            {"role": "system", "content": "$prompt"},
//            {"role": "user", "content": "$question"}
//          ]
//        }
//    """.trimIndent()

    // 发送POST请求，包含Authorization和Content-Type头
    val response = HttpRequest.post(baseUrl).header("Authorization", "Bearer $apiKey")  // 设置Authorization头
        .header("Content-Type", "application/json")  // 设置Content-Type头
        .body(toJson)  // 设置请求体
        .execute()
    // 返回响应内容
    return response.body()
}

private fun dbask(question: String): String? {
    val promtTempla = getDbaPromtTemplate()


    val response = getdashScopeResponse(
        question, promtTempla
    )
    return response
}


fun quesDba(question: String): FormDTO? {
    if (question.isBlank()) {
        return defaultdTO()
    }
    try {
        val settings = MyPluginSettingsService.getInstance().state
// 修改设置项
        val getenvBySetting = settings.ollamaUrl
        val equals = settings.modelManufacturer == OLLAMA
        val enableOllama =
//         !StrUtil.equals(getenvBySetting, "http://localhost:11434")||
        equals
        if (enableOllama) {
            val ollamaModelName = settings.modelNameOffline
            var promtTempla = getDbaPromtTemplate()
            promtTempla = StrUtil.cleanBlank(promtTempla)
            //启用ollama
            val aiUtil = AiUtil(ollamaModelName, question, promtTempla).ask(FormDTO::class.java)
//            val aiUtil = AiUtil(ollamaModelName, "你好", "").ask(FormDTO::class.java)
            return aiUtil
        }
        val dbask = dbask(question)
        val parseObject = dbask?.parseObject(Dba::class.java)

        val joinToString = parseObject?.choices?.map {
            val content = it?.message?.content
            content
        }?.joinToString()
        val let = joinToString?.let { extractMarkdownBlockContent(it) }
        val parseObject1 = let?.parseObject(FormDTO::class.java) ?: return defaultdTO()
        return parseObject1
    } catch (e: Exception) {
        showErrorMsg(e.message.toString())
        return defaultdTO()
    }
}

fun defaultdTO(): FormDTO {
    val fieldDTO = FieldDTO("String", "字段名", "字段注释")
    return FormDTO("示例表名", "示例英文名", "示例数据库类型", "示例数据库名称", listOf(fieldDTO))
}