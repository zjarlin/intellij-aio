package site.addzero.gradle.buddy.wrapper

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile

/**
 * 在 gradle-wrapper.properties 文件中，光标在 distributionUrl 行上时，
 * Alt+Enter 提供更新到最新版本（腾讯云/阿里云/官方镜像）的意图操作。
 */
class UpdateWrapperIntention : IntentionAction, PriorityAction {

    private var cachedCurrentVersion: String? = null

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.HIGH

    override fun getFamilyName(): String = "Gradle Buddy"

    override fun getText(): String {
        val ver = cachedCurrentVersion
        return if (ver != null) {
            "(Gradle Buddy) Update Gradle wrapper (current: $ver)"
        } else {
            "(Gradle Buddy) Update Gradle wrapper to latest"
        }
    }

    override fun startInWriteAction(): Boolean = false

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.Html("更新 distributionUrl 为最新 Gradle 版本的镜像地址。")
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        if (file.name != "gradle-wrapper.properties") return false
        if (editor == null) return false

        val document = editor.document
        val offset = editor.caretModel.offset
        val lineNumber = document.getLineNumber(offset)
        val lineStart = document.getLineStartOffset(lineNumber)
        val lineEnd = document.getLineEndOffset(lineNumber)
        val lineText = document.getText(com.intellij.openapi.util.TextRange(lineStart, lineEnd))

        if (!lineText.trimStart().startsWith("distributionUrl=")) return false

        val url = lineText.substringAfter("distributionUrl=").trim()
        cachedCurrentVersion = GradleWrapperUpdater.extractVersionFromUrl(url)
        return true
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        if (editor == null) return

        val document = editor.document
        val text = document.text
        val urlLine = text.lines().firstOrNull { it.trimStart().startsWith("distributionUrl=") } ?: return
        val currentUrl = urlLine.substringAfter("distributionUrl=").trim()
        val currentType = GradleWrapperUpdater.extractTypeFromUrl(currentUrl)

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project, "Fetching latest Gradle version...", true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                val latestVersion = GradleWrapperUpdater.fetchLatestVersion()

                ApplicationManager.getApplication().invokeLater {
                    if (latestVersion == null) {
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("GradleBuddy")
                            .createNotification(
                                "Failed to fetch latest Gradle version",
                                "Could not reach services.gradle.org",
                                NotificationType.ERROR
                            ).notify(project)
                        return@invokeLater
                    }

                    val currentVersion = GradleWrapperUpdater.extractVersionFromUrl(currentUrl)
                    if (currentVersion == latestVersion) {
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("GradleBuddy")
                            .createNotification(
                                "Already at latest Gradle $latestVersion",
                                NotificationType.INFORMATION
                            ).notify(project)
                        return@invokeLater
                    }

                    // 弹出镜像选择
                    val items = GradleWrapperUpdater.MIRRORS.map { mirror ->
                        val newUrl = GradleWrapperUpdater.buildDistributionUrl(latestVersion, currentType, mirror)
                        mirror.name to newUrl
                    }

                    JBPopupFactory.getInstance()
                        .createPopupChooserBuilder(items)
                        .setTitle("Select mirror — Gradle $latestVersion")
                        .setRenderer(com.intellij.ui.SimpleListCellRenderer.create("") { it.first })
                        .setItemChosenCallback { (mirrorName, newUrl) ->
                            WriteCommandAction.runWriteCommandAction(
                                project, "Update Gradle Wrapper", null, {
                                    val newText = text.lines().joinToString("\n") { line ->
                                        if (line.trimStart().startsWith("distributionUrl=")) {
                                            "distributionUrl=$newUrl"
                                        } else line
                                    }
                                    document.setText(newText)
                                    PsiDocumentManager.getInstance(project).commitDocument(document)
                                    FileDocumentManager.getInstance().saveDocument(document)
                                }
                            )
                            NotificationGroupManager.getInstance()
                                .getNotificationGroup("GradleBuddy")
                                .createNotification(
                                    "Gradle wrapper updated to $latestVersion",
                                    "Mirror: $mirrorName",
                                    NotificationType.INFORMATION
                                ).notify(project)
                        }
                        .createPopup()
                        .showInBestPositionFor(editor)
                }
            }
        })
    }
}
