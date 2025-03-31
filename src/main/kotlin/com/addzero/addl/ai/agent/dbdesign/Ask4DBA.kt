
import com.addzero.addl.FieldDTO
import com.addzero.addl.FormDTO
import com.addzero.addl.ai.util.ai.AiUtil
import com.addzero.addl.ai.util.ai.Promt.DBA
import com.addzero.addl.ktututil.parseObject
import com.addzero.common.kt_util.isNull


data class Qwendto(
    val model: String,
    val messages: List<MyMessage>,
)


data class MyMessage(
    val role: String = "",
    val content: String = "",
)


fun quesDba(question: String): FormDTO? {
    if (question.isBlank()) {
        return defaultdTO()
    }
    try {
        val init = AiUtil.INIT(question, DBA)
        val dbask = init.ask(FormDTO::class.java)
        val parseObject1 = dbask.parseObject(FormDTO::class.java)

        if (parseObject1.isNull()&&dbask.isNotBlank()) {
            throw RuntimeException("解析出错了但AI原始回答Json为：$dbask")
        }

        return parseObject1
    } catch (e: Exception) {
//        showErrorMsg(e.message.toString())
        throw e
        return defaultdTO()
    }
}

fun defaultdTO(): FormDTO {
    val fieldDTO = FieldDTO("String", "字段名", "字段注释")
    return FormDTO("示例表名", "示例英文名", "示例数据库类型", "示例数据库名称", listOf(fieldDTO))
}