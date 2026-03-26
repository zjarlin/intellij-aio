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
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import site.addzero.gradle.buddy.i18n.GradleBuddyBundle
import site.addzero.network.call.maven.util.MavenCentralSearchUtil

/**
 * Version Catalog Update Dependency Intention
 *
 * 在 libs.versions.toml 文件中升级依赖版本
 * 支持格式:
 * - junit-jupiter-api = { group = "org.junit.jupiter", name = "junit-jupiter-api", version.ref = "jupiter" }
 * - junit-jupiter-api = { module = "org.junit.jupiter:junit-jupiter-api", version.ref = "jupiter" }
 * - junit-jupiter-api = { module = "org.junit.jupiter:junit-jupiter-api", version = "5.10.0" }
 *
 * Priority: HIGH - 在版本目录文件中优先显示此intention
 */
class VersionCatalogUpdateDependencyIntention : IntentionAction, PriorityAction {

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.HIGH

    override fun getFamilyName(): String = GradleBuddyBundle.message("common.family.gradle.buddy")

    override fun getText(): String = GradleBuddyBundle.message("intention.version.catalog.update.dependency.latest")

    override fun startInWriteAction(): Boolean = false

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.Html(
            GradleBuddyBundle.message("intention.version.catalog.update.dependency.latest.preview")
        )
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        if (!file.name.endsWith(".toml")) {
            return false
        }

        val virtualFile = file.virtualFile
        if (virtualFile != null) {
            val path = virtualFile.path
            // 检查是否在 gradle/ 目录下（支持 gradle/libs.versions.toml 或其他位置）
            if (!path.contains("/gradle/")) {
                return false
            }
        }

        val offset = editor?.caretModel?.offset ?: 0
        val element = file.findElementAt(offset) ?: return false
        return detectCatalogDependency(element) != null
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        // 移除硬编码的文件名检查，因为 isAvailable() 已经做了更灵活的检查
        val offset = editor?.caretModel?.offset ?: 0
        val element = file.findElementAt(offset) ?: return

        val dependencyInfo = detectCatalogDependency(element) ?: return

        ProgressManager.getInstance().run(
            object : Task.Backgroundable(
                project,
                GradleBuddyBundle.message("intention.version.catalog.update.dependency.latest.task"),
                true
            ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true

                val latestVersion = runCatching {
                    MavenCentralSearchUtil.getLatestVersion(dependencyInfo.groupId, dependencyInfo.artifactId)
                }.getOrNull()

                ApplicationManager.getApplication().invokeLater {
                    if (latestVersion == null) {
                        Messages.showWarningDialog(
                            project,
                            GradleBuddyBundle.message(
                                "intention.version.catalog.update.dependency.latest.not.found.content",
                                dependencyInfo.groupId,
                                dependencyInfo.artifactId
                            ),
                            GradleBuddyBundle.message("intention.version.catalog.update.dependency.latest.not.found.title")
                        )
                        return@invokeLater
                    }

                    if (latestVersion == dependencyInfo.currentVersion) {
                        Messages.showInfoMessage(
                            project,
                            GradleBuddyBundle.message(
                                "intention.version.catalog.update.dependency.latest.no.change.content",
                                latestVersion
                            ),
                            GradleBuddyBundle.message("intention.version.catalog.update.dependency.latest.no.change.title")
                        )
                        return@invokeLater
                    }

                    WriteCommandAction.runWriteCommandAction(project) {
                        updateCatalogDependency(file, dependencyInfo, latestVersion)
                    }
                }
            }
        })
    }

    private fun updateCatalogDependency(file: PsiFile, info: CatalogDependencyInfo, newVersion: String) {
        val document = file.viewProvider.document ?: return
        val text = document.text

        if (info.isVersionRef) {
            // 查找并更新版本引用
            val versionPattern = Regex("""${Regex.escape(info.versionKey)}\s*=\s*["']${Regex.escape(info.currentVersion)}["']""")
            val versionMatch = versionPattern.find(text)
            if (versionMatch != null) {
                val newVersionText = versionMatch.value.replace(info.currentVersion, newVersion)
                val startOffset = text.indexOf(versionMatch.value)
                if (startOffset >= 0) {
                    document.replaceString(startOffset, startOffset + versionMatch.value.length, newVersionText)
                }
            }
        } else {
            // 直接更新版本
            val oldText = "${info.key} = ${info.originalValue}"
            val newText = oldText.replace(info.currentVersion, newVersion)
            val startOffset = text.indexOf(oldText, info.approximateOffset.coerceAtLeast(0))
            if (startOffset >= 0) {
                document.replaceString(startOffset, startOffset + oldText.length, newText)
            }
        }
    }

    private fun detectCatalogDependency(element: PsiElement): CatalogDependencyInfo? {
        val lineText = getLineText(element)
        val fullText = element.containingFile?.text ?: lineText

        // 格式1: junit-jupiter-api = { group = "org.junit.jupiter", name = "junit-jupiter-api", version.ref = "jupiter" }
        val groupPattern = Regex("""(\w+(?:-\w+)*)\s*=\s*\{\s*group\s*=\s*"([^"]+)"\s*,\s*name\s*=\s*"([^"]+)"\s*,\s*version\.ref\s*=\s*"([^"]+)"\s*\}""")
        groupPattern.find(lineText)?.let { match ->
            val (key, groupId, artifactId, versionRef) = match.destructured
            val currentVersion = findVersionRef(fullText, versionRef)
            if (currentVersion != null) {
                return createDependencyInfo(key, groupId, artifactId, currentVersion, versionRef, true, match.value, element.textOffset)
            }
        }

        // 格式2: junit-jupiter-api = { module = "org.junit.jupiter:junit-jupiter-api", version.ref = "jupiter" }
        val modulePattern = Regex("""(\w+(?:-\w+)*)\s*=\s*\{\s*module\s*=\s*"([^:]+):([^"]+)"\s*,\s*version\.ref\s*=\s*"([^"]+)"\s*\}""")
        modulePattern.find(lineText)?.let { match ->
            val (key, groupId, artifactId, versionRef) = match.destructured
            val currentVersion = findVersionRef(fullText, versionRef)
            if (currentVersion != null) {
                return createDependencyInfo(key, groupId, artifactId, currentVersion, versionRef, true, match.value, element.textOffset)
            }
        }

        // 格式3: junit-jupiter-api = { module = "org.junit.jupiter:junit-jupiter-api", version = "5.10.0" }
        val directVersionPattern = Regex("""(\w+(?:-\w+)*)\s*=\s*\{\s*module\s*=\s*"([^:]+):([^"]+)"\s*,\s*version\s*=\s*"([^"]+)"\s*\}""")
        directVersionPattern.find(lineText)?.let { match ->
            val (key, groupId, artifactId, version) = match.destructured
            return createDependencyInfo(key, groupId, artifactId, version, "", false, match.value, element.textOffset)
        }

        return null
    }

    private fun createDependencyInfo(
        key: String,
        groupId: String,
        artifactId: String,
        currentVersion: String,
        versionKey: String,
        isVersionRef: Boolean,
        originalValue: String,
        textOffset: Int
    ): CatalogDependencyInfo {
        return CatalogDependencyInfo(
            key = key,
            groupId = groupId,
            artifactId = artifactId,
            currentVersion = currentVersion,
            versionKey = versionKey,
            isVersionRef = isVersionRef,
            originalValue = originalValue,
            approximateOffset = textOffset - 100
        )
    }

    private fun findVersionRef(fullText: String, versionKey: String): String? {
        val versionPattern = Regex("""${Regex.escape(versionKey)}\s*=\s*["']([^"']+)["']""")
        val match = versionPattern.find(fullText)
        return match?.groupValues?.get(1)
    }

    private fun getLineText(element: PsiElement): String {
        val file = element.containingFile ?: return ""
        val offset = element.textOffset
        val document = file.viewProvider.document ?: return ""
        val lineNumber = document.getLineNumber(offset)
        val lineStart = document.getLineStartOffset(lineNumber)
        val lineEnd = document.getLineEndOffset(lineNumber)
        return document.getText(TextRange(lineStart, lineEnd))
    }

    private data class CatalogDependencyInfo(
        val key: String,
        val groupId: String,
        val artifactId: String,
        val currentVersion: String,
        val versionKey: String,
        val isVersionRef: Boolean,
        val originalValue: String,
        val approximateOffset: Int
    )
}
