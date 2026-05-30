package site.addzero.koog.agent

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleManager
import java.util.concurrent.ConcurrentHashMap
import site.addzero.koog.agent.notify.KoogAgentNotifications
import site.addzero.koog.agent.settings.KoogAgentSettingsService

@Service(Service.Level.PROJECT)
class KoogAgentCommentGenerationService(
    private val project: Project,
) {
    private val log = Logger.getInstance(KoogAgentCommentGenerationService::class.java)
    private val inFlightKeys = ConcurrentHashMap.newKeySet<String>()

    fun canGenerateAt(
        document: Document,
        triggerOffset: Int,
    ): Boolean {
        val settings = KoogAgentSettingsService.getInstance().snapshot()
        if (!settings.enabled) {
            return false
        }
        return createGenerationRequest(document, triggerOffset) != null
    }

    fun generateAt(
        document: Document,
        triggerOffset: Int,
        contextScope: KoogAgentContextScope,
    ) {
        ProgressManager.getInstance().run(
            object : Task.Backgroundable(project, "ide-kit Generate Code", true) {
                override fun run(indicator: ProgressIndicator) {
                    generateForDocument(document, triggerOffset, contextScope)
                }
            },
        )
    }

    private fun generateForDocument(
        document: Document,
        triggerOffset: Int,
        contextScope: KoogAgentContextScope,
    ) {
        val request = ReadAction.compute<KoogAgentGenerationRequest?, Throwable> {
            createGenerationRequest(document, triggerOffset, contextScope)
        } ?: return

        if (!inFlightKeys.add(request.key)) {
            return
        }

        try {
            val settings = KoogAgentSettingsService.getInstance().snapshot()
            val code = KoogAgentCodeGenerator.generate(settings, request)
            if (code.isBlank()) {
                return
            }
            ApplicationManager.getApplication().invokeLater(
                {
                    applyGeneratedCode(document, request, code)
                },
                ModalityState.nonModal(),
            )
        } catch (error: Throwable) {
            log.warn("ide-kit AI code generation failed", error)
            KoogAgentNotifications.error(project, "ide-kit AI generation failed: ${error.message ?: error.javaClass.simpleName}")
        } finally {
            inFlightKeys.remove(request.key)
        }
    }

    private fun createGenerationRequest(
        document: Document,
        triggerOffset: Int,
        contextScope: KoogAgentContextScope = KoogAgentContextScope.NEARBY,
    ): KoogAgentGenerationRequest? {
        val virtualFile = FileDocumentManager.getInstance().getFile(document) ?: return null
        if (!isEligibleFile(virtualFile)) {
            return null
        }
        val fileIndex = ProjectFileIndex.getInstance(project)
        if (!fileIndex.isInContent(virtualFile) || fileIndex.isInLibrary(virtualFile)) {
            return null
        }
        if (document.textLength == 0) {
            return null
        }

        val offset = triggerOffset.coerceIn(0, document.textLength)
        val lineNumber = document.getLineNumber((offset - 1).coerceAtLeast(0))
        val candidates = sequenceOf(lineNumber, lineNumber - 1)
            .filter { line -> line in 0 until document.lineCount }
            .distinct()

        for (line in candidates) {
            val lineStart = document.getLineStartOffset(line)
            val lineEnd = document.getLineEndOffset(line)
            val lineText = document.getText(TextRange(lineStart, lineEnd))
            val instruction = KoogAgentCommentParser.extractInstruction(lineText) ?: continue
            val leadingIndent = lineText.takeWhile { it == ' ' || it == '\t' }
            val context = KoogAgentContextCollector.collect(document, line)
            val focusedContext = KoogAgentContextCollector.focusedContext(project, document, lineStart)
            val key = "${virtualFile.path}:$lineStart:${instruction.hashCode()}"
            return KoogAgentGenerationRequest(
                filePath = virtualFile.path,
                language = resolveLanguage(virtualFile),
                instruction = instruction,
                contextScope = contextScope,
                caretLineNumber = lineNumber + 1,
                commentLineNumber = line + 1,
                insertionLineNumber = line + 2,
                fullFileContent = when (contextScope) {
                    KoogAgentContextScope.NEARBY -> null
                    KoogAgentContextScope.FULL_FILE -> KoogAgentContextCollector.fullFileContent(document)
                },
                focusedContext = focusedContext,
                beforeContext = context.before,
                afterContext = context.after,
                insertOffset = lineEnd,
                leadingIndent = leadingIndent,
                key = key,
            )
        }
        return null
    }

    private fun applyGeneratedCode(
        document: Document,
        request: KoogAgentGenerationRequest,
        rawCode: String,
    ) {
        if (project.isDisposed || request.insertOffset > document.textLength) {
            return
        }
        val placement = KoogAgentGeneratedCodePlacementDecider.decide(request, rawCode)
        val targetIndent = when (placement) {
            KoogAgentGeneratedCodePlacement.INSERT_SNIPPET -> request.leadingIndent
            KoogAgentGeneratedCodePlacement.REPLACE_FOCUSED_CONTEXT ->
                request.focusedContext?.rawContent?.let(KoogAgentIndentSupport::leadingIndentOf).orEmpty()
        }
        val code = KoogAgentIndentSupport.alignToIndent(rawCode, targetIndent).trimEnd()
        if (code.isBlank()) {
            return
        }

        WriteCommandAction.writeCommandAction(project)
            .withName("ide-kit Generate Code")
            .run<RuntimeException> {
                val changedRange = when (placement) {
                    KoogAgentGeneratedCodePlacement.INSERT_SNIPPET -> insertSnippet(document, request, code)
                    KoogAgentGeneratedCodePlacement.REPLACE_FOCUSED_CONTEXT -> replaceFocusedContext(document, request, code)
                } ?: return@run
                val psiDocumentManager = PsiDocumentManager.getInstance(project)
                psiDocumentManager.commitDocument(document)
                val psiFile = psiDocumentManager.getPsiFile(document) ?: return@run
                if (changedRange.startOffset < changedRange.endOffset) {
                    CodeStyleManager.getInstance(project).reformatText(
                        psiFile,
                        changedRange.startOffset,
                        changedRange.endOffset,
                    )
                }
            }
    }

    private fun insertSnippet(
        document: Document,
        request: KoogAgentGenerationRequest,
        code: String,
    ): TextRange {
        val insertion = buildString {
            append('\n')
            append(code)
            append('\n')
        }
        document.insertString(request.insertOffset, insertion)
        return TextRange(
            (request.insertOffset + 1).coerceAtMost(document.textLength),
            (request.insertOffset + insertion.length).coerceAtMost(document.textLength),
        )
    }

    private fun replaceFocusedContext(
        document: Document,
        request: KoogAgentGenerationRequest,
        code: String,
    ): TextRange? {
        val focusedContext = request.focusedContext ?: return null
        if (focusedContext.startOffset < 0 || focusedContext.endOffset > document.textLength) {
            return null
        }
        if (focusedContext.startOffset >= focusedContext.endOffset) {
            return null
        }
        document.replaceString(focusedContext.startOffset, focusedContext.endOffset, code)
        return TextRange(
            focusedContext.startOffset,
            (focusedContext.startOffset + code.length).coerceAtMost(document.textLength),
        )
    }

    private fun isEligibleFile(file: VirtualFile): Boolean {
        if (file.isDirectory || !file.isInLocalFileSystem) {
            return false
        }
        return file.extension?.lowercase() in SUPPORTED_EXTENSIONS
    }

    private fun resolveLanguage(file: VirtualFile): String {
        return when (file.extension?.lowercase()) {
            "kt", "kts" -> "Kotlin"
            "java" -> "Java"
            "groovy" -> "Groovy"
            "scala" -> "Scala"
            "js", "jsx" -> "JavaScript"
            "ts", "tsx" -> "TypeScript"
            "py" -> "Python"
            "rs" -> "Rust"
            "go" -> "Go"
            "xml" -> "XML"
            "html" -> "HTML"
            "css" -> "CSS"
            "sql" -> "SQL"
            else -> file.extension?.uppercase() ?: "source code"
        }
    }

    private companion object {
        private val SUPPORTED_EXTENSIONS = setOf(
            "kt",
            "kts",
            "java",
            "groovy",
            "scala",
            "js",
            "jsx",
            "ts",
            "tsx",
            "py",
            "rs",
            "go",
            "xml",
            "html",
            "css",
            "sql",
        )
    }
}
