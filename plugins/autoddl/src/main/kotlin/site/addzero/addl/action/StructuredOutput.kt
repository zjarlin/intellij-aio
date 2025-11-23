
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.Project
import site.addzero.addl.action.StructuredInputDialog
import site.addzero.addl.ai.util.ai.AiUtil
import site.addzero.addl.ai.util.ai.ctx.AiCtx
import site.addzero.addl.ktututil.toJson
import site.addzero.addl.settings.SettingContext
import site.addzero.util.NotificationUtil
import site.addzero.util.ShowContentUtil.openTextInEditor
import site.addzero.util.lsi_impl.impl.intellij.context.lsiContext
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.IOException

class StructuredOutput : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(PlatformDataKeys.EDITOR)
        val psiFile = e.getData(LangDataKeys.PSI_FILE)
        if (project == null || editor == null || psiFile == null) {
            return
        }
        // 获取剪贴板的文本
        val clipboardText = getClipboardText()
        // 打开自定义的多行输入对话框
        val dialog = StructuredInputDialog(project, clipboardText)
        if (dialog.showAndGet()) {
            val question = dialog.getContextText()
            val promptTemplate = dialog.getPromptText()

            // 调用结构化输出接口
            val response = try {
                val callStructuredOutputInterface2 = callStructuredOutputInterface(project, question, promptTemplate)
                callStructuredOutputInterface2
            } catch (e: Exception) {
                NotificationUtil.showError(project, e.message!!)
                ""
            }
            if (response.isNotBlank()) {
                // 在新文件中打开响应结果
                project.openTextInEditor(
                    response, sqlPrefix = "Structured", fileTypeSuffix = ".json"
                )
            }

        }
    }

    private fun getClipboardText(): String {
        return try {
            Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as String
        } catch (e: UnsupportedFlavorException) {
            ""
        } catch (e: IOException) {
            ""
        }
    }


    private fun callStructuredOutputInterface(project: Project, question: String, promptTemplate: String): String {
        val context = project.lsiContext()
        val lsiClass = context.currentClass ?: return "无法获取当前类信息"
        val settings = SettingContext.settings
        val modelManufacturer = settings.modelManufacturer

        // 使用LSI生成字段信息
        val fields = lsiClass.fields
        val fieldMap = fields.associate { field ->
            (field.comment ?: field.name) to (field.name ?: "")
        }
        
        // 生成JSON结构
        val jsonMap = fields.associate { field ->
            (field.name ?: "") to (field.type?.simpleName ?: "String")
        }
        val jsonString = jsonMap.toJson()
        
        // 构建结构化输出提示
        val buildStructureOutPutPrompt = AiUtil.buildStructureOutPutPrompt(fieldMap)
        
        val (newPrompt, quesCtx) = AiCtx.structuredOutputContext(
            question, promptTemplate, buildStructureOutPutPrompt, buildStructureOutPutPrompt
        )
        
        val response = AiUtil.INIT(modelManufacturer, question, promptTemplate)
            .ask(jsonString, buildStructureOutPutPrompt)
        
        return response
    }

}
