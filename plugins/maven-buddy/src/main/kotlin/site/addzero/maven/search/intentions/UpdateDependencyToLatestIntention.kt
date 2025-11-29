package site.addzero.maven.search.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import site.addzero.network.call.maven.util.MavenCentralSearchUtil

/**
 * 升级依赖到最新版本的意图操作
 * 
 * 支持：
 * - Gradle Kotlin DSL: implementation("g:a:v")
 * - Maven pom.xml: <version>v</version>
 */
class UpdateDependencyToLatestIntention : PsiElementBaseIntentionAction(), IntentionAction {

    override fun getFamilyName(): String = "Maven Buddy"

    override fun getText(): String = "Update dependency to latest version"

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val file = element.containingFile ?: return false
        val fileName = file.name
        
        return when {
            fileName.endsWith(".gradle.kts") -> detectGradleKtsDependency(element) != null
            fileName == "pom.xml" -> detectMavenDependency(element) != null
            else -> false
        }
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val file = element.containingFile ?: return
        val fileName = file.name
        
        val dependencyInfo = when {
            fileName.endsWith(".gradle.kts") -> detectGradleKtsDependency(element)
            fileName == "pom.xml" -> detectMavenDependency(element)
            else -> null
        } ?: return

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Fetching latest version...", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                
                val latestVersion = runCatching {
                    MavenCentralSearchUtil.getLatestVersion(dependencyInfo.groupId, dependencyInfo.artifactId)
                }.getOrNull()

                ApplicationManager.getApplication().invokeLater {
                    if (latestVersion == null) {
                        Messages.showWarningDialog(
                            project,
                            "Could not find latest version for ${dependencyInfo.groupId}:${dependencyInfo.artifactId}",
                            "Update Failed"
                        )
                        return@invokeLater
                    }

                    if (latestVersion == dependencyInfo.currentVersion) {
                        Messages.showInfoMessage(
                            project,
                            "Already at latest version: $latestVersion",
                            "No Update Needed"
                        )
                        return@invokeLater
                    }

                    WriteCommandAction.runWriteCommandAction(project) {
                        replaceVersion(file, dependencyInfo, latestVersion)
                    }
                }
            }
        })
    }

    private fun replaceVersion(file: PsiFile, info: DependencyInfo, newVersion: String) {
        val document = file.viewProvider.document ?: return
        val text = document.text
        
        val oldText = info.fullMatch
        val newText = oldText.replace(info.currentVersion, newVersion)
        
        val startOffset = text.indexOf(oldText, info.approximateOffset.coerceAtLeast(0))
        if (startOffset >= 0) {
            document.replaceString(startOffset, startOffset + oldText.length, newText)
        }
    }

    /**
     * 检测 Gradle KTS 依赖声明
     * 
     * 支持格式：
     * - implementation("g:a:v")
     * - api("g:a:v")
     * - testImplementation("g:a:v")
     * - 等等
     */
    private fun detectGradleKtsDependency(element: PsiElement): DependencyInfo? {
        val text = element.text
        val lineText = getLineText(element)
        
        val pattern = Regex("""(\w+)\s*\(\s*["']([^:]+):([^:]+):([^"']+)["']\s*\)""")
        val match = pattern.find(lineText) ?: return null
        
        val (_, groupId, artifactId, version) = match.destructured
        
        return DependencyInfo(
            groupId = groupId,
            artifactId = artifactId,
            currentVersion = version,
            fullMatch = match.value,
            approximateOffset = element.textOffset - 100
        )
    }

    /**
     * 检测 Maven pom.xml 依赖版本
     * 
     * 支持格式：
     * <dependency>
     *   <groupId>g</groupId>
     *   <artifactId>a</artifactId>
     *   <version>v</version>
     * </dependency>
     */
    private fun detectMavenDependency(element: PsiElement): DependencyInfo? {
        val file = element.containingFile ?: return null
        val text = file.text
        val offset = element.textOffset
        
        val versionTagPattern = Regex("""<version>([^<]+)</version>""")
        val versionMatch = versionTagPattern.find(text.substring(maxOf(0, offset - 50), minOf(text.length, offset + 50)))
            ?: return null
        
        val dependencyBlockStart = text.lastIndexOf("<dependency>", offset)
        if (dependencyBlockStart < 0) return null
        
        val dependencyBlockEnd = text.indexOf("</dependency>", dependencyBlockStart)
        if (dependencyBlockEnd < 0 || dependencyBlockEnd < offset) return null
        
        val dependencyBlock = text.substring(dependencyBlockStart, dependencyBlockEnd + "</dependency>".length)
        
        val groupIdMatch = Regex("""<groupId>([^<]+)</groupId>""").find(dependencyBlock) ?: return null
        val artifactIdMatch = Regex("""<artifactId>([^<]+)</artifactId>""").find(dependencyBlock) ?: return null
        val versionInBlockMatch = Regex("""<version>([^<]+)</version>""").find(dependencyBlock) ?: return null
        
        return DependencyInfo(
            groupId = groupIdMatch.groupValues[1],
            artifactId = artifactIdMatch.groupValues[1],
            currentVersion = versionInBlockMatch.groupValues[1],
            fullMatch = versionInBlockMatch.value,
            approximateOffset = dependencyBlockStart
        )
    }

    private fun getLineText(element: PsiElement): String {
        val file = element.containingFile ?: return ""
        val document = file.viewProvider.document ?: return ""
        val offset = element.textOffset
        val lineNumber = document.getLineNumber(offset)
        val lineStart = document.getLineStartOffset(lineNumber)
        val lineEnd = document.getLineEndOffset(lineNumber)
        return document.getText(com.intellij.openapi.util.TextRange(lineStart, lineEnd))
    }

    private data class DependencyInfo(
        val groupId: String,
        val artifactId: String,
        val currentVersion: String,
        val fullMatch: String,
        val approximateOffset: Int
    )
}
