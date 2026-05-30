package site.addzero.koog.agent

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.JBColor

class KoogAgentGenerateCodeIntention : PsiElementBaseIntentionAction(), IntentionAction {
    override fun getFamilyName(): String {
        return "ide-kit"
    }

    override fun getText(): String {
        return "(ide-kit) 根据注释生成代码"
    }

    override fun startInWriteAction(): Boolean {
        return false
    }

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val activeEditor = editor ?: return false
        return project.service<KoogAgentCommentGenerationService>()
            .canGenerateAt(activeEditor.document, activeEditor.caretModel.offset, activeEditor)
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val activeEditor = editor ?: return
        val service = project.service<KoogAgentCommentGenerationService>()
        if (activeEditor.selectionModel.hasSelection()) {
            service.generateAt(activeEditor.document, activeEditor.caretModel.offset, KoogAgentContextScope.SELECTION, activeEditor)
            return
        }

        chooseNearbyTargetAndGenerate(project, activeEditor, service)
    }

    private fun chooseNearbyTargetAndGenerate(
        project: Project,
        editor: Editor,
        service: KoogAgentCommentGenerationService,
    ) {
        PsiDocumentManager.getInstance(project).commitDocument(editor.document)
        val candidates = ReadAction.compute<List<KoogAgentSelectionCandidate>, Throwable> {
            KoogAgentSelectionCandidateCollector.collect(project, editor.document, editor.caretModel.offset)
        }
        if (candidates.isEmpty()) {
            service.generateAt(editor.document, editor.caretModel.offset, KoogAgentContextScope.NEARBY, editor)
            return
        }

        val items = listOf(NearbyTargetChoice.InsertOnly) +
            candidates.map { candidate -> NearbyTargetChoice.ReplaceCandidate(candidate) }
        val previewHighlighter = NearbyTargetPreviewHighlighter(editor)

        val popup = JBPopupFactory.getInstance()
            .createPopupChooserBuilder(items)
            .setTitle("(ide-kit) 选择就近作用范围")
            .setAdText("选择候选范围会直接替换；选择插入则只在注释下方追加。")
            .setItemSelectedCallback { choice ->
                previewHighlighter.show(choice?.candidateOrNull())
            }
            .setItemChosenCallback { choice ->
                previewHighlighter.clear()
                when (choice) {
                    NearbyTargetChoice.InsertOnly ->
                        service.generateAt(editor.document, editor.caretModel.offset, KoogAgentContextScope.NEARBY, editor)
                    is NearbyTargetChoice.ReplaceCandidate ->
                        generateReplacingCandidate(editor, service, choice.candidate)
                }
            }
            .createPopup()
        popup.addListener(object : JBPopupListener {
            override fun beforeShown(event: LightweightWindowEvent) {
                previewHighlighter.show(items.firstOrNull()?.candidateOrNull())
            }

            override fun onClosed(event: LightweightWindowEvent) {
                previewHighlighter.clear()
            }
        })
        popup.showInBestPositionFor(editor)
    }

    private fun generateReplacingCandidate(
        editor: Editor,
        service: KoogAgentCommentGenerationService,
        candidate: KoogAgentSelectionCandidate,
    ) {
        if (candidate.startOffset < 0 || candidate.endOffset > editor.document.textLength || candidate.startOffset >= candidate.endOffset) {
            return
        }
        editor.selectionModel.setSelection(candidate.startOffset, candidate.endOffset)
        service.generateAt(editor.document, editor.caretModel.offset, KoogAgentContextScope.SELECTION, editor)
    }

    private sealed class NearbyTargetChoice {
        object InsertOnly : NearbyTargetChoice() {
            override fun toString(): String {
                return "不替换，仅在注释下方插入"
            }
        }

        data class ReplaceCandidate(
            val candidate: KoogAgentSelectionCandidate,
        ) : NearbyTargetChoice() {
            override fun toString(): String {
                return "替换 ${candidate}"
            }
        }
    }

    private fun NearbyTargetChoice.candidateOrNull(): KoogAgentSelectionCandidate? {
        return when (this) {
            NearbyTargetChoice.InsertOnly -> null
            is NearbyTargetChoice.ReplaceCandidate -> candidate
        }
    }

    private class NearbyTargetPreviewHighlighter(
        private val editor: Editor,
    ) {
        private var highlighter: RangeHighlighter? = null

        fun show(candidate: KoogAgentSelectionCandidate?) {
            clear()
            candidate ?: return
            if (candidate.startOffset < 0 || candidate.endOffset > editor.document.textLength || candidate.startOffset >= candidate.endOffset) {
                return
            }

            highlighter = editor.markupModel.addRangeHighlighter(
                candidate.startOffset,
                candidate.endOffset,
                HighlighterLayer.SELECTION - 10,
                TextAttributes().apply {
                    backgroundColor = PREVIEW_BACKGROUND
                    effectColor = PREVIEW_BORDER
                },
                HighlighterTargetArea.EXACT_RANGE,
            )
            editor.scrollingModel.scrollToCaret(com.intellij.openapi.editor.ScrollType.MAKE_VISIBLE)
        }

        fun clear() {
            highlighter?.dispose()
            highlighter = null
        }

        companion object {
            private val PREVIEW_BACKGROUND = JBColor(0x335F9F, 0x335F9F)
            private val PREVIEW_BORDER = JBColor(0x6EA8FE, 0x6EA8FE)
        }
    }
}
