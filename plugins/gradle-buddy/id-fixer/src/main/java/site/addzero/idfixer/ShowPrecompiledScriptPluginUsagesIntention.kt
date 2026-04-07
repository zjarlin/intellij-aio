package site.addzero.idfixer

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtFile
import site.addzero.gradle.buddy.i18n.GradleBuddyBundle

/**
 * 在预编译脚本插件文件上查看使用处。
 */
class ShowPrecompiledScriptPluginUsagesIntention : PsiElementBaseIntentionAction(), IntentionAction {

    override fun getText(): String = GradleBuddyBundle.message("intention.precompiled.plugin.usages")

    override fun getFamilyName(): String = GradleBuddyBundle.message("common.family.gradle.buddy")

    override fun startInWriteAction(): Boolean = false

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.Html(
            GradleBuddyBundle.message("intention.precompiled.plugin.usages.preview")
        )
    }

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val file = element.containingFile as? KtFile ?: return false
        return PrecompiledScriptPluginUsagesSupport.resolveCurrentPluginInfo(project, file.virtualFile) != null
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val file = element.containingFile as? KtFile ?: return
        val virtualFile = file.virtualFile ?: return
        val pluginInfo = PrecompiledScriptPluginUsagesSupport.resolveCurrentPluginInfo(project, virtualFile) ?: return

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            GradleBuddyBundle.message("intention.precompiled.plugin.usages.task", pluginInfo.fullyQualifiedId),
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = GradleBuddyBundle.message(
                    "intention.precompiled.plugin.usages.task",
                    pluginInfo.fullyQualifiedId
                )

                val usages = ReadAction.compute<List<PrecompiledScriptPluginUsagesSupport.Usage>, Throwable> {
                    PrecompiledScriptPluginUsagesSupport.findUsages(project, virtualFile, pluginInfo)
                }
                ApplicationManager.getApplication().invokeLater {
                    PrecompiledScriptPluginUsagesSupport.presentUsages(project, editor, pluginInfo, usages)
                }
            }
        })
    }
}
