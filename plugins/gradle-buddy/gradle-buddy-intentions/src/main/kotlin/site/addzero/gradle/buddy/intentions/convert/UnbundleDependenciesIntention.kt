package site.addzero.gradle.buddy.intentions.convert

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import site.addzero.gradle.buddy.settings.GradleBuddySettingsService

/**
 * 将 bundle 引用展开为多行独立的 catalog 依赖
 *
 * 使用场景：光标在 implementation(libs.bundles.xxx) 上时 Alt+Enter
 * 效果：读取 TOML 中 bundle 的成员列表，展开为多行 implementation(libs.yyy)
 *
 * 与 CreateBundleFromDependenciesIntention 互为逆操作。
 */
class UnbundleDependenciesIntention : IntentionAction, PriorityAction {

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.NORMAL
    override fun getFamilyName(): String = "Gradle Buddy"
    override fun getText(): String = "(Gradle Buddy) Expand bundle to individual dependencies"
    override fun startInWriteAction(): Boolean = true

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.Html("将 bundle 引用展开为多行独立的版本目录依赖。")
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        if (editor == null || !file.name.endsWith(".gradle.kts")) return false
        return detectBundleRef(editor) != null
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        if (editor == null) return
        val ref = detectBundleRef(editor) ?: return

        val catalogFile = GradleBuddySettingsService.getInstance(project).resolveVersionCatalogFile(project)
        val aliases = CreateBundleFromDependenciesIntention.parseBundleAliases(catalogFile, ref.bundleName)
        if (aliases.isEmpty()) return

        val document = editor.document
        val lineNum = document.getLineNumber(editor.caretModel.offset)
        val lineStart = document.getLineStartOffset(lineNum)
        val lineEnd = document.getLineEndOffset(lineNum)
        val lineText = document.getText(com.intellij.openapi.util.TextRange(lineStart, lineEnd))

        // 检测缩进
        val indent = lineText.takeWhile { it.isWhitespace() }

        // 生成展开后的多行
        val expanded = aliases.joinToString("\n") { alias ->
            val accessor = alias.replace('-', '.').replace('_', '.')
            "$indent${ref.configuration}(libs.$accessor)"
        }

        WriteCommandAction.runWriteCommandAction(project, "Expand Bundle: ${ref.bundleName}", null, {
            document.replaceString(lineStart, lineEnd, expanded)
        })
    }

    private data class BundleRef(
        val configuration: String,
        val bundleName: String,
        /** 完整匹配的文本范围 */
        val matchText: String
    )

    /**
     * 检测光标所在行是否是 bundle 引用。
     * 匹配: implementation(libs.bundles.xxx) / api(libs.bundles.xxx.yyy)
     */
    private fun detectBundleRef(editor: Editor): BundleRef? {
        val document = editor.document
        val offset = editor.caretModel.offset
        val lineNum = document.getLineNumber(offset)
        val lineStart = document.getLineStartOffset(lineNum)
        val lineEnd = document.getLineEndOffset(lineNum)
        val lineText = document.getText(com.intellij.openapi.util.TextRange(lineStart, lineEnd))

        val pattern = Regex("""(\w+)\s*\(\s*libs\.bundles\.([A-Za-z0-9_.]+)\s*\)""")
        val match = pattern.find(lineText) ?: return null
        val config = match.groupValues[1]
        // accessor: filekit -> bundleName: filekit; xxx.yyy -> xxx-yyy
        val accessor = match.groupValues[2]
        val bundleName = accessor.replace('.', '-')
        return BundleRef(config, bundleName, match.value)
    }
}
