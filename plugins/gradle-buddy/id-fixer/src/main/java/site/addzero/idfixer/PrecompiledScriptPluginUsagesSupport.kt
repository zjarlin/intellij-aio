package site.addzero.idfixer

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.SimpleListCellRenderer
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import site.addzero.gradle.buddy.i18n.GradleBuddyBundle

/**
 * 预编译脚本插件使用处扫描与跳转支持。
 */
object PrecompiledScriptPluginUsagesSupport {

    data class Usage(
        val file: com.intellij.openapi.vfs.VirtualFile,
        val offset: Int,
        val lineNumber: Int,
        val lineText: String,
        val matchedPluginId: String
    )

    fun resolveCurrentPluginInfo(project: Project, file: com.intellij.openapi.vfs.VirtualFile?): PluginIdInfo? {
        val virtualFile = file ?: return null
        if (!isPrecompiledScriptPluginFile(virtualFile)) {
            return null
        }
        return PluginIdScanner(project).extractPluginInfo(virtualFile)
    }

    fun findUsages(
        project: Project,
        sourceFile: com.intellij.openapi.vfs.VirtualFile,
        pluginInfo: PluginIdInfo
    ): List<Usage> {
        val targetIds = linkedSetOf(pluginInfo.fullyQualifiedId)
        if (pluginInfo.shortId.isNotBlank()) {
            targetIds += pluginInfo.shortId
        }

        val psiManager = PsiManager.getInstance(project)
        val scope = GlobalSearchScope.projectScope(project)
        val files = FilenameIndex.getAllFilesByExt(project, "kts", scope)

        return files.asSequence()
            .filter { candidate ->
                candidate != sourceFile &&
                    candidate.name.endsWith(".gradle.kts") &&
                    !shouldSkipPath(candidate.path)
            }
            .flatMap { candidate ->
                ProgressManager.checkCanceled()
                val psiFile = psiManager.findFile(candidate) as? KtFile ?: return@flatMap emptySequence()
                collectUsages(psiFile, targetIds).asSequence()
            }
            .sortedWith(compareBy<Usage>({ it.file.path }, { it.lineNumber }, { it.offset }))
            .toList()
    }

    fun presentUsages(
        project: Project,
        editor: Editor?,
        pluginInfo: PluginIdInfo,
        usages: List<Usage>
    ) {
        when {
            usages.isEmpty() -> {
                notify(
                    project = project,
                    title = GradleBuddyBundle.message("intention.precompiled.plugin.usages.none.title"),
                    content = GradleBuddyBundle.message(
                        "intention.precompiled.plugin.usages.none.content",
                        pluginInfo.fullyQualifiedId
                    ),
                    type = NotificationType.INFORMATION
                )
            }

            usages.size == 1 -> {
                navigate(project, usages.first())
            }

            else -> {
                val items = usages.map { usage ->
                    UsagePresentation(
                        usage = usage,
                        relativePath = toRelativePath(project, usage.file.path)
                    )
                }
                val popup = JBPopupFactory.getInstance()
                    .createPopupChooserBuilder(items)
                    .setTitle(
                        GradleBuddyBundle.message(
                            "intention.precompiled.plugin.usages.popup.title",
                            pluginInfo.fullyQualifiedId,
                            usages.size
                        )
                    )
                    .setRenderer(SimpleListCellRenderer.create("") { item ->
                        "${item.relativePath}:${item.usage.lineNumber}  ${item.usage.lineText}"
                    })
                    .setNamerForFiltering { item ->
                        "${item.relativePath}:${item.usage.lineNumber} ${item.usage.lineText}"
                    }
                    .setItemChosenCallback { item ->
                        navigate(project, item.usage)
                    }
                    .createPopup()

                if (editor != null) {
                    popup.showInBestPositionFor(editor)
                } else {
                    popup.showInFocusCenter()
                }
            }
        }
    }

    private fun collectUsages(file: KtFile, targetIds: Set<String>): List<Usage> {
        val fileText = file.text
        return file.collectDescendantsOfType<KtCallExpression>()
            .asSequence()
            .filter { callExpression -> callExpression.calleeExpression?.text == "id" }
            .filter(::isInsidePluginsBlock)
            .mapNotNull { callExpression ->
                val stringExpression = callExpression.valueArguments.firstOrNull()?.getArgumentExpression()
                    as? KtStringTemplateExpression
                    ?: return@mapNotNull null
                val pluginId = extractLiteralString(stringExpression) ?: return@mapNotNull null
                if (pluginId !in targetIds) {
                    return@mapNotNull null
                }
                createUsage(fileText, file.virtualFile, stringExpression.textRange.startOffset, pluginId)
            }
            .toList()
    }

    private fun createUsage(
        content: String,
        file: com.intellij.openapi.vfs.VirtualFile,
        offset: Int,
        pluginId: String
    ): Usage {
        val lineStart = content.lastIndexOf('\n', startIndex = (offset - 1).coerceAtLeast(0))
            .let { if (it == -1) 0 else it + 1 }
        val lineEnd = content.indexOf('\n', startIndex = offset)
            .let { if (it == -1) content.length else it }
        val lineNumber = content.take(offset).count { it == '\n' } + 1
        val lineText = content.substring(lineStart, lineEnd).trim()
        return Usage(
            file = file,
            offset = offset,
            lineNumber = lineNumber,
            lineText = lineText,
            matchedPluginId = pluginId
        )
    }

    private fun extractLiteralString(expression: KtStringTemplateExpression): String? {
        if (expression.entries.any { entry -> entry !is KtLiteralStringTemplateEntry }) {
            return null
        }
        return expression.entries.joinToString(separator = "") { entry -> entry.text }
    }

    private fun isInsidePluginsBlock(callExpression: KtCallExpression): Boolean {
        var current: com.intellij.psi.PsiElement? = callExpression
        while (current != null) {
            if (current is KtLambdaExpression) {
                val lambdaArgument = current.parent as? KtLambdaArgument
                val parentCall = lambdaArgument?.parent as? KtCallExpression
                if (parentCall?.calleeExpression?.text == "plugins") {
                    return true
                }
            }
            current = current.parent
        }
        return false
    }

    private fun isPrecompiledScriptPluginFile(file: com.intellij.openapi.vfs.VirtualFile): Boolean {
        if (file.isDirectory || !file.name.endsWith(".gradle.kts")) {
            return false
        }
        val normalizedPath = file.path.replace('\\', '/')
        if (!normalizedPath.contains("/src/main/kotlin/")) {
            return false
        }
        return normalizedPath.contains("/build-logic/") || normalizedPath.contains("/buildSrc/")
    }

    private fun shouldSkipPath(path: String): Boolean {
        val normalizedPath = path.replace('\\', '/')
        return normalizedPath.contains("/build/") ||
            normalizedPath.contains("/out/") ||
            normalizedPath.contains("/target/") ||
            normalizedPath.contains("/node_modules/") ||
            normalizedPath.contains("/.gradle/") ||
            normalizedPath.contains("/.idea/")
    }

    private fun toRelativePath(project: Project, path: String): String {
        val basePath = project.basePath?.replace('\\', '/')?.trimEnd('/') ?: return path
        val normalizedPath = path.replace('\\', '/')
        return normalizedPath.removePrefix("$basePath/").ifBlank { normalizedPath }
    }

    private fun navigate(project: Project, usage: Usage) {
        OpenFileDescriptor(project, usage.file, usage.offset).navigate(true)
    }

    private fun notify(project: Project, title: String, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("GradleBuddy")
            .createNotification(title, content, type)
            .notify(project)
    }

    private data class UsagePresentation(
        val usage: Usage,
        val relativePath: String
    )
}
