package site.addzero.gradle.buddy.intentions.catalog

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiFile
import site.addzero.gradle.buddy.intentions.select.VersionSelectionDialog
import site.addzero.network.call.maven.util.MavenCentralSearchUtil

/**
 * Fix Version Intention - 修复版本引用
 *
 * 当依赖引用了一个不属于它的版本变量时（比如共享的 LTS 版本），
 * 此 intention 会为该工件创建一个专属的版本变量，并更新引用。
 *
 * 例如:
 * 原始: addzero-jimmer-ext-lowquery = { module = "site.addzero:jimmer-ext-lowquery", version.ref = "addzeroLTSVersion" }
 * 修复后:
 *   [versions] 中新增: jimmer-ext-lowquery = "最新版本"
 *   [libraries] 中更新: version.ref = "jimmer-ext-lowquery"
 *
 * 幂等: 如果 artifactId 对应的版本变量已存在，直接引用，不重复创建。
 */
class VersionCatalogFixVersionIntention : IntentionAction, PriorityAction {

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.HIGH

    override fun getFamilyName(): String = "Gradle Buddy"

    override fun getText(): String = "(Gradle Buddy) Fix version - create own version ref"

    override fun startInWriteAction(): Boolean = false

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.Html(
            "Creates a dedicated version variable for this artifact (from Maven Central) and updates the version.ref to point to it."
        )
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        if (!file.name.endsWith(".toml")) return false

        val virtualFile = file.virtualFile
        if (virtualFile != null && !virtualFile.path.contains("/gradle/")) return false

        val offset = editor?.caretModel?.offset ?: return false
        val element = file.findElementAt(offset) ?: return false

        val dep = VersionCatalogDependencyHelper.detectCatalogDependencyAt(element) ?: return false
        // 仅对使用 version.ref 的依赖生效
        if (!dep.isVersionRef) return false

        val targetVersionKey = dep.artifactId
        val fullText = file.text

        if (dep.versionKey == targetVersionKey) {
            // version.ref 已经和 artifactId 一致，但如果变量未定义，仍需创建
            val versionExists = findVersionValue(fullText, targetVersionKey) != null
            return !versionExists
        }

        // version.ref 指向了别的变量，需要修复
        return true
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        val offset = editor?.caretModel?.offset ?: return
        val element = file.findElementAt(offset) ?: return
        val dep = VersionCatalogDependencyHelper.detectCatalogDependencyAt(element) ?: return
        if (!dep.isVersionRef) return

        val targetVersionKey = dep.artifactId
        val fullText = file.text
        val refAlreadyCorrect = dep.versionKey == targetVersionKey

        // 检查目标版本变量是否已存在
        val existingVersion = findVersionValue(fullText, targetVersionKey)
        if (existingVersion != null) {
            if (refAlreadyCorrect) {
                // 变量已存在且引用已正确，无需操作
                return
            }
            // 幂等：版本变量已存在，直接更新引用
            WriteCommandAction.runWriteCommandAction(project) {
                updateVersionRef(file, dep, targetVersionKey)
            }
            return
        }

        // 需要从 Maven Central 获取版本并让用户选择
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Fetching versions from Maven Central...", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true

                val versions = runCatching {
                    MavenCentralSearchUtil.searchAllVersions(dep.groupId, dep.artifactId, 50)
                        .map { it.latestVersion }
                        .distinct()
                        .sortedDescending()
                }.getOrElse { listOf() }

                ApplicationManager.getApplication().invokeLater {
                    if (versions.isEmpty()) {
                        Messages.showWarningDialog(
                            project,
                            "Could not fetch versions for ${dep.groupId}:${dep.artifactId}",
                            "Fix Version Failed"
                        )
                        return@invokeLater
                    }

                    val dialog = VersionSelectionDialog(
                        project,
                        "Select Version - ${dep.groupId}:${dep.artifactId}",
                        versions,
                        dep.currentVersion
                    )
                    if (!dialog.showAndGet()) return@invokeLater
                    val selectedVersion = dialog.getSelectedVersion() ?: return@invokeLater

                    WriteCommandAction.runWriteCommandAction(project) {
                        if (refAlreadyCorrect) {
                            // 只需创建版本变量，引用已经正确
                            addVersionVariable(file, targetVersionKey, selectedVersion)
                        } else {
                            addVersionVariableAndUpdateRef(file, dep, targetVersionKey, selectedVersion)
                        }
                    }
                }
            }
        })
    }

    /**
     * 在 [versions] 部分查找指定 key 的版本值
     */
    private fun findVersionValue(fullText: String, versionKey: String): String? {
        val pattern = Regex("""(?m)^\s*${Regex.escape(versionKey)}\s*=\s*["']([^"']+)["']""")
        return pattern.find(fullText)?.groupValues?.get(1)
    }

    /**
     * 仅更新依赖行的 version.ref 指向新的版本变量 key
     */
    private fun updateVersionRef(file: PsiFile, dep: VersionCatalogDependencyHelper.CatalogDependencyInfo, newVersionKey: String) {
        val document = file.viewProvider.document ?: return
        val oldRef = """version.ref = "${dep.versionKey}""""
        val newRef = """version.ref = "$newVersionKey""""
        val newLine = dep.lineText.replace(oldRef, newRef)
        document.replaceString(
            dep.lineStartOffset,
            dep.lineStartOffset + dep.lineText.length,
            newLine
        )
    }

    /**
     * 仅在 [versions] 中添加版本变量（不更新引用，因为引用已正确）
     */
    private fun addVersionVariable(file: PsiFile, versionKey: String, version: String) {
        val document = file.viewProvider.document ?: return
        val text = document.text
        val insertOffset = findVersionsSectionEndOffset(text)
        if (insertOffset < 0) {
            val versionBlock = "[versions]\n$versionKey = \"$version\"\n\n"
            document.insertString(0, versionBlock)
        } else {
            val newVersionLine = "$versionKey = \"$version\"\n"
            document.insertString(insertOffset, newVersionLine)
        }
    }

    /**
     * 在 [versions] 末尾添加新版本变量，并更新依赖的 version.ref
     */
    private fun addVersionVariableAndUpdateRef(
        file: PsiFile,
        dep: VersionCatalogDependencyHelper.CatalogDependencyInfo,
        newVersionKey: String,
        version: String
    ) {
        val document = file.viewProvider.document ?: return
        val text = document.text

        // 找到 [versions] 部分的末尾位置（下一个 section 开始之前）
        val insertOffset = findVersionsSectionEndOffset(text)
        if (insertOffset < 0) {
            // 没有 [versions] 部分，在文件开头创建
            val versionBlock = "[versions]\n$newVersionKey = \"$version\"\n\n"
            document.insertString(0, versionBlock)
            // 插入后偏移量变化，需要重新定位依赖行
            val newText = document.text
            updateVersionRefInText(document, newText, dep, newVersionKey)
        } else {
            // 在 [versions] 末尾插入新版本变量
            val newVersionLine = "$newVersionKey = \"$version\"\n"
            document.insertString(insertOffset, newVersionLine)
            // 插入后偏移量可能变化，重新定位依赖行
            val newText = document.text
            updateVersionRefInText(document, newText, dep, newVersionKey)
        }
    }

    /**
     * 在文档文本中查找并更新依赖的 version.ref
     */
    private fun updateVersionRefInText(
        document: com.intellij.openapi.editor.Document,
        text: String,
        dep: VersionCatalogDependencyHelper.CatalogDependencyInfo,
        newVersionKey: String
    ) {
        // 通过依赖 key 重新定位行
        val depKeyPattern = Regex("""(?m)^\s*${Regex.escape(dep.key)}\s*=\s*\{[^}]*version\.ref\s*=\s*"${Regex.escape(dep.versionKey)}"[^}]*\}""")
        val match = depKeyPattern.find(text) ?: return
        val oldRef = """version.ref = "${dep.versionKey}""""
        val newRef = """version.ref = "$newVersionKey""""
        val newLine = match.value.replace(oldRef, newRef)
        document.replaceString(match.range.first, match.range.last + 1, newLine)
    }

    /**
     * 找到 [versions] 部分的末尾偏移量（最后一个非空行之后）
     */
    private fun findVersionsSectionEndOffset(text: String): Int {
        val lines = text.split('\n')
        var inVersions = false
        var lastVersionLineEnd = -1
        var offset = 0

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed == "[versions]") {
                inVersions = true
                offset += line.length + 1
                continue
            }
            if (inVersions) {
                if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    // 遇到下一个 section，返回上一个有效行的末尾
                    return if (lastVersionLineEnd >= 0) lastVersionLineEnd else offset
                }
                if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                    lastVersionLineEnd = offset + line.length + 1
                }
            }
            offset += line.length + 1
        }

        // 如果 [versions] 是最后一个 section
        return if (inVersions && lastVersionLineEnd >= 0) lastVersionLineEnd else -1
    }
}
