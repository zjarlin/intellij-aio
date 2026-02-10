package site.addzero.gradle.buddy.intentions.catalog

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * 一键将 [libraries] 中所有 `module = "group:artifact"` 短格式
 * 转换为 `group = "...", name = "..."` 长格式。
 *
 * 转换前:
 *   my-lib = { module = "com.example:my-lib", version.ref = "myLib" }
 *
 * 转换后:
 *   my-lib = { group = "com.example", name = "my-lib", version.ref = "myLib" }
 *
 * 仅在光标位于 .versions.toml 文件且存在 module = "..." 声明时可用。
 */
class VersionCatalogModuleToGroupNameIntention : IntentionAction, PriorityAction {

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.NORMAL

    override fun getFamilyName(): String = "Gradle Buddy"

    override fun getText(): String = "(Gradle Buddy) Convert all module= to group= name="

    override fun startInWriteAction(): Boolean = false

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.Html(
            """Converts all <code>module = "group:artifact"</code> declarations in [libraries] to <code>group = "...", name = "..."</code> format."""
        )
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        if (!file.name.endsWith(".toml")) return false
        val virtualFile = file.virtualFile
        if (virtualFile != null && !virtualFile.path.contains("/gradle/")) return false
        // 只要文件中存在 module = "xxx:yyy" 就可用
        return MODULE_PATTERN.containsMatchIn(file.text)
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        val document = file.viewProvider.document ?: return
        val text = document.text

        val newText = convertAllModuleToGroupName(text)
        if (newText == text) return

        WriteCommandAction.runWriteCommandAction(project, "Convert module= to group= name=", null, {
            document.replaceString(0, document.textLength, newText)
        })
    }

    companion object {
        /**
         * Matches: module = "group:artifact"
         * Captures: group (1), artifact (2)
         */
        private val MODULE_PATTERN = Regex("""module\s*=\s*"([^":]+):([^"]+)"""")

        /**
         * 将文本中所有 `module = "group:artifact"` 替换为 `group = "group", name = "artifact"`
         */
        fun convertAllModuleToGroupName(text: String): String {
            return MODULE_PATTERN.replace(text) { match ->
                val group = match.groupValues[1]
                val artifact = match.groupValues[2]
                """group = "$group", name = "$artifact""""
            }
        }
    }
}
