package site.addzero.gradle.buddy.intentions.convert

import com.intellij.openapi.editor.Document
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import site.addzero.gradle.buddy.intentions.util.GradleProjectRoots

/**
 * 复用“插件声明 -> 全仓库扫描/注释”相关的 PSI 和文本处理逻辑。
 */
internal object GradlePluginCommentSupport {

    fun isTargetGradleKtsFile(file: PsiFile): Boolean {
        return file.name.endsWith(".gradle.kts")
    }

    fun detectTargetPlugin(element: PsiElement): TargetPlugin? {
        val stringExpression = PsiTreeUtil.getParentOfType(element, KtStringTemplateExpression::class.java, false)
        if (stringExpression != null) {
            val callExpression = PsiTreeUtil.getParentOfType(stringExpression, KtCallExpression::class.java)
                ?: return null
            val resolved = resolvePluginDeclaration(callExpression) ?: return null
            if (callExpression.valueArguments.singleOrNull()?.getArgumentExpression() == stringExpression) {
                return resolved
            }
        }

        val callExpression = PsiTreeUtil.getParentOfType(element, KtCallExpression::class.java, false)
            ?: return null
        return resolvePluginDeclaration(callExpression)
    }

    fun collectTargetGradleKtsFiles(project: com.intellij.openapi.project.Project): List<VirtualFile> {
        val files = mutableListOf<VirtualFile>()
        val seen = linkedSetOf<String>()

        for (baseDir in GradleProjectRoots.collectSearchRoots(project)) {
            VfsUtilCore.visitChildrenRecursively(baseDir, object : VirtualFileVisitor<Void>() {
                override fun visitFile(file: VirtualFile): Boolean {
                    if (file.isDirectory) {
                        if (file.name in SKIP_DIR_NAMES || file.name.startsWith('.')) {
                            return false
                        }
                        return true
                    }

                    if (file.name.endsWith(".gradle.kts") && seen.add(file.path)) {
                        files += file
                    }
                    return true
                }
            })
        }

        return files
    }

    fun collectFileCandidates(file: PsiFile, targetPluginId: String): FileCandidates {
        val document = file.viewProvider.document ?: return FileCandidates(emptyList())
        val lines = linkedSetOf<Int>()

        val callExpressions = PsiTreeUtil.collectElementsOfType(file, KtCallExpression::class.java)
            .distinctBy { it.textRange.startOffset to it.textRange.endOffset }

        for (callExpression in callExpressions) {
            val pluginDeclaration = resolvePluginDeclaration(callExpression) ?: continue
            if (pluginDeclaration.pluginId != targetPluginId) {
                continue
            }

            val startLine = document.getLineNumber(callExpression.textRange.startOffset)
            val safeEndOffset = (callExpression.textRange.endOffset - 1).coerceAtLeast(callExpression.textRange.startOffset)
            val endLine = document.getLineNumber(safeEndOffset)
            val extendedEndLine = extendPluginDeclarationEndLine(document, endLine)
            for (line in startLine..extendedEndLine) {
                val lineText = document.getText(
                    com.intellij.openapi.util.TextRange(
                        document.getLineStartOffset(line),
                        document.getLineEndOffset(line)
                    )
                )
                if (lineText.trim().startsWith("//")) {
                    continue
                }
                lines += line
            }
        }

        return FileCandidates(lines.toList().sorted())
    }

    fun rewriteText(originalText: String, lineNumbers: List<Int>): String {
        if (lineNumbers.isEmpty()) {
            return originalText
        }

        val lines = originalText.split('\n').toMutableList()
        for (lineNumber in lineNumbers.sortedDescending()) {
            if (lineNumber !in lines.indices) {
                continue
            }
            lines[lineNumber] = commentLine(lines[lineNumber])
        }
        return lines.joinToString("\n")
    }

    private fun extendPluginDeclarationEndLine(document: Document, endLine: Int): Int {
        var currentEndLine = endLine
        while (currentEndLine + 1 < document.lineCount) {
            val nextLine = document.getText(
                com.intellij.openapi.util.TextRange(
                    document.getLineStartOffset(currentEndLine + 1),
                    document.getLineEndOffset(currentEndLine + 1)
                )
            ).trim()

            if (nextLine.isBlank() || nextLine.startsWith("//")) {
                break
            }
            if (nextLine.startsWith("version ") || nextLine.startsWith("apply ")) {
                currentEndLine++
                continue
            }
            break
        }
        return currentEndLine
    }

    private fun commentLine(line: String): String {
        if (line.trim().startsWith("//")) {
            return line
        }

        val indentationLength = line.indexOfFirst { !it.isWhitespace() }.let { index ->
            if (index < 0) {
                line.length
            } else {
                index
            }
        }

        val indentation = line.substring(0, indentationLength)
        val content = line.substring(indentationLength)
        return indentation + "// " + content
    }

    private fun resolvePluginDeclaration(callExpression: KtCallExpression): TargetPlugin? {
        if (!isInsidePluginsBlock(callExpression)) {
            return null
        }

        val callee = callExpression.calleeExpression?.text ?: return null
        val stringExpression = callExpression.valueArguments.singleOrNull()?.getArgumentExpression() as? KtStringTemplateExpression
            ?: return null
        if (stringExpression.entries.any { it !is KtLiteralStringTemplateEntry }) {
            return null
        }
        val value = stringExpression.entries.joinToString(separator = "") { it.text }
        if (value.isBlank()) {
            return null
        }

        return when (callee) {
            "id" -> TargetPlugin(
                pluginId = value,
                displayName = "id(\"$value\")"
            )

            "kotlin" -> TargetPlugin(
                pluginId = "org.jetbrains.kotlin.$value",
                displayName = "kotlin(\"$value\")"
            )

            else -> null
        }
    }

    private fun isInsidePluginsBlock(element: PsiElement): Boolean {
        var current: PsiElement? = element
        while (current != null) {
            if (current is KtLambdaExpression) {
                val lambdaArgument = current.parent as? org.jetbrains.kotlin.psi.KtLambdaArgument
                val callExpression = lambdaArgument?.parent as? KtCallExpression
                if (callExpression?.calleeExpression?.text == "plugins") {
                    return true
                }
            }
            current = current.parent
        }
        return false
    }

    data class TargetPlugin(
        val pluginId: String,
        val displayName: String
    )

    data class FileCandidates(
        val lineNumbers: List<Int>
    )

    private val SKIP_DIR_NAMES = setOf(
        "build", "out", ".gradle", ".idea", "node_modules", "target", ".git"
    )
}
