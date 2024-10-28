import com.addzero.addl.action.StructuredInputDialog
import com.addzero.addl.ai.consts.ChatModels.OLLAMA
import com.addzero.addl.ai.util.ai.ctx.AiCtx
import com.addzero.addl.ktututil.toJson
import com.addzero.addl.settings.SettingContext
import com.addzero.addl.util.Pojo2JsonUtil
import com.addzero.addl.util.ShowSqlUtil
import com.addzero.addl.util.fieldinfo.PsiUtil
import com.addzero.addl.util.fieldinfo.PsiUtil.psiCtx
import com.addzero.ai.modules.ai.util.ai.ai_abs_builder.AiUtil
import com.addzero.common.kt_util.isNotBlank
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
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
                callStructuredOutputInterface(project, question, promptTemplate)
            } catch (e: Exception) {
                ShowSqlUtil.showErrorMsg(e.message!!)
                ""
            }
            if (response.isNotBlank()) {
                // 在新文件中打开响应结果
                ShowSqlUtil.openSqlInEditor(
                    project, response, fileTypeSuffix = ".json", sqlPrefix = "Structured"
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
        val (editor1, psiClass, ktClass, psiFile, virtualFile, classPath1) = psiCtx(project)
        val settings = SettingContext.settings
        val modelName =
            if (settings.modelManufacturer == OLLAMA) {
                settings.modelNameOffline
            } else {
                settings.modelNameOnline

            }

        val any = if (ktClass == null) {
            psiClass ?: return ""
            val (jsonString, buildStructureOutPutPrompt) = javaPromt(
                psiClass!!, project, question, promptTemplate
            )

            val ask =
                AiUtil(modelName = modelName, question, promptTemplate).ask(jsonString, buildStructureOutPutPrompt)
            ask
        } else {

            val generateMap = Pojo2Json4ktUtil.generateMap(ktClass, project)
            val jsonString = generateMap.toJson()
            val extractInterfaceMetaInfo = PsiUtil.extractInterfaceMetaInfo(ktClass)

            val associateBy = extractInterfaceMetaInfo.associateBy({ it.comment }, { it.name })
            val buildStructureOutPutPrompt = AiUtil.buildStructureOutPutPrompt(associateBy)

            val (newPrompt, quesCtx) = AiCtx.structuredOutputContext(
                question, promptTemplate, buildStructureOutPutPrompt, buildStructureOutPutPrompt
            )
            val ask1 =
                AiUtil(modelName = modelName, question, promptTemplate).ask(jsonString, buildStructureOutPutPrompt)
            ask1
        }

//        PsiUtil.
//        val toJson = any.toJson()
//        return toJson
        return any
    }

    private fun javaPromt(
        psiClass: PsiClass,
        project: Project,
        question: String,
        promptTemplate: String,
    ): Pair<String, String> {
        val generateMap = Pojo2JsonUtil.generateMap(psiClass, project)
        val jsonString = generateMap.toJson()
        val extractInterfaceMetaInfo = PsiUtil.extractInterfaceMetaInfo(psiClass)

        val associateBy = extractInterfaceMetaInfo.associateBy({ it.comment }, { it.name })
        val buildStructureOutPutPrompt = AiUtil.buildStructureOutPutPrompt(associateBy)

        val (newPrompt, quesCtx) = AiCtx.structuredOutputContext(
            question, promptTemplate, buildStructureOutPutPrompt, buildStructureOutPutPrompt
        )
        return Pair(jsonString, buildStructureOutPutPrompt)
    }
}